package com.service.web.app.models.entity;

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

    // Constructors, Getters, Setters
}
