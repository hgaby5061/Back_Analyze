package com.service.web.app.controllers;

import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.RequestEntity.HeadersBuilder;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.service.web.app.models.entity.Document;
import com.service.web.app.models.entity.Relations;
import com.service.web.app.models.service.Extractor;
import com.service.web.app.models.service.GraphKnow;
import com.service.web.app.models.service.INerService;

@RestController
@RequestMapping("/nlp")

public class NerController {

	@Autowired
	private INerService nerServcie;

	@Autowired
	private Extractor extract;

	@Autowired
	private GraphKnow graph;

	@PostMapping("/relations")
	public ResponseEntity<String> extract(@RequestBody List<String> doc) {
		// List<Relations> rel = null;
		String rel = null;
		if (doc.size() > 0) {
			System.out.println(doc.size());
			// Recibir del front solo los textos de cada disurso

			rel = extract.extractTriples(doc);
			if (rel == null) {
				return ResponseEntity.badRequest().body(rel);
			}

			System.out.println("TERMINO RELATIONS");
			return ResponseEntity.ok(rel);
		}

		return ResponseEntity.badRequest().body(rel);
	}

	@PostMapping("/ner")
	public ResponseEntity<List<Document>> entities(@RequestBody List<Document> document) {
		List<Document> entity = null;
		if (document.size() > 0) {
			entity = nerServcie.entyByText(document);
			if (entity == null) {
				return ResponseEntity.badRequest().body(entity);
			}
			System.out.println("TERMINO NER");
			return ResponseEntity.ok(entity);
		}
		return ResponseEntity.badRequest().body(entity);
	}

	@PostMapping("/graph")
	public ResponseEntity<String> graph(@RequestBody List<Document> document) {
		String entity = null;
		if (document.size() > 0) {
			entity = graph.generateGraph(document);
			if (entity == null) {
				return ResponseEntity.badRequest().body("Faltan datos");
			}
			System.out.println("TERMINO GRAPH");
			return ResponseEntity.ok(entity);
		}
		return ResponseEntity.badRequest().body("No hay documentos");
	}

}
