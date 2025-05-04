package com.service.web.app.models.entity;

import java.util.Objects;

public class Edge {
    String source; // ID del nodo fuente
    String target; // ID del nodo objetivo
    String relationship; // Nombre de la relación (lema normalizado)

    public Edge(String source, String target, String relationship) {
        this.source = source;
        this.target = target;
        this.relationship = relationship;

    }

    // Getters y métodos necesarios (equals, hashCode, toString)
    public String getSource() {
        return source;
    }

    public String getTarget() {
        return target;
    }

    public String getRelationship() {
        return relationship;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        Edge edge = (Edge) o;
        // Considera relaciones dirigidas y sensibles a mayúsculas/minúsculas en la
        // relación si es necesario
        return Objects.equals(source, edge.source) &&
                Objects.equals(target, edge.target) &&
                Objects.equals(relationship, edge.relationship);
    }

    @Override
    public int hashCode() {
        return Objects.hash(source, target, relationship);
    }

    @Override
    public String toString() {
        return String.format("Edge{source='%s', target='%s', relationship='%s'}",
                source, target, relationship);
    }

}
