package com.service.web.app.models.util;

import org.apache.tika.langdetect.optimaize.OptimaizeLangDetector;


public class LenguageDetectorImpl {

	
	public static String languageDetector(String text) {
		OptimaizeLangDetector lang = (OptimaizeLangDetector) new OptimaizeLangDetector().loadModels();
        lang.addText(text);
        String langString = lang.detect().getLanguage();
        return langString;

	}

}
