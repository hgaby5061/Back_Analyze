package com.service.web.app.models.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.service.web.app.models.entity.GraphNode;
import com.service.web.app.models.entity.GraphEdge;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Service
public class GraphExtractorService implements Extractor {

    private final String CORENLP_URL = "http://localhost:9000";
    private final RestTemplate restTemplate = new RestTemplate();
    private int nodeIdCounter = 1;

    @Override
    public String extractTriples(List<String> inputText) {
        try {
            Map<String, GraphNode> nodes = new LinkedHashMap<>();
            List<GraphEdge> edges = new ArrayList<>();
            Map<String, Integer> degreeMap = new HashMap<>();
            Map<String, String> textToNodeId = new HashMap<>();
            Map<String, Object> result = new HashMap<>();
            for(String textDoc:inputText){
            for(String text:splitTextIntoChunks(textDoc)){
            Map<String, Object> parsedJson = fetchFromCoreNLP(text);

           
            Map<String, String> flatGroups = groupFlatDependencies(parsedJson);

            extractEntities(parsedJson, nodes, textToNodeId, flatGroups);
            extractRelations(parsedJson, nodes, edges, degreeMap, textToNodeId, flatGroups);
            calculateImportance(nodes, degreeMap);

            List<GraphNode> nodeList = new ArrayList<>(nodes.values());
            List<GraphEdge> edgeList = edges;

           
            result.put("nodes", nodeList);
            result.put("edges", edgeList);
            nodeIdCounter = 0;
        }}
            return new Gson().toJson(result);

        } catch (Exception e) {
            throw new RuntimeException("Error procesando el texto", e);
        }
    }
    private List<String> splitTextIntoChunks(String text) {
        text="El estudiante trabaja en la Universidad de Las Villas. Python es un software. Guido van Rossu creó el lenguaje Python en los Países Bajos.";

        List<String> chunks = new ArrayList<>();
        String[] sentences = text.split("(?<=[.!?])\\s*");
    
        StringBuilder currentChunk = new StringBuilder();
        int count = 0;
    
        for (String sentence : sentences) {
            currentChunk.append(sentence).append(" ");
            count++;
            if (count == 20) {
                chunks.add(currentChunk.toString().trim());
                currentChunk.setLength(0); // Limpia el StringBuilder para el próximo fragmento
                count = 0;
            }
        }
    
        // Agregar el último fragmento si hay oraciones sobrantes
        if (currentChunk.length() > 0) {
            chunks.add(currentChunk.toString().trim());
        }
    
        return chunks;
    }
   

    private Map<String, Object> fetchFromCoreNLP(String text) throws Exception {
                String properties = "{\"annotators\":\"tokenize,ssplit,mwt,pos,ner,depparse,kbp,natlog,openie\"," +
                "\"tokenize.language\":\"es\"," +
                "\"outputFormat\":\"json\"}";
        String encodedProps = URLEncoder.encode(properties, StandardCharsets.UTF_8);
        String url = CORENLP_URL + "/?properties=" + encodedProps;

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("application/text-plain; charset=UTF-8"));
        HttpEntity<String> request = new HttpEntity<>(text, headers);

        ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);
        ObjectMapper mapper = new ObjectMapper();
        System.out.println(response.getBody());
        return mapper.readValue(response.getBody(), Map.class);
    }

    private void extractEntities(Map<String, Object> parsed, Map<String, GraphNode> nodes,
            Map<String, String> textToId, Map<String, String> flatGroups) {

        List<Map<String, Object>> sentences = (List<Map<String, Object>>) parsed.get("sentences");

        for (Map<String, Object> sentence : sentences) {
            List<Map<String, Object>> entityMentions = (List<Map<String, Object>>) sentence.get("entitymentions");

            if (entityMentions != null) {
                for (Map<String, Object> ent : entityMentions) {
                    String text = (String) ent.get("text");
                    String type = (String) ent.get("ner");
                    double confidence = 1.0;
                    /* Map<String, Object> conf = (Map<String, Object>) ent.get("nerConfidences");
                    if (conf != null && conf.containsKey(type)) {
                        confidence = ((Number) conf.get(type)).doubleValue();
                    } */
                    addNode(text, type, nodes, textToId);
                }
            }

            /*
             * List<Map<String, Object>> tokens = (List<Map<String, Object>>)
             * sentence.get("tokens");
             * for (Map<String, Object> token : tokens) {
             * String ner = (String) token.get("ner");
             * String word = (String) token.get("word");
             * 
             * if (!"O".equals(ner) && !textToId.containsKey(word)) {
             * addNode(word, ner, 1.0, nodes, textToId);
             * }
             * }
             */
        }

        // Agregar entidades compuestas por dependencias (flat, compound)
        /*
         * for (Map.Entry<String, String> entry : flatGroups.entrySet()) {
         * String combined = entry.getKey();
         * if (!textToId.containsKey(combined)) {
         * addNode(combined, "Concepto", 1.0, nodes, textToId);
         * }
         * }
         */
        for (String combined : flatGroups.keySet()) {
            if (!textToId.containsKey(combined)) {
                addNode(combined, "Concepto",  nodes, textToId);
            }
        }
    }

    private void extractRelations(Map<String, Object> parsed,
            Map<String, GraphNode> nodes,
            List<GraphEdge> edges,
            Map<String, Integer> degrees,
            Map<String, String> textToId, Map<String, String> flatGroups) {

        Map<String, String> kbpMap = Map.of(
                "per:title", "es",
                "per:employee_of", "trabaja para",
                "org:top_members/employees", "tiene como miembro",
                "org:country_of_headquarters", "ubicado en",
                "per:countries_of_residence", "vive en");

        List<Map<String, Object>> sentences = (List<Map<String, Object>>) parsed.get("sentences");

        for (Map<String, Object> sentence : sentences) {
            List<Map<String, Object>> openie = (List<Map<String, Object>>) sentence.get("openie");
            if (openie != null) {
                Set<String> usedSubjects = new HashSet<>();
                for (Map<String, Object> triple : openie) {
                    String subject = (String) triple.get("subject");
                    String object = (String) triple.get("object");
                    String relation = (String) triple.get("relation");
                    System.out.println(triple);
                    System.out.println(textToId);
                    connect(subject, object, relation, nodes, edges, degrees, textToId);
                    if (subject.equals("que") || !textToId.containsKey(subject)) {
                        subject = resolveSubject(sentence, relation);
                    }

                    if (object == null || object.trim().isEmpty())
                        continue;

                    if (!textToId.containsKey(subject)) {
                        addNode(subject, "Concepto",  nodes, textToId);
                    }
                    if (!textToId.containsKey(object)) {
                        addNode(object, "Concepto",  nodes, textToId);
                    }

                    if (!usedSubjects.contains(subject + "_" + relation)) {
                        usedSubjects.add(subject + "_" + relation);
                        connect(subject, object, relation, nodes, edges, degrees, textToId);
                    }
                }
            }

            List<Map<String, Object>> kbp = (List<Map<String, Object>>) sentence.get("kbp");
            if (kbp != null) {
                for (Map<String, Object> triple : kbp) {
                    String subject = (String) triple.get("subject");
                    String object = (String) triple.get("object");
                    String relation = kbpMap.getOrDefault((String) triple.get("relation"),
                            (String) triple.get("relation"));

                    connect(subject, object, relation, nodes, edges, degrees, textToId);
                }
            }

            List<Map<String, Object>> dependencies = (List<Map<String, Object>>) sentence.get("basicDependencies");
            for (Map<String, Object> dep : dependencies) {
                String type = (String) dep.get("dep");
                String gov = (String) dep.get("governorGloss");
                String depWord = (String) dep.get("dependentGloss");
                for (String key : flatGroups.keySet()) {
                    if (key.contains(depWord)) {
                        depWord = key; // Devuelve la clave que contiene depWord
                    }
                }
                if ("appos".equals(type) || "nsubj".equals(type)) {
                    connect(depWord, gov, "es", nodes, edges, degrees, textToId);
                } else if ("nmod".equals(type)) {
                    connect(gov, depWord, "de", nodes, edges, degrees, textToId);
                }
            }
        }
    }

    private String resolveSubject(Map<String, Object> sentence, String relationVerb) {
        List<Map<String, Object>> dependencies = (List<Map<String, Object>>) sentence.get("basicDependencies");
        for (Map<String, Object> dep : dependencies) {
            if ("nsubj".equals(dep.get("dep")) && relationVerb.equals(dep.get("governorGloss"))) {
                System.out.println((String) dep.get("dependentGloss"));
                return (String) dep.get("dependentGloss");
            }
        }
        return relationVerb;
    }

    private void calculateImportance(Map<String, GraphNode> nodes, Map<String, Integer> degrees) {
        for (GraphNode node : nodes.values()) {
            int degree = degrees.getOrDefault(node.getText(), 0);
            node.setImportance( node.getFrequency() * (1 + degree));
        }
    }

    private void addNode(String text, String type, 
            Map<String, GraphNode> nodes, Map<String, String> textToId) {
        if (!textToId.containsKey(text)) {
            String id = String.valueOf(nodeIdCounter++);
            GraphNode node = new GraphNode(id, type.equals("O") ? "Concepto" : type, text, 1, 0.0);
            //node.setConfidence(confidence);
            nodes.put(id, node);
            textToId.put(text, id);
        } else {
            String id = textToId.get(text);
            GraphNode node = nodes.get(id);
            node.setFrequency(node.getFrequency() + 1);
            //node.setConfidence(Math.max(node.getConfidence(), confidence));
        }
    }

    private void connect(String fromText, String toText, String relation,
            Map<String, GraphNode> nodes, List<GraphEdge> edges,
            Map<String, Integer> degrees, Map<String, String> textToId) {
        String sourceId = textToId.get(fromText);
        String targetId = textToId.get(toText);
        if (sourceId != null && targetId != null) {
            edges.add(new GraphEdge(sourceId, targetId, relation));
            degrees.put(fromText, degrees.getOrDefault(fromText, 0) + 1);
            degrees.put(toText, degrees.getOrDefault(toText, 0) + 1);
        }
    }

    private Map<String, String> groupFlatDependencies(Map<String, Object> parsed) {
        Map<String, String> combinedMap = new HashMap<>();

        List<Map<String, Object>> sentences = (List<Map<String, Object>>) parsed.get("sentences");
        for (Map<String, Object> sentence : sentences) {
            List<Map<String, Object>> dependencies = (List<Map<String, Object>>) sentence.get("basicDependencies");
            Map<Integer, String> indexToWord = new HashMap<>();
            List<Map<String, Object>> tokens = (List<Map<String, Object>>) sentence.get("tokens");
            for (Map<String, Object> token : tokens) {
                indexToWord.put((Integer) token.get("index"), (String) token.get("word"));
            }

            for (Map<String, Object> dep : dependencies) {
                if ("flat".equals(dep.get("dep"))) {
                    String governor = indexToWord.get(dep.get("governor"));
                    String dependent = indexToWord.get(dep.get("dependent"));
                    String combined = governor + " " + dependent;
                    combinedMap.put(combined, combined);
                }
            }
        }
        return combinedMap;
    }
}
