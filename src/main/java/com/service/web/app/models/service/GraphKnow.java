package com.service.web.app.models.service;

import java.util.List;

import com.service.web.app.models.entity.Document;

public interface GraphKnow {

	public String generateGraph(List<Document> docs);
}
