package csce_vis.hw5;

import myutils.math.Mat3;

public abstract class Shape {
	public enum Type {
		AABB
	}

	public Type type;
	public float mass;
	public Mat3 moment;

	public Shape() {

	}

	protected abstract void computeMass();
}
