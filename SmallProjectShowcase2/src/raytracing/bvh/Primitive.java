package raytracing.bvh;

import lwjglengine.graphics.Material;
import myutils.math.Mat4;
import myutils.math.Vec3;

public abstract class Primitive extends Shape {
	public static final int PRIMITIVE_TYPE_SPHERE = 1;
	public static final int PRIMITIVE_TYPE_TRIANGLE = 2;

	public Material material;

	public Primitive(Material material) {
		this.material = new Material(material);
	}
}

class Sphere extends Primitive {
	public Vec3 center;
	public float radius;
	public Material material;

	public Sphere(Vec3 center, float radius, Material material) {
		super(material);
		this.center = new Vec3(center);
		this.radius = radius;
	}
}

class Triangle extends Primitive {
	public Vec3 a, b, c;
	public Material material;

	public Triangle(Vec3 a, Vec3 b, Vec3 c, Material material) {
		super(material);
		this.a = new Vec3(a);
		this.b = new Vec3(b);
		this.c = new Vec3(c);
	}
}
