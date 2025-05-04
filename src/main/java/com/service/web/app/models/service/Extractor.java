package com.service.web.app.models.service;

import java.util.List;

import com.service.web.app.models.entity.Discurs;
import com.service.web.app.models.entity.GraphResult;
import com.service.web.app.models.entity.Relations;

public interface Extractor {
	/*
	 * public List<Document> extraction(List<Document> doc); public List<Document>
	 * extractAnot(List<Document> docum); public List<Document>
	 * extractProp(List<Document> docum);
	 */
	public String extractTriples(List<String> doc);

	public GraphResult extractTriplesFromDocuments(List<Discurs> documents);
}
