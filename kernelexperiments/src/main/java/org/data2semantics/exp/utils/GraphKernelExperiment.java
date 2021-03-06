package org.data2semantics.exp.utils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.data2semantics.proppred.kernels.KernelUtils;
import org.data2semantics.proppred.kernels.graphkernels.GraphKernel;
import org.data2semantics.proppred.learners.Prediction;
import org.data2semantics.proppred.learners.libsvm.LibSVM;
import org.data2semantics.proppred.learners.libsvm.LibSVMParameters;
import org.data2semantics.tools.graphs.DirectedMultigraphWithRoot;
import org.data2semantics.tools.graphs.Edge;
import org.data2semantics.tools.graphs.Vertex;
import org.openrdf.model.Value;

public class GraphKernelExperiment extends KernelExperiment<GraphKernel> {
	private LibSVMParameters svmParms;
	private List<Value> labels;
	private List<DirectedMultigraphWithRoot<Vertex<String>,Edge<String>>> dataset;
	private Result accR;
	private Result f1R;
	private Result compR;

	
	public GraphKernelExperiment(GraphKernel kernel, long[] seeds,
			LibSVMParameters svmParms, List<DirectedMultigraphWithRoot<Vertex<String>,Edge<String>>> dataset,
		    List<Value> labels) {
		super(kernel, seeds);
		this.svmParms = svmParms;
		this.labels = labels;
		this.dataset = dataset;
		
		accR  = new Result();
		f1R   = new Result();
		compR = new Result();
		
		results.add(accR);
		results.add(f1R);
		results.add(compR);
	}



	public void run() {		
		long tic, toc;
		
		List<Value> tempLabels = new ArrayList<Value>();
		tempLabels.addAll(labels);

		tic = System.currentTimeMillis();
		double[][] matrix = kernel.compute(dataset);
		toc = System.currentTimeMillis();
				
		double[] acc = new double[seeds.length];
		double[] f1 = new double[seeds.length];
		
		accR.setLabel("Accuracy");
		f1R.setLabel("F1");
		compR.setLabel("kernel comp time");
		
		for (int j = 0; j < seeds.length; j++) {
			matrix = KernelUtils.shuffle(matrix, seeds[j]);
			Collections.shuffle(tempLabels, new Random(seeds[j]));
			
			double[] target = LibSVM.createTargets(tempLabels);
			
			// set the weights man
			Map<Double, Double> counts = LibSVM.computeClassCounts(target);
			int[] wLabels = new int[counts.size()];
			double[] weights = new double[counts.size()];
			
			for (double label : counts.keySet()) {
				wLabels[(int) label - 1] = (int) label;
				weights[(int) label - 1] = 1 / counts.get(label);
			}
			svmParms.setWeightLabels(wLabels);
			svmParms.setWeights(weights);
			
			
			
			Prediction[] predB = LibSVM.crossValidate(matrix, target, svmParms, 10);
			acc[j] = LibSVM.computeAccuracy(LibSVM.createTargets(tempLabels), LibSVM.extractLabels(predB));
			f1[j]  = LibSVM.computeF1(LibSVM.createTargets(tempLabels), LibSVM.extractLabels(predB));
		}
		
		accR.setScores(acc);
		f1R.setScores(f1);
		
		double[] comp = {0.0, 0.0};
		comp[0] = toc - tic;
		comp[1] = toc - tic;
		compR.setScores(comp);
	}
	
	
}
