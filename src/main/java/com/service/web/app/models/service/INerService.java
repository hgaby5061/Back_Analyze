package com.service.web.app.models.service;

import java.util.List;

import com.service.web.app.models.entity.Document;

public interface INerService {
	public List<Document> entyByText(List<Document> doc);

}
