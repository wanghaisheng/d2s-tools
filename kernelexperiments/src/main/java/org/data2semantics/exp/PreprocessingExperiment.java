package org.data2semantics.exp;

import java.sql.Date;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import org.data2semantics.exp.RDFMLExperiment;
import org.data2semantics.exp.molecules.GraphUtils;
import org.data2semantics.exp.molecules.MoleculeGraphExperiment;
import org.data2semantics.exp.molecules.RDFIntersectionSubTreeSlashBurnKernel;
import org.data2semantics.exp.molecules.RDFWLSubTreeSlashBurnKernel;
import org.data2semantics.exp.molecules.WLSubTreeKernel;
import org.data2semantics.exp.utils.KernelExperiment;
import org.data2semantics.exp.utils.RDFGraphKernelExperiment;
import org.data2semantics.exp.utils.RDFLinearKernelExperiment;
import org.data2semantics.exp.utils.RDFOldKernelExperiment;
import org.data2semantics.exp.utils.Result;
import org.data2semantics.exp.utils.ResultsTable;
import org.data2semantics.proppred.kernels.rdfgraphkernels.RDFCombinedKernel;
import org.data2semantics.proppred.kernels.rdfgraphkernels.RDFFeatureVectorKernel;
import org.data2semantics.proppred.kernels.rdfgraphkernels.RDFGraphKernel;
import org.data2semantics.proppred.kernels.rdfgraphkernels.RDFIntersectionSubTreeKernel;
import org.data2semantics.proppred.kernels.rdfgraphkernels.RDFIntersectionTreeEdgeVertexPathKernel;
import org.data2semantics.proppred.kernels.rdfgraphkernels.RDFIntersectionTreeEdgeVertexPathWithTextKernel;
import org.data2semantics.proppred.kernels.rdfgraphkernels.RDFIntersectionTreeEdgeVertexWithSuperTypesPathKernel;
import org.data2semantics.proppred.kernels.rdfgraphkernels.RDFWLBiSubTreeKernel;
import org.data2semantics.proppred.kernels.rdfgraphkernels.RDFWLSubTreeKernel;
import org.data2semantics.proppred.kernels.rdfgraphkernels.RDFWLSubTreeWithTextKernel;
import org.data2semantics.proppred.learners.evaluation.Error;
import org.data2semantics.proppred.learners.evaluation.EvaluationFunction;
import org.data2semantics.proppred.learners.evaluation.EvaluationUtils;
import org.data2semantics.proppred.learners.evaluation.F1;
import org.data2semantics.proppred.learners.liblinear.LibLINEARParameters;
import org.data2semantics.proppred.learners.libsvm.LibSVM;
import org.data2semantics.proppred.learners.libsvm.LibSVMParameters;
import org.data2semantics.tools.rdf.RDFFileDataSet;
import org.nodes.DTGraph;
import org.nodes.DTNode;
import org.nodes.DegreeComparator;
import org.nodes.Node;
import org.nodes.algorithms.SlashBurn;
import org.nodes.classification.Classification;
import org.nodes.classification.Classified;
import org.nodes.rdf.InformedAvoidance;
import org.nodes.rdf.InstanceHelper;
import org.nodes.util.MaxObserver;
import org.nodes.util.Functions.Dir;
import org.openrdf.model.Resource;
import org.openrdf.model.Statement;
import org.openrdf.model.Value;
import org.openrdf.model.vocabulary.RDF;
import org.openrdf.rio.RDFFormat;

public class PreprocessingExperiment extends RDFMLExperiment {
	private static String AIFB = "datasets/aifb-fixed_complete.n3";
	private static String TASK2 = "C:\\Users\\Gerben\\Dropbox\\D2S\\Task2\\LDMC_Task2_train.ttl";


	public static void main(String[] args) {

		for (int i = 3; i < 4; i++) {
			switch (i) {
			case 0: createAffiliationPredictionDataSet(AIFB, 1); break;
			case 1: createCommitteeMemberPredictionDataSet(); break;
			case 2: createTask2DataSet(TASK2, 1,11); break;
			case 3: dataset = new RDFFileDataSet("C:\\Users\\Gerben\\Dropbox\\data_bgs_ac_uk_ALL", RDFFormat.NTRIPLES);
			createGeoDataSet(1, 1, "http://data.bgs.ac.uk/ref/Lexicon/hasLithogenesis"); break;
			}
			experiment();
		}
	}
	public static void experiment() {
		
		long[] seeds = {11,21,31,41,51,61,71,81,91,101};
		double[] cs = {0.0001, 0.001, 0.01, 0.1, 1, 10, 100, 1000, 10000};	

		// --------------
		// Learning Algorithm settings
		List<EvaluationFunction> evalFuncs = new ArrayList<EvaluationFunction>();
		evalFuncs.add(new Error());
		evalFuncs.add(new F1());

		List<Double> target = EvaluationUtils.createTarget(labels);

		LibSVMParameters svmParms = new LibSVMParameters(LibSVMParameters.C_SVC, cs);
		svmParms.setNumFolds(10);

		svmParms.setWeightLabels(EvaluationUtils.computeWeightLabels(target));
		svmParms.setWeights(EvaluationUtils.computeWeights(target));
		//---------

		//---------
		// Results Table
		ResultsTable resTable = new ResultsTable();
		resTable.setDigits(3);
		//---------

		//-------
		//Data graph, with the label information
		List<Statement> allStmts = GraphUtils.getStatements4Depth(dataset, instances, 3, false);
		List<Statement> allStmts2 = dataset.getStatements(null, null, null, false);
		System.out.println("reduced: " + allStmts.size() + ", full: " + allStmts2.size());
		
		allStmts.removeAll(blackList);
		DTGraph<String,String> sGraph = org.nodes.data.RDF.createDirectedGraph(allStmts, null, null);
		System.out.println("Total nodes: " + sGraph.nodes().size());

		List<DTNode<String,String>> instanceNodes = new ArrayList<DTNode<String,String>>();
		for (Resource i : instances) {
			instanceNodes.add(sGraph.node(i.toString()));
		}
		//--------


		//--------
		// Get the different hub lists
		int maxHubs = 1000;

		// RDF.Type hubs
		List<DTNode<String,String>> RDFTypeHubs = GraphUtils.getTypeHubs(sGraph);

		// Regular Degree
		Comparator<Node<String>> compRegDeg = new DegreeComparator<String>();
		MaxObserver<Node<String>> obsRegDeg = new MaxObserver<Node<String>>(maxHubs + instances.size(), compRegDeg);
		obsRegDeg.observe(sGraph.nodes());
		List<DTNode<String,String>> regDegreeHubs = new ArrayList<DTNode<String,String>>();
		for (Node<String> n : obsRegDeg.elements()) {
			regDegreeHubs.add((DTNode<String,String>) n);
		}

		// Signature Degree
		Comparator<DTNode<String,String>> compSigDeg = new SlashBurn.SignatureComparator<String,String>();
		MaxObserver<DTNode<String,String>> obsSigDeg = new MaxObserver<DTNode<String,String>>(maxHubs + instances.size(), compSigDeg);				
		obsSigDeg.observe(sGraph.nodes());
		List<DTNode<String,String>> sigDegreeHubs = new ArrayList<DTNode<String,String>>(obsSigDeg.elements());

		// Informed Degree
		List<Integer> classes = new ArrayList<Integer>();
		for (double d : target) {
			classes.add((int) d);
		}
		Classified<DTNode<String, String>> classified = Classification.combine(instanceNodes, classes);

		InformedAvoidance ia = new InformedAvoidance(sGraph, classified, 3);	

		Comparator<DTNode<String, String>> compUnInformed = ia.uninformedComparator(3);
		MaxObserver<DTNode<String,String>> obsUnInformed = new MaxObserver<DTNode<String,String>>(maxHubs + instances.size(), compUnInformed);
		obsUnInformed.observe(sGraph.nodes());
		List<DTNode<String,String>> unInformedDegreeHubs = new ArrayList<DTNode<String,String>>(obsUnInformed.elements());

		Iterator<DTNode<String, String>> ite = unInformedDegreeHubs.iterator();
		while(ite.hasNext())
			if(! ia.viableHub(ite.next(), 3, 3))
				ite.remove();

		Comparator<DTNode<String, String>> compInformed = ia.informedComparator(3);
		MaxObserver<DTNode<String,String>> obsInformed = new MaxObserver<DTNode<String,String>>(maxHubs + instances.size(), compInformed);
		obsInformed.observe(sGraph.nodes());
		List<DTNode<String,String>> informedDegreeHubs = new ArrayList<DTNode<String,String>>(obsInformed.elements());

		ite = informedDegreeHubs.iterator();
		while(ite.hasNext())
			if(! ia.viableHub(ite.next(), 3, 3))
				ite.remove();

		// Remove hubs from list that are root nodes
		List<DTNode<String,String>> rn = new ArrayList<DTNode<String,String>>();
		Set<String> is = new HashSet<String>();
		for (Resource r : instances) {
			is.add(r.toString());
		}
		for (DTNode<String,String> n : sGraph.nodes()) {
			if (is.contains(n.label())) {
				rn.add(n);
			}
		}
		RDFTypeHubs.removeAll(rn);
		regDegreeHubs.removeAll(rn);
		sigDegreeHubs.removeAll(rn);
		unInformedDegreeHubs.removeAll(rn);
		informedDegreeHubs.removeAll(rn);

		List<List<DTNode<String,String>>> hubLists = new ArrayList<List<DTNode<String,String>>>();
		hubLists.add(RDFTypeHubs);
		hubLists.add(regDegreeHubs);
		hubLists.add(sigDegreeHubs);
		hubLists.add(unInformedDegreeHubs);
		hubLists.add(informedDegreeHubs);


		boolean forward = true;
		int it = 6;
		int depth = 3;
		int[] hubThs = {0,1,2,3,4,5,10,20,30,40,50,100};
		//	int[] hubThs = {20};

		int[] iterations = {0,1,2,3,4,5,6};

		MoleculeGraphExperiment<DTGraph<String,String>> exp;

		for (int i : iterations) {
			resTable.newRow("Baseline: " + i);
			List<DTNode<String,String>> newIN = new ArrayList<DTNode<String,String>>(instanceNodes);

			exp = new MoleculeGraphExperiment<DTGraph<String,String>>(new WLSubTreeKernel(i, true, forward), 
					seeds, svmParms, GraphUtils.getSubGraphs(sGraph, newIN, depth), target, evalFuncs);

			System.out.println("running baseline, it: " + i);
			exp.run();

			for (Result res : exp.getResults()) {
				resTable.addResult(res);
			}
		}
		System.out.println(resTable);

		for (int th : hubThs) {
			resTable.newRow("Hub Threshold: " + th);

			for (List<DTNode<String,String>> hubList : hubLists) {

				///*
				List<DTNode<String,String>> newIN = new ArrayList<DTNode<String,String>>(instanceNodes);
				DTGraph<String,String> newG = GraphUtils.simplifyGraph(sGraph, GraphUtils.createHubMap(hubList, th), newIN, false, true);
				System.out.println("New #links: "+ newG.numLinks() + ", old #links: " + sGraph.numLinks());
				
				exp = new MoleculeGraphExperiment<DTGraph<String,String>>(new WLSubTreeKernel(it, true, forward), 
						seeds, svmParms, GraphUtils.getSubGraphs(newG, newIN, depth), target, evalFuncs);

				System.out.println("running, remove hubs, th: " + th);
				exp.run();

				for (Result res : exp.getResults()) {
					resTable.addResult(res);
				}

				newIN = new ArrayList<DTNode<String,String>>(instanceNodes);
				newG = GraphUtils.simplifyGraph(sGraph, GraphUtils.createHubMap(hubList, th), newIN, true, false);
				System.out.println("New #links: "+ newG.numLinks() + ", old #links: " + sGraph.numLinks());
				
				exp = new MoleculeGraphExperiment<DTGraph<String,String>>(new WLSubTreeKernel(it, true, forward), 
						seeds, svmParms, GraphUtils.getSubGraphs(newG, newIN, depth), target, evalFuncs);

				System.out.println("running, relabel hubs, th: " + th);
				exp.run();

				for (Result res : exp.getResults()) {
					resTable.addResult(res);
				}

				newIN = new ArrayList<DTNode<String,String>>(instanceNodes);
				newG = GraphUtils.simplifyGraph(sGraph, GraphUtils.createHubMap(hubList, th), newIN, true, true);
				System.out.println("New #links: "+ newG.numLinks() + ", old #links: " + sGraph.numLinks());

				exp = new MoleculeGraphExperiment<DTGraph<String,String>>(new WLSubTreeKernel(it, true, forward), 
						seeds, svmParms, GraphUtils.getSubGraphs(newG, newIN, depth), target, evalFuncs);

				System.out.println("running, relabel+remove hubs, th: " + th);
				exp.run();

				for (Result res : exp.getResults()) {
					resTable.addResult(res);
				}
				//*/

			}
			System.out.println(resTable);
		}

		resTable.addCompResults(resTable.getBestResults());
		System.out.println(resTable);		
		System.out.println(resTable.allScoresToString());
		
		saveResults(resTable.toString(), "results_" + System.currentTimeMillis() + ".txt");
		saveResults(resTable.allScoresToString(), "results_" + System.currentTimeMillis() + ".txt");
	}



	private static void createAffiliationPredictionDataSet(String dataFile, double frac) {
		Random rand = new Random(1);

		// Read in data set
		dataset = new RDFFileDataSet(dataFile, RDFFormat.N3);

		// Extract all triples with the affiliation predicate
		List<Statement> stmts = dataset.getStatementsFromStrings(null, "http://swrc.ontoware.org/ontology#affiliation", null);

		// initialize the lists of instances and labels
		instances = new ArrayList<Resource>();
		labels = new ArrayList<Value>();

		// The subjects of the affiliation triples will we our instances and the objects our labels
		for (Statement stmt : stmts) {
			if (rand.nextDouble() <= frac) {
				instances.add(stmt.getSubject());
				labels.add(stmt.getObject());
			}
		}

		//capClassSize(20, 1);
		removeSmallClasses(5);
		// the blackLists data structure
		blackList = new ArrayList<Statement>();
		blackLists = new HashMap<Resource, List<Statement>>();

		// For each instance we add the triples that give the label of the instance (i.e. the URI of the affiliation)
		// In this case this is the affiliation triple and the reverse relation triple, which is the employs relation.
		for (Resource instance : instances) {
			blackList.addAll(dataset.getStatementsFromStrings(instance.toString(), "http://swrc.ontoware.org/ontology#affiliation", null));
			blackList.addAll(dataset.getStatementsFromStrings(null, "http://swrc.ontoware.org/ontology#employs", instance.toString()));
		}

		for (Resource instance : instances) {
			blackLists.put(instance, blackList);
		}
	}

	private static void createGeoDataSet(long seed, double fraction, String property) {
		createGeoDataSet(seed, fraction, 10, property);
	}

	private static void createGeoDataSet(long seed, double fraction, int minSize, String property) {
		String majorityClass = "http://data.bgs.ac.uk/id/Lexicon/Class/LS";
		Random rand = new Random(seed);

		List<Statement> stmts = dataset.getStatementsFromStrings(null, "http://www.w3.org/2000/01/rdf-schema#isDefinedBy", "http://data.bgs.ac.uk/ref/Lexicon/NamedRockUnit");
		System.out.println(dataset.getLabel());

		System.out.println("Component Rock statements: " + stmts.size());
		instances = new ArrayList<Resource>();
		labels = new ArrayList<Value>();
		blackList = new ArrayList<Statement>();

		// http://data.bgs.ac.uk/ref/Lexicon/hasRockUnitRank
		// http://data.bgs.ac.uk/ref/Lexicon/hasTheme

		for(Statement stmt: stmts) {
			List<Statement> stmts2 = dataset.getStatementsFromStrings(stmt.getSubject().toString(), property, null);

			if (stmts2.size() > 1) {
				System.out.println("more than 1 Class");
			}

			for (Statement stmt2 : stmts2) {

				if (rand.nextDouble() <= fraction) {
					instances.add(stmt2.getSubject());

					labels.add(stmt2.getObject());
					/*
				if (stmt2.getObject().toString().equals(majorityClass)) {
					labels.add(ds.createLiteral("pos"));
				} else {
					labels.add(ds.createLiteral("neg"));
				}
					 */
				}
			}
		}


		//capClassSize(50, seed);
		removeSmallClasses(minSize);
		createBlackList();

		Map<Value, Integer> labelMap = new HashMap<Value, Integer>();

		System.out.println(LibSVM.computeClassCounts(LibSVM.createTargets(labels, labelMap)));
		System.out.println(labelMap);
	}

	private static void createCommitteeMemberPredictionDataSet() {
		RDFFileDataSet testSetA = new RDFFileDataSet("datasets/iswc-2011-complete.rdf", RDFFormat.RDFXML);
		//testSetA.addFile("datasets/eswc-2011-complete.rdf", RDFFormat.RDFXML);
		//testSetA.addFile("datasets/eswc-2012-complete.rdf", RDFFormat.RDFXML);
		//testSetA.addFile("datasets/eswc-2008-complete.rdf", RDFFormat.RDFXML);
		//testSetA.addFile("datasets/eswc-2009-complete.rdf", RDFFormat.RDFXML);
		//testSetA.addFile("datasets/iswc-2012-complete.rdf", RDFFormat.RDFXML);
		//testSetA.addFile("datasets/iswc-2011-complete.rdf", RDFFormat.RDFXML);
		testSetA.addFile("datasets/iswc-2010-complete.rdf", RDFFormat.RDFXML);
		//testSetA.addFile("datasets/iswc-2009-complete.rdf", RDFFormat.RDFXML);
		//testSetA.addFile("datasets/iswc-2008-complete.rdf", RDFFormat.RDFXML);

		RDFFileDataSet testSetB = new RDFFileDataSet("datasets/iswc-2012-complete.rdf", RDFFormat.RDFXML);

		instances = new ArrayList<Resource>();
		List<Resource> instancesB = new ArrayList<Resource>();
		labels = new ArrayList<Value>();
		List<Statement> stmts = testSetA.getStatementsFromStrings(null, "http://www.w3.org/1999/02/22-rdf-syntax-ns#type", "http://xmlns.com/foaf/0.1/Person");
		for (Statement stmt : stmts) {
			instancesB.add(stmt.getSubject()); 
		}	

		int pos = 0, neg = 0;
		for (Resource instance : instancesB) {
			if (!testSetB.getStatements(instance, null, null).isEmpty()) {
				instances.add(instance);
				if (testSetB.getStatementsFromStrings(instance.toString(), "http://data.semanticweb.org/ns/swc/ontology#holdsRole", "http://data.semanticweb.org/conference/iswc/2012/pc-member/research", false).size() > 0) {
					labels.add(testSetA.createLiteral("true"));
					pos++;
				} else {
					labels.add(testSetA.createLiteral("false"));
					neg++;
				}
			}
		}

		dataset = testSetA;		
		blackList = new ArrayList<Statement>();

		System.out.println("Pos and Neg: " + pos + " " + neg);
		System.out.println("Baseline acc: " + Math.max(pos, neg) / ((double)pos+neg));
	}

	private static void createTask2DataSet(String dataFile, double fraction, long seed) {
		RDFFileDataSet d = new RDFFileDataSet(dataFile, RDFFormat.TURTLE);

		dataset = d;

		Random rand = new Random(seed);



		List<Statement> stmts = dataset.getStatementsFromStrings(null, RDF.TYPE.toString(), "http://purl.org/procurement/public-contracts#Contract");
		instances = new ArrayList<Resource>();
		labels = new ArrayList<Value>();
		blackList = new ArrayList<Statement>();

		for(Statement stmt: stmts) {
			List<Statement> stmts2 = dataset.getStatementsFromStrings(stmt.getSubject().toString(), "http://example.com/multicontract", null);

			for (Statement stmt2 : stmts2) {

				if (rand.nextDouble() < fraction) {
					instances.add(stmt2.getSubject());
					labels.add(stmt2.getObject());
				}
			}
		}

		removeSmallClasses(5);
		createBlackList();

		System.out.println(EvaluationUtils.computeClassCounts(EvaluationUtils.createTarget(labels)));
	}


}
