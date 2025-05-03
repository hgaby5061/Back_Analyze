package com.service.web.app.models.service;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.service.web.app.models.entity.Discurs;
import com.service.web.app.models.entity.Edge;
import com.service.web.app.models.entity.GraphResult;
import com.service.web.app.models.entity.Node;

import edu.stanford.nlp.ie.util.RelationTriple;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreAnnotations.KBPTriplesAnnotation;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.naturalli.NaturalLogicAnnotations;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.SemanticGraphCoreAnnotations;
import edu.stanford.nlp.semgraph.SemanticGraphEdge;
import edu.stanford.nlp.trees.GrammaticalRelation;
import edu.stanford.nlp.util.CoreMap;

@Service
class KnowledgeGraphExtractor implements Extractor {

	// --- Instancia Singleton de CoreNLP y almacenamiento del grafo ---
	private final StanfordCoreNLP pipeline;
	// Usamos ConcurrentHashMap para seguridad en hilos (buena práctica en Spring)
	// Mapa para nodos: Clave = ID del nodo (lema normalizado), Valor = Objeto Node
	private final Map<String, Node> nodes = new ConcurrentHashMap<>();
	// Set para aristas: Garantiza unicidad basado en Edge.equals/hashCode
	private final Set<Edge> edges = Collections.newSetFromMap(new ConcurrentHashMap<>());

	// Mapeo para relaciones KBP (mantenido)
	private static final Map<String, String> KBP_RELATION_MAP = Map.ofEntries(
			Map.entry("org:city_of_headquarters", "sede en"),
			Map.entry("per:title", "tiene título"),
			Map.entry("org:country_of_headquarters", "país sede"),
			Map.entry("per:employee_or_member_of", "miembro de"),
			Map.entry("per:origin", "origen"),
			Map.entry("org:alternate_names", "alias"),
			Map.entry("per:alternate_names", "alias"),
			Map.entry("per:cities_of_residence", "reside en"),
			Map.entry("per:countries_of_residence", "reside en país"),
			Map.entry("org:subsidiaries", "subsidiaria"),
			Map.entry("org:parents", "matriz de")
	// ... añadir más mapeos según se descubran
	);

	public KnowledgeGraphExtractor() {
		// --- Configuración de CoreNLP ---
		Properties props = new Properties();

		// Cargar archivo Spanish.properties desde el classpath
		try (InputStream input = KnowledgeGraphExtractor.class.getClassLoader()
				.getResourceAsStream("StanfordCoreNLP-spanish.properties")) {
			if (input == null) {
				throw new IOException("No se encontró el archivo Spanish.properties");
			}
			props.load(input); // Carga las propiedades en el objeto props
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		// 1. DEFINIR ANOTADORES: Lista explícita de los que usaremos.
		// Incluye los de las props por defecto + coref + openie.
		// OMITIMOS 'parse' para MEJOR RENDIMIENTO, ya que nos basamos en 'depparse'.
		// Incluimos 'mwt' (Multi-Word Tokenizer), importante para español.
		props.setProperty("annotators", "tokenize,ssplit,mwt,pos,lemma,depparse,ner,coref,kbp,natlog,openie");

		// 2. CONFIGURACIÓN GENERAL Y DE IDIOMA (Algunas pueden estar en el archivo
		// .properties, pero definirlas aquí asegura consistencia)
		// props.setProperty("tokenize.language", "es");
		// props.setProperty("ssplit.boundaryTokenRegex", "[.]|[!?]+|[\\n\\r]+"); //
		// Separadores de oración
		// props.setProperty("ssplit.newlineIsSentenceBreak", "always"); // Tratar
		// saltos de línea como fin de oración
		// (útil en discursos)

		// 3. CONFIGURACIÓN DE ANOTADORES ESPECÍFICOS (Modelos)
		// Usar modelos específicos de español y sobrescribir defaults si es necesario

		// MWT (Multi-Word Tokenizer) - Usar el default de español
		// props.setProperty("mwt.mappingFile",
		// "edu/stanford/nlp/models/mwt/spanish/spanish-mwt.tsv");

		// POS Tagging - Usar el default UD (Universal Dependencies) de español
		// props.setProperty("pos.model",
		// "edu/stanford/nlp/models/pos-tagger/spanish-ud.tagger");

		// Lemmatization - Se basa en POS, no requiere modelo explícito aquí normalmente

		// NER (Named Entity Recognition) - Usar el default Ancora de español
		// props.setProperty("ner.language", "es"); // Redundante si
		// tokenize.language=es, pero explícito
		// props.setProperty("ner.model",
		// "edu/stanford/nlp/models/ner/spanish.ancora.distsim.s512.crf.ser.gz");
		// props.setProperty("ner.applyNumericClassifiers", "true"); // Reconocer
		// números
		// props.setProperty("ner.useSUTime", "true"); // Reconocer fechas/tiempos con
		// SUTime
		// Opcional: Cargar gazetteers adicionales si tienes listas específicas de tu
		// dominio
		// props.setProperty("ner.fine.regexner.mapping",
		// "path/to/your/gazetteer_mapping.tag");
		props.setProperty("ner.fine.regexner.ignorecase", "true");

		// Dependency Parsing - ***SOBRESCRIBIR*** el default por el modelo PyTorch más
		// nuevo
		// props.setProperty("depparse.language", "spanish");
		// props.setProperty("depparse.model",
		// "edu/stanford/nlp/models/parser/nndep/UD_Spanish-AnCora_pytorch.pt");

		// Coreference Resolution - Configurar para español
		// props.setProperty("coref.language", "es");
		// props.setProperty("coref.algorithm", "neural"); // El más avanzado (puede
		// requerir descarga de modelo adicional
		// la primera vez)
		// Podría ser necesario especificar el modelo si no se descarga automáticamente:
		// props.setProperty("coref.statistical.model",
		// "edu/stanford/nlp/models/coref/statistical/es_coref_model.ser.gz"); // Ajusta
		// la ruta si es necesario

		// KBP (Knowledge Base Population) - Usar configuración basada en reglas por
		// defecto
		// props.setProperty("kbp.language", "es");
		// props.setProperty("kbp.model", "none"); // Asegura que use solo reglas
		// props.setProperty("kbp.semgrex",
		// "edu/stanford/nlp/models/kbp/spanish/semgrex");
		// props.setProperty("kbp.tokensregex",
		// "edu/stanford/nlp/models/kbp/spanish/tokensregex");
		// props.setProperty("entitylink.wikidict",
		// "edu/stanford/nlp/models/kbp/spanish/wikidict_spanish.tsv"); // Para enlazar
		// a Wikipedia si se usa entitylink

		// OpenIE (Open Information Extraction) - Configurar
		props.setProperty("openie.resolve_coref", "true"); // Intentar usar coreferencia para mejores triples
		props.setProperty("openie.ignore_affinity", "false"); // Usar afinidad para filtrar
		props.setProperty("openie.affinity_probability_cap", "0.9"); // Umbral de confianza
		// props.setProperty("openie.splitter.model",
		// "edu/stanford/nlp/models/naturalli/clause_splitter_ud_ancora.ser.gz"); //
		// Separador de cláusulas para
		// español UD

		// Inicializar la pipeline (puede tardar un poco la primera vez)
		System.out.println("Inicializando pipeline de CoreNLP con configuración personalizada...");
		this.pipeline = new StanfordCoreNLP(props);
		System.out.println("Pipeline lista.");
	}

	/**
	 * Procesa el texto y extrae el grafo de conocimiento.
	 * Es synchronized para seguridad si se llama desde múltiples hilos en Spring.
	 * Limpia el estado interno (nodos/aristas) antes de cada extracción.
	 * 
	 * @param text Texto en español a analizar.
	 * @return GraphResult con listas de nodos y aristas.
	 */
	@Override
	public synchronized GraphResult extractTriplesFromDocuments(List<Discurs> documents) {
		// 1. Limpiar estado de la extracción anterior
		nodes.clear();
		edges.clear();
		System.out.printf("Iniciando extracción para %d documentos...\n", documents.size());

		// 2. Iterar sobre cada documento
		for (Discurs doc : documents) {
			String docId = doc.getId();
			System.out.printf("Procesando Documento ID: %s\n", doc.getId()); // Asume que Document tiene un id
			String text = doc.getText(); // Asume que Document tiene el contenido

			if (text == null || text.isBlank()) {
				System.out.printf("  Documento ID: %s está vacío, saltando.\n", doc.getId());
				continue;
			}

			// 3. Dividir el texto del documento en chunks
			List<String> chunks = splitTextIntoChunks(text);
			System.out.printf("  Dividido en %d chunks.\n", chunks.size());

			// 4. Procesar cada chunk y acumular resultados
			for (int i = 0; i < chunks.size(); i++) {
				System.out.printf("    Procesando chunk %d/%d...\n", i + 1, chunks.size());
				// Llama al método interno que realmente ejecuta CoreNLP
				processTextChunk(chunks.get(i), docId);
			}
			System.out.printf("  Documento ID: %s procesado.\n", doc.getId());
		}

		// 5. Post-procesamiento final (sobre el grafo acumulado)
		System.out.println("Calculando importancia de nodos...");
		calculateNodeImportance();

		// 6. Devolver el grafo acumulado de todos los documentos
		System.out.println("Extracción completada.");
		return new GraphResult(new ArrayList<>(nodes.values()), new ArrayList<>(edges));
	}

	private List<String> splitTextIntoChunks(String text) {
		List<String> chunks = new ArrayList<>();
		// Regex mejorada para manejar espacios después de ., !? y saltos de línea
		String[] sentences = text.split("(?<=[.!?])\\s+|[\n\r]+");
		StringBuilder currentChunk = new StringBuilder();
		// Puedes hacer configurable el tamaño del chunk (ej. 10 oraciones)
		final int CHUNK_SIZE_SENTENCES = 10;
		int sentenceCount = 0;

		for (String sentence : sentences) {
			String trimmedSentence = sentence.trim();
			if (!trimmedSentence.isEmpty()) {
				currentChunk.append(trimmedSentence).append(" "); // Añade espacio entre oraciones
				sentenceCount++;
				if (sentenceCount == CHUNK_SIZE_SENTENCES) {
					chunks.add(currentChunk.toString().trim());
					currentChunk.setLength(0); // Reinicia el StringBuilder
					sentenceCount = 0;
				}
			}
		}

		// Añadir el último chunk si contiene algo
		if (currentChunk.length() > 0) {
			chunks.add(currentChunk.toString().trim());
		}
		return chunks;
	}

	private void processTextChunk(String text, String docId) {
		// 2. Anotar el documento
		Annotation document = new Annotation(text);
		try {
			pipeline.annotate(document);
			// System.out.println(document.get(CoreAnnotations.MentionsAnnotation.class));
		} catch (Exception e) {
			System.err.println("Error durante la anotación de CoreNLP: " + e.getMessage());
			// Considera lanzar una excepción personalizada o devolver un grafo vacío
			return;
		}

		// Opcional: Obtener cadenas de correferencia para análisis más profundo si es
		// necesario
		// Map<Integer, CorefChain> corefChains =
		// document.get(CorefCoreAnnotations.CorefChainAnnotation.class);

		// 3. Iterar sobre las oraciones y extraer información
		List<CoreMap> sentences = document.get(CoreAnnotations.SentencesAnnotation.class);
		if (sentences == null) {
			System.err.println("No se encontraron oraciones en el texto.");
			return;
		}

		for (CoreMap sentence : sentences) {

			// --- Estrategia de Extracción Combinada ---

			// A. Extracción con KBP (Basada en reglas por defecto)
			// Intenta extraer relaciones si las reglas KBP coinciden
			// La salida puede variar, a menudo son triples específicos o anotaciones en
			// tokens
			// Nota: Esta parte es experimental, KBP basado en reglas puede no dar muchos
			// resultados generales

			List<RelationTriple> kbpRelations = sentence.get(KBPTriplesAnnotation.class); // Esta clave puede variar
			if (kbpRelations != null && !kbpRelations.isEmpty()) {
				System.out.println("KBP Relations found: " + kbpRelations.size()); // Debug
				System.out.println(kbpRelations);
				for (RelationTriple triple : kbpRelations) {
					processTriple(triple.subject, triple.relationLemmaGloss(), triple.object, "KBP", docId);
				}
			}

			// B. Extracción con OpenIE
			Collection<RelationTriple> openieTriples = sentence
					.get(NaturalLogicAnnotations.RelationTriplesAnnotation.class);
			if (openieTriples != null) {
				for (RelationTriple triple : openieTriples) {
					System.out.println(triple);
					// Procesa el triple de OpenIE
					processTriple(triple.subject, getSpanLemma(triple.relation), triple.object, "OpenIE", docId);
				}
			}

			// C. Extracción basada en Dependencias (Complementaria y Robusta)
			SemanticGraph dependencies = sentence
					.get(SemanticGraphCoreAnnotations.EnhancedPlusPlusDependenciesAnnotation.class);
			if (dependencies != null) {
				extractRelationsFromDependencies(dependencies, sentence, docId);
			} else {
				System.err.println("Advertencia: No se encontró grafo de dependencias para una oración.");
			}

			// D. Identificar Conceptos Relevantes (Nodos no NER)
			extractConcepts(sentence, docId);
		}

	}

	/**
	 * Normaliza texto para usarlo como ID. Minúsculas, trim, espacios simples.
	 */
	private String normalizeForId(String text) {
		if (text == null || text.isBlank()) {
			return null;
		}
		String normalized = text.toLowerCase().trim().replaceAll("\\s+", " ");
		// Opcional: quitar acentos
		// normalized = Normalizer.normalize(normalized,
		// Normalizer.Form.NFD).replaceAll("\\p{M}", "");
		return normalized;
	}

	/**
	 * Obtiene el texto original concatenado de un span de CoreLabel.
	 */
	private String getSpanOriginalText(List<CoreLabel> span) {
		if (span == null || span.isEmpty())
			return null;
		return span.stream().map(CoreLabel::originalText).collect(Collectors.joining(" ")).trim();
	}

	// --- Métodos de Procesamiento y Extracción ---

	/**
	 * Procesa un triple (sujeto, relación, objeto) genérico y lo añade al grafo.
	 * Usa los spans (listas de CoreLabel) para obtener texto, lemas y tipos.
	 */
	private void processTriple(List<CoreLabel> subjectSpan, String relationLemma, List<CoreLabel> objectSpan,
			String sourceMethod, String docId) {
		if (subjectSpan == null || subjectSpan.isEmpty() || objectSpan == null || objectSpan.isEmpty()
				|| relationLemma == null || relationLemma.isBlank()) {
			return; // Ignorar triples incompletos
		}

		/*
		 * String subjText =
		 * subjectSpan.stream().map(CoreLabel::originalText).reduce("", (a, b) -> a +
		 * " " + b).trim();
		 * String objText = objectSpan.stream().map(CoreLabel::originalText).reduce("",
		 * (a, b) -> a + " " + b).trim();
		 * 
		 * String subjLemma = getSpanLemma(subjectSpan); // Usar lema como ID
		 * String objLemma = getSpanLemma(objectSpan);
		 */ // Usar lema como ID

		// --- Determinar ID, Nombre y Tipo para Sujeto y Objeto ---
		NodeInfo subjInfo = getNodeInfoFromSpan(subjectSpan); // Ya no necesita docId aquí
		NodeInfo objInfo = getNodeInfoFromSpan(objectSpan);

		if (subjInfo == null || objInfo == null || subjInfo.id.equals(objInfo.id)) {
			return; // Evitar relaciones vacías o auto-referencias simples
		}

		// Determinar tipo (priorizando NER sobre Concepto)
		// String subjType = getNodeTypeFromTokenList(subjectSpan, "Concepto");
		// String objType = getNodeTypeFromTokenList(objectSpan, "Concepto");

		String finalRelation = relationLemma.toLowerCase().trim();
		if ("KBP".equals(sourceMethod)) {
			finalRelation = KBP_RELATION_MAP.getOrDefault(finalRelation, finalRelation);
		} else {
			if (finalRelation.equals("ser") || finalRelation.equals("estar")) {
				finalRelation = "es";
			}
			// Podrías añadir más limpieza aquí si OpenIE da relaciones raras
		}

		// --- Añadir al grafo ---
		// Explicación: Nodos se añaden/actualizan primero. Si ya existen, solo se
		// actualiza
		// frecuencia y se añade el docId. La arista solo se añade si no existe ya
		// exactamente igual (mismo origen, destino y relación).
		addNode(subjInfo.id, subjInfo.name, subjInfo.type, docId);
		addNode(objInfo.id, objInfo.name, objInfo.type, docId);
		addEdge(subjInfo.id, objInfo.id, finalRelation); // System.out.printf("[%s] Added Edge: (%s)-[%s]->(%s)\n",
															// sourceMethod,
		// subjLemma, relationLemma.toLowerCase().trim(), objLemma); // Debug
	}

	/**
	 * Extrae ID, nombre y tipo de un span de CoreLabel.
	 * **CLAVE:** Maneja correctamente entidades NER multi-palabra.
	 */
	private NodeInfo getNodeInfoFromSpan(List<CoreLabel> span) {
		if (span == null || span.isEmpty())
			return null;

		String fullOriginalText = getSpanOriginalText(span); // Texto completo original
		String firstNer = span.get(0).ner();
		String nodeType = "Concepto"; // Tipo por defecto
		String nodeId = null;

		// 1. Comprobar si es una entidad NER multi-palabra consistente
		boolean isConsistentNer = false;
		if (firstNer != null && !firstNer.equals("O")) {
			isConsistentNer = true;
			for (int i = 1; i < span.size(); i++) {
				if (!firstNer.equals(span.get(i).ner())) {
					isConsistentNer = false;
					break;
				}
			}
			if (isConsistentNer) {
				nodeType = firstNer; // Usar el tipo NER detectado (sin mapeo)
			}
		}

		// 2. Generar ID y determinar Tipo final
		if (isConsistentNer) {
			// Es una entidad NER completa (ej: "16 de noviembre de 1996", "FIDEL CASTRO
			// RUZ")
			nodeId = normalizeForId(fullOriginalText); // ID basado en el texto completo normalizado
			// nodeType ya está asignado arriba
		} else {
			// No es NER consistente o es un concepto/frase mixta.
			// Usar lema del último token como ID (o buscar head word si se quiere más
			// precisión)
			CoreLabel lastToken = span.get(span.size() - 1);
			nodeId = normalizeForId(getLemma(lastToken));
			// Si el último token tenía un NER individual, usarlo, si no, es Concepto.
			String lastTokenNer = lastToken.ner();
			if (lastTokenNer != null && !lastTokenNer.equals("O")) {
				nodeType = lastTokenNer; // Usar NER del último token si existe
			} else {
				nodeType = "Concepto"; // Fallback a Concepto
			}
		}

		if (nodeId == null || nodeId.isBlank()) {
			// Si no se pudo generar ID (ej. solo signos de puntuación), intentar con el
			// texto completo
			nodeId = normalizeForId(fullOriginalText);
			if (nodeId == null || nodeId.isBlank())
				return null; // Aún no se pudo
			nodeType = "Concepto"; // Asignar tipo genérico si usamos texto completo como fallback de ID
		}

		return new NodeInfo(nodeId, fullOriginalText, nodeType); // Siempre devolver el texto original completo como
																	// nombre
	}

	/**
	 * Extracción complementaria usando patrones sobre el grafo de dependencias.
	 */
	private void extractRelationsFromDependencies(SemanticGraph dependencies, CoreMap sentence, String docId) {
		// Ejemplo: Patrón Sujeto-Verbo-Objeto (nsubj -> VERB -> obj/obl)
		for (IndexedWord verb : dependencies.getAllNodesByPartOfSpeechPattern("VERB")) {
			String verbLemma = getLemma(verb);
			if (verbLemma == null || verbLemma.isBlank())
				continue;

			String relation = verbLemma;
			if (relation.equals("ser") || relation.equals("estar"))
				relation = "es";
			else if (relation.equals("haber"))
				relation = "tiene";

			List<IndexedWord> subjects = new ArrayList<>();
			List<IndexedWord> objects = new ArrayList<>();

			// Buscar sujetos (nsubj)
			dependencies.getParentsWithReln(verb, "nsubj").forEach(subjects::add);
			// Considerar también sujetos pasivos (nsubj:pass) si es relevante
			dependencies.getParentsWithReln(verb, "nsubj:pass").forEach(subjects::add);

			// Buscar objetos (obj, iobj) y complementos oblicuos (obl) que actúan como
			// objetos semánticos
			dependencies.getChildrenWithReln(verb, GrammaticalRelation.valueOf("obj")).forEach(objects::add); // Objeto
																												// directo
			dependencies.getChildrenWithReln(verb, GrammaticalRelation.valueOf("iobj")).forEach(objects::add); // Objeto
																												// indirecto
			dependencies.getChildrenWithReln(verb, GrammaticalRelation.valueOf("obl")).forEach(objects::add); // Oblicuo

			// Crear relaciones S-V-O
			for (IndexedWord subj : subjects) {
				for (IndexedWord obj : objects) {
					NodeInfo subjInfo = getNodeInfoFromWord(subj); // No necesita docId aquí
					NodeInfo objInfo = getNodeInfoFromWord(obj);

					if (subjInfo != null && objInfo != null && !subjInfo.id.equals(objInfo.id)) {
						addNode(subjInfo.id, subjInfo.name, subjInfo.type, docId); // Pasar docId aquí
						addNode(objInfo.id, objInfo.name, objInfo.type, docId); // Pasar docId aquí
						addEdge(subjInfo.id, objInfo.id, relation);
					}
				}
			}
		}
		// Ejemplo: Patrón Nominal Modificador (nmod + case) - "presidente de México"
		for (SemanticGraphEdge edge : dependencies.edgeListSorted()) {
			GrammaticalRelation rel = edge.getRelation();

			if (rel.getShortName().equals("nmod")) {
				IndexedWord gov = edge.getGovernor(); // Ej: presidente
				IndexedWord dep = edge.getDependent(); // Ej: México

				NodeInfo govInfo = getNodeInfoFromWord(gov);
				NodeInfo depInfo = getNodeInfoFromWord(dep);

				if (govInfo == null || depInfo == null || govInfo.id.equals(depInfo.id))
					continue;

				String shortRelName = rel.getShortName();
				String relationLabel = null; // Etiqueta legible final

				if (shortRelName.equals("nmod")) { // "presidente de México"
					relationLabel = dependencies.getChildrenWithReln(dep, GrammaticalRelation.valueOf("case"))
							.stream().map(this::getLemma).filter(l -> l != null && !l.isBlank()).findFirst()
							.orElse("relacionado con"); // Usar preposición o fallback genérico
				} else if (shortRelName.equals("appos")) { // "AMLO, el presidente"
					relationLabel = "es (descripción)";
				} else if (shortRelName.equals("amod")) { // "distribución desigual"
					// Crear nodo para el adjetivo y relación "tiene característica"
					addNode(govInfo.id, govInfo.name, govInfo.type, docId);
					addNode(depInfo.id, depInfo.name, depInfo.type, docId); // Añadir adjetivo como nodo
					addEdge(govInfo.id, depInfo.id, "tiene característica");
					continue; // Ya se añadió la arista para amod
				}
				// Añadir más patrones aquí si se desea (ej. acl para cláusulas relativas)
				// else if (shortRelName.equals("acl")) { ... }

				// Si se encontró una etiqueta legible para nmod o appos, añadir nodos y arista
				if (relationLabel != null) {
					addNode(govInfo.id, govInfo.name, govInfo.type, docId);
					addNode(depInfo.id, depInfo.name, depInfo.type, docId);
					// La dirección semántica suele ser Gobernador -> Dependiente para nmod y appos
					addEdge(govInfo.id, depInfo.id, relationLabel);
				}
			}

			/*
			 * // Buscar la preposición ('case') asociada al modificador
			 * String preposition = dependencies.getChildrenWithReln(modifier,
			 * GrammaticalRelation.valueOf("case"))
			 * .stream()
			 * .map(this::getLemma)
			 * .findFirst()
			 * .orElse(null); // Ej: "de"
			 * 
			 * if (preposition != null && !preposition.isBlank()) {
			 * String headLemma = getLemma(headNoun);
			 * String modLemma = getLemma(modifier);
			 * 
			 * if (headLemma.equals(modLemma))
			 * continue;
			 * 
			 * String headType = getNodeTypeFromIndexedWord(headNoun, "Concepto");
			 * String modType = getNodeTypeFromIndexedWord(modifier, "Concepto");
			 * String headText = headNoun.originalText();
			 * String modText = modifier.originalText();
			 * 
			 * addNode(headLemma, headText, headType);
			 * addNode(modLemma, modText, modType);
			 * // Relación: head --(preposition)--> modifier (o al revés, según semántica
			 * // deseada)
			 * // Usualmente la dirección nmod es MODIFIER -> HEAD, la relación semántica es
			 * // HEAD -(prep)-> MODIFIER
			 * addEdge(headLemma, modLemma, preposition);
			 */
			// System.out.printf("[DepParse NMOD] Added Edge: (%s)-[%s]->(%s)\n", headLemma,
			// preposition, modLemma); // Debug

			// Ejemplo: Aposición (appos) - "AMLO, el presidente"
			/*
			 * if (rel.equals("appos")) {
			 * IndexedWord headEntity = edge.getGovernor();
			 * IndexedWord apposEntity = edge.getDependent();
			 * String headLemma = getLemma(headEntity);
			 * String apposLemma = getLemma(apposEntity);
			 * 
			 * if (headLemma.equals(apposLemma))
			 * continue;
			 * 
			 * String headType = getNodeTypeFromIndexedWord(headEntity, "Concepto");
			 * String apposType = getNodeTypeFromIndexedWord(apposEntity, "Concepto");
			 * String headText = headEntity.originalText();
			 * String apposText = apposEntity.originalText();
			 * 
			 * addNode(headLemma, headText, headType);
			 * addNode(apposLemma, apposText, apposType);
			 * // Añadir una relación de equivalencia o atributo
			 * addEdge(headLemma, apposLemma, "es"); // O "tiene_aposición", "alias", etc.
			 * // System.out.printf("[DepParse APPOS] Added Edge: (%s)-[%s]->(%s)\n",
			 * // headLemma, "es", apposLemma); // Debug
			 */ // }

			// Añadir más patrones aquí (amod para adjetivos, acl para cláusulas relativas,
			// etc.)
		}
	}

	/**
	 * Extrae ID, nombre y tipo de un IndexedWord (usado en dependencias).
	 */
	private NodeInfo getNodeInfoFromWord(IndexedWord word) {
		if (word == null)
			return null;

		String originalText = word.originalText();
		String lemma = getLemma(word);
		String ner = word.ner();
		String nodeType = "Concepto"; // Default

		if (ner != null && !ner.equals("O")) {
			nodeType = ner; // Usar NER tag original
		}

		// ID basado en lema para palabras individuales
		String nodeId = normalizeForId(lemma);
		if (nodeId == null || nodeId.isBlank()) {
			nodeId = normalizeForId(originalText); // Fallback a texto original si no hay lema
			if (nodeId == null || nodeId.isBlank())
				return null;
		}

		return new NodeInfo(nodeId, originalText, nodeType);
	}

	/**
	 * Identifica sustantivos, adjetivos y verbos relevantes no capturados por NER
	 * como Conceptos.
	 */
	private void extractConcepts(CoreMap sentence, String docId) {
		for (CoreLabel token : sentence.get(CoreAnnotations.TokensAnnotation.class)) {
			String ner = token.ner();
			String pos = token.tag(); // Part-of-Speech tag (UD tags)

			// Incluir Sustantivos (NOUN), Nombres Propios (PROPN), Adjetivos (ADJ), Verbos
			// (VERB)
			// Excluir auxiliares (AUX), determinantes (DET), etc. Ajusta según necesidad.
			boolean isRelevantPOS = pos != null && (pos.startsWith("NOUN") || pos.startsWith("PROPN")
			/* || pos.startsWith("ADJ") */);

			// Si no es una entidad NER reconocida Y es un tipo de palabra relevante
			if ((ner == null || ner.equals("O")) && isRelevantPOS) {
				String conceptLemma = getLemma(token);
				String conceptText = token.word();
				String conceptId = normalizeForId(conceptLemma);

				// Añadir como nodo tipo Concepto solo si no existe ya como entidad NER
				if (conceptId != null && !conceptLemma.isBlank()) {
					addNode(conceptId, conceptText, "Concepto", docId);
				}
			}
		}
	}

	// --- Métodos Auxiliares (Gestión de Nodos/Aristas, Normalización, Tipos) ---

	/**
	 * Añade o actualiza un nodo en el mapa 'nodes'.
	 * Usa el ID (lema) para la unicidad. Incrementa frecuencia.
	 * Prioriza tipos NER sobre 'Concepto'. Actualiza el nombre si el nuevo es más
	 * largo.
	 */
	private void addNode(String id, String nodeName, String nodeType, String docId) {
		if (id == null || id.isBlank())
			return;

		nodes.compute(id, (key, existingNode) -> {
			if (existingNode == null) {
				return new Node(key, nodeName, nodeType, docId);
			} else {
				existingNode.incrementFrequency();
				existingNode.addDocumentId(docId);
				// Lógica de actualización de tipo: Priorizar NER sobre Concepto
				if (!isConceptType(nodeType) && isConceptType(existingNode.getType())) {
					existingNode.setType(nodeType); // Actualiza de Concepto a NER específico
				}
				// Actualizar nombre si el nuevo es más descriptivo (ej. más largo)
				if (nodeName != null && nodeName.length() > existingNode.getName().length()
						|| existingNode.getName().equals(existingNode.getId())) {
					if (nodeName != null
							&& (!nodeName.equals(id) || existingNode.getName().equals(existingNode.getId()))) {
						existingNode.setName(nodeName);
					}
				}
				return existingNode;
			}
		});
	}

	/**
	 * Añade una arista al Set 'edges'. Evita duplicados y auto-referencias.
	 * Normaliza la relación a minúsculas.
	 */
	private void addEdge(String sourceId, String targetId, String relationship) {
		if (sourceId == null || targetId == null || relationship == null ||
				sourceId.isBlank() || targetId.isBlank() || relationship.isBlank() ||
				sourceId.equals(targetId)) { // Evitar auto-referencias o vacíos
			return;
		}
		// El Set<Edge> se encarga de la unicidad basado en equals/hashCode de Edge
		edges.add(new Edge(sourceId, targetId, relationship.toLowerCase().trim()));
	}

	// Obtiene el lema de un token (o la palabra si no hay lema)
	private String getLemma(CoreLabel token) {
		if (token == null)
			return null;
		String lemma = token.lemma();
		String wordLower = token.word().toLowerCase().trim();
		if (lemma != null && !lemma.isBlank() && !lemma.equals(wordLower)) {
			return token.word().toLowerCase().trim();
		} else {
			return wordLower;
		}
	}

	private String getLemma(IndexedWord iWord) {
		if (iWord == null)
			return null;
		String lemma = iWord.lemma();
		String wordLower = iWord.word().toLowerCase().trim();
		if (lemma != null && !lemma.isBlank() && !lemma.equals(wordLower)) {
			return lemma.toLowerCase().trim();
		} else {
			return wordLower;
		}
	}

	// Obtiene el lema principal de un span (heurística: lema del último token)
	private String getSpanLemma(List<CoreLabel> span) {
		if (span == null || span.isEmpty())
			return null;
		// Podría mejorarse buscando el 'head' del span en el grafo de dependencias
		return getLemma(span.get(span.size() - 1));
	}

	// Determina el tipo de nodo (NER o default) para un span de tokens
	private String getNodeTypeFromTokenList(List<CoreLabel> tokens, String defaultType) {
		if (tokens == null || tokens.isEmpty())
			return defaultType;
		// Buscar la primera etiqueta NER no "O" en el span
		for (CoreLabel token : tokens) {
			String ner = token.ner();
			if (ner != null && !ner.equals("O")) {
				System.out.println(token);
				// Mapear tipos NER si es necesario (ej. ORG -> ORGANIZATION)
				return mapNerTag(ner);
			}
		}
		return defaultType;
	}

	// Determina el tipo de nodo (NER o default) para un IndexedWord
	private String getNodeTypeFromIndexedWord(IndexedWord iWord, String defaultType) {
		if (iWord == null)
			return defaultType;
		String ner = iWord.ner();
		if (ner != null && !ner.equals("O")) {
			return mapNerTag(ner);
		}
		return defaultType;
	}

	// Mapea tags NER de CoreNLP a los tipos usados en el grafo (opcional, si
	// difieren)
	private String mapNerTag(String nerTag) {
		// Ejemplo: si CoreNLP usa 'ORG', y tú quieres 'ORGANIZATION'
		// if ("ORG".equals(nerTag)) return TYPE_ENTITY_ORGANIZATION;
		// if ("LOC".equals(nerTag)) return TYPE_ENTITY_LOCATION;
		// if ("PER".equals(nerTag)) return TYPE_ENTITY_PERSON;
		// Por ahora, asumimos que los tipos coinciden con las constantes definidas
		return nerTag;
	}

	// Verifica si un tipo es 'Concepto'
	private boolean isConceptType(String type) {
		return "Concepto".equals(type);
	}

	// Calcula la importancia de los nodos (ejemplo simple: log(frecuencia))
	private void calculateNodeImportance() {
		if (nodes.isEmpty())
			return;
		// Podrías usar algoritmos más complejos como PageRank sobre tu grafo si lo
		// necesitas
		for (Node node : nodes.values()) {
			node.setImportance(Math.log1p(node.getFrequency())); // log1p = log(1+frecuencia)
		}
	}

	@Override
	public String extractTriples(List<String> doc) {
		// TODO Auto-generated method stub
		throw new UnsupportedOperationException("Unimplemented method 'extractTriples'");
	}

}