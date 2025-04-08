package com.service.web.app.models.util;

import edu.stanford.nlp.pipeline.*;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.SemanticGraphCoreAnnotations;
import edu.stanford.nlp.semgraph.SemanticGraphEdge;
import edu.stanford.nlp.ling.IndexedWord;

import java.util.*;

public class Relaciones {

	public void main(String text,List<String> namedEntities) {
		// Initialize Stanford CoreNLP pipeline
		Properties props = new Properties();
		props.setProperty("annotators", "tokenize,ssplit,pos,lemma,ner,parse");
		props.setProperty("parse.model", "edu/stanford/nlp/models/srparser/spanishSR.beam.ser.gz");
		props.setProperty("depparse.model", "edu/stanford/nlp/models/parser/nndep/UD_Spanish.gz");
		props.setProperty("depparse.language", "spanish");
		 
		StanfordCoreNLP pipeline = new StanfordCoreNLP(props);
//"Elon Musk founded SpaceX and co-founded Tesla."
		//"Barack Obama was born in Hawaii. He was elected president in 2008."
		// List of documents to process
		/*List<String> documents = Arrays.asList(
				"Gerardo Lozada, Secretario: plantea que se tomaron 9 acuerdos en la reunión pasada, cumpliéndose 7 de salida al exterior. Siguen pendientes los acuerdos No.  2 y 3.\r\n"
				+ "A2: proponer a la administración que estudie vías posibles para que la facultad haga las inversiones necesarias y las presente al núcleo.\r\n"
				+ "A3: proponer a la administración encontrar vías para que se realicen inversiones y planificación de cómo actuar en los próximos años.\r\n"
				+ "Fernando Vecino, Secretario General del núcleo: Se tomaron dos acuerdos más relacionados con la gestión económica de la facultad y Roberto rindió cuenta al respecto. En busca de solución por parte de Roberto se están buscando las posibilidades de lo que se puede hacer en función de la apertura de país hacia nuevas actividades económicas.\r\n"
				+ ""
				);*/

		// Create a map to store the graph
		Map<String, Set<String>> entityMap = new HashMap<>();
		Set<Relation> relations = new HashSet<>();

		// Process each document
		//for (String text : documents) {
			// Create an annotation object
			Annotation document = new Annotation(text);

			// Annotate the document
			pipeline.annotate(document);

			// Get sentences from the document
			List<CoreMap> sentences = document.get(CoreAnnotations.SentencesAnnotation.class);

			for (CoreMap sentence : sentences) {
				//System.out.println(sentence);
				// Get the dependency parse tree for the sentence
				SemanticGraph dependencies = sentence
						.get(SemanticGraphCoreAnnotations.EnhancedPlusPlusDependenciesAnnotation.class);
				//System.out.println(dependencies);
				
				// Extract entities
				/*List<CoreLabel> tokens = sentence.get(CoreAnnotations.TokensAnnotation.class);
				for (CoreLabel token : tokens) {
					String word = token.get(CoreAnnotations.TextAnnotation.class);
					String ner = token.get(CoreAnnotations.NamedEntityTagAnnotation.class);

					if (!ner.equals("O")) {
						entityMap.computeIfAbsent(ner, k -> new HashSet<>()).add(word);
					}
				}*/

				// Extract relations
				for (SemanticGraphEdge edge : dependencies.edgeListSorted()) {
					
					IndexedWord governor = edge.getGovernor();
					IndexedWord dependent = edge.getDependent();
					String relation = edge.getRelation().getLongName();

					String governorNER = governor.get(CoreAnnotations.NamedEntityTagAnnotation.class);
					String dependentNER = dependent.get(CoreAnnotations.NamedEntityTagAnnotation.class);
					

					//if (!governorNER.equals("O") && !dependentNER.equals("O")) {
						//relations.add(new Relation(governor.word(), dependent.word(), relation));
					if(governor!=null&&dependent!=null)
		                if (namedEntities.contains(governor.toString()) && namedEntities.contains(dependent.toString())) {
		                    relations.add(new Relation(governor.word(), dependent.word(), relation));
		                
				}
			}
		}
		// Print the knowledge graph
		System.out.println("Entities:");
		for (Map.Entry<String, Set<String>> entry : entityMap.entrySet()) {
			System.out.println(entry.getKey() + ": " + entry.getValue());
		}

		System.out.println("Relations:");
		for (Relation relation : relations) {
			System.out.println(relation);
		}
	}
}

// Class to represent relations between entities
class Relation {
	private String entity1;
	private String entity2;
	private String relation;

	public Relation(String entity1, String entity2, String relation) {
		this.entity1 = entity1;
		this.entity2 = entity2;
		this.relation = relation;
	}

	@Override
	public String toString() {
		return entity1 + " -" + relation + "-> " + entity2;
	}

	@Override
	public int hashCode() {
		return Objects.hash(entity1, entity2, relation);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null || getClass() != obj.getClass())
			return false;
		Relation relation1 = (Relation) obj;
		return Objects.equals(entity1, relation1.entity1) && Objects.equals(entity2, relation1.entity2)
				&& Objects.equals(relation, relation1.relation);
	}
}
