package gr.uom.java.ast.util.math;

import java.util.ArrayList;
import java.util.HashSet;

import gr.uom.java.distance.Entity;

public abstract class Clustering {

	protected ArrayList<ArrayList<Double>> distanceList;
	protected double[][] distanceMatrix;
	
	public static Clustering getInstance(int type, double[][] distanceMatrix) {
		switch(type) {
		case 0:
			return new Hierarchical(distanceMatrix);
		default:
			return null;
		}
	}
	
	public abstract HashSet<Cluster> clustering(ArrayList<Entity> entities);
	
	
}
