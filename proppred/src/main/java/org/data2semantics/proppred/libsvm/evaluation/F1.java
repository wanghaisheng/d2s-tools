package org.data2semantics.proppred.libsvm.evaluation;

import java.util.HashMap;
import java.util.Map;

import org.data2semantics.proppred.libsvm.Prediction;

public class F1 implements EvaluationFunction {

	public double computeScore(double[] target, Prediction[] prediction) {
		Map<Double, Double> counts = new HashMap<Double, Double>();

		for (int i = 0; i < target.length; i++) {
			if (!counts.containsKey(target[i])) {
				counts.put(target[i], 1.0);
			} else {
				counts.put(target[i], counts.get(target[i]) + 1);
			}
		}

		double f1 = 0, temp1 = 0, temp2 = 0;
		
		for (double label : counts.keySet()) {
			for (int i = 0; i < prediction.length; i++) {
				if ((prediction[i].getLabel() == label && target[i] == label)) {
					temp1 += 1;
				}
				if ((prediction[i].getLabel() == label || target[i] == label)) {
					temp2 += 1;
				}
			}
			f1 += temp1 / temp2;
			temp1 = 0;
			temp2 = 0;
		}	
		return f1 / ((double) counts.size());
	}

	public boolean isBetter(double scoreA, double scoreB) {
		return (scoreA > scoreB) ? true : false;
	}

	public String getLabel() {
		return "F1";
	}

	public boolean isHigherIsBetter() {
		return true;
	}
}
