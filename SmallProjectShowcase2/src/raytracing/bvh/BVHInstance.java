package raytracing.bvh;

import myutils.math.Mat4;

public class BVHInstance extends Shape {
	private BVH baseBVH;
	private Mat4 transform;
	
	public BVHInstance(BVH bvh, Mat4 transform) {
		this.transform = new Mat4(transform);
		this.baseBVH = bvh;
	}
	
	public BVH getBVH() {
		return this.baseBVH;
	}
	
	public Mat4 getTransform() {
		return this.transform;
	}
}
