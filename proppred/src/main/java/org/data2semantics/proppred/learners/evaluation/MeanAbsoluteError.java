package org.data2semantics.proppred.learners.evaluation;

import org.data2semantics.proppred.learners.Prediction;

public class MeanAbsoluteError implements EvaluationFunction {
	
	public double computeScore(double[] target, Prediction[] prediction) {
		double error = 0;
		for (int i = 0; i < target.length; i++) {
			error += Math.abs((target[i] - prediction[i].getLabel()));
		}
		return error / ((double) target.length);
	}

	public boolean isBetter(double scoreA, double scoreB) {
		if (scoreA < scoreB) {
			return true;
		}
		return false;
	}
	
	public String getLabel() {
		return "MAE";
	}
	
	public boolean isHigherIsBetter() {
		return false;
	}
	

}
