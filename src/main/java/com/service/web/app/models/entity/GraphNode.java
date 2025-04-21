package com.service.web.app.models.entity;

// GraphNode.java
public class GraphNode {
    private String id;
    private String type;
    private String name;
    private int frequency;
    private double importance;
    //private double confidence;

    public GraphNode(String id, String type, String text, int frequency, double importance) {
        this.id = id;
        this.type = type;
        this.name = text;
        this.frequency = frequency;
        this.importance = importance;
        // this.confidence = 1.0;
    }

    /* public double getConfidence() {
        return confidence;
    }

    public void setConfidence(double confidence) {
        this.confidence = confidence;
    } */

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getText() {
        return name;
    }

    public void setText(String name) {
        this.name = name;
    }

    public int getFrequency() {
        return frequency;
    }

    public void setFrequency(int frequency) {
        this.frequency = frequency;
    }

    public double getImportance() {
        return importance;
    }

    public void setImportance(double importance) {
        this.importance = importance;
    }

    // Constructors, Getters, Setters
}
