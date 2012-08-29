package org.data2semantics.tools.experiments;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.data2semantics.tools.graphs.DirectedMultigraphWithRoot;
import org.data2semantics.tools.graphs.Edge;
import org.data2semantics.tools.graphs.GraphFactory;
import org.data2semantics.tools.graphs.Graphs;
import org.data2semantics.tools.graphs.Vertex;
import org.data2semantics.tools.rdf.RDFDataSet;
import org.openrdf.model.Statement;
import org.openrdf.model.URI;

import edu.uci.ics.jung.graph.DirectedGraph;
import edu.uci.ics.jung.graph.util.Pair;


// TODO add random seed to dataset initialization
public class DataSetFactory {

	public static GraphClassificationDataSet createClassificationDataSet(DataSetParameters params) {
		return createClassificationDataSet(params.getRdfDataSet(), params.getProperty(), params.getBlackList(), params.getDepth(), params.isIncludeInverse(), params.isIncludeInference());
	}


	public static GraphClassificationDataSet createClassificationDataSet(RDFDataSet rdfDataSet, String property, List<String> blackList, int depth, boolean includeInverse, boolean includeInference) {
		List<DirectedMultigraphWithRoot<Vertex<String>, Edge<String>>> graphs = new ArrayList<DirectedMultigraphWithRoot<Vertex<String>, Edge<String>>>();
		List<String> labels = new ArrayList<String>();
		StringBuffer label = new StringBuffer();

		label.append(rdfDataSet.getLabel());
		label.append(", ");
		label.append(property);
		label.append(", depth=");
		label.append(depth);
		label.append(", Inverse=");
		label.append(includeInverse);
		label.append(", Inference=");
		label.append(includeInference);

		List<Statement> triples = rdfDataSet.getStatementsFromStrings(null, property, null, false);	

		for (Statement triple : triples) {
			if (triple.getSubject() instanceof URI) {
				DirectedMultigraphWithRoot<Vertex<String>, Edge<String>> graph = GraphFactory.copyDirectedGraph2GraphWithRoot(GraphFactory.createDirectedGraph(rdfDataSet.getSubGraph((URI) triple.getSubject(), depth, includeInverse, includeInference)));
				graphs.add(graph);
				labels.add(triple.getObject().toString());
				graph.setRootVertex((findVertex(graph, triple.getSubject().toString())));
				Graphs.removeVerticesAndEdges(graph, null, blackList);
			}
		}

		return new GraphClassificationDataSet(label.toString(), graphs, labels);
	}

	
	public static LinkPredictionDataSet createLinkPredictonDataSet(DataSetParameters params) {
		return createLinkPredictonDataSet(params.getRdfDataSet(), params.getClassA(), params.getClassB(), params.getProperty(), params.getBlackList(), params.getDepth(), params.isIncludeInverse(), params.isIncludeInference());
	}
	

	public static LinkPredictionDataSet createLinkPredictonDataSet(RDFDataSet rdfDataSet, String classA, String classB, String property, List<String> blackList, int depth, boolean includeInverse, boolean includeInference) {
		List<DirectedMultigraphWithRoot<Vertex<String>, Edge<String>>> graphsA = new ArrayList<DirectedMultigraphWithRoot<Vertex<String>, Edge<String>>>();
		List<DirectedMultigraphWithRoot<Vertex<String>, Edge<String>>> graphsB = new ArrayList<DirectedMultigraphWithRoot<Vertex<String>, Edge<String>>>();
		List<Vertex<String>> rootVerticesA = new ArrayList<Vertex<String>>();
		List<Vertex<String>> rootVerticesB = new ArrayList<Vertex<String>>();
		Map<Pair<DirectedMultigraphWithRoot<Vertex<String>,Edge<String>>>, Boolean> labels = new HashMap<Pair<DirectedMultigraphWithRoot<Vertex<String>,Edge<String>>>, Boolean>();
		
		StringBuffer label = new StringBuffer();
		label.append(rdfDataSet.getLabel());
		label.append(", ");
		label.append(property);
		label.append(", depth=");
		label.append(depth);
		label.append(", Inverse=");
		label.append(includeInverse);
		label.append(", Inference=");
		label.append(includeInference);
		

		List<Statement> triples = rdfDataSet.getStatementsFromStrings(null, "http://www.w3.org/1999/02/22-rdf-syntax-ns#type", classA, false);	
		for (Statement triple : triples) {
			DirectedMultigraphWithRoot<Vertex<String>, Edge<String>> graph = GraphFactory.copyDirectedGraph2GraphWithRoot(GraphFactory.createDirectedGraph(rdfDataSet.getSubGraph((URI) triple.getSubject(), depth, includeInverse, includeInference)));
			graphsA.add(graph);
			graph.setRootVertex((findVertex(graph, triple.getSubject().toString())));
			rootVerticesA.add(findVertex(graph, triple.getSubject().toString()));
			Graphs.removeVerticesAndEdges(graph, null, blackList);
		}
		
		triples = rdfDataSet.getStatementsFromStrings(null, "http://www.w3.org/1999/02/22-rdf-syntax-ns#type", classB, false);	
		for (Statement triple : triples) {
			DirectedMultigraphWithRoot<Vertex<String>, Edge<String>> graph = GraphFactory.copyDirectedGraph2GraphWithRoot(GraphFactory.createDirectedGraph(rdfDataSet.getSubGraph((URI) triple.getSubject(), depth, includeInverse, includeInference)));
			graphsB.add(graph);
			graph.setRootVertex((findVertex(graph, triple.getSubject().toString())));
			rootVerticesB.add(findVertex(graph, triple.getSubject().toString()));
			Graphs.removeVerticesAndEdges(graph, null, blackList);
		}
		
		for (int i = 0; i < rootVerticesA.size(); i++) {
			for (int j = 0; j < rootVerticesB.size(); j++) {
				List<Statement> triples3 = rdfDataSet.getStatementsFromStrings(rootVerticesA.get(i).getLabel(), null, rootVerticesB.get(j).getLabel(), false);
				labels.put(new Pair<DirectedMultigraphWithRoot<Vertex<String>, Edge<String>>>(graphsA.get(i), graphsB.get(j)), false);
				for (Statement triple : triples3) {
					if (triple.getPredicate().toString().equals(property)) {
						labels.put(new Pair<DirectedMultigraphWithRoot<Vertex<String>, Edge<String>>>(graphsA.get(i), graphsB.get(j)), true);
					}
				}
			}
		}

		return new LinkPredictionDataSet(label.toString(), graphsA, graphsB, labels);
	}




	private static Vertex<String> findVertex(DirectedGraph<Vertex<String>, Edge<String>> graph, String vertexLabel) {	
		for (Vertex<String> vertex : graph.getVertices()) {
			if (vertex.getLabel().equals(vertexLabel)) {
				return vertex;
			}
		}
		return null;
	}

}
