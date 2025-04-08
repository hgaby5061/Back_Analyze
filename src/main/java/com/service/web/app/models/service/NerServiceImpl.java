package com.service.web.app.models.service;

import java.io.StringReader;
import java.util.*;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.pipeline.*;
import edu.stanford.nlp.util.*;
import edu.stanford.nlp.time.*;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TypedDependency;
import edu.stanford.nlp.parser.nndep.DependencyParser;
import edu.stanford.nlp.simple.*;


import edu.stanford.nlp.ling.HasWord;
import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.ling.TaggedWord;
import edu.stanford.nlp.parser.nndep.DependencyParser;
import edu.stanford.nlp.process.DocumentPreprocessor;
import edu.stanford.nlp.tagger.maxent.MaxentTagger;
import edu.stanford.nlp.trees.GrammaticalStructure;
import edu.stanford.nlp.util.logging.Redwood;

import com.service.web.app.models.entity.Document;
import com.service.web.app.models.util.LenguageDetectorImpl;
import com.service.web.app.models.util.NamedEntityPropertiesImpl;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class NerServiceImpl implements INerService{	
	
	
	@Override
	public List<Document> entyByText(List<Document> doc) {
		List<String> entities=new ArrayList<>();
		for (Document d : doc) {
			String lang = LenguageDetectorImpl.languageDetector(d.getText());
	        Properties props = NamedEntityPropertiesImpl.nerProperties(lang);
	        StanfordCoreNLP pipeline = new StanfordCoreNLP(props);
	        CoreDocument coreDoc = new CoreDocument(d.getText());
	        pipeline.annotate(coreDoc);
	        entities = coreDoc.entityMentions().stream()
	                .filter(x -> x.text().length() > 1)
	                .map(x -> x.text())
	                .collect(Collectors.toList());
	        		
	        d.setEntities(entities);
		}
        
        return doc;

	}
        /*List<String> entityTypes = coreDoc.entityMentions().stream()
                .filter(x -> x.text().length() > 1)
                .map(x -> x.entityType())
                .collect(Collectors.toList());   
        
        
        for(int i=0;i<entities.size();i++){
        
        enti.add(entities.get(i)+" --> "+entityTypes.get(i));
        }*/
        
        
        

}
