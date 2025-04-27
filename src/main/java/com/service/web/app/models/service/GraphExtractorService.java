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
    // Lista ampliada de stopwords en español
    private static final Set<String> STOPWORDS = Set.of(
            "de", "en", "qué", "es", "son", "ser", "un", "una", "el", "la", "los", "las", "y", "con", "para", "por",
            "se", "su");

    // Tipos NER a excluir
    private static final Set<String> EXCLUDED_NER = Set.of(
            "NUMBER", "TIME", "MONEY", "PERCENT");

    @Override
    public String extractTriples(List<String> inputText) {
        try {
            Map<String, GraphNode> nodes = new LinkedHashMap<>();
            Set<GraphEdge> edges = new LinkedHashSet<>();
            Map<String, Integer> degreeMap = new HashMap<>();
            Map<String, String> textToNodeId = new HashMap<>();
            Map<String, Object> result = new HashMap<>();

            for (String textDoc : inputText) {
                for (String text : splitTextIntoChunks(textDoc)) {
                    Map<String, Object> parsedJson = fetchFromCoreNLP(text);

                    Map<String, String> flatGroups = groupFlatDependencies(parsedJson);

                    extractEntities(parsedJson, nodes, textToNodeId, flatGroups);
                    extractRelations(parsedJson, nodes, edges, degreeMap, textToNodeId, flatGroups);
                    calculateImportance(nodes, degreeMap);
                }
            }

            List<GraphNode> nodeList = new ArrayList<>(nodes.values());
            Set<GraphEdge> edgeList = edges;

            result.put("nodes", nodeList);
            result.put("edges", edgeList);
            nodeIdCounter = 1; // Reiniciar para la próxima ejecución

            return new Gson().toJson(result);

        } catch (Exception e) {
            throw new RuntimeException("Error procesando el texto", e);
        }
    }

    private List<String> splitTextIntoChunks(String text) {
        List<String> chunks = new ArrayList<>();
        String[] sentences = text.split("(?<=[.!?])\\s*");
        StringBuilder currentChunk = new StringBuilder();
        int count = 0;

        for (String sentence : sentences) {
            currentChunk.append(sentence).append(" ");
            count++;
            if (count == 10) {
                chunks.add(currentChunk.toString().trim());
                currentChunk.setLength(0);
                count = 0;
            }
        }

        if (currentChunk.length() > 0) {
            chunks.add(currentChunk.toString().trim());
        }
        return chunks;
    }

    private Map<String, Object> fetchFromCoreNLP(String text) throws Exception {
        String properties = "{\"annotators\":\"tokenize,ssplit,mwt,pos,depparse,ner,kbp,natlog,openie\"," +
                "\"tokenize.language\": \"es\"," +
                "\"pos.model\": \"edu/stanford/nlp/models/pos-tagger/spanish-ud.tagger\"," +
                "\"depparse.model\":\"edu/stanford/nlp/models/parser/nndep/UD_Spanish.gz\"," +
                "\"parse.type\":\"enhancedPlusPlusDependencies\"," +
                "\"outputFormat\":\"json\"}";
        String encodedProps = URLEncoder.encode(properties, StandardCharsets.UTF_8);
        String url = CORENLP_URL + "/?properties=" + encodedProps;

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("text/plain; charset=UTF-8"));
        HttpEntity<String> request = new HttpEntity<>(text, headers);

        ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);
        ObjectMapper mapper = new ObjectMapper();
        return mapper.readValue(response.getBody(), Map.class);
    }

    private void extractEntities(Map<String, Object> parsed, Map<String, GraphNode> nodes,
            Map<String, String> textToNodeId, Map<String, String> flatGroups) {
        List<Map<String, Object>> sentences = (List<Map<String, Object>>) parsed.get("sentences");

        for (Map<String, Object> sentence : sentences) {
            List<Map<String, Object>> tokens = (List<Map<String, Object>>) sentence.get("tokens");
            Map<Integer, String> indexToLemma = new HashMap<>();
            for (Map<String, Object> token : tokens) {
                indexToLemma.put((Integer) token.get("index"), (String) token.get("lemma"));
            }
            List<Map<String, Object>> entityMentions = (List<Map<String, Object>>) sentence.get("entitymentions");

            if (entityMentions != null) {
                for (Map<String, Object> ent : entityMentions) {
                    String text = (String) ent.get("text");
                    String type = (String) ent.get("ner");
                    String lemma = indexToLemma.get(ent.get("headToken"));
                    if (!EXCLUDED_NER.contains(type)) {
                        // Añadir nodo con tipo NER real
                        addNode(text, type, nodes, textToNodeId, null);
                    }
                }
            }

            for (String combined : flatGroups.keySet()) {
                String lemma = combined.toLowerCase();
                if (isValidEntity(sentence, combined)) {
                    addNode(combined, "Concepto", nodes, textToNodeId, lemma);
                }
            }
        }
    }

    private void extractRelations(Map<String, Object> parsed, Map<String, GraphNode> nodes,
            Set<GraphEdge> edges, Map<String, Integer> degreeMap,
            Map<String, String> textToNodeId, Map<String, String> flatGroups) {
        Map<String, String> kbpMap = Map.of(
                "per:title", "es",
                "per:employee_of", "trabaja para",
                "org:top_members_employees", "tiene como miembro",
                "org:country_of_headquarters", "ubicado en",
                "per:countries_of_residence", "vive en");

        List<Map<String, Object>> sentences = (List<Map<String, Object>>) parsed.get("sentences");

        for (Map<String, Object> sentence : sentences) {
            List<Map<String, Object>> tokens = (List<Map<String, Object>>) sentence.get("tokens");
            Map<Integer, String> indexToPos = new HashMap<>();
            Map<Integer, String> indexToLemma = new HashMap<>();
            for (Map<String, Object> token : tokens) {
                indexToLemma.put((Integer) token.get("index"), (String) token.get("lemma"));
                indexToPos.put((Integer) token.get("index"), (String) token.get("pos"));
            }

            // Procesar OpenIE
            List<Map<String, Object>> openie = (List<Map<String, Object>>) sentence.get("openie");
            if (openie != null) {
                for (Map<String, Object> triple : openie) {
                    String subject = (String) triple.get("subject");
                    String object = (String) triple.get("object");
                    String relation = (String) triple.get("relation");

                    if (isValidRelation(relation, indexToPos)) {
                        if (isValidTriple(sentence, subject, object)) {
                            addNodeIfValid(subject, "Concepto", sentence, nodes, textToNodeId);
                            addNodeIfValid(object, "Concepto", sentence, nodes, textToNodeId);

                            // Obtener lemmas para subject y object
                            String subjectLemma = obtenerLemmaDeTexto(sentence, subject);
                            String objectLemma = obtenerLemmaDeTexto(sentence, object);

                            connect(subjectLemma, objectLemma, relation, nodes, edges, degreeMap, textToNodeId);

                        }
                    }

                }
            }

            // Procesar KBP
            List<Map<String, Object>> kbp = (List<Map<String, Object>>) sentence.get("kbp");
            if (kbp != null) {
                for (Map<String, Object> triple : kbp) {
                    System.out.println(triple);
                    String subject = (String) triple.get("subject");
                    String object = (String) triple.get("object");
                    String relation = kbpMap.getOrDefault((String) triple.get("relation"),
                            (String) triple.get("relation"));

                    if (isValidTriple(sentence, subject, object)) {
                        addNodeIfValid(subject, "Concepto", sentence, nodes, textToNodeId);
                        addNodeIfValid(object, "Concepto", sentence, nodes, textToNodeId);

                        // Obtener lemmas para subject y object
                        String subjectLemma = obtenerLemmaDeTexto(sentence, subject);
                        String objectLemma = obtenerLemmaDeTexto(sentence, object);

                        connect(subjectLemma, objectLemma, relation, nodes, edges, degreeMap, textToNodeId);

                    }
                }
            }

            // Procesar dependencias
            extractFromDependencies(sentence, nodes, edges, degreeMap, textToNodeId);
        }
    }

    private boolean isValidEntity(Map<String, Object> sentence, String text) {

        if (text.contains("\n") || STOPWORDS.contains(text.toLowerCase())) {
            System.out.println(text + "No es valid entity");
            return false;
        }

        List<Map<String, Object>> tokens = (List<Map<String, Object>>) sentence.get("tokens");
        boolean containsWord = false;
        for (Map<String, Object> token : tokens) {
            String word = (String) token.get("word");
            String pos = (String) token.get("pos");
            String ner = (String) token.get("ner");
            System.out.println(word + "este es word para" + text);
            if (text.contains(word)) {

                containsWord = true;
                if (!isValidPos(pos) || ner.equals("O")) {
                    return false;
                }
            }
        }
        // Si no contiene ninguna palabra del texto, no es válido
        if (!containsWord) {
            return false;
        }
        return true;
    }

    private boolean isValidPos(String pos) {
        return pos.startsWith("N") || pos.startsWith("PROPN") || pos.startsWith("ADJ");
    }

    private boolean isValidTriple(Map<String, Object> sentence, String subject, String object) {
        System.out.println(subject + "" + object);
        return subject != null && object != null && isValidEntity(sentence, subject) && isValidEntity(sentence, object);
    }

    private boolean isValidRelation(String relation, Map<Integer, String> indexToPos) {
        if (relation == null || relation.isEmpty())
            return false;
        String[] words = relation.split(" ");
        System.out.println(relation);
        for (String word : words) {
            if (word.length() < 3) {
                return false;
            }
        }
        return true;
    }

    private void addNodeIfValid(String text, String type, Map<String, Object> sentence,
            Map<String, GraphNode> nodes, Map<String, String> textToNodeId) {

        if (isValidEntity(sentence, text)) {
            System.err.println(textToNodeId);
            System.out.println("node valid y valid entity con kbp" + text);
            // Evitar crear nodo "Concepto" si ya existe nodo con tipo NER distinto de
            // "Concepto" para el mismo texto
            boolean existsNER = nodes.values().stream()
                    .anyMatch(n -> n.getText().equals(text) && !n.getType().equals("Concepto"));
            if (type.equals("Concepto") && existsNER) {
                System.out.println("este nodo ya existe" + text);
                return;
            }
            // List<Map<String, Object>> tokens = (List<Map<String, Object>>)
            // sentence.get("tokens");
            if (!nodes.containsKey(text)) {
                String lemma = obtenerLemmaDeTexto(sentence, text);
                System.out.println(nodes);
                System.out.println(lemma + "" + text);
                addNode(text, type, nodes, textToNodeId, lemma);
            }

        }
    }

    private String obtenerLemmaDeTexto(Map<String, Object> sentence, String text) {
        List<Map<String, Object>> tokens = (List<Map<String, Object>>) sentence.get("tokens");
        return tokens.stream()
                .filter(t -> text.equals(t.get("word")))
                .findFirst()
                .map(t -> (String) t.get("lemma"))
                .orElse(text);
    }

    private void extractFromDependencies(Map<String, Object> sentence, Map<String, GraphNode> nodes,
            Set<GraphEdge> edges, Map<String, Integer> degreeMap,
            Map<String, String> textToNodeId) {
        List<Map<String, Object>> deps = (List<Map<String, Object>>) sentence.get("enhancedPlusPlusDependencies");
        Map<String, String> wordToGov = new HashMap<>();
        Map<String, String> wordToRel = new HashMap<>();
        Map<String, String> verbToSubject = new HashMap<>();
        Map<String, String> verbToObject = new HashMap<>();

        for (Map<String, Object> dep : deps) {
            String depWord = (String) dep.get("dependentGloss");
            String govWord = (String) dep.get("governorGloss");
            String type = (String) dep.get("dep");

            if ("nsubj".equals(type)) {
                verbToSubject.put(govWord, depWord);
            } else if ("obj".equals(type)) {
                verbToObject.put(govWord, depWord);
            }

            wordToGov.put(depWord, govWord);
            wordToRel.put(depWord, type);
        }

        for (Map<String, Object> dep : deps) {
            String subject = (String) dep.get("dependentGloss");
            if ("nsubj".equals(dep.get("dep"))) {
                String verb = (String) dep.get("governorGloss");
                for (Map<String, Object> objDep : deps) {
                    if ("obj".equals(objDep.get("dep")) && objDep.get("governorGloss").equals(verb)) {
                        String object = (String) objDep.get("dependentGloss");
                        if (isValidTriple(sentence, subject, object)) {
                            addNodeIfValid(subject, "Concepto", sentence, nodes, textToNodeId);
                            addNodeIfValid(object, "Concepto", sentence, nodes, textToNodeId);
                            connect(subject, object, verb, nodes, edges, degreeMap, textToNodeId);
                        }
                    }
                }
            }
        }
        for (String verb : verbToSubject.keySet()) {
            String subject = verbToSubject.get(verb);
            if (verbToObject.containsKey(verb)) {
                String object = verbToObject.get(verb);
                if (isValidTriple(sentence, subject, object)) {
                    addNodeIfValid(subject, "Concepto", sentence, nodes, textToNodeId);
                    addNodeIfValid(object, "Concepto", sentence, nodes, textToNodeId);
                    connect(subject, object, verb, nodes, edges, degreeMap, textToNodeId);
                }
            }
        }
    }

    private void calculateImportance(Map<String, GraphNode> nodes, Map<String, Integer> degreeMap) {
        for (GraphNode node : nodes.values()) {
            int degree = degreeMap.getOrDefault(node.getText(), 0);
            node.setImportance(node.getFrequency() * (1 + degree));
        }
    }

    private void addNode(String text, String type, Map<String, GraphNode> nodes, Map<String, String> textToNodeId,
            String lemma) {
        String normalizedText = lemma != null ? lemma : text.toLowerCase();
        // Buscar si ya existe nodo con el mismo texto y tipo NER distinto de "Concepto"
        Optional<String> existingKey = textToNodeId.keySet().stream()
                .filter(k -> k.startsWith(normalizedText + "_") && !k.endsWith("_Concepto"))
                .findFirst();

        String uniqueKey;
        if (existingKey.isPresent()) {
            // Mantener tipo NER existente para evitar sobrescritura
            uniqueKey = existingKey.get();
        } else {
            uniqueKey = normalizedText + "_" + type;
        }

        if (!textToNodeId.containsKey(uniqueKey)) {
            String id = String.valueOf(nodeIdCounter++);
            GraphNode node = new GraphNode(id, uniqueKey.split("_", 2)[1], text, 1, 0.0);
            nodes.put(id, node);
            textToNodeId.put(uniqueKey, id);
        } else {
            String id = textToNodeId.get(uniqueKey);
            GraphNode node = nodes.get(id);
            node.setFrequency(node.getFrequency() + 1);
        }
    }

    private void connect(String fromText, String toText, String relation,
            Map<String, GraphNode> nodes, Set<GraphEdge> edges,
            Map<String, Integer> degreeMap, Map<String, String> textToId) {
        // Buscar claves exactas para fromText y toText con cualquier tipo
        /*
         * Optional<String> fromKeyOpt = textToNodeId.keySet().stream()
         * .filter(k -> k.startsWith(fromText + "_"))
         * .findFirst();
         * Optional<String> toKeyOpt = textToNodeId.keySet().stream()
         * .filter(k -> k.startsWith(toText + "_"))
         * .findFirst();
         * 
         * if (fromKeyOpt.isEmpty() || toKeyOpt.isEmpty()) {
         * return;
         * }
         * 
         * String fromKey = fromKeyOpt.get();
         * String toKey = toKeyOpt.get();
         */

        String fromKey = fromText + "_" + getNodeType(fromText, nodes);
        String toKey = toText + "_" + getNodeType(toText, nodes);
        String sourceId = textToId.get(fromKey);
        String targetId = textToId.get(toKey);

        if (sourceId != null && targetId != null && !sourceId.equals(targetId)) {
            // Check if the edge already exists to avoid duplicates
            boolean edgeExists = edges.stream().anyMatch(edge -> edge.getSource().equals(sourceId) &&
                    edge.getTarget().equals(targetId) && edge.getrelationship().equals(relation)

            );
            if (!edgeExists) {
                edges.add(new GraphEdge(sourceId, targetId, relation));
                degreeMap.put(fromText, degreeMap.getOrDefault(fromText, 0) + 1);
                degreeMap.put(toText, degreeMap.getOrDefault(toText, 0) + 1);
            }
        }
    }

    private String getNodeType(String text, Map<String, GraphNode> nodes) {
        return nodes.values().stream()
                .filter(n -> n.getText().equalsIgnoreCase(text))
                .findFirst()
                .map(GraphNode::getType)
                .orElse("Concepto");
    }

    private Map<String, String> groupFlatDependencies(Map<String, Object> parsed) {
        Map<String, String> combinedMap = new HashMap<>();

        List<Map<String, Object>> sentences = (List<Map<String, Object>>) parsed.get("sentences");
        for (Map<String, Object> sentence : sentences) {
            List<Map<String, Object>> dependencies = (List<Map<String, Object>>) sentence
                    .get("enhancedPlusPlusDependencies");
            Map<Integer, String> indexToWord = new HashMap<>();
            Map<Integer, String> indexToPos = new HashMap<>();
            List<Map<String, Object>> tokens = (List<Map<String, Object>>) sentence.get("tokens");
            for (Map<String, Object> token : tokens) {
                int index = (Integer) token.get("index");
                indexToWord.put(index, (String) token.get("word"));
                indexToPos.put(index, (String) token.get("pos"));
            }

            Map<Integer, StringBuilder> combinedPhrases = new HashMap<>();
            for (Map<String, Object> dep : dependencies) {
                String type = (String) dep.get("dep");
                Integer govIndex = (Integer) dep.get("governor");
                Integer depIndex = (Integer) dep.get("dependent");
                String depPos = indexToPos.get(depIndex);
                String govPos = indexToPos.get(govIndex);

                if (("flat".equals(type) || "compound".equals(type) || "amod".equals(type)) &&
                        (depPos.startsWith("N") || govPos.startsWith("N"))) {
                    String govWord = indexToWord.get(govIndex);
                    String depWord = indexToWord.get(depIndex);
                    if (!combinedPhrases.containsKey(govIndex)) {
                        combinedPhrases.put(govIndex, new StringBuilder(govWord));
                    }
                    combinedPhrases.get(govIndex).append(" ").append(depWord);
                }
            }

            for (StringBuilder phrase : combinedPhrases.values()) {
                String combined = phrase.toString().trim();
                if (isValidEntity(sentence, combined)) {
                    combinedMap.put(combined, combined);
                }
            }
        }
        return combinedMap;
    }
}