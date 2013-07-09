package org.data2semantics.exp.dmold;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.data2semantics.exp.RDFMLExperiment;
import org.data2semantics.exp.utils.KernelExperiment;
import org.data2semantics.exp.utils.RDFLinearKernelExperiment;
import org.data2semantics.exp.utils.Result;
import org.data2semantics.exp.utils.ResultsTable;
import org.data2semantics.proppred.kernels.KernelUtils;
import org.data2semantics.proppred.kernels.rdfgraphkernels.RDFCombinedKernel;
import org.data2semantics.proppred.kernels.rdfgraphkernels.RDFFeatureVectorKernel;
import org.data2semantics.proppred.kernels.rdfgraphkernels.RDFIntersectionTreeEdgePathKernel;
import org.data2semantics.proppred.kernels.rdfgraphkernels.RDFIntersectionTreeEdgeVertexPathKernel;
import org.data2semantics.proppred.kernels.rdfgraphkernels.RDFIntersectionTreeEdgeVertexPathWithTextKernel;
import org.data2semantics.proppred.kernels.rdfgraphkernels.RDFSimpleTextKernel;
import org.data2semantics.proppred.kernels.rdfgraphkernels.RDFWLSubTreeKernel;
import org.data2semantics.proppred.kernels.rdfgraphkernels.RDFWLSubTreeKernelTree;
import org.data2semantics.proppred.kernels.rdfgraphkernels.RDFWLSubTreeWithTextKernel;
import org.data2semantics.proppred.learners.Prediction;
import org.data2semantics.proppred.learners.SparseVector;
import org.data2semantics.proppred.learners.evaluation.Accuracy;
import org.data2semantics.proppred.learners.evaluation.EvaluationFunction;
import org.data2semantics.proppred.learners.evaluation.EvaluationUtils;
import org.data2semantics.proppred.learners.evaluation.F1;
import org.data2semantics.proppred.learners.evaluation.MeanAbsoluteError;
import org.data2semantics.proppred.learners.evaluation.MeanSquaredError;
import org.data2semantics.proppred.learners.evaluation.Task1Score;
import org.data2semantics.proppred.learners.evaluation.Task1ScoreForBins;
import org.data2semantics.proppred.learners.evaluation.Task1ScoreForBothBins;
import org.data2semantics.proppred.learners.liblinear.LibLINEAR;
import org.data2semantics.proppred.learners.liblinear.LibLINEARParameters;
import org.data2semantics.proppred.learners.libsvm.LibSVM;
import org.data2semantics.proppred.learners.text.TextUtils;
import org.data2semantics.tools.rdf.RDFFileDataSet;
import org.data2semantics.tools.rdf.RDFMultiDataSet;
import org.data2semantics.tools.rdf.RDFSparqlDataSet;
import org.openrdf.model.Resource;
import org.openrdf.model.Statement;
import org.openrdf.model.Value;
import org.openrdf.model.util.LiteralUtil;
import org.openrdf.model.vocabulary.RDF;
import org.openrdf.rio.RDFFormat;

public class Task2Classifier extends RDFMLExperiment {
	private static List<Resource> testInstances;
	
	public static void main(String[] args) {
		createTask2DataSet();

		double[] cs = {0.0001, 0.001, 0.01, 0.1, 1, 10, 100, 1000, 10000};	
		double[] ps1 = {1};
		
	
		boolean inference = true;

		List<Double> targets = EvaluationUtils.createTarget(labels);

		LibLINEARParameters linParms = new LibLINEARParameters(LibLINEARParameters.SVC_DUAL, cs);
		linParms.setEvalFunction(new Accuracy());
		linParms.setDoCrossValidation(true);
		linParms.setNumFolds(5);
		linParms.setEps(0.001);
		linParms.setPs(ps1);

		Map<Double, Double> counts = EvaluationUtils.computeClassCounts(targets);
		
		System.out.println(counts);
		
		int[] wLabels = new int[counts.size()];
		double[] weights = new double[counts.size()];

		for (double label : counts.keySet()) {
			wLabels[(int) label - 1] = (int) label;
			weights[(int) label - 1] = 1 / counts.get(label);
		}
		linParms.setWeightLabels(wLabels);
		linParms.setWeights(weights);

		
		RDFFeatureVectorKernel kernel = new RDFIntersectionTreeEdgeVertexPathWithTextKernel(2, false, inference, false);
		
		List<Resource> allInstances = new ArrayList<Resource>(instances);
		allInstances.addAll(testInstances);
		
		System.out.println("Computing kernel....");
		SparseVector[] fv = kernel.computeFeatureVectors(dataset, allInstances, blackList);
		System.out.println("Computing TFIDF....");
		fv = TextUtils.computeTFIDF(Arrays.asList(fv)).toArray(new SparseVector[1]);
		fv = KernelUtils.normalize(fv);
		
		SparseVector[] trainFV = Arrays.copyOfRange(fv, 0, instances.size());
		SparseVector[] testFV = Arrays.copyOfRange(fv, instances.size(), allInstances.size());
		
		double[] targetA = new double[targets.size()];
		
		for (int i = 0; i < targets.size(); i++) {
			targetA[i] = targets.get(i);
		}
		
		Prediction[] pred = LibLINEAR.testLinearModel(LibLINEAR.trainLinearModel(trainFV, targetA, linParms), testFV);
		
		

		for (int i = 0; i < pred.length; i++) {
			double val = pred[i].getLabel();
			System.out.println(testInstances.get(i) + ", " + val);
		}

	}

	private static void createTask2DataSet() {
		RDFFileDataSet train = new RDFFileDataSet("C:\\Users\\Gerben\\Dropbox\\D2S\\Task2\\LDMC_Task2_train.ttl", RDFFormat.TURTLE);
		RDFFileDataSet test = new RDFFileDataSet("C:\\Users\\Gerben\\Dropbox\\D2S\\Task2\\LDMC_Task2_test.ttl", RDFFormat.TURTLE);
		
		RDFFileDataSet d = new RDFFileDataSet("C:\\Users\\Gerben\\Dropbox\\D2S\\Task2\\LDMC_Task2_train.ttl", RDFFormat.TURTLE);
		d.addFile("C:\\Users\\Gerben\\Dropbox\\D2S\\Task2\\LDMC_Task2_test.ttl", RDFFormat.TURTLE);
		dataset = d;

		List<Statement> stmts = train.getStatementsFromStrings(null, RDF.TYPE.toString(), "http://purl.org/procurement/public-contracts#Contract");
		instances = new ArrayList<Resource>();
		labels = new ArrayList<Value>();
		blackList = new ArrayList<Statement>();

		for(Statement stmt: stmts) {
			List<Statement> stmts2 = train.getStatementsFromStrings(stmt.getSubject().toString(), "http://example.com/multicontract", null);

			for (Statement stmt2 : stmts2) {
				instances.add(stmt2.getSubject());
				labels.add(stmt2.getObject());			
			}
		}

		//removeSmallClasses(5);
		createBlackList();
		
		stmts = test.getStatementsFromStrings(null, RDF.TYPE.toString(), "http://purl.org/procurement/public-contracts#Contract");
		
		testInstances = new ArrayList<Resource>();
		
		for(Statement stmt: stmts) {
			testInstances.add(stmt.getSubject());
		}

	}


}
