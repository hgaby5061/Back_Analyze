package com.service.web.app.models.service;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
import edu.stanford.nlp.coref.CorefCoreAnnotations;
import edu.stanford.nlp.coref.data.CorefChain;
import edu.stanford.nlp.coref.data.CorefChain.CorefMention;

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
			Map.entry("org:top_members_employees", "dirigido por"),
			Map.entry("per:origin", "origen"),
			Map.entry("org:alternate_names", "alias"),
			Map.entry("per:alternate_names", "alias"),
			Map.entry("per:cities_of_residence", "reside en"),
			Map.entry("per:countries_of_residence", "reside en país"),
			Map.entry("org:subsidiaries", "subsidiaria"),
			Map.entry("org:parents", "matriz de")
	// ... añadir más mapeos según se descubran
	);

	// Mapa de relaciones mejoradas para traducción legible
	private static final Map<String, String> RELATION_TRANSLATIONS = Map.of(
			"nsubj", "es sujeto de",
			"obj", "actúa sobre",
			"nmod", "asociado a",
			"acl:relcl", "que");

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
		props.setProperty("annotators", "tokenize,ssplit,mwt,pos,lemma,depparse,ner,kbp,coref,natlog,openie");

		props.setProperty("ner.fine.regexner.ignorecase", "true");

		// OpenIE (Open Information Extraction) - Configurar
		props.setProperty("openie.resolve_coref", "true"); // Intentar usar coreferencia para mejores triples
		props.setProperty("openie.ignore_affinity", "false"); // Usar afinidad para filtrar
		props.setProperty("openie.affinity_probability_cap", "0.6"); // Umbral de confianza
		props.setProperty("openie.triple.strict", "false");
		props.setProperty("openie.affinity.threads", "3");

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
			// - Limpiar Texto ---

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

		cleanIsolatedNodes();
		mergeSimilarRelations();

		// 6. Devolver el grafo acumulado de todos los documentos
		System.out.println("Extracción completada.");
		return new GraphResult(new ArrayList<>(nodes.values()), new ArrayList<>(edges));
	}

	private List<String> splitTextIntoChunks(String text) {
		List<String> chunks = new ArrayList<>();
		// Regex mejorada para manejar espacios después de ., !? y saltos de línea
		String[] sentences = text.split("(?<=[.!?])\\s+|[\n\r]+");
		System.out.println(sentences.length);
		StringBuilder currentChunk = new StringBuilder();
		// Puedes hacer configurable el tamaño del chunk (ej. 10 oraciones)
		final int CHUNK_SIZE_SENTENCES = 10;
		int sentenceCount = 0;

		for (String sentence : sentences) {
			String trimmedSentence = sentence.trim();
			String cleanedText = trimmedSentence.replaceAll("[\\n\\r]+", " ").trim();
			if (!trimmedSentence.isEmpty()) {
				currentChunk.append(cleanedText).append(" "); // Añade espacio entre oraciones
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
			String cleanedText = currentChunk.toString().replaceAll("[\\n\\r]+", " ").trim();
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

		// Habilitar resolución de correferencia
		Map<Integer, CorefChain> corefChains = document.get(CorefCoreAnnotations.CorefChainAnnotation.class);
		if (corefChains != null) {
			processCoreferences(corefChains, docId);
		}

		// 3. Iterar sobre las oraciones y extraer información
		List<CoreMap> sentences = document.get(CoreAnnotations.SentencesAnnotation.class);
		if (sentences == null) {
			System.err.println("No se encontraron oraciones en el texto.");
			return;
		}

		for (CoreMap sentence : sentences) {
			// Mapa temporal para esta oración: ID original -> ID combinado (por amod)
			Map<String, String> localWordIdToCombinedId = new HashMap<>();

			// --- Estrategia de Extracción Combinada ---

			// PASO 1: Identificar Nodos Canónicos desde Mentions
			List<CoreMap> mentions = sentence.get(CoreAnnotations.MentionsAnnotation.class);
			if (mentions != null) {
				System.out.println(mentions);
				for (CoreMap mention : mentions) {
					String mentionText = mention.get(CoreAnnotations.TextAnnotation.class);
					String mentionId = normalizeForId(mentionText);
					if (mentionId == null || mentionId.isBlank())
						continue;
					String nerTag = mention.get(CoreAnnotations.NamedEntityTagAnnotation.class);
					String nodeType = (nerTag != null && (!nerTag.equals("NUMBER") || !nerTag.equals("O"))) ? nerTag
							: "MENTION";
					addNode(mentionId, mentionText, nodeType, docId);
				}
			}

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
					processRelationTriple(triple.subject, triple.relationLemmaGloss(), triple.object, "KBP", docId,
							localWordIdToCombinedId);
				}
			}

			// B. Extracción con OpenIE
			Collection<RelationTriple> openieTriples = sentence
					.get(NaturalLogicAnnotations.RelationTriplesAnnotation.class);
			if (openieTriples != null) {
				for (RelationTriple triple : openieTriples) {
					System.out.println(triple);
					// Procesa el triple de OpenIE
					String relationLemma = getSpanLemma(triple.relation);
					processRelationTriple(triple.subject, relationLemma, triple.object, "OpenIE", docId,
							localWordIdToCombinedId); // Pasar mapa local
				}
			}

			// C. Extracción basada en Dependencias (Complementaria y Robusta INCLUYE AMOD
			// COMBINADO Y PATRONES NUEVOS)
			SemanticGraph dependencies = sentence
					.get(SemanticGraphCoreAnnotations.EnhancedPlusPlusDependenciesAnnotation.class);
			if (dependencies != null) {
				extractRelationsFromDependencies(dependencies, docId, localWordIdToCombinedId);
			} else {
				System.err.println("Advertencia: No se encontró grafo de dependencias para una oración.");
			}

			// D. Identificar Conceptos Relevantes (Nodos no NER)
			extractConceptsFallback(sentence, docId);
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
	private void processRelationTriple(List<CoreLabel> subjectSpan, String relationLemma, List<CoreLabel> objectSpan,
			String sourceMethod, String docId, Map<String, String> localWordIdToCombinedId) {
		if (subjectSpan == null || subjectSpan.isEmpty() || objectSpan == null || objectSpan.isEmpty()
				|| relationLemma == null || relationLemma.isBlank()) {
			return; // Ignorar triples incompletos
		}

		// 1. Resolver spans a IDs (crea nodo si no existe)
		String subjOriginalId = resolveSpanToNodeId(subjectSpan, docId);
		String objOriginalId = resolveSpanToNodeId(objectSpan, docId);

		if (subjOriginalId == null || objOriginalId == null)
			return;

		// 2. Aplicar mapeo local (amod combinado)
		String subjFinalId = localWordIdToCombinedId.getOrDefault(subjOriginalId, subjOriginalId);
		String objFinalId = localWordIdToCombinedId.getOrDefault(objOriginalId, objOriginalId);

		if (subjFinalId.equals(objFinalId))
			return; // Evitar auto-referencias

		// 3. Limpiar/Traducir la relación
		String finalRelation = relationLemma.toLowerCase().trim();
		if ("KBP".equals(sourceMethod)) {
			finalRelation = KBP_RELATION_MAP.getOrDefault(finalRelation, finalRelation);
		} else {
			if (finalRelation.equals("ser") || finalRelation.equals("estar"))
				finalRelation = "es";
		}

		// 4. Añadir la arista
		addEdge(subjFinalId, objFinalId, finalRelation);
	}

	/**
	 * Extrae ID, nombre y tipo de un span de CoreLabel.
	 * **CLAVE:** Maneja correctamente entidades NER multi-palabra.
	 */
	private String resolveSpanToNodeId(List<CoreLabel> span, String docId) {
		if (span == null || span.isEmpty())
			return null;

		String spanText = getSpanOriginalText(span);
		String normalizedSpanTextId = normalizeForId(spanText);
		if (normalizedSpanTextId != null && nodes.containsKey(normalizedSpanTextId)) {
			nodes.get(normalizedSpanTextId).addDocumentId(docId);
			return normalizedSpanTextId;
		}
		NodeInfo nodeInfo = getNodeInfoFromSpan(span);
		if (nodeInfo != null) {
			addNode(nodeInfo.id, nodeInfo.name, nodeInfo.type, docId);
			return nodeInfo.id;
		}
		return null; // nombre
	}

	// resolveWordToNodeId (Sin cambios - crea nodo si no existe)
	private String resolveWordToNodeId(IndexedWord word, String docId) {
		if (word == null)
			return null;
		String wordText = word.originalText();
		String normalizedWordTextId = normalizeForId(wordText);
		String lemma = getLemma(word);
		String normalizedLemmaId = normalizeForId(lemma);
		if (normalizedWordTextId != null && nodes.containsKey(normalizedWordTextId)) {
			nodes.get(normalizedWordTextId).addDocumentId(docId);
			return normalizedWordTextId;
		}
		if (normalizedLemmaId != null && nodes.containsKey(normalizedLemmaId)) {
			nodes.get(normalizedLemmaId).addDocumentId(docId);
			return normalizedLemmaId;
		}
		NodeInfo nodeInfo = getNodeInfoFromWord(word);
		if (nodeInfo != null) {
			addNode(nodeInfo.id, nodeInfo.name, nodeInfo.type, docId);
			return nodeInfo.id;
		}
		return null;
	}

	/**
	 * Extracción complementaria usando patrones sobre el grafo de dependencias.
	 */
	private void extractRelationsFromDependencies(SemanticGraph dependencies, String docId,
			Map<String, String> localWordIdToCombinedId) {
		// --- PASO 4.1: Detectar y Procesar AMOD para combinar nodos ---
		processAmodCombinations(dependencies, docId, localWordIdToCombinedId);

		// --- PASO 4.2: Extraer Relaciones SVO (Sujeto-Verbo-Objeto) ---
		extractSvoRelations(dependencies, docId, localWordIdToCombinedId);

		// --- PASO 4.3: Extraer Relaciones NMOD y APPOS ---
		extractNmodApposRelations(dependencies, docId, localWordIdToCombinedId);

		// --- PASO 4.4: Extraer Relaciones Cópula+Complemento ---
		extractCopulaRelations(dependencies, docId, localWordIdToCombinedId);

		// --- PASO 4.5: Extraer Relaciones de Cláusulas Relativas (ACL:RELCL) ---
		extractRelativeClauseRelations(dependencies, docId, localWordIdToCombinedId);

		// --- Añadir más patrones aquí si es necesario ---
	}

	// --- NUEVO: Procesar AMOD ---
	private void processAmodCombinations(SemanticGraph dependencies, String docId,
			Map<String, String> localWordIdToCombinedId) {
		for (SemanticGraphEdge edge : dependencies.findAllRelns(GrammaticalRelation.valueOf("amod"))) {
			IndexedWord govWord = edge.getGovernor(); // El sustantivo
			IndexedWord depWord = edge.getDependent(); // El adjetivo

			NodeInfo govInfo = getNodeInfoFromWord(govWord); // Obtener info original
			NodeInfo depInfo = getNodeInfoFromWord(depWord);

			if (govInfo == null || depInfo == null)
				continue;

			// Crear texto y ID combinados (ej: "gran parte") - El orden puede depender
			// Asumimos Adjetivo + Sustantivo para español general, ajustar si es necesario
			String combinedText = depInfo.name + " " + govInfo.name;
			String combinedId = normalizeForId(combinedText);
			if (combinedId == null || combinedId.isBlank())
				continue;

			// Crear/actualizar el nodo combinado
			addNode(combinedId, combinedText, govInfo.type, docId); // Usar tipo del sustantivo

			// Registrar el mapeo para esta oración
			// Mapear tanto el ID original del sustantivo como el del adjetivo al ID
			// combinado
			localWordIdToCombinedId.put(govInfo.id, combinedId);
			localWordIdToCombinedId.put(depInfo.id, combinedId);
			// System.out.println("AMOD Combined: " + govInfo.id + " + " + depInfo.id + " ->
			// " + combinedId); // DEBUG
		}
	}

	// --- SVO (con negación) ---
	private void extractSvoRelations(SemanticGraph dependencies, String docId,
			Map<String, String> localWordIdToCombinedId) {
		for (IndexedWord verb : dependencies.getAllNodesByPartOfSpeechPattern("VERB")) {
			String verbLemma = getLemma(verb);
			if (verbLemma == null || verbLemma.isBlank())
				continue;

			String relation = verbLemma;
			if (relation.equals("ser") || relation.equals("estar"))
				relation = "es";
			else if (relation.equals("haber"))
				relation = "tiene";

			boolean isNegated = dependencies.getChildrenWithReln(verb, GrammaticalRelation.valueOf("advmod"))
					.stream().map(this::getLemma).anyMatch(modLemma -> modLemma != null && modLemma.equals("no"));
			if (isNegated)
				relation = "no " + relation;

			Set<IndexedWord> subjects = dependencies.getChildrenWithReln(verb, GrammaticalRelation.valueOf("nsubj"));
			subjects.addAll(dependencies.getChildrenWithReln(verb, GrammaticalRelation.valueOf("nsubj:pass")));

			List<IndexedWord> objects = new ArrayList<>();
			objects.addAll(dependencies.getChildrenWithReln(verb, GrammaticalRelation.valueOf("obj")));
			objects.addAll(dependencies.getChildrenWithReln(verb, GrammaticalRelation.valueOf("iobj")));
			objects.addAll(dependencies.getChildrenWithReln(verb, GrammaticalRelation.valueOf("obl")));

			for (IndexedWord subjWord : subjects) {
				for (IndexedWord objWord : objects) {
					String subjOriginalId = resolveWordToNodeId(subjWord, docId);
					String objOriginalId = resolveWordToNodeId(objWord, docId);
					if (subjOriginalId == null || objOriginalId == null)
						continue;

					String subjFinalId = localWordIdToCombinedId.getOrDefault(subjOriginalId, subjOriginalId);
					String objFinalId = localWordIdToCombinedId.getOrDefault(objOriginalId, objOriginalId);

					if (!subjFinalId.equals(objFinalId)) {
						addEdge(subjFinalId, objFinalId, relation);
					}
				}
			}
		}
	}

	// --- NMOD y APPOS ---
	private void extractNmodApposRelations(SemanticGraph dependencies, String docId,
			Map<String, String> localWordIdToCombinedId) {
		for (SemanticGraphEdge edge : dependencies.edgeIterable()) { // Iterar sobre todas las aristas
			GrammaticalRelation rel = edge.getRelation();
			String shortRelName = rel.getShortName();

			if (shortRelName.equals("nmod") || shortRelName.equals("appos")) {
				IndexedWord govWord = edge.getGovernor();
				IndexedWord depWord = edge.getDependent();

				String govOriginalId = resolveWordToNodeId(govWord, docId);
				String depOriginalId = resolveWordToNodeId(depWord, docId);
				if (govOriginalId == null || depOriginalId == null)
					continue;

				String govFinalId = localWordIdToCombinedId.getOrDefault(govOriginalId, govOriginalId);
				String depFinalId = localWordIdToCombinedId.getOrDefault(depOriginalId, depOriginalId);

				if (govFinalId.equals(depFinalId))
					continue;

				String relationLabel = null;
				if (shortRelName.equals("nmod")) {
					relationLabel = dependencies.getChildrenWithReln(depWord, GrammaticalRelation.valueOf("case"))
							.stream().map(this::getLemma).filter(l -> l != null && !l.isBlank()).findFirst()
							.orElse("relacionado con");
				} else { // appos
					relationLabel = "es (descripción)";
				}
				// Traducir relación a forma legible si está en el mapa
				String readableRel = RELATION_TRANSLATIONS.getOrDefault(shortRelName, relationLabel);
				addEdge(govFinalId, depFinalId, readableRel);
			}
		}
	}

	// --- NUEVO: Cópula + Complemento ---
	private void extractCopulaRelations(SemanticGraph dependencies, String docId,
			Map<String, String> localWordIdToCombinedId) {
		for (SemanticGraphEdge edge : dependencies.findAllRelns(GrammaticalRelation.valueOf("cop"))) {
			IndexedWord verbWord = edge.getGovernor(); // El verbo cópula (ser, estar)
			IndexedWord complementWord = edge.getDependent(); // El predicado (adjetivo, sustantivo)

			// Encontrar sujeto del verbo
			Set<IndexedWord> subjects = dependencies.getChildrenWithReln(verbWord,
					GrammaticalRelation.valueOf("nsubj"));
			if (subjects.isEmpty())
				continue; // Necesita sujeto
			IndexedWord subjWord = subjects.iterator().next(); // Asumir un sujeto principal

			// Encontrar complementos oblicuos del PREDICADO (complementWord)
			Set<IndexedWord> obliques = dependencies.getChildrenWithReln(complementWord,
					GrammaticalRelation.valueOf("obl"));

			// Resolver IDs originales
			String subjOriginalId = resolveWordToNodeId(subjWord, docId);
			String complementOriginalId = resolveWordToNodeId(complementWord, docId);
			if (subjOriginalId == null || complementOriginalId == null)
				continue;

			// Aplicar mapeo local
			String subjFinalId = localWordIdToCombinedId.getOrDefault(subjOriginalId, subjOriginalId);
			String complementFinalId = localWordIdToCombinedId.getOrDefault(complementOriginalId, complementOriginalId); // El
																															// predicado
																															// también
																															// puede
																															// ser
																															// combinado
																															// (ej.
																															// "buen
																															// presidente")

			// Nueva detección de negación ampliada
			Set<String> negationWords = Set.of("no", "nunca", "jamás", "tampoco");

			boolean isNegatedVerb = dependencies.getChildrenWithReln(verbWord,
					GrammaticalRelation.valueOf("advmod"))
					.stream()
					.anyMatch(w -> negationWords.contains(w.lemma().toLowerCase()));

			boolean isNegatedCompl = dependencies.getChildrenWithReln(complementWord,
					GrammaticalRelation.valueOf("advmod"))
					.stream()
					.anyMatch(w -> negationWords.contains(w.lemma().toLowerCase()));

			String negationPrefix = (isNegatedVerb || isNegatedCompl) ? "no " : "";

			// Crear relación básica Sujeto -[es/está]-> Complemento
			String baseRelation = negationPrefix + getLemma(verbWord); // ej "no ser"
			if (baseRelation.equals("ser") || baseRelation.equals("estar"))
				baseRelation = "es"; // Simplificar a "es"
			else if (baseRelation.equals("no ser") || baseRelation.equals("no estar"))
				baseRelation = "no es"; // Simplificar negado
			addEdge(subjFinalId, complementFinalId, baseRelation);

			// Crear relaciones con los complementos oblicuos
			for (IndexedWord oblWord : obliques) {
				String oblOriginalId = resolveWordToNodeId(oblWord, docId);
				if (oblOriginalId == null)
					continue;
				String oblFinalId = localWordIdToCombinedId.getOrDefault(oblOriginalId, oblOriginalId);

				if (subjFinalId.equals(oblFinalId))
					continue; // Evitar auto-relación

				// Obtener preposición
				String preposition = dependencies.getChildrenWithReln(oblWord, GrammaticalRelation.valueOf("case"))
						.stream().map(this::getLemma).filter(l -> l != null && !l.isBlank()).findFirst()
						.orElse("a/de"); // Fallback preposición

				// Crear relación: Sujeto -[relación compuesta]-> Oblicuo
				// Ej: colonialismo -[no es ajeno a]-> subdesarrollo
				String complexRelation = String.format("%s%s %s", negationPrefix, getLemma(complementWord), preposition)
						.trim(); // ej: "no ajeno a"
				addEdge(subjFinalId, oblFinalId, complexRelation);
			}
		}
	}

	// --- NUEVO: Cláusulas Relativas ---
	private void extractRelativeClauseRelations(SemanticGraph dependencies, String docId,
			Map<String, String> localWordIdToCombinedId) {
		// Buscar relaciones acl:relcl (entidad_modificada <- verbo_relativo)
		for (SemanticGraphEdge edge : dependencies.findAllRelns(GrammaticalRelation.valueOf("acl:relcl"))) {
			IndexedWord modifiedEntityWord = edge.getGovernor(); // Ej: pobreza
			IndexedWord relativeVerbWord = edge.getDependent(); // Ej: sufre

			String modifiedEntityOriginalId = resolveWordToNodeId(modifiedEntityWord, docId);
			if (modifiedEntityOriginalId == null)
				continue;
			String modifiedEntityFinalId = localWordIdToCombinedId.getOrDefault(modifiedEntityOriginalId,
					modifiedEntityOriginalId);

			String relativeVerbLemma = getLemma(relativeVerbWord);
			if (relativeVerbLemma == null || relativeVerbLemma.isBlank())
				continue;

			// Buscar sujeto y objeto DENTRO de la cláusula relativa (dependientes del verbo
			// relativo)
			Set<IndexedWord> relSubjects = dependencies.getChildrenWithReln(relativeVerbWord,
					GrammaticalRelation.valueOf("nsubj"));
			Set<IndexedWord> relObjects = dependencies.getChildrenWithReln(relativeVerbWord,
					GrammaticalRelation.valueOf("obj"));
			relObjects.addAll(dependencies.getChildrenWithReln(relativeVerbWord, GrammaticalRelation.valueOf("obl"))); // Incluir
																														// oblicuos
																														// como
																														// objetos
																														// semánticos

			// Caso 1: La entidad modificada es el SUJETO semántico del verbo relativo
			// Ej: "el hombre que canta" -> hombre <- canta (nsubj: que -> canta)
			// Crear relación: entidad_modificada -[verbo_relativo]-> objeto_relativo
			if (!relSubjects.isEmpty() && relSubjects.iterator().next().lemma().equals("que")) { // Si el sujeto es
																									// "que" (refiere a
																									// la entidad)
				for (IndexedWord relObjWord : relObjects) {
					String relObjOriginalId = resolveWordToNodeId(relObjWord, docId);
					if (relObjOriginalId == null)
						continue;
					String relObjFinalId = localWordIdToCombinedId.getOrDefault(relObjOriginalId, relObjOriginalId);
					if (!modifiedEntityFinalId.equals(relObjFinalId)) {
						addEdge(modifiedEntityFinalId, relObjFinalId, relativeVerbLemma); // Ej: hombre -[canta]->
																							// cancion
					}
				}
			}

			// Caso 2: La entidad modificada es el OBJETO semántico del verbo relativo
			// Ej: "la pobreza que sufre la gente" -> pobreza <- sufre (nsubj: gente ->
			// sufre, obj: que -> sufre)
			// Crear relación: sujeto_relativo -[verbo_relativo]-> entidad_modificada
			// O invertir: entidad_modificada -[es V-ido por]-> sujeto_relativo
			if (!relObjects.isEmpty() && relObjects.iterator().next().lemma().equals("que")) { // Si el objeto es "que"
																								// (refiere a la
																								// entidad)
				for (IndexedWord relSubjWord : relSubjects) {
					String relSubjOriginalId = resolveWordToNodeId(relSubjWord, docId);
					if (relSubjOriginalId == null)
						continue;
					String relSubjFinalId = localWordIdToCombinedId.getOrDefault(relSubjOriginalId, relSubjOriginalId);
					if (!relSubjFinalId.equals(modifiedEntityFinalId)) {
						// Opción A: Dirección activa
						// addEdge(relSubjFinalId, modifiedEntityFinalId, relativeVerbLemma); // Ej:
						// gente -[sufre]-> pobreza
						// Opción B: Dirección pasiva/descriptiva (puede ser más clara)
						addEdge(modifiedEntityFinalId, relSubjFinalId, "es " + relativeVerbLemma + " por"); // Ej:
																											// pobreza
																											// -[es
																											// sufrida
																											// por]->
																											// gente
					}
				}
			}
		}
	}

	// --- IDENTIFICACIÓN DE CONCEPTOS (FALLBACK) ---
	private void extractConceptsFallback(CoreMap sentence, String docId) {
		// (Implementación sin cambios)
		for (CoreLabel token : sentence.get(CoreAnnotations.TokensAnnotation.class)) {
			String ner = token.ner();
			String pos = token.tag();
			boolean isRelevantPOS = pos != null && (pos.startsWith("NOUN") || pos.startsWith("PROPN"));
			if ((ner == null || ner.equals("O")) && isRelevantPOS) {
				String conceptLemma = getLemma(token);
				String conceptId = normalizeForId(conceptLemma);
				String conceptText = token.word();
				String normalizedTextId = normalizeForId(conceptText);
				if (conceptId != null && !conceptId.isBlank() && !nodes.containsKey(conceptId)) {
					if (normalizedTextId == null || !nodes.containsKey(normalizedTextId)) {
						addNode(conceptId, conceptText, "Concepto", docId);
					}
				}
			}
		}
	}

	private static final Set<String> IRRELEVANT_TERMS = Set.of(
			"se", "hoy", "que", "x", "mu", "xx", "xxv", "su");

	/**
	 * Analiza un span para determinar su ID, Nombre y Tipo.
	 * Prioriza ID de texto completo normalizado para NERs consistentes.
	 * Usa ID de lema normalizado como fallback.
	 */
	private NodeInfo getNodeInfoFromSpan(List<CoreLabel> span) {
		// (Implementación sin cambios respecto a la versión anterior - parece correcta)
		if (span == null || span.isEmpty())
			return null;
		String fullOriginalText = getSpanOriginalText(span);
		String firstNer = span.get(0).ner();
		String nodeType = "Concepto";
		String nodeId = null;
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
				nodeType = firstNer;
			}
		}
		if (isConsistentNer) {
			nodeId = normalizeForId(fullOriginalText);
		} else {
			CoreLabel lastToken = span.get(span.size() - 1);
			nodeId = normalizeForId(getLemma(lastToken));
			String lastTokenNer = lastToken.ner();
			if (lastTokenNer != null && !lastTokenNer.equals("O")) {
				nodeType = lastTokenNer;
			} else {
				nodeType = "Concepto";
			}
		}
		if (nodeId == null || nodeId.isBlank()) {
			nodeId = normalizeForId(fullOriginalText);
			if (nodeId == null || nodeId.isBlank())
				return null;
			nodeType = "Concepto";
		}
		if (IRRELEVANT_TERMS.contains(nodeId) || nodeId.matches("\\d+")) {
			return null;
		}
		return new NodeInfo(nodeId, fullOriginalText, nodeType);
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

	// Lista de stopwords en español para filtrar nodos irrelevantes
	private static final Set<String> SPANISH_STOPWORDS = Set.of(
			"el", "la", "los", "las", "un", "una", "unos", "unas", "de", "del", "al", "lo",
			"y", "e", "o", "u", "que", "cual", "cuyo", "donde", "como", "cuando", "a", "en",
			"con", "por", "para", "sin", "sobre", "entre", "hacia", "desde", "se", "sus",
			"tu", "tus", "mi", "mis", "nos", "vos", "su", "aquél", "ésa", "esto", "eso", "aquello");

	/**
	 * Añade o actualiza un nodo en el mapa 'nodes'.
	 * Gestiona entidades anidadas: si el nodo es contenido en otro, actualiza el
	 * nodo padre.
	 * Usa el ID (lema) para la unicidad. Incrementa frecuencia.
	 * Prioriza tipos NER sobre 'Concepto'. Actualiza el nombre si el nuevo es más
	 * largo.
	 */
	private void addNode(String id, String nodeName, String nodeType, String docId) {
		if (shouldSkipNode(id, nodeType))
			return;

		// Buscar entidades contenedoras
		String parentId = findParentEntity(id);
		if (parentId != null) {
			nodes.compute(parentId, (key, parent) -> {
				parent.addDocumentId(docId);
				parent.incrementFrequency();
				return parent;
			});
			return;
		}

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

	private boolean shouldSkipNode(String id, String type) {
		if (id == null || id.isBlank() || SPANISH_STOPWORDS.contains(id)) {
			return true;
		}
		if ("NUMBER".equals(type)) {
			// Permitir números que tengan exactamente 4 dígitos (posibles años)
			return !(id.matches("\\d{4}"));
		}
		return false;
	}

	private String findParentEntity(String candidateId) {
		return nodes.keySet().stream()
				.filter(existingId -> existingId.contains(candidateId) || candidateId.contains(existingId))
				.max(java.util.Comparator.comparingInt(String::length))
				.orElse(null);
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

	private void processCoreferences(Map<Integer, CorefChain> corefChains, String docId) {
		corefChains.values().forEach(chain -> {
			List<CorefMention> mentions = chain.getMentionsInTextualOrder();
			if (mentions.size() > 1) {
				String representative = normalizeForId(mentions.get(0).mentionSpan);
				mentions.stream().skip(1)
						.map(m -> normalizeForId(m.mentionSpan))
						.filter(id -> nodes.containsKey(id))
						.forEach(id -> mergeNodes(representative, id));
			}
		});
	}

	private void mergeNodes(String mainId, String synonymId) {
		Node main = nodes.get(mainId);
		Node synonym = nodes.get(synonymId);

		if (main != null && synonym != null) {
			// Fusionar documentos y frecuencia
			main.getDocumentIds().addAll(synonym.getDocumentIds());
			main.setFrequency(main.getFrequency() + synonym.getFrequency());

			// Redirigir aristas
			edges.forEach(e -> {
				if (e.getSource().equals(synonymId))
					e.setSource(mainId);
				if (e.getTarget().equals(synonymId))
					e.setTarget(mainId);
			});

			nodes.remove(synonymId);
		}
	}

	private void cleanIsolatedNodes() {
		Set<String> connectedNodes = edges.stream()
				.flatMap(e -> Stream.of(e.getSource(), e.getTarget()))
				.collect(Collectors.toSet());

		nodes.entrySet().removeIf(entry -> !connectedNodes.contains(entry.getKey()) &&
				entry.getValue().getFrequency() < 2);
	}

	private void mergeSimilarRelations() {
		Map<String, Edge> relationMap = new HashMap<>();
		new ArrayList<>(edges).forEach(e -> {
			String key = e.getSource() + "-" + e.getTarget();
			Edge existing = relationMap.get(key);
			if (existing != null) {
				existing.setRelationship(existing.getRelationship() + "|" + e.getRelationship());
				edges.remove(e);
			} else {
				relationMap.put(key, e);
			}
		});
	}

}
