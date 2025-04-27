package com.service.web.app.models.entity;

import java.util.Objects;

// GraphEdge.java
public class GraphEdge {
    private String source;
    private String target;
    private String relationship;

    public String getSource() {
        return source;
    }

    public GraphEdge(String source, String target, String relationship) {
        this.source = source;
        this.target = target;
        this.relationship = relationship;
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

    public String getrelationship() {
        return relationship;
    }

    public void setrelationship(String relationship) {
        this.relationship = relationship;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        GraphEdge edge = (GraphEdge) o;
        return source.equals(edge.source) &&
                target.equals(edge.target) &&
                relationship.equals(edge.relationship);
    }

    @Override
    public int hashCode() {
        return Objects.hash(source, target, relationship);
    }

    // Constructors, Getters, Setters
}
