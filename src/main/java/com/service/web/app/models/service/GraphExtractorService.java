package com.service.web.app.models.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.service.web.app.models.entity.GraphEdge;
import com.service.web.app.models.entity.GraphNode;
import com.service.web.app.models.entity.Relations;

import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class GraphExtractorService implements Extractor {

    private final String CORENLP_URL = "http://localhost:9000";
    private final RestTemplate restTemplate = new RestTemplate();

    @Override
    public String extractTriples(String inputText) {
        try {
            // Propiedades para análisis en español
            String properties = "{\"annotators\":\"tokenize,ssplit,mwt,pos,ner,depparse,kbp,natlog,openie\"," +
                    "\"tokenize.language\":\"es\"," +
                    "\"outputFormat\":\"json\"}";
            String encodedProps = URLEncoder.encode(properties, StandardCharsets.UTF_8);
            String url = CORENLP_URL + "/?properties=" + encodedProps;

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType("application/text-plain; charset=UTF-8"));
            HttpEntity<String> request = new HttpEntity<>(inputText, headers);

            ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);
            String responseBody = response.getBody();

            // Parse JSON
            ObjectMapper mapper = new ObjectMapper();
            Map<String, Object> jsonMap = mapper.readValue(responseBody, Map.class);
            try {
                Map<String, Object> responseGraph = processGraph(jsonMap);
                return new Gson().toJson(responseGraph);
            } catch (Exception e) {
                // TODO: handle exception
                e.printStackTrace();
            }
        } catch (Exception e) {
            throw new RuntimeException("Error al procesar el texto con CoreNLP", e);
        }
        return null;
    }

    private Map<String, Object> processGraph(Map<String, Object> parsedJson) {
        Map<String, GraphNode> nodes = new HashMap<>();
        List<GraphEdge> edges = new ArrayList<>();
        Map<String, Integer> frequencyMap = new HashMap<>();
        Map<String, Integer> degreeMap = new HashMap<>();

        List<Map<String, Object>> sentences = (List<Map<String, Object>>) parsedJson.get("sentences");
        for (Map<String, Object> sentence : sentences) {
            // 1. Entity mentions → nodos
            List<Map<String, Object>> entityMentions = (List<Map<String, Object>>) sentence.get("entitymentions");
            if (entityMentions != null) {
                for (Map<String, Object> ent : entityMentions) {
                    String text = (String) ent.get("text");
                    String type = (String) ent.get("ner");
                    addNode(nodes, frequencyMap, degreeMap, text, type);
                }
            }

            // 2. Tokens NER → nodos adicionales
            List<Map<String, Object>> tokens = (List<Map<String, Object>>) sentence.get("tokens");
            for (Map<String, Object> token : tokens) {
                String ner = (String) token.get("ner");
                if (!"O".equals(ner)) {
                    String text = (String) token.get("word");
                    addNode(nodes, frequencyMap, degreeMap, text, ner);
                }
            }

            // 3. OpenIE triplets → relaciones
            List<Map<String, Object>> openie = (List<Map<String, Object>>) sentence.get("openie");
            if (openie != null) {
                for (Map<String, Object> triplet : openie) {
                    String subject = (String) triplet.get("subject");
                    String relation = (String) triplet.get("relation");
                    String object = (String) triplet.get("object");

                    addNode(nodes, frequencyMap, degreeMap, subject, "Concepto");
                    addNode(nodes, frequencyMap, degreeMap, object, "Concepto");

                    edges.add(new GraphEdge(subject, object, relation));
                    incrementDegree(degreeMap, subject);
                    incrementDegree(degreeMap, object);
                }
            }
        }

        // 4. Calcular importancia
        for (GraphNode node : nodes.values()) {
            int freq = frequencyMap.getOrDefault(node.getText(), 1);
            int degree = degreeMap.getOrDefault(node.getText(), 0);
            node.setFrequency(freq);
            node.setImportance(freq * (1 + degree));
        }

        // Resultado empaquetado
        Map<String, Object> graph = new HashMap<>();
        graph.put("nodes", new ArrayList<>(nodes.values()));
        graph.put("edges", edges);
        return graph;
    }

    private void addNode(Map<String, GraphNode> nodes, Map<String, Integer> freqMap,
            Map<String, Integer> degMap, String text, String type) {
        nodes.computeIfAbsent(text, k -> {
            GraphNode node = new GraphNode();
            node.setId(UUID.randomUUID().toString());
            node.setText(text);
            node.setType(type);
            return node;
        });
        freqMap.put(text, freqMap.getOrDefault(text, 0) + 1);
    }

    private void incrementDegree(Map<String, Integer> map, String key) {
        map.put(key, map.getOrDefault(key, 0) + 1);
    }

}
