package raytracing.bvh;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL13.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL21.*;
import static org.lwjgl.opengl.GL30.*;
import static org.lwjgl.opengl.GL31.*;
import static org.lwjgl.opengl.GL32.*;
import static org.lwjgl.opengl.GL33.*;
import static org.lwjgl.opengl.GL43.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;
import static org.lwjgl.opengl.GL31.*;
import static org.lwjgl.opengl.GL33.*;

import java.util.List;
import java.util.Queue;

import lwjglengine.graphics.Material;
import lwjglengine.graphics.ShaderStorageBuffer;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;

import myutils.math.Mat4;
import myutils.math.MathUtils;
import myutils.math.Vec3;
import myutils.misc.Pair;

public class BVHManager {
	//just manages the root
	
	private BVH root;
	
	private static final long SSBO_SIZE = (1 << 29);
	private ShaderStorageBuffer bvhSSBO, boundingBoxSSBO, primitiveSSBO, materialSSBO;
	
	public BVHManager() {	
		this.root = new BVH();
		
		this.bvhSSBO = new ShaderStorageBuffer();
		this.boundingBoxSSBO = new ShaderStorageBuffer();
		this.primitiveSSBO = new ShaderStorageBuffer();
		this.materialSSBO = new ShaderStorageBuffer();
		
		this.bvhSSBO.setUsage(GL_STATIC_READ);
		this.boundingBoxSSBO.setUsage(GL_STATIC_READ);
		this.primitiveSSBO.setUsage(GL_STATIC_READ);
		this.materialSSBO.setUsage(GL_STATIC_READ);
		
		this.bvhSSBO.setSize(SSBO_SIZE * 4);
		this.boundingBoxSSBO.setSize(SSBO_SIZE * 4);
		this.primitiveSSBO.setSize(SSBO_SIZE * 4);
		this.materialSSBO.setSize(SSBO_SIZE * 4);
	}
	
	public void kill() {
		this.bvhSSBO.kill();
		this.boundingBoxSSBO.kill();
		this.primitiveSSBO.kill();
		this.materialSSBO.kill();
	}
	
	public ShaderStorageBuffer getBVHSSBO() {
		return this.bvhSSBO;
	}
	
	public ShaderStorageBuffer getBoundingBoxSSBO() {
		return this.boundingBoxSSBO;
	}
	
	public ShaderStorageBuffer getPrimitiveSSBO() {
		return this.primitiveSSBO;
	}
	
	public ShaderStorageBuffer getMaterialSSBO() {
		return this.materialSSBO;
	}
	
	public BVH getRoot() {
		return this.root;
	}
	
	public void addBVHInstance(BVH bvh, Mat4 transform) {
		this.root.addBVHInstance(bvh, transform);
	}
	
	public void addSphere(Vec3 center, float radius, Material material) {
		this.root.addSphere(center, radius, material);
	}
	
	public void addTriangle(Vec3 a, Vec3 b, Vec3 c, Material material) {
		this.root.addTriangle(a, b, c, material);
	}
	
	/**
	 * Populates the SSBOs with BVH and primitive information
	 * @return
	 */
	public void build() {
		System.out.println("BVHManager : Start Build");
		long startTime = System.currentTimeMillis();
		
		// -- BUILD BVH TREES --
		//find all unique bvhs under the root
		HashSet<BVH> bvhSet = new HashSet<>();
		{
			Queue<BVH> q = new ArrayDeque<>();
			q.add(this.root);
			while(q.size() != 0) {
				BVH cur = q.peek();
				q.poll();
				bvhSet.add(cur);
				for(Shape s : cur.shapes) {
					if(!(s instanceof BVHInstance)) {
						continue;
					}
					BVH next = ((BVHInstance) s).getBVH();
					if(bvhSet.contains(next)) {
						continue;
					}
					bvhSet.add(next);
					q.add(next);
				}
			}
		}
		
		//find bvh dependencies and topological sort them
		HashMap<BVH, List<BVH>> bvhDependencies = new HashMap<>();
		HashMap<BVH, Integer> bvhIndeg = new HashMap<>();
		{	
			//find dependencies
			for(BVH cur : bvhSet) {
				bvhIndeg.put(cur, bvhIndeg.getOrDefault(cur, 0));
				List<BVH> dependencies = new ArrayList<>();
				for(Shape s : cur.shapes) {
					if(!(s instanceof BVHInstance)) {
						continue;
					}
					BVH next = ((BVHInstance) s).getBVH();
					dependencies.add(next);
					bvhIndeg.put(next, bvhIndeg.getOrDefault(bvhIndeg, 0) + 1);
				}
				bvhDependencies.put(cur, dependencies);
			}
		}
		List<BVH> bvhOrdering = new ArrayList<>();
		{
			//toposort
			Queue<BVH> q = new ArrayDeque<>();
			for(BVH cur : bvhSet) {
				if(bvhIndeg.get(cur) == 0) {
					q.add(cur);
				}
			}
			while(q.size() != 0) {
				BVH cur = q.peek();
				q.poll();
				bvhOrdering.add(cur);
				for(BVH next : bvhDependencies.get(cur)) {
					bvhIndeg.put(next, bvhIndeg.get(next) - 1);
					if(bvhIndeg.get(next) == 0) {
						q.add(next);
					}
				}
			}
			//check if toposort is impossible
			if(bvhOrdering.size() != bvhSet.size()) {
				throw new RuntimeException("There is a circular dependency");
			}
		}
		
		//construct bvhs in reverse topological order
		HashMap<BVH, BoundingBox> bvhBoundingBoxes = new HashMap<>();
		BVHNode[] bvhNodes = new BVHNode[bvhSet.size()];
		for(int i = bvhNodes.length - 1; i >= 0; i--) {
			//aggregate all bounding boxes belonging to current bvh into list
			BVH cur = bvhOrdering.get(i);
			List<Pair<BoundingBox, Shape>> shapes = new ArrayList<>();
			for(Shape s : cur.shapes) {
				if(s instanceof Primitive) {
					shapes.add(new Pair<>(new BoundingBox((Primitive) s), s));
				}
				else if (s instanceof BVHInstance) {
					BVHInstance inst = (BVHInstance) s;
					BVH next = inst.getBVH();
					BoundingBox box = new BoundingBox(bvhBoundingBoxes.get(next), inst.getTransform());
					shapes.add(new Pair<>(box, s));
				}
			}
			//construct current bvh's model space bounding box. 
			BoundingBox curBox = new BoundingBox();
			for(int j = 0; j < shapes.size(); j++) {
				curBox.add(shapes.get(j).first);
			}
			bvhBoundingBoxes.put(cur, curBox);
			//construct bvh tree
			bvhNodes[i] = this.buildTree(shapes);
		}
		
		//-- SERIALIZE TREES --
		ArrayList<Integer> bvhData = new ArrayList<>();
		ArrayList<Float> boundingBoxData = new ArrayList<>();
		ArrayList<Float> primitiveData = new ArrayList<>();
		ArrayList<Float> materialData = new ArrayList<>();
		
		bvhData.add(-1); //reserved for telling where the root bvh starts	
		
		//print bvhs in reverse topological order
		HashMap<BVH, Integer> bvhSSBOIndexes = new HashMap<>();
		for(int i = bvhOrdering.size() - 1; i >= 0; i--) {
			bvhSSBOIndexes.put(bvhOrdering.get(i), bvhData.size());
			this.serializeTree(bvhNodes[i], bvhData, boundingBoxData, primitiveData, materialData, bvhSSBOIndexes);
		}
		bvhData.set(0, bvhSSBOIndexes.get(this.root));
		
		//transfer to SSBOs 
		int[] bvhInts = new int[bvhData.size()];
		float[] boundingBoxFloats = new float[boundingBoxData.size()];
		float[] primitiveFloats = new float[primitiveData.size()];
		float[] materialFloats = new float[materialData.size()];
		
		for(int i = 0; i < bvhData.size(); i++) {
			bvhInts[i] = bvhData.get(i);
		}
		for(int i = 0; i < boundingBoxData.size(); i++) {
			boundingBoxFloats[i] = boundingBoxData.get(i);
		}
		for(int i = 0; i < primitiveData.size(); i++) {
			primitiveFloats[i] = primitiveData.get(i);
		}
		for(int i = 0; i < materialData.size(); i++) {
			materialFloats[i] = materialData.get(i);
		}
		
		this.bvhSSBO.setSubData(bvhInts, 0);
		this.boundingBoxSSBO.setSubData(boundingBoxFloats, 0);
		this.primitiveSSBO.setSubData(primitiveFloats, 0);
		this.materialSSBO.setSubData(materialFloats, 0);
		
		long timeElapsed = System.currentTimeMillis() - startTime;
		System.out.println("BVHManager : End Build, " + timeElapsed + " millis");
		System.out.println("BVH Buffer Length : " + bvhInts.length);
		
//		for(int i : bvhData) {
//			System.out.println(i);
//		}
//		for(float i : boundingBoxData) {
//			System.out.println(i);
//		}
//		System.exit(0);
		
	}
	
	//returns the next free index in the buffer
	private void serializeTree(BVHNode cur, ArrayList<Integer> bvhData, ArrayList<Float> boundingBoxData, ArrayList<Float> primitiveData, ArrayList<Float> materialData, HashMap<BVH, Integer> bvhSSBOIndexes) {
		//write offset to current node's bounding box
		bvhData.add(boundingBoxData.size());
		{
			BoundingBox b = cur.boundingBox;
			boundingBoxData.add(b.bMin.x);
			boundingBoxData.add(b.bMin.y);
			boundingBoxData.add(b.bMin.z);
			boundingBoxData.add(b.bMax.x);
			boundingBoxData.add(b.bMax.y);
			boundingBoxData.add(b.bMax.z);
		}
		
		if(cur.isLeaf) {
			//this is a leaf
			bvhData.add(-1); //signal that this is a leaf
			bvhData.add(primitiveData.size());	//offset to start of primitive data
			bvhData.add(materialData.size());	//offset to start of material data
			bvhData.add(cur.shapes.size());	//how many primitives do we have?
			
			//sort primitives so that all the bvh instances come first
			Collections.sort(cur.shapes, (a, b) -> {
				int a_val = a instanceof Primitive? 1 : 0;
				int b_val = b instanceof Primitive? 1 : 0;
				return Integer.compare(a_val, b_val);
			});
			
			//store references to all primitives under this node
			//in bvh data, we're going to know the type of each primitive
			for(Shape s : cur.shapes) {
				if(s instanceof Primitive) {
					//primitives don't have model transforms
					if(s instanceof Sphere) {
						bvhData.add(Primitive.PRIMITIVE_TYPE_SPHERE);
						Sphere sphere = (Sphere) s;
						primitiveData.add(sphere.center.x);
						primitiveData.add(sphere.center.y);
						primitiveData.add(sphere.center.z);
						primitiveData.add(sphere.radius);
					}
					else if(s instanceof Triangle) {
						bvhData.add(Primitive.PRIMITIVE_TYPE_TRIANGLE);
						Triangle tri = (Triangle) s;
						primitiveData.add(tri.a.x);
						primitiveData.add(tri.a.y);
						primitiveData.add(tri.a.z);
						primitiveData.add(tri.b.x);
						primitiveData.add(tri.b.y);
						primitiveData.add(tri.b.z);
						primitiveData.add(tri.c.x);
						primitiveData.add(tri.c.y);
						primitiveData.add(tri.c.z);
					}
					
					//write material
					Primitive p = (Primitive) s;
					Material m = p.material;
					float[] m_floats = m.toFloatArr();
					for(int i = 0; i < m_floats.length; i++) {
						materialData.add(m_floats[i]);
					}
				}
				else if(s instanceof BVHInstance) {
					bvhData.add(Shape.SHAPE_TYPE_BVH_INSTANCE);
					
					//save bvh root offset 
					BVHInstance b = (BVHInstance) s;
					bvhData.add(bvhSSBOIndexes.get(b.getBVH()));
					
					//to check collision against bvh instance, transform ray into model space and see if it collides with bvh bounding box
					//just have to save model transform here. 
					Mat4 transform = b.getTransform();
					float[] t_floats = transform.toFloatArray();
					for(int i = 0; i < t_floats.length; i++) {
						primitiveData.add(t_floats[i]);
					}	
				}
			}
			return;
		}
		
		//this is not a leaf
		//index for first child is implicitly right after the second child offset
		int bvhSecondChildInd = bvhData.size();
		bvhData.add(-1);	//reserve index for offset to second child
		
		//write children
		this.serializeTree(cur.a, bvhData, boundingBoxData, primitiveData, materialData, bvhSSBOIndexes);
		bvhData.set(bvhSecondChildInd, bvhData.size());
		this.serializeTree(cur.b, bvhData, boundingBoxData, primitiveData, materialData, bvhSSBOIndexes);
	}
	
	private BVHNode buildTree(List<Pair<BoundingBox, Shape>> shapes) {
		if(shapes.size() == 1) {
			return new BVHNode(shapes.get(0).first, shapes.get(0).second);
		}
		else if(shapes.size() == 2) {
			BVHNode c0 = new BVHNode(shapes.get(0).first, shapes.get(0).second);
			BVHNode c1 = new BVHNode(shapes.get(1).first, shapes.get(1).second);
			return new BVHNode(c0, c1);
		}
		
		//we want to try to split the shapes into two groups such that it minimizes overlaps. 
		//ideally, we want no overlap between the two children, but that isn't always possible. 
		//for each partition, we use the surface area heuristic to judge how good it is, then
		//greedily pick the best one. 
		
		//TODO perhaps just try all 3 axes. Sometimes largest seperation doesn't mean best split
		
		//find the axis with the largest range, and compute total bounding box
		BoundingBox totalBox = new BoundingBox();
		Vec3 minCenter = new Vec3(shapes.get(0).first.center);
		Vec3 maxCenter = new Vec3(shapes.get(0).first.center);
		for(int i = 0; i < shapes.size(); i++) {
			totalBox.add(shapes.get(i).first);
			minCenter = MathUtils.min(minCenter, shapes.get(i).first.center);
			maxCenter = MathUtils.max(maxCenter, shapes.get(i).first.center);
		}
		
		//sort shapes along axis with largest range
		float xRange = maxCenter.x - minCenter.x;
		float yRange = maxCenter.y - minCenter.y;
		float zRange = maxCenter.z - minCenter.z;
		if(xRange >= Math.max(yRange, zRange)) {
			Collections.sort(shapes, (a, b) -> Float.compare(a.first.center.x, b.first.center.x));
		}
		else if(yRange >= Math.max(xRange, zRange)) {
			Collections.sort(shapes, (a, b) -> Float.compare(a.first.center.y, b.first.center.y));
		}
		else {
			Collections.sort(shapes, (a, b) -> Float.compare(a.first.center.z, b.first.center.z));
		}
		
		//for each index, compute cost of partition
		float traversalCost = 0.5f;
		float intersectCost = 1.0f;
		float minPartitionCost = shapes.size() * intersectCost;	//if we don't partition
		int minPartitionIndex = -1;	//if -1, then we don't partition
		BoundingBox[] pfxBox = new BoundingBox[shapes.size()], sfxBox = new BoundingBox[shapes.size()];
		pfxBox[0] = new BoundingBox(shapes.get(0).first);
		sfxBox[shapes.size() - 1] = new BoundingBox(shapes.get(shapes.size() - 1).first);
		for(int i = 1; i < shapes.size(); i++) {
			pfxBox[i] = new BoundingBox(pfxBox[i - 1]);
			pfxBox[i].add(shapes.get(i).first);
		}
		for(int i = shapes.size() - 2; i >= 0; i--) {
			sfxBox[i] = new BoundingBox(sfxBox[i + 1]);
			sfxBox[i].add(shapes.get(i).first);
		}
		float tot_sa = totalBox.calcSurfaceArea();
		for(int i = 0; i < shapes.size() - 1; i++) {
			float c0_sa = pfxBox[i].calcSurfaceArea();
			float c1_sa = sfxBox[i + 1].calcSurfaceArea();
			float curCost = traversalCost + (i + 1) * intersectCost * (c0_sa / tot_sa) + (shapes.size() - (i + 1)) * intersectCost * (c1_sa / tot_sa);
			if(curCost < minPartitionCost) {
				minPartitionCost = curCost;
				minPartitionIndex = i;
			}
		}
		
		//create partition, and recursively solve for children nodes
		if(minPartitionIndex == -1) {
			//insert a leaf here
			List<Shape> shapeList = new ArrayList<>();
			for(int i = 0; i < shapes.size(); i++) {
				shapeList.add(shapes.get(i).second);
			}
			return new BVHNode(totalBox, shapeList);
		}
		//partition into two lists
		List<Pair<BoundingBox, Shape>> c0_list = new ArrayList<>(), c1_list = new ArrayList<>();
		for(int i = 0; i < shapes.size(); i++) {
			if(i <= minPartitionIndex) {
				c0_list.add(shapes.get(i));
			}
			else {
				c1_list.add(shapes.get(i));
			}
		}
		return new BVHNode(this.buildTree(c0_list), this.buildTree(c1_list));
	}
}

class BVHNode {
	public boolean isLeaf = false;
	public BoundingBox boundingBox;
	
	//if is not a leaf
	public BVHNode a = null, b = null;	//children
	
	//if is leaf
	public List<Shape> shapes;
	
	public BVHNode(BVHNode a, BVHNode b) {
		this.boundingBox = new BoundingBox(a.boundingBox);
		this.boundingBox.add(b.boundingBox);
		this.a = a;
		this.b = b;
	}
	
	public BVHNode(BoundingBox boundingBox, Shape shape) {
		this.isLeaf = true;
		this.shapes = Arrays.asList(shape);
		this.boundingBox = new BoundingBox(boundingBox);
	}
	
	public BVHNode(BoundingBox boundingBox, List<Shape> shapes) {
		this.isLeaf = true;
		this.shapes = shapes;
		this.boundingBox = new BoundingBox(boundingBox);
	}
}

class BoundingBox {
	public boolean isEmpty = true;
	public Vec3 bMin, bMax, center;
	
	public BoundingBox() {
		this.isEmpty = true;
	}
	
	public BoundingBox(Primitive p) {
		this.isEmpty = false;
		if(p instanceof Sphere) {
			this.calcBounds((Sphere) p);
		}
		else if(p instanceof Triangle) {
			this.calcBounds((Triangle) p);
		}
		this.center = MathUtils.lerp(this.bMin, 0, this.bMax, 1, 0.5f);
	}
	
	public BoundingBox(BoundingBox other) {
		this.isEmpty = other.isEmpty;
		this.bMin = new Vec3(other.bMin);
		this.bMax = new Vec3(other.bMax);
		this.center = new Vec3(other.center);
	}
	
	/**
	 * New axis aligned bounding box containing the other one after applying the transform.
	 * @param other
	 * @param transform
	 */
	public BoundingBox(BoundingBox other, Mat4 transform) {
		if(other.isEmpty) {
			return;
		}
		
		Vec3[] corners = new Vec3[8];
		corners[0] = new Vec3(other.bMin.x, other.bMin.y, other.bMin.z);
		corners[1] = new Vec3(other.bMax.x, other.bMin.y, other.bMin.z);
		corners[2] = new Vec3(other.bMin.x, other.bMax.y, other.bMin.z);
		corners[3] = new Vec3(other.bMax.x, other.bMax.y, other.bMin.z);
		corners[4] = new Vec3(other.bMin.x, other.bMin.y, other.bMax.z);
		corners[5] = new Vec3(other.bMax.x, other.bMin.y, other.bMax.z);
		corners[6] = new Vec3(other.bMin.x, other.bMax.y, other.bMax.z);
		corners[7] = new Vec3(other.bMax.x, other.bMax.y, other.bMax.z);
		for(int i = 0; i < corners.length; i++) {
			corners[i] = transform.mul(corners[i], 1);
		}
		
		this.bMin = MathUtils.min(corners);
		this.bMax = MathUtils.max(corners);
		this.center = MathUtils.lerp(this.bMin, 0, this.bMax, 1, 0.5f);
	}
	
	private void calcBounds(Sphere sphere) {
		this.bMin = new Vec3(sphere.center).sub(new Vec3(sphere.radius));
		this.bMax = new Vec3(sphere.center).add(new Vec3(sphere.radius));
	}
	
	private void calcBounds(Triangle triangle) {
		this.bMin = MathUtils.min(new Vec3[] {triangle.a, triangle.b, triangle.c});
		this.bMax = MathUtils.max(new Vec3[] {triangle.a, triangle.b, triangle.c});
	}
	
	/**
	 * Modifies this bounding box to also contain the input bounding box
	 * @param other
	 */
	public void add(BoundingBox other) {
		if(other.isEmpty) {
			return;
		}
		if(this.isEmpty) {
			this.isEmpty = false;
			this.bMin = new Vec3(other.bMin);
			this.bMax = new Vec3(other.bMax);
			return;
		}
		this.bMin = MathUtils.min(this.bMin, other.bMin);
		this.bMax = MathUtils.max(this.bMax, other.bMax);
	}
	
	public float calcSurfaceArea() {
		Vec3 side = new Vec3(this.bMin, this.bMax);
		return 2f * (side.x * side.y + side.y * side.z + side.z * side.x);
	}
}
