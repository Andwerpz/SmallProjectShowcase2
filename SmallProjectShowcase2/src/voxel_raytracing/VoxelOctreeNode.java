package voxel_raytracing;

import java.util.ArrayList;

import myutils.file.SystemUtils;
import myutils.math.MathUtils;
import myutils.math.Vec3;
import myutils.math.Vec4;

public class VoxelOctreeNode {

	//TODO 
	// - optimize so that the serialized form only stores voxels that are adjacent to air

	//children bits correspond to their position within the parent node
	//i -> xyz
	//0 -> 000
	//1 -> 001
	//2 -> 010
	//3 -> 011
	//4 -> 100
	//5 -> 101
	//6 -> 110
	//7 -> 111

	private VoxelOctreeNode parent = null;
	private VoxelOctreeNode[] children = null;
	private int nrEmptySubtrees;

	private int r, g, b, a; //0 - 255

	private int size; //must be a power of 2

	private boolean isRoot = false;

	public VoxelOctreeNode(int size) {
		//raise size to the nearest power of 2
		{
			int tmp = 1;
			while (tmp < size) {
				tmp *= 2;
			}
			size = tmp;
		}

		this.parent = null;
		this.children = new VoxelOctreeNode[8];
		this.nrEmptySubtrees = 8;

		this.isRoot = true;

		this.size = size;
	}

	private VoxelOctreeNode(VoxelOctreeNode parent) {
		this.parent = parent;
		this.children = new VoxelOctreeNode[8];
		this.nrEmptySubtrees = 8;

		this.isRoot = false;

		this.size = parent.size / 2;
	}

	private static final int HEADER_SIZE_POW_BITS = 32;
	private static final int COLOR_BITS = 8;
	private static final int CHILD_OFFSET_BITS = 32;

	/**
	 * Returns the number of bits required to store the subtree in serial form
	 * 
	 * Header takes 32 bits
	 * Leaves take 32 bits
	 * Non-leaves take 8 * 32 bits
	 * 
	 * @param root
	 * @return
	 */
	public static int countRequiredBits(VoxelOctreeNode root) {
		int ans = 0;
		if (root.isRoot) {
			//header
			ans += HEADER_SIZE_POW_BITS;
		}

		if (root.size == 1) {
			ans += COLOR_BITS * 4;
		}
		else {
			ans += CHILD_OFFSET_BITS * 8;
			for (int i = 0; i < 8; i++) {
				if (root.children[i] != null) {
					ans += countRequiredBits(root.children[i]);
				}
			}
		}

		return ans;
	}

	/**
	 * Converts subtree into bits
	 * Nodes are 4 byte aligned
	 * 
	 * Format:
	 * HEADER
	 * K = 32 bit unsigned int
	 * the size of the root is 2^K
	 * 
	 * IF node is leaf, 
	 * color bits
	 * color bits = R + G + B + A
	 * R, G, B, A = 8 bit unsigned int
	 * 
	 * ELSE node is not leaf,
	 * child bits
	 * child bits = child rel offset * 8
	 * child rel offset = 32 bit unsigned int
	 * offset is relative to the first bit of the parent.
	 * if child is null, then offset = 0
	 * 
	 * all numbers will be stored in big endian
	 * 
	 * @param root
	 * @return
	 */
	public static boolean[] serialize(VoxelOctreeNode root) {
		assert root.isRoot;

		boolean[] bits = new boolean[countRequiredBits(root)];

		//header
		int sizePow = 0;
		int tmp = 1;
		while (tmp < root.size) {
			tmp *= 2;
			sizePow++;
		}
		for (int i = 0; i < HEADER_SIZE_POW_BITS; i++) {
			bits[i] = (((sizePow >> (HEADER_SIZE_POW_BITS - 1 - i)) & 1) == 1);
		}

		_serialize(bits, HEADER_SIZE_POW_BITS, root);

		return bits;
	}

	/**
	 * Returns the number of bits used to encode the input subtree
	 * @param bits
	 * @param start
	 * @param root
	 * @return
	 */
	private static int _serialize(boolean[] bits, int start, VoxelOctreeNode root) {
		int nrBits = 0;
		int ptr = start;
		if (root.size == 1) {
			//leaf node, encode color
			for (int i = 0; i < COLOR_BITS; i++) {
				bits[ptr++] = (((root.r >> (COLOR_BITS - 1 - i)) & 1) == 1);
			}
			for (int i = 0; i < 8; i++) {
				bits[ptr++] = (((root.g >> (COLOR_BITS - 1 - i)) & 1) == 1);
			}
			for (int i = 0; i < 8; i++) {
				bits[ptr++] = (((root.b >> (COLOR_BITS - 1 - i)) & 1) == 1);
			}
			for (int i = 0; i < 8; i++) {
				bits[ptr++] = (((root.a >> (COLOR_BITS - 1 - i)) & 1) == 1);
			}
			nrBits = COLOR_BITS * 4;
		}
		else {
			int offset = CHILD_OFFSET_BITS * 8;
			for (int i = 0; i < 8; i++) {
				if (root.children[i] == null) {
					//encode null offset
					for (int j = 0; j < CHILD_OFFSET_BITS; j++) {
						bits[ptr++] = false;
					}
				}
				else {
					//encode offset
					for (int j = 0; j < CHILD_OFFSET_BITS; j++) {
						bits[ptr++] = (((offset >> (CHILD_OFFSET_BITS - 1 - j)) & 1) == 1);
					}
					offset += _serialize(bits, start + offset, root.children[i]);
				}
			}
			nrBits = offset;
		}
		return nrBits;
	}

	/**
	 * Turns serialized octree bits back into octree nodes
	 * @param bits
	 * @return
	 */
	public static VoxelOctreeNode deserialize(boolean[] bits) {
		int rootSizePow = 0;
		for (int i = 0; i < HEADER_SIZE_POW_BITS; i++) {
			rootSizePow *= 2;
			rootSizePow += bits[i] ? 1 : 0;
		}
		int rootSize = (1 << rootSizePow);
		VoxelOctreeNode root = _deserialize(bits, HEADER_SIZE_POW_BITS, rootSize);
		root.isRoot = true;
		return root;
	}

	private static VoxelOctreeNode _deserialize(boolean[] bits, int start, int size) {
		VoxelOctreeNode root = new VoxelOctreeNode(size);
		root.isRoot = false;
		int ptr = start;
		if (size == 1) {
			//parse leaf
			root.r = 0;
			root.g = 0;
			root.b = 0;
			root.a = 0;
			for (int i = 0; i < COLOR_BITS; i++) {
				root.r = (root.r << 1) + (bits[ptr++] ? 1 : 0);
			}
			for (int i = 0; i < COLOR_BITS; i++) {
				root.g = (root.g << 1) + (bits[ptr++] ? 1 : 0);
			}
			for (int i = 0; i < COLOR_BITS; i++) {
				root.b = (root.b << 1) + (bits[ptr++] ? 1 : 0);
			}
			for (int i = 0; i < COLOR_BITS; i++) {
				root.a = (root.a << 1) + (bits[ptr++] ? 1 : 0);
			}
		}
		else {
			//parse children
			for (int i = 0; i < 8; i++) {
				int offset = 0;
				for (int j = 0; j < CHILD_OFFSET_BITS; j++) {
					offset = (offset << 1) + (bits[ptr++] ? 1 : 0);
				}

				//null child
				if (offset == 0) {
					continue;
				}

				root.children[i] = _deserialize(bits, start + offset, size / 2);
				root.nrEmptySubtrees--;
			}
		}
		return root;
	}

	public void addVoxel(int x, int y, int z, int r, int g, int b) {
		//check if is outside range
		if (x < 0 || y < 0 || z < 0 || x >= this.size || y >= this.size || z >= this.size) {
			//it's outside of the range of the current voxel
			assert this.isRoot;
			return;
		}

		if (this.size == 1) {
			//this is the leaf node
			this.r = r;
			this.g = g;
			this.b = b;
			return;
		}

		//insert into a child
		int ind = 0;
		if (x >= this.size / 2) {
			x -= this.size / 2;
			ind += (1 << 0);
		}
		if (y >= this.size / 2) {
			y -= this.size / 2;
			ind += (1 << 1);
		}
		if (z >= this.size / 2) {
			z -= this.size / 2;
			ind += (1 << 2);
		}
		if (this.children[ind] == null) {
			this.nrEmptySubtrees--;
			this.children[ind] = new VoxelOctreeNode(this);
		}
		this.children[ind].addVoxel(x, y, z, r, g, b);
	}

	/**
	 * Return true if the subtree represented by the node is empty
	 * If a subtree is empty, the parent node will remove the reference to the child.
	 * @param x
	 * @param y
	 * @param z
	 * @return
	 */
	public boolean removeVoxel(int x, int y, int z) {
		//check if is outside range
		if (x < 0 || y < 0 || z < 0 || x >= this.size || y >= this.size || z >= this.size) {
			//it's outside of the range of the current voxel
			assert this.isRoot;
			return this.nrEmptySubtrees == 8;
		}

		if (this.size == 1) {
			//we're removing this leaf node
			return true;
		}

		//remove from child
		int ind = 0;
		if (x >= this.size / 2) {
			x -= this.size / 2;
			ind += (1 << 0);
		}
		if (y >= this.size / 2) {
			y -= this.size / 2;
			ind += (1 << 1);
		}
		if (z >= this.size / 2) {
			z -= this.size / 2;
			ind += (1 << 2);
		}
		if (this.children[ind] != null) {
			if (this.children[ind].removeVoxel(x, y, z)) {
				this.nrEmptySubtrees++;
				this.children[ind] = null;
			}
		}
		return this.nrEmptySubtrees == 8;
	}

	@Override
	public boolean equals(Object o) {
		if (o == null) {
			return false;
		}
		if (o == this) {
			return true;
		}
		if (!(o instanceof VoxelOctreeNode)) {
			return false;
		}
		VoxelOctreeNode n = (VoxelOctreeNode) o;
		if (this.size != n.size) {
			return false;
		}
		if (this.size == 1) {
			if (this.r != n.r || this.g != n.g || this.b != n.b) {
				return false;
			}
		}
		else {
			if (this.isRoot != n.isRoot) {
				return false;
			}
			if (this.nrEmptySubtrees != n.nrEmptySubtrees) {
				return false;
			}
			for (int i = 0; i < 8; i++) {
				if (this.children[i] == null) {
					if (n.children[i] != null) {
						return false;
					}
					if (n.children[i] == null) {
						continue;
					}
				}
				if (!this.children[i].equals(n.children[i])) {
					return false;
				}
			}
		}
		return true;
	}

	@Override
	public String toString() {
		StringBuilder res = new StringBuilder();
		res.append("{");
		if (this.size == 1) {
			res.append(this.r + ", " + this.g + " " + this.b);
		}
		else {
			res.append("[");
			for (int i = 0; i < 8; i++) {
				if (this.children[i] == null) {
					res.append("null");
				}
				else {
					res.append(this.children[i].toString());
				}
				if (i != 7) {
					res.append(", ");
				}
			}
			res.append("]");
		}
		res.append("}");
		return res.toString();
	}

}
