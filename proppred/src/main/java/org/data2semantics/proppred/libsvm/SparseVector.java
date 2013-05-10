package org.data2semantics.proppred.libsvm;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

public class SparseVector {
	private TreeMap<Integer, Double> vector;
	private int[] indices;
	private double[] values;
	
	private boolean converted;
	
	public SparseVector() {
		vector = new TreeMap<Integer,Double>();
		converted = false;
	}
	
	public void setValue(int index, double value) {
		vector.put(index, value);
		converted = false;
	}
	
	public double getValue(int index) {
		Double value = vector.get(index);
		if (value != null) {
			return value;
		} else {
			return 0;
		}
	}
	
	public Set<Integer> getIndices() {
		return vector.keySet();
	}
	
	public int size() {
		return vector.size();
	}
	
	public double dot(SparseVector v2) {
		int i = 0, j = 0;
		double ret = 0;
		
		if (!converted) {
			convert2Arrays();
		}
		if (!v2.converted) {
			v2.convert2Arrays();
		}
		
		while (i < indices.length && j < v2.indices.length) {
			if (indices[i] > v2.indices[j]) {
				j++;
			} else if (indices[i] < v2.indices[j]) {
				i++;
			} else {
				ret += values[i] * v2.values[j];
				i++;
				j++;
			}		
		}
		return ret;
	}	
	
	public void clearConversion() {
		converted = false;
		indices = null;
		values = null;
	}
	
	private void convert2Arrays() {
		indices = new int[vector.size()];
		values = new double[vector.size()];
		
		int i = 0;
		for (int key : vector.keySet()) {
			indices[i] = key;
			values[i] = vector.get(key);
			i++;
		}
		converted = true;
	}
	
	/*
	svm_node[] convert2svmNodes() {
		svm_node[] ret = new svm_node[vector.size()];
		
		int i = 0;
		for (int key : vector.keySet()) {
			ret[i] = new svm_node();
			ret[i].index = key;
			ret[i].value = vector.get(key);
			i++;
		}
		return ret;
	}
	*/
}
