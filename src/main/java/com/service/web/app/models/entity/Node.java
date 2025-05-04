package com.service.web.app.models.entity;

import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class Node {
    String id; // Usaremos el nombre normalizado (lema/texto) como ID
    String name; // Texto más legible del nodo
    int frequency;
    double importance; // Calcularemos al final
    String type; // Tipo NER o "Concepto"
    private Set<String> documentIds = ConcurrentHashMap.newKeySet(); // IDs de

    public Node(String id, String name, String type, String documentId) {
        this.id = id;
        // Usar el nombre más largo/completo encontrado para mejor lectura
        this.name = name;
        this.frequency = 1;
        this.importance = 0.0; // Inicializar
        this.type = type;
        this.documentIds.add(documentId);
    }

    public Set<String> getDocumentIds() {
        return documentIds;
    }

    public void setDocumentIds(Set<String> documentIds) {
        this.documentIds = documentIds;
    }

    // Getters y métodos necesarios (equals, hashCode, toString)
    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public int getFrequency() {
        return frequency;
    }

    public double getImportance() {
        return importance;
    }

    public String getType() {
        return type;
    }

    public void incrementFrequency() {
        this.frequency++;
    }

    public void addDocumentId(String docId) {
        if (docId != null) {
            this.documentIds.add(docId);
        }
    }

    public void setType(String type) {
        this.type = type;
    }

    public void setImportance(double importance) {
        this.importance = importance;
    }

    public void setName(String name) {
        this.name = name;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        Node node = (Node) o;
        return Objects.equals(id, node.id); // ID único
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return String.format("Node{id='%s', name='%s', freq=%d, importance=%.2f, type='%s'}",
                id, name, frequency, importance, type);
    }
}
