package com.service.web.app.models.entity;

import java.util.List;

public class GraphResult {
	List<Node> nodes;
	List<Edge> edges;

	// Constructor, Getters
	public GraphResult(List<Node> nodes, List<Edge> edges) {
		this.nodes = nodes;
		this.edges = edges;
	}

	public List<Node> getNodes() {
		return nodes;
	}

	public List<Edge> getEdges() {
		return edges;
	}
}