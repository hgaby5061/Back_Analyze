package com.service.web.app.models.service;

import java.util.List;

import com.service.web.app.models.entity.Document;
import com.service.web.app.models.entity.Relations;

public interface Extractor {
	/*
	 * public List<Document> extraction(List<Document> doc); public List<Document>
	 * extractAnot(List<Document> docum); public List<Document>
	 * extractProp(List<Document> docum);
	 */
	public String extractTriples(String doc);
}
