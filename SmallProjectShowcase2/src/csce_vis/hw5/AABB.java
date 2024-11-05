package csce_vis.hw5;

import myutils.math.Mat3;
import myutils.math.MathUtils;
import myutils.math.Vec3;

public class AABB extends Shape {

	//we keep AABB centered at the origin, so this is essentially the 'radius' on each axis. 
	public Vec3 half_dim;

	public AABB(Vec3 dimensions) {
		this.type = Type.AABB;
		this.half_dim = dimensions.mul(0.5f);

		this.computeMass();
	}

	@Override
	protected void computeMass() {
		float w = this.half_dim.x * 2;
		float h = this.half_dim.y * 2;
		float d = this.half_dim.z * 2;
		this.mass = w * h * d;
		this.moment = Mat3.identity();
		this.moment.mat[0][0] = (this.mass * (h * h + d * d)) / 12.0f;
		this.moment.mat[1][1] = (this.mass * (w * w + d * d)) / 12.0f;
		this.moment.mat[2][2] = (this.mass * (w * w + h * h)) / 12.0f;
	}

	public Vec3[] getVertexList() {
		Vec3[] ret = new Vec3[8];
		ret[0] = new Vec3(half_dim.x, half_dim.y, half_dim.z);
		ret[1] = new Vec3(-half_dim.x, half_dim.y, half_dim.z);
		ret[2] = new Vec3(-half_dim.x, -half_dim.y, half_dim.z);
		ret[3] = new Vec3(half_dim.x, -half_dim.y, half_dim.z);
		ret[4] = new Vec3(half_dim.x, half_dim.y, -half_dim.z);
		ret[5] = new Vec3(-half_dim.x, half_dim.y, -half_dim.z);
		ret[6] = new Vec3(-half_dim.x, -half_dim.y, -half_dim.z);
		ret[7] = new Vec3(half_dim.x, -half_dim.y, -half_dim.z);
		return ret;
	}

	public int[][] getEdgeList() {
		int[][] ret = new int[12][];
		ret[0] = new int[] { 0, 1 };
		ret[1] = new int[] { 1, 2 };
		ret[2] = new int[] { 2, 3 };
		ret[3] = new int[] { 3, 0 };
		ret[4] = new int[] { 4, 5 };
		ret[5] = new int[] { 5, 6 };
		ret[6] = new int[] { 6, 7 };
		ret[7] = new int[] { 7, 4 };
		ret[8] = new int[] { 0, 4 };
		ret[9] = new int[] { 1, 5 };
		ret[10] = new int[] { 2, 6 };
		ret[11] = new int[] { 3, 7 };
		return ret;
	}

}
