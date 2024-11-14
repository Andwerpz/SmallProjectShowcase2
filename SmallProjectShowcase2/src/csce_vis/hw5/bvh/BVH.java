package csce_vis.hw5.bvh;

import java.util.ArrayList;

import myutils.misc.Pair;

public class BVH {
	//bvh that only accepts sphere bounding 'boxes'. 
	//should return a list of intersecting bounding boxes

	public BVHNode root = null;

	public BVH(ArrayList<AABB> aabb_list) {
		//TODO
	}

	private BVHNode buildTree(ArrayList<Pair<AABB, Integer>> aabb_list) {
		return null; //TODO
	}

	public ArrayList<Integer> getIntersections(AABB aabb) {
		return null; //TODO
	}

	class BVHNode {
		public AABB bounding_box;

		public boolean is_leaf;
		public ArrayList<Pair<AABB, Integer>> boxes = null;

		public BVHNode a = null, b = null;

		public BVHNode(AABB _bounding_box, BVHNode _a, BVHNode _b) {
			this.bounding_box = new AABB(_bounding_box);

			this.is_leaf = false;
			this.a = _a;
			this.b = _b;
		}

		public BVHNode(AABB _bounding_box, ArrayList<Pair<AABB, Integer>> _boxes) {
			this.bounding_box = new AABB(_bounding_box);

			this.is_leaf = true;
			this.boxes = _boxes;
		}
	}
}
