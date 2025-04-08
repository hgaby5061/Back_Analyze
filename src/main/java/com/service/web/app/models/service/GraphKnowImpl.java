package com.service.web.app.models.service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.service.web.app.models.entity.DocGraph;
import com.service.web.app.models.entity.Document;
import com.service.web.app.models.entity.RelationGraph;
import com.service.web.app.models.entity.Relations;

@Service
public class GraphKnowImpl implements GraphKnow {

	@Override
	public String generateGraph(List<Document> docs) {
		String json = "";
		DocGraph jsonDoc = null;
		ObjectMapper mapper = new ObjectMapper();
		List<String> enti = new ArrayList<>();
		List<RelationGraph> re = new ArrayList<>();

		for (Document document : docs) {
			if (!document.getEntities().isEmpty() && !document.getRelations().isEmpty()) {
				enti.addAll(document.getEntities());
				/*
				 * List<RelationGraph> doc = document.getRelations().stream() .map(d -> new
				 * RelationGraph(d.getGovernor(), d.getDependent(), d.getRelation()))
				 * .collect(Collectors.toList());
				 */
				// re.addAll(doc);
			} else
				return null;
		}
		jsonDoc = new DocGraph(enti, re);
		try {
			json = mapper.writeValueAsString(jsonDoc);
		} catch (JsonProcessingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return json;
	}

}
