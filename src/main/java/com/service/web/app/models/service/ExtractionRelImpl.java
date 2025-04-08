package com.service.web.app.models.service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.service.web.app.models.entity.Relations;

@Service
public class ExtractionRelImpl implements Extractor {

    private final String CORENLP_URL = "https://corenlp-server.loca.lt";
    private final RestTemplate restTemplate = new RestTemplate();

    public List<Relations> extractTriples(List<String> textDoc) {
        List<Relations> relations = new ArrayList<>();
        for (String text : textDoc) {
            String requestJson = "{" + "\"annotators\": \"openie\"," + "\"input\": \"" + text + "\","
                    + "\"language\": \"es\"" + "}";

            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.postForObject(
                    CORENLP_URL + "/?properties=" + URLEncoder.encode(requestJson, StandardCharsets.UTF_8), null,
                    Map.class);
            System.out.println(response);

            relations.addAll(processResponse(response));
        }
        return relations;
    }

    private List<Relations> processResponse(Map<String, Object> response) {
        List<Relations> triples = new ArrayList<>();

        List<Map<String, Object>> sentences = (List<Map<String, Object>>) response.get("sentences");
        for (Map<String, Object> sentence : sentences) {
            List<Map<String, Object>> openie = (List<Map<String, Object>>) sentence.get("openie");
            for (Map<String, Object> tripleData : openie) {
                Relations triple = new Relations((String) tripleData.get("subject"), (String) tripleData.get("object"),
                        (String) tripleData.get("relation"));
                /*
                 * triple.setSubject((String) tripleData.get("subject"));
                 * triple.setRelation((String) tripleData.get("relation"));
                 * triple.setObject((String) tripleData.get("object"));
                 */
                triples.add(triple);
                System.out.println();
            }
        }
        return triples;
    }
}