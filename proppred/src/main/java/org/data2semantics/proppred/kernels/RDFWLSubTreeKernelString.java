package org.data2semantics.proppred.kernels;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.data2semantics.proppred.libsvm.SparseVector;
import org.data2semantics.tools.graphs.Edge;
import org.data2semantics.tools.graphs.Vertex;
import org.data2semantics.tools.rdf.RDFDataSet;
import org.openrdf.model.Resource;
import org.openrdf.model.Statement;
import edu.uci.ics.jung.graph.DirectedGraph;
import edu.uci.ics.jung.graph.DirectedSparseMultigraph;
import edu.uci.ics.jung.graph.util.EdgeType;

/**
 * This class implements a WL kernel directly on an RDF graph. The difference with a normal WL kernel is that subgraphs are not 
 * explicitly extracted. However we use the idea of subgraph implicitly by tracking for each vertex/edge the distance from an instance vertex.
 * For one thing, this leads to the fact that 1 black list is applied to the entire RDF graph, instead of 1 (small) blacklist per graph. 
 * 
 *
 * 
 * @author Gerben
 *
 */
public class RDFWLSubTreeKernelString implements RDFGraphKernel, RDFFeatureVectorKernel {
	private static final String ROOT_LABEL = "1";
	private static final String BLANK_VERTEX_LABEL = "1";
	private static final String BLANK_EDGE_LABEL   = "2";

	private Map<String, String> labelMap;
	private Map<String, Vertex<Map<Integer,String>>> instanceVertices;
	private Map<String, Map<Vertex<Map<Integer,String>>, Integer>> instanceVertexIndexMap;
	private Map<String, Map<Edge<Map<Integer,String>>, Integer>> instanceEdgeIndexMap;

	private int labelCounter;
	private int depth;
	private int iterations;
	private boolean inference;
	private boolean blankLabels;
	private String label;
	private boolean normalize;


	public RDFWLSubTreeKernelString(int iterations, int depth, boolean inference, boolean normalize, boolean blankLabels) {
		this(iterations, depth, inference, normalize);
		this.blankLabels = blankLabels;

	}

	public RDFWLSubTreeKernelString(int iterations, int depth, boolean inference, boolean normalize) {
		this.normalize = normalize;
		this.label = "RDF_WL_Kernel_" + depth + "_" + iterations;
		this.blankLabels = false;

		labelMap = new HashMap<String, String>();
		instanceVertices = new HashMap<String, Vertex<Map<Integer,String>>>();
		this.instanceVertexIndexMap = new HashMap<String, Map<Vertex<Map<Integer,String>>, Integer>>();
		this.instanceEdgeIndexMap = new HashMap<String, Map<Edge<Map<Integer,String>>, Integer>>();

		labelCounter = 2;
		this.depth = depth;
		this.inference = inference;
		this.iterations = iterations;
	}


	public RDFWLSubTreeKernelString() {
		this(2, 2, false, true);
	}



	public String getLabel() {
		return label;
	}

	public void setNormalize(boolean normalize) {
		this.normalize = normalize;
	}

	public SparseVector[] computeFeatureVectors(RDFDataSet dataset, List<Resource> instances, List<Statement> blackList) {
		SparseVector[] featureVectors = new SparseVector[instances.size()];
		for (int i = 0; i < featureVectors.length; i++) {
			featureVectors[i] = new SparseVector();
		}	

		DirectedGraph<Vertex<Map<Integer,String>>,Edge<Map<Integer,String>>> graph = createGraphFromRDF(dataset, instances, blackList);
		createInstanceIndexMaps(graph, instances);

		if (blankLabels) {
			setBlankLabels(graph);
		}

		computeFVs(graph, instances, Math.sqrt(1.0 / ((double) (iterations + 1))), featureVectors);

		int startLabel = 1; // start at 1, since featureVectors need to start at index 1
		for (int i = 0; i < iterations; i++) {	
			relabelGraph2MultisetLabels(graph, startLabel);
			startLabel = labelCounter;
			compressGraphLabels(graph);
			computeFVs(graph, instances, Math.sqrt((2.0 + i) / ((double) (iterations + 1))), featureVectors);
		}
		if (this.normalize) {
			featureVectors = KernelUtils.normalize(featureVectors);
		}

		return featureVectors;
	}

	public double[][] compute(RDFDataSet dataset, List<Resource> instances, List<Statement> blackList) {
		SparseVector[] featureVectors = computeFeatureVectors(dataset, instances, blackList);
		double[][] kernel = KernelUtils.initMatrix(instances.size(), instances.size());
		computeKernelMatrix(instances, featureVectors, kernel);
		return kernel;
	}


	/*
	public double[][] compute(RDFDataSet dataset, List<Resource> instances, List<Statement> blackList) {
		DirectedGraph<Vertex<Map<Integer,String>>,Edge<Map<Integer,String>>> graph = createGraphFromRDF(dataset, instances, blackList);
		createInstanceIndexMaps(graph, instances);

		if (blankLabels) {
			setBlankLabels(graph);
		}

		SparseVector[] featureVectors = new SparseVector[instances.size()];
		for (int i = 0; i < featureVectors.length; i++) {
			featureVectors[i] = new SparseVector();
		}
		computeFVs(graph, instances, 1, featureVectors);

		double[][] kernel = initMatrix(instances.size(), instances.size());
		computeKernelMatrix(instances, featureVectors, kernel, 1.0 / ((double) (iterations + 1)));


		int startLabel = 1; // start at 1, since featureVectors need to start at index 1
		for (int i = 0; i < iterations; i++) {	
			relabelGraph2MultisetLabels(graph, startLabel);
			startLabel = labelCounter;
			compressGraphLabels(graph);

			featureVectors = new SparseVector[instances.size()];
			for (int j = 0; j < featureVectors.length; j++) {
				featureVectors[j] = new SparseVector();
			}	
			computeFVs(graph, instances, 1, featureVectors);
			computeKernelMatrix(instances, featureVectors, kernel, (2.0 + i) / ((double) (iterations + 1)));
		}
		if (this.normalize) {
			kernel = Kernel.normalize(kernel);
		}
		return kernel;
	}
	 */

	private DirectedGraph<Vertex<Map<Integer,String>>,Edge<Map<Integer,String>>> createGraphFromRDF(RDFDataSet dataset, List<Resource> instances, List<Statement> blackList) {
		Map<String, Vertex<Map<Integer,String>>> vertexMap = new HashMap<String, Vertex<Map<Integer, String>>>();
		Map<String, Edge<Map<Integer,String>>> edgeMap = new HashMap<String, Edge<Map<Integer, String>>>();

		DirectedGraph<Vertex<Map<Integer,String>>,Edge<Map<Integer,String>>> graph = new DirectedSparseMultigraph<Vertex<Map<Integer,String>>,Edge<Map<Integer,String>>>();

		List<Resource> queryNodes = new ArrayList<Resource>();
		List<Resource> newQueryNodes;
		List<Statement> result;

		Vertex<Map<Integer,String>> startV;
		Vertex<Map<Integer,String>> newV;
		Edge<Map<Integer,String>> newE;

		String idStr, idStr2;

		for (Resource instance : instances) {
			idStr = instance.toString();			
			// If the instance is already part of the graph (because it was retrieved for an earlier instance), 
			// then we use that one, but we need to change the labels to the start label of instance nodes, for which we use ROOT_LABEL
			if (vertexMap.containsKey(idStr)) {
				startV = vertexMap.get(idStr);
				for (int di : startV.getLabel().keySet()) {
					startV.getLabel().put(di, ROOT_LABEL);
				}

				// Else we construct a new node for the instance, and label it with ROOT_LABEL for the provided depth
			} else {
				startV = new Vertex<Map<Integer,String>>(new HashMap<Integer, String>());
				vertexMap.put(idStr, startV);
				graph.addVertex(startV);
			}
			startV.getLabel().put(depth, ROOT_LABEL); 
			labelMap.put(idStr, ROOT_LABEL); // This label is (re)set to ROOT_LABEL
			instanceVertices.put(idStr, startV); // So that we can reconstruct subgraphs later, we save the instance vertices

			queryNodes.add(instance);

			for (int i = depth - 1; i >= 0; i--) {
				newQueryNodes = new ArrayList<Resource>();

				for (Resource queryNode : queryNodes) {
					result = dataset.getStatements(queryNode, null, null, inference);

					for (Statement stmt : result) {

						// Process new vertex
						idStr = stmt.getObject().toString();
						if (vertexMap.containsKey(idStr)) { // existing vertex
							newV = vertexMap.get(idStr);				 
							newV.getLabel().put(i, labelMap.get(idStr)); // Set the label for depth i to the already existing label for this vertex

						} else { // New vertex
							newV = new Vertex<Map<Integer,String>>(new HashMap<Integer, String>());
							labelMap.put(idStr, Integer.toString(labelCounter));
							newV.getLabel().put(i, Integer.toString(labelCounter));
							labelCounter++;
							vertexMap.put(idStr, newV);
							graph.addVertex(newV);
						}

						// Process new Edge
						idStr = stmt.toString();
						idStr2 = stmt.getPredicate().toString();
						if (edgeMap.containsKey(idStr)) { // existing edge
							newE = edgeMap.get(idStr);
							newE.getLabel().put(i, labelMap.get(idStr2)); // Set the label for depth i to the already existing label for this edge

						} else { // new edge
							newE = new Edge<Map<Integer,String>>(new HashMap<Integer,String>());
							if (!labelMap.containsKey(idStr2)) { // Edge labels are not unique, in contrast to vertex labels, thus we need to check whether it exists already
								labelMap.put(idStr2, Integer.toString(labelCounter));
								labelCounter++;
							}
							newE.getLabel().put(i, labelMap.get(idStr2));
							edgeMap.put(idStr, newE);	
							graph.addEdge(newE, vertexMap.get(stmt.getSubject().toString()), newV, EdgeType.DIRECTED);
						}


						// Store the object nodes if the loop continues (i>0) and if its a Resource
						if (i > 0 && stmt.getObject() instanceof Resource) {
							newQueryNodes.add((Resource) stmt.getObject());
						}
					}
				}

				queryNodes = newQueryNodes;
			}		
		}

		// Remove edges for statements on the blackList
		for (Statement stmt : blackList) {
			graph.removeEdge(edgeMap.get(stmt.toString()));
		}
		return graph;
	}


	private void createInstanceIndexMaps(DirectedGraph<Vertex<Map<Integer,String>>, Edge<Map<Integer,String>>> graph, List<Resource> instances) {
		Vertex<Map<Integer, String>> startV;
		List<Vertex<Map<Integer, String>>> frontV, newFrontV;
		Map<Vertex<Map<Integer, String>>, Integer> vertexIndexMap;
		Map<Edge<Map<Integer, String>>, Integer> edgeIndexMap;

		for (int i = 0; i < instances.size(); i++) {				
			vertexIndexMap = new HashMap<Vertex<Map<Integer, String>>, Integer>();
			edgeIndexMap   = new HashMap<Edge<Map<Integer, String>>, Integer>();

			instanceVertexIndexMap.put(instances.get(i).toString(), vertexIndexMap);
			instanceEdgeIndexMap.put(instances.get(i).toString(), edgeIndexMap);


			// Get the start node
			startV = instanceVertices.get(instances.get(i).toString());
			frontV = new ArrayList<Vertex<Map<Integer,String>>>();
			frontV.add(startV);

			// Process the start node
			vertexIndexMap.put(startV, depth);

			for (int j = depth - 1; j >= 0; j--) {
				newFrontV = new ArrayList<Vertex<Map<Integer,String>>>();
				for (Vertex<Map<Integer, String>> qV : frontV) {
					for (Edge<Map<Integer, String>> edge : graph.getOutEdges(qV)) {
						// Process the edge, if we haven't seen it before
						if (!edgeIndexMap.containsKey(edge)) {
							edgeIndexMap.put(edge, j);
						}

						// Process the vertex if we haven't seen it before
						if (!vertexIndexMap.containsKey(graph.getDest(edge))) {
							vertexIndexMap.put(graph.getDest(edge), j);
						}

						// Add the vertex to the new front, if we go into a new round
						if (j > 0) {
							newFrontV.add(graph.getDest(edge));
						}
					}
				}
				frontV = newFrontV;
			}
		}		
	}



	private void relabelGraph2MultisetLabels(DirectedGraph<Vertex<Map<Integer,String>>, Edge<Map<Integer,String>>> graph, int startLabel) {
		Map<String, Bucket<VertexIndexPair>> bucketsV = new HashMap<String, Bucket<VertexIndexPair>>();
		Map<String, Bucket<EdgeIndexPair>> bucketsE   = new HashMap<String, Bucket<EdgeIndexPair>>();

		// Initialize buckets
		for (int i = startLabel; i < labelCounter; i++) {
			bucketsV.put(Integer.toString(i), new Bucket<VertexIndexPair>(Integer.toString(i)));
			bucketsE.put(Integer.toString(i), new Bucket<EdgeIndexPair>(Integer.toString(i)));
		}

		// 1. Fill buckets 
		// Add each edge source (i.e.) start vertex to the bucket of the edge label
		for (Edge<Map<Integer,String>> edge : graph.getEdges()) {
			// for each label we add a vertex-index-pair to the bucket
			for (int index : edge.getLabel().keySet()) {
				bucketsV.get(edge.getLabel().get(index)).getContents().add(new VertexIndexPair(graph.getDest(edge), index));
			}
		}

		// Add each incident edge to the bucket of the node label
		for (Vertex<Map<Integer,String>> vertex : graph.getVertices()) {			
			Collection<Edge<Map<Integer,String>>> v2 = graph.getOutEdges(vertex);	

			for (int index : vertex.getLabel().keySet()) {
				if (index > 0) { // If index is 0 then we treat it as a fringe node, thus the label will not be propagated to the edges
					for (Edge<Map<Integer,String>> e2 : v2) {
						bucketsE.get(vertex.getLabel().get(index)).getContents().add(new EdgeIndexPair(e2, index - 1));
					}
				}
			}
		}	

		// 2. add bucket labels to existing labels
		// Change the original label to a prefix label

		for (Edge<Map<Integer,String>> edge : graph.getEdges()) {
			for (int i : edge.getLabel().keySet()) {
				edge.getLabel().put(i,edge.getLabel().get(i) + "_");   
			}

		}
		for (Vertex<Map<Integer,String>> vertex : graph.getVertices()) {
			for (int i : vertex.getLabel().keySet()) {
				vertex.getLabel().put(i, vertex.getLabel().get(i) + "_"); 
			}
		}

		// 3. Relabel to the labels in the buckets
		for (int i = startLabel; i < labelCounter; i++) {
			// Process vertices
			Bucket<VertexIndexPair> bucketV = bucketsV.get(Integer.toString(i));			
			for (VertexIndexPair vp : bucketV.getContents()) {
				vp.getVertex().getLabel().put(vp.getIndex(), vp.getVertex().getLabel().get(vp.getIndex()) + bucketV.getLabel());
			}
			// Process edges
			Bucket<EdgeIndexPair> bucketE = bucketsE.get(Integer.toString(i));			
			for (EdgeIndexPair ep : bucketE.getContents()) {
				ep.getEdge().getLabel().put(ep.getIndex(), ep.getEdge().getLabel().get(ep.getIndex()) + bucketE.getLabel());
			}
		}
	}


	private void compressGraphLabels(DirectedGraph<Vertex<Map<Integer,String>>, Edge<Map<Integer,String>>> graph) {
		String label;

		for (Edge<Map<Integer,String>> edge : graph.getEdges()) {
			for (int i : edge.getLabel().keySet()) {
				label = labelMap.get(edge.getLabel().get(i));						
				if (label == null) {					
					label = Integer.toString(labelCounter);
					labelCounter++;
					labelMap.put(edge.getLabel().get(i), label);				
				}
				edge.getLabel().put(i, label);
			}
		}

		for (Vertex<Map<Integer,String>> vertex : graph.getVertices()) {
			for (int i : vertex.getLabel().keySet()) {
				label = labelMap.get(vertex.getLabel().get(i));
				if (label == null) {
					label = Integer.toString(labelCounter);
					labelCounter++;
					labelMap.put(vertex.getLabel().get(i), label);
				}
				vertex.getLabel().put(i, label);
			}
		}
	}



	/**
	 * The computation of the feature vectors assumes that each edge and vertex is only processed once. We can encounter the same
	 * vertex/edge on different depths during computation, this could lead to multiple counts of the same vertex, possibly of different
	 * depth labels.
	 * 
	 * @param graph
	 * @param instances
	 * @param weight
	 * @param featureVectors
	 */
	private void computeFVs(DirectedGraph<Vertex<Map<Integer,String>>, Edge<Map<Integer,String>>> graph, List<Resource> instances, double weight, SparseVector[] featureVectors) {
		int index;
		Map<Vertex<Map<Integer,String>>, Integer> vertexIndexMap;
		Map<Edge<Map<Integer,String>>, Integer> edgeIndexMap;

		for (int i = 0; i < instances.size(); i++) {
			featureVectors[i].setLastIndex(labelCounter - 1);
			
			vertexIndexMap = instanceVertexIndexMap.get(instances.get(i).toString());
			for (Vertex<Map<Integer,String>> vertex : vertexIndexMap.keySet()) {
				index = Integer.parseInt(vertex.getLabel().get(vertexIndexMap.get(vertex)).toString());
				featureVectors[i].setValue(index, featureVectors[i].getValue(index) + weight);
			}
			edgeIndexMap = instanceEdgeIndexMap.get(instances.get(i).toString());
			for (Edge<Map<Integer,String>> edge : edgeIndexMap.keySet()) {
				index = Integer.parseInt(edge.getLabel().get(edgeIndexMap.get(edge)).toString());
				featureVectors[i].setValue(index, featureVectors[i].getValue(index) + weight);
			}
		}
	}



	/**
	 * The computation of the feature vectors assumes that each edge and vertex is only processed once. We can encounter the same
	 * vertex/edge on different depths during computation, this could lead to multiple counts of the same vertex, possibly of different
	 * depth labels.
	 * 
	 * 
	 * @param graph
	 * @param instances
	 * @param startLabel
	 * @param featureVectors
	 */

	/*
	private void computeFeatureVectors(DirectedGraph<Vertex<Map<Integer,String>>, Edge<Map<Integer,String>>> graph, List<Resource> instances, int startLabel, SparseVector[] featureVectors) {
		int index;
		Map<Vertex<Map<Integer,String>>, Integer> vertexIndexMap;
		Map<Edge<Map<Integer,String>>, Integer> edgeIndexMap;

		for (int i = 0; i < instances.size(); i++) {
			featureVectors[i] = new SparseVector();
			//featureVectors[i] = new double[labelCounter - startLabel];
			//Arrays.fill(featureVectors[i], 0.0);

			vertexIndexMap = instanceVertexIndexMap.get(instances.get(i).toString());
			for (Vertex<Map<Integer,String>> vertex : vertexIndexMap.keySet()) {
				index = Integer.parseInt(vertex.getLabel().get(vertexIndexMap.get(vertex))) - startLabel;
				featureVectors[i].setValue(index, featureVectors[i].getValue(index) + 1);
				//featureVectors[i][index] += 1.0;
			}
			edgeIndexMap = instanceEdgeIndexMap.get(instances.get(i).toString());
			for (Edge<Map<Integer,String>> edge : edgeIndexMap.keySet()) {
				index = Integer.parseInt(edge.getLabel().get(edgeIndexMap.get(edge))) - startLabel;
				featureVectors[i].setValue(index, featureVectors[i].getValue(index) + 1);
				//featureVectors[i][index] += 1.0;
			}
		}
	}*/


	/*
	private void computeFeatureVectors(DirectedGraph<Vertex<Map<Integer,String>>, Edge<Map<Integer,String>>> graph, List<Resource> instances, int startLabel, double[][] featureVectors) {
		int index;
		Vertex<Map<Integer, String>> startV;
		List<Vertex<Map<Integer, String>>> frontV, newFrontV;
		Set<Vertex<Map<Integer, String>>> proccedV;
		Set<Edge<Map<Integer, String>>> proccedE;


		for (int i = 0; i < instances.size(); i++) {		
			// new empty processed lists
			proccedV = new HashSet<Vertex<Map<Integer, String>>>();
			proccedE = new HashSet<Edge<Map<Integer, String>>>();

			// Initialize current feature vector to all 0's
			featureVectors[i] = new double[labelCounter - startLabel];		
			Arrays.fill(featureVectors[i], 0.0);

			// Get the start node
			startV = instanceVertices.get(instances.get(i).toString());
			frontV = new ArrayList<Vertex<Map<Integer,String>>>();
			frontV.add(startV);

			// Process the start node
			index = Integer.parseInt(startV.getLabel().get(depth)) - startLabel;		
			featureVectors[i][index] += 1.0;
			proccedV.add(startV);

			for (int j = depth - 1; j >= 0; j--) {
				newFrontV = new ArrayList<Vertex<Map<Integer,String>>>();
				for (Vertex<Map<Integer, String>> qV : frontV) {
					for (Edge<Map<Integer, String>> edge : graph.getOutEdges(qV)) {
						// Process the edge, if we haven't seen it before
						if (!proccedE.contains(edge)) {
							index = Integer.parseInt(edge.getLabel().get(j)) - startLabel;
							featureVectors[i][index] += 1.0;
							proccedE.add(edge);
						}

						// Process the vertex if we haven't seen it before
						if (!proccedV.contains(graph.getDest(edge))) {
							index = Integer.parseInt(graph.getDest(edge).getLabel().get(j)) - startLabel;
							featureVectors[i][index] += 1.0;
							proccedV.add(graph.getDest(edge));
						}

						// Add the vertex to the new front, if we go into a new round
						if (j > 0) {
							newFrontV.add(graph.getDest(edge));
						}
					}
				}
				frontV = newFrontV;
			}
		}
	}
	 */

	/*
	private void computeKernelMatrix(List<Resource> instances, SparseVector[] featureVectors, double[][] kernel, double weight) {
		for (int i = 0; i < instances.size(); i++) {
			for (int j = i; j < instances.size(); j++) {
				kernel[i][j] += featureVectors[i].dot(featureVectors[j]) * weight;
				kernel[j][i] = kernel[i][j];
			}
		}
	}
	 */


	private void computeKernelMatrix(List<Resource> instances, SparseVector[] featureVectors, double[][] kernel) {
		for (int i = 0; i < instances.size(); i++) {
			for (int j = i; j < instances.size(); j++) {
				kernel[i][j] += featureVectors[i].dot(featureVectors[j]);
				kernel[j][i] = kernel[i][j];
			}
		}
	}

	private void setBlankLabels(DirectedGraph<Vertex<Map<Integer,String>>, Edge<Map<Integer,String>>> graph) {
		for (Vertex<Map<Integer,String>> v : graph.getVertices()) {
			for (int k : v.getLabel().keySet()) {
				v.getLabel().put(k, BLANK_VERTEX_LABEL);
			}
		}

		for (Edge<Map<Integer,String>> e : graph.getEdges()) {
			for (int k : e.getLabel().keySet()) {
				e.getLabel().put(k, BLANK_EDGE_LABEL);
			}
		}	
	}


	private class VertexIndexPair {
		private Vertex<Map<Integer,String>> vertex;
		private int index;

		public VertexIndexPair(Vertex<Map<Integer, String>> vertex, int index) {
			this.vertex = vertex;
			this.index = index;
		}

		public Vertex<Map<Integer, String>> getVertex() {
			return vertex;
		}
		public int getIndex() {
			return index;
		}		
	}

	private class EdgeIndexPair {
		private Edge<Map<Integer,String>> edge;
		private int index;

		public EdgeIndexPair(Edge<Map<Integer, String>> edge, int index) {
			this.edge = edge;
			this.index = index;
		}

		public Edge<Map<Integer, String>> getEdge() {
			return edge;
		}
		public int getIndex() {
			return index;
		}		
	}

}
