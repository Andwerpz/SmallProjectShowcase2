package csce_vis.hw5;

import myutils.math.Mat3;

public abstract class Shape {
	//should always try to store the shape with center of mass at origin. 

	public enum Type {
		KDOP
	}

	public Type type;
	public float mass;
	public Mat3 moment;
}
