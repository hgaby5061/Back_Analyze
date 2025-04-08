package com.service.web.app.models.entity;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonProperty;

public class Relations {

	// @JsonProperty("source")
	private String subject;

	// @JsonProperty("target")
	private String object;

	// @JsonProperty("label")
	private String relation;

	public Relations(String subject, String object, String relation) {
		// TODO Auto-generated constructor stub
		this.object = object;
		this.subject = subject;
		this.relation = relation;
	}

	// Sobrescribir equals
	@Override
	public boolean equals(Object o) {
		if (this == o)
			return true;
		if (!(o instanceof Relations))
			return false;
		Relations relations = (Relations) o;
		return subject.equals(relations.getSubject()) && object.equals(relations.getObject())
				&& relation.equals(relations.getRelation());
	}

	// Sobrescribir hashCode
	@Override
	public int hashCode() {
		return Objects.hash(subject, object, relation);
	}

	public String getSubject() {
		return subject;
	}

	public void setSubject(String subject) {
		this.subject = subject;
	}

	public String getObject() {
		return object;
	}

	public void setObject(String object) {
		this.object = object;
	}

	public String getRelation() {
		return relation;
	}

	public void setRelation(String relation) {
		this.relation = relation;
	}

}
