package com.service.web.app.models.util;

import java.util.Properties;


public class NamedEntityPropertiesImpl {

	
	public static Properties nerProperties(String lang) {
		Properties props = new Properties();
        if (lang.equals("en")) {
            props.setProperty("annotators", "tokenize,ssplit,pos,lemma,ner,parse,dcoref");
           // props.setProperty("ner.applyFineGrained", "false");
            props.setProperty("ner.useSUTime", "false");
        } else if (lang.equals("es")) {
            props.put("annotators", "tokenize, ssplit, pos, lemma, ner");
            props.setProperty("tokenize.language", "es");
            props.setProperty("pos.language", "es");
            props.setProperty("ner.language", "es");
            props.setProperty("sutime.language", "spanish");
            props.setProperty("ner.model", "edu/stanford/nlp/models/ner/spanish.ancora.distsim.s512.crf.ser.gz");
            //props.setProperty("customTimeExpressionRecognition", "true");
            props.setProperty("ner.applyFineGrained", "false");
        }
        return props;

	}

}
