package com.service.web.app.models.entity;

import com.fasterxml.jackson.annotation.JsonProperty;

public class RelationGraph {

	@JsonProperty("source")
	private String source;
	@JsonProperty("target")
	private String target;
	@JsonProperty("label")
	private String label;

	
	public RelationGraph(String source, String target, String label) {
		this.source = source;
		this.target = target;
		this.label = label;
	}

	public String getSource() {
		return source;
	}

	public void setSource(String source) {
		this.source = source;
	}

	public String getTarget() {
		return target;
	}

	public void setTarget(String target) {
		this.target = target;
	}

	public String getLabel() {
		return label;
	}

	public void setLabel(String label) {
		this.label = label;
	}

}
