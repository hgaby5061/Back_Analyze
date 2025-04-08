package com.service.web.app.models.entity;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

public class Document {
	
	private String name;
	private String dateCreation;
	private String text;
	
	
	private List<String> entities;
	
	
	private List<Relations> relations;
	
	public Document(List<String> enti, List<Relations> rel) {		
		this.entities=enti;
		this.relations=rel;
	}

	public List<String> getEntities() {
		return entities;
	}

	public void setEntities(List<String> entities) {
		this.entities = entities;
	}
	
	public void setRelations(List<Relations> relations) {
		this.relations = relations;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getDateCreation() {
		return dateCreation;
	}

	public void setDateCreation(String dateCreation) {
		this.dateCreation = dateCreation;
	}

	public String getText() {
		return text;
	}

	public void setText(String text) {
		this.text = text;
	}

	public List<Relations> getRelations() {
		return relations;
	}

}
