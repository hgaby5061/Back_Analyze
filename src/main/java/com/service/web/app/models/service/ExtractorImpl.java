package com.service.web.app.models.service;

import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Properties;

import javax.annotation.Nonnull;

import org.springframework.stereotype.Service;

import com.service.web.app.models.entity.Document;
import com.service.web.app.models.entity.Relations;

import edu.stanford.nlp.ie.util.RelationTriple;
import edu.stanford.nlp.international.spanish.SpanishVerbStripper;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.naturalli.NaturalLogicAnnotations;
import edu.stanford.nlp.naturalli.OpenIE;
import edu.stanford.nlp.naturalli.SentenceFragment;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.util.CoreMap;

public class ExtractorImpl {

	public List<Document> extraction(List<Document> doc) {
		// Create the Stanford CoreNLP pipeline

		Properties props = new Properties();
		System.out.println("RELATIONS PROP 1");

		props.setProperty("annotators", "tokenize,ssplit,pos,lemma,ner,depparse,coref,natlog,openie");
		props.setProperty("sutime.language", "spanish");
		props.setProperty("lang", "es");

		// ESTABLECER DOS VECES LAS PROPIEDADES PQ A VECES IDENTIFICA UNO Y NO OTROS

		// StanfordCoreNLP pipeline = new StanfordCoreNLP(pro);
		StanfordCoreNLP pipeline = new StanfordCoreNLP(props);

		// Annotate an example document.
		// Annotation text = new Annotation("Obama nació en Hawaii. Él es nuestro
		// presidente.");
		for (Document document : doc) {
			List<Relations> relation = new ArrayList<>();
			Annotation text = new Annotation(document.getText());
			// pipeline.annotate(text);

			pipeline.annotate(text);

			document.setRelations(forAnot(text, relation));
		}
		return doc;
	}

	// @Override
	public List<Document> extractAnot(List<Document> docum) {

		Properties pro = new Properties();
		System.out.println("RELATIONS PROP 2");

		pro.setProperty("annotators", "tokenize,ssplit,pos,lemma,ner,depparse,natlog,openie");
		pro.setProperty("depparse.model", "edu/stanford/nlp/models/parser/nndep/UD_Spanish.gz");
		pro.setProperty("ner.useSUTime", "false");
		// pro.setProperty("depparse.language", "spanish");
		pro.setProperty("ner.model", "edu/stanford/nlp/models/ner/spanish.kbp.ancora.distsim.s512.crf.ser.gz");
		pro.setProperty("ner.model", "edu/stanford/nlp/models/ner/spanish.ancora.distsim.s512.crf.ser.gz");
		// pro.setProperty("pos.language", "es");
		// pro.setProperty("tokenize.language", "es");
		// pro.setProperty("ner.language", "es");
		pro.setProperty("ner.applyFineGrained", "false");
		// pro.setProperty("pos.model","edu/stanford/nlp/models/pos-tagger/spanish-ud.tagger");

		/*
		 * String local = "D:\\csv Modulo\\data.csv";
		 * 
		 * CSVWriter writer = null; try { writer = new CSVWriter(new FileWriter(local));
		 * } catch (IOException e) { // TODO Auto-generated catch block
		 * e.printStackTrace(); }
		 */

		StanfordCoreNLP pipe = new StanfordCoreNLP(pro);
		for (Document document : docum) {
			List<Relations> relation = new ArrayList<>();
			Annotation text = new Annotation(document.getText());
			pipe.annotate(text);

			for (Relations relations : forAnot(text, relation)) {
				if (!document.getRelations().contains(relations))
					document.getRelations().add(relations);
			}
			/*
			 * for (Relations relations : document.getRelations()) { String[] csvData =
			 * {relations.getGovernor(), relations.getDependent(), relations.getRelation()
			 * }; if (writer != null) writer.writeNext(csvData); }
			 */

		}

		/*
		 * try {
		 * 
		 * writer.close();
		 * 
		 * } catch (IOException e) { // TODO Auto-generated catch block
		 * e.printStackTrace(); }
		 */
		return docum;
	}

	// @Override
	public List<Document> extractProp(List<Document> docum) {
		Properties pro = new Properties();
		System.out.println("RELATIONS PROP 3");

		pro.setProperty("annotators", "tokenize,ssplit,pos,lemma,ner,depparse,natlog,openie");
		pro.setProperty("depparse.model", "edu/stanford/nlp/models/parser/nndep/UD_Spanish.gz");
		// pro.setProperty("ner.useSUTime", "false");
		pro.setProperty("ner.applyFineGrained", "false");
		pro.setProperty("pos.model", "edu/stanford/nlp/models/pos-tagger/spanish-ud.tagger");

		StanfordCoreNLP pipe = new StanfordCoreNLP(pro);
		for (Document document : docum) {
			List<Relations> relation = new ArrayList<>();
			Annotation text = new Annotation(document.getText());
			pipe.annotate(text);

			for (Relations relations : forAnot(text, relation)) {
				if (!document.getRelations().contains(relations))
					document.getRelations().add(relations);
			}
		}

		return docum;
	}

	private List<Relations> forAnot(Annotation textAnotate, List<Relations> relation) {

		// PROBAR SI EN EL FOR SE PUEDE PONER DIRECTO RELATIONTRIPLEANNOTATION Y MEJORA
		// EL RESULTADO
		for (CoreMap sentence : textAnotate.get(CoreAnnotations.SentencesAnnotation.class)) {
			// Get the OpenIE triples for the sentence

			Collection<RelationTriple> triples = sentence.get(NaturalLogicAnnotations.RelationTriplesAnnotation.class);
			// Print the triples
			Relations rel = null;
			for (RelationTriple triple : triples) {
				rel = new Relations(triple.subjectGloss(), triple.relationGloss(), triple.objectGloss());

				relation.add(rel);

			}
		}
		return relation;
	}

}
