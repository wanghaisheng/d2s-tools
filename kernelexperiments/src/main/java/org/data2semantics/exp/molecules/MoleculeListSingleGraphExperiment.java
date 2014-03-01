package org.data2semantics.exp.molecules;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.data2semantics.exp.utils.KernelExperiment;
import org.data2semantics.exp.utils.Result;
import org.data2semantics.mustard.kernels.graphkernels.GraphKernel;
import org.data2semantics.proppred.kernels.KernelUtils;
import org.data2semantics.proppred.learners.Prediction;
import org.data2semantics.proppred.learners.evaluation.EvaluationFunction;
import org.data2semantics.proppred.learners.libsvm.LibSVM;
import org.data2semantics.proppred.learners.libsvm.LibSVMParameters;
import org.nodes.DTGraph;
import org.nodes.DTNode;
import org.nodes.UGraph;

public class MoleculeListSingleGraphExperiment extends KernelExperiment<RDFDTGraphIntersectionSubTreeKernel> {
	private LibSVMParameters svmParms;
	private List<Double> labels;
	private DTGraph<String,String> graph;
	private List<EvaluationFunction> evalFunctions;
	private Result compR;
	private Map<EvaluationFunction, double[]> resultMap;
	private List<RDFDTGraphIntersectionSubTreeKernel> kernels;
	private List<DTNode<String,String>> iNodes;
	
	
	public MoleculeListSingleGraphExperiment(List<RDFDTGraphIntersectionSubTreeKernel> kernels, long[] seeds, LibSVMParameters svmParms, DTGraph<String,String> graph, List<DTNode<String,String>> iNodes, List<Double> labels, List<EvaluationFunction> evalFunctions) {
		super(null, seeds);
		this.svmParms = svmParms;
		this.labels = labels;
		this.graph = graph;
		this.evalFunctions = evalFunctions;
		this.kernels = kernels;
		this.iNodes = iNodes;
		
		resultMap = new HashMap<EvaluationFunction,double[]>();
		
		for (EvaluationFunction evalFunc : evalFunctions) {
			Result res = new Result();
			double[] resA = new double[seeds.length];
			res.setLabel(evalFunc.getLabel());
			res.setScores(resA);
			res.setHigherIsBetter(evalFunc.isHigherIsBetter());
			results.add(res);
			resultMap.put(evalFunc, resA);
		}
		
		compR = new Result();
		results.add(compR);
	}

	@Override
	public void run() {
		long tic, toc;

		List<Double> tempLabels = new ArrayList<Double>();
		tempLabels.addAll(labels);

		Map<String, double[][]> matrices = new HashMap<String, double[][]>();
		
		tic = System.currentTimeMillis();
		System.out.println("Computing kernels...");	
		for (RDFDTGraphIntersectionSubTreeKernel kernel : kernels) {
			double[][] matrix = kernel.compute(graph, iNodes);
			matrices.put(kernel.getLabel(), matrix);
		}
		toc = System.currentTimeMillis();
		
		compR.setLabel("kernel comp time");

		System.out.println("Performing CV...");
		for (int j = 0; j < seeds.length; j++) {
			for (String k : matrices.keySet()) {
				matrices.put(k, KernelUtils.shuffle(matrices.get(k), seeds[j]));
			}
			Collections.shuffle(tempLabels, new Random(seeds[j]));		
			
			double[] target = new double[tempLabels.size()];
			for (int i = 0; i < target.length; i++) {
				target[i] = tempLabels.get(i);
			}

			Prediction[] pred = LibSVM.crossValidateWithMultipleKernels(matrices, target, svmParms, svmParms.getNumFolds());
				
			for (EvaluationFunction ef : evalFunctions) {
				resultMap.get(ef)[j] = ef.computeScore(target, pred);	
			}	
		}

		double[] comp = {toc - tic};
		compR.setScores(comp);

		
	}

}
