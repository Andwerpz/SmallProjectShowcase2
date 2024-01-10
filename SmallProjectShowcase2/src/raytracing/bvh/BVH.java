package raytracing.bvh;

import java.util.ArrayList;
import java.util.List;

import lwjglengine.graphics.Material;
import myutils.math.Mat4;
import myutils.math.Vec3;

public class BVH {
	protected List<Shape> shapes;
	
	public BVH() {
		this.shapes = new ArrayList<>();
	}
	
	public void addShape(Shape s) {
		this.shapes.add(s);
	}
	
	public void addBVHInstance(BVH bvh, Mat4 transform) {
		BVHInstance bvhInstance = new BVHInstance(bvh, transform);
		this.addShape(bvhInstance);
	}
	
	public void addSphere(Vec3 center, float radius, Material material) {
		Sphere sphere = new Sphere(center, radius, material);
		this.addShape(sphere);
	}
	
	public void addTriangle(Vec3 a, Vec3 b, Vec3 c, Material material) {
		Triangle triangle = new Triangle(a, b, c, material);
		this.addShape(triangle);
	}
	
}
