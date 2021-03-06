package org.data2semantics.proppred.kernels;

import java.util.Arrays;
import java.util.Collections;
import java.util.Random;

import org.data2semantics.proppred.learners.SparseVector;


/** 
 * Class with utility methods for kernels
 * 
 * @author Gerben
 *
 */
public class KernelUtils {
	public static final String ROOTID = "ROOT1337"; // Special root label used in some kernels
	
	/**
	 * Shuffle a kernel matrix with seed to initialize the Random object
	 * 
	 * @param kernel, 2D matrix of doubles
	 * @param seed, for the Random object
	 * @return the shuffled kernel matrix
	 */
	public static double[][] shuffle(double[][] kernel, long seed) {		
		Double[][] kernelDouble = convert2DoubleObjects(kernel);		
		for (int i = 0; i < kernel.length; i++) {
			Collections.shuffle(Arrays.asList(kernelDouble[i]), new Random(seed));
		}
		Collections.shuffle(Arrays.asList(kernelDouble), new Random(seed));
		return convert2DoublePrimitives(kernelDouble);
	}
	
	/**
	 * Convert an array of SparseVectors to a kernel matrix, by computing the dot product of all the SparseVectors
	 * 
	 * @param featureVectors, an array of SparseVectors
	 * @return a 2d array of doubles, the kernel matrix
	 */
	public static double[][] featureVectors2Kernel(SparseVector[] featureVectors) {
		double[][] kernel = initMatrix(featureVectors.length, featureVectors.length);
	
		for (int i = 0; i < featureVectors.length; i++) {
			for (int j = i; j < featureVectors.length; j++) {
				kernel[i][j] = featureVectors[i].dot(featureVectors[j]);
				kernel[j][i] = kernel[i][j];
			}
		}
		return kernel;
	}
	
	/**
	 * Convert an array of SparseVectors to binary SparseVectors, i.e. only 0 or 1 values.
	 * 
	 * @param featureVectors
	 * @return an array of binary SparseVectors (the array is not copied, so the original array is returned)
	 */
	public static SparseVector[] convert2BinaryFeatureVectors(SparseVector[] featureVectors) {
		for (SparseVector fv : featureVectors) {
			for (int index : fv.getIndices()) {
				if (fv.getValue(index) == 0.0) {
					fv.setValue(index, 1);
				}
			}
		}
		return featureVectors;
	}
	
	/**
	 * Normalize an array of SparseVectors
	 * 
	 * @param featureVectors
	 * @return an array of normalized SparseVectors (the orignal array is returned, it is not copied)
	 */
	public static SparseVector[] normalize(SparseVector[] featureVectors) {
		double norm = 0;
		for (int i = 0; i < featureVectors.length; i++) {
			norm = Math.sqrt(featureVectors[i].dot(featureVectors[i]));
			norm = (norm == 0) ? 1 : norm; // In case we have 0-vector
					
			for (int index : featureVectors[i].getIndices()) {
				featureVectors[i].setValue(index, featureVectors[i].getValue(index) / norm);
			}
			featureVectors[i].clearConversion();
		}
		return featureVectors;	
	}
	
	/**
	 * Normalize a kernel matrix.
	 * 
	 * @param kernel
	 * @return normalized matrix, which is the same matrix, not copied
	 */
	public static double[][] normalize(double[][] kernel) {
		double[] ss = new double[kernel.length];
		
		for (int i = 0; i < ss.length; i++) {
			ss[i] = kernel[i][i];
		}
			
		for (int i = 0; i < kernel.length; i++) {
			for (int j = i; j < kernel[i].length; j++) {
				kernel[i][j] /= Math.sqrt(ss[i] * ss[j]);
				kernel[j][i] = kernel[i][j];
			}
		}
		return kernel;
	}
	
	/**
	 * Normalize an asymmetric train-test kernel matrix
	 * 
	 * @param kernel, the kernel matrix
	 * @param trainSS, the self-similarities for the train set, (i.e. the kernel values k(i,i))
	 * @param testSS, the self-similarities for the test set
	 * @return a normalized matrix, which is the same array, not a copy
	 */
	public static double[][] normalize(double[][] kernel, double[] trainSS, double[] testSS) {
		for (int i = 0; i < kernel.length; i++) {
			for (int j = 0; j < kernel[i].length; j++) {
				kernel[i][j] /= Math.sqrt(testSS[i] * trainSS[j]);
			}
		}
		return kernel;
	}
	
	/**
	 * create a 2d array of doubles with the sizeRows and sizeColumns dimensions.
	 * 
	 * @param sizeRows
	 * @param sizeColumns
	 * @return
	 */
	public static double[][] initMatrix(int sizeRows, int sizeColumns) {
		double[][] kernel = new double[sizeRows][sizeColumns];
		for (int i = 0; i < sizeRows; i++) {
			Arrays.fill(kernel[i], 0.0);
		}
		return kernel;
	}
	
	/**
	 * Compute the dot product between two feature vectors represented as double arrays
	 * 
	 * @param fv1
	 * @param fv2
	 * @return the dot product between fv1 and fv2
	 */
	public static double dotProduct(double[] fv1, double[] fv2) {
		double sum = 0.0;		
		for (int i = 0; i < fv1.length && i < fv2.length; i++) {
			//sum += (fv1[i] != 0 && fv2[i] != 0) ? fv1[i] * fv2[i]: 0;
			sum += fv1[i] * fv2[i];
		}	
		return sum;
	}
	
	
	// Privates 	
	private static Double[][] convert2DoubleObjects(double[][] kernel) {
		Double[][] kernelDouble = new Double[kernel.length][kernel[0].length];
		
		for (int i = 0; i < kernel.length; i++) {
			for (int j = 0; j < kernel[i].length; j++) {
				kernelDouble[i][j] = new Double(kernel[i][j]);
			}
		}
		return kernelDouble;
	}
	
	private static double[][] convert2DoublePrimitives(Double[][] kernelDouble) {
		double[][] kernel = new double[kernelDouble.length][kernelDouble[0].length];
		
		for (int i = 0; i < kernelDouble.length; i++) {
			for (int j = 0; j < kernelDouble[i].length; j++) {
				kernel[i][j] = kernelDouble[i][j].doubleValue();
			}
		}
		return kernel;
	}

	
	
}
