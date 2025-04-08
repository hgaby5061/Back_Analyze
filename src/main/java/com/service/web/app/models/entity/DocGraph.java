package com.service.web.app.models.entity;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

public class DocGraph {
	
	@JsonProperty("entities")
	List<String> entities;
	
	@JsonProperty("relations")
	List<RelationGraph> relations;

	public DocGraph(List<String> entities, List<RelationGraph> relations) {
		this.entities = entities;
		this.relations = relations;
	}

	
}
