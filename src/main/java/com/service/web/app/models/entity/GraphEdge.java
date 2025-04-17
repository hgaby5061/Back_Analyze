package com.service.web.app.models.entity;

// GraphEdge.java
public class GraphEdge {
    private String source;
    private String target;
    private String relation;

    public String getSource() {
        return source;
    }

    public GraphEdge(String source, String target, String relation) {
        this.source = source;
        this.target = target;
        this.relation = relation;
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

    public String getRelation() {
        return relation;
    }

    public void setRelation(String relation) {
        this.relation = relation;
    }

    // Constructors, Getters, Setters
}
