package raytracing.bvh;

import java.util.List;

import myutils.math.Mat4;
import myutils.math.MathUtils;
import myutils.math.Vec3;

public abstract class Shape {
	public static final int SHAPE_TYPE_BVH_INSTANCE = 0; //for nesting. 
	public static final int SHAPE_TYPE_PRIMITIVE = 1;

	public Shape() {

	}
}
