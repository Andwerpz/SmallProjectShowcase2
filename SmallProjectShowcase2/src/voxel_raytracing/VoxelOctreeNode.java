package voxel_raytracing;

import java.util.ArrayList;

import myutils.file.SystemUtils;
import myutils.math.IVec3;
import myutils.math.MathUtils;
import myutils.math.Vec3;
import myutils.math.Vec4;

public class VoxelOctreeNode {

	//TODO 
	// - store per vertex per face AO information
	//   - 6 faces
	//   - each face needs to store 4 vertices
	//   - each vertex should have 2 bits
	//   - total of 6 * 4 * 2 = 48 bits
	//   - could also just store adjacent voxel info and let the gpu figure it out. 

	//the serialized form only stores voxels that are adjacent to air. 

	//children bits correspond to their position within the parent node
	//i -> zyx
	//0 -> 000
	//1 -> 001
	//2 -> 010
	//3 -> 011
	//4 -> 100
	//5 -> 101
	//6 -> 110
	//7 -> 111

	private VoxelOctreeManager manager;
	private VoxelOctreeNode root;

	private VoxelOctreeNode parent = null;
	private VoxelOctreeNode[] children = null;
	private int nrEmptySubtrees;

	private int r, g, b, a; //[0, 255], color data
	private int nx, ny, nz, nw; //[-127, 127], normal data

	private int size; //must be a power of 2

	private boolean isRoot = false;
	private IVec3 offset;

	private IVec3 relOffset;

	//if true, it means that somewhere in this subtree, there exists a leaf that is adjacent to air. 
	private boolean isAirAdjacent;
	private int nrAirAdjacentSubtrees;

	//used by leaf nodes.
	private int nrAdjacentVoxels;

	public VoxelOctreeNode(VoxelOctreeManager manager, int size, IVec3 offset) {
		//raise size to the nearest power of 2
		{
			int tmp = 1;
			while (tmp < size) {
				tmp *= 2;
			}
			size = tmp;
		}

		this.manager = manager;
		this.root = this;

		assert this.manager != null : "Must have manager D:";

		this.parent = null;
		this.children = new VoxelOctreeNode[8];
		this.nrEmptySubtrees = 8;

		this.isRoot = true;
		this.offset = new IVec3(offset);

		this.relOffset = new IVec3();

		this.size = size;

		this.isAirAdjacent = true;
		this.nrAirAdjacentSubtrees = 8;
		this.nrAdjacentVoxels = 0;
	}

	private VoxelOctreeNode(VoxelOctreeNode parent, IVec3 relOffset) {
		this.manager = parent.manager;
		this.root = parent.root;

		this.parent = parent;
		this.children = new VoxelOctreeNode[8];
		this.nrEmptySubtrees = 8;

		this.isRoot = false;

		this.relOffset = new IVec3(relOffset);

		this.size = parent.size / 2;

		this.isAirAdjacent = true;
		this.nrAirAdjacentSubtrees = 8;
		this.nrAdjacentVoxels = 0;
	}

	public boolean isInside(IVec3 v) {
		return !(v.x < 0 || v.y < 0 || v.z < 0 || v.x >= this.size || v.y >= this.size || v.z >= this.size);
	}

	private int calcChildInd(IVec3 relCoord) {
		IVec3 v = new IVec3(relCoord);

		//make sure coords are inside node
		assert this.isInside(v);

		//calculate index
		int ind = 0;
		if (v.x >= this.size / 2) {
			v.x -= this.size / 2;
			ind += (1 << 0);
		}
		if (v.y >= this.size / 2) {
			v.y -= this.size / 2;
			ind += (1 << 1);
		}
		if (v.z >= this.size / 2) {
			v.z -= this.size / 2;
			ind += (1 << 2);
		}
		return ind;
	}

	private IVec3 calcChildCoord(IVec3 coord) {
		assert this.size != 1;
		return coord.mod(this.size / 2);
	}

	private static final int HEADER_SIZE_POW_BITS = 32;
	private static final int HEADER_OFFSET_COMPONENT_BITS = 32;
	private static final int COLOR_COMPONENT_BITS = 8;
	private static final int NORMAL_COMPONENT_BITS = 8;
	private static final int CHILD_OFFSET_BITS = 32;

	/**
	 * Returns the number of bits required to store the subtree in serial form
	 * 
	 * Header takes 32 + 32 * 3 bits
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
			ans += HEADER_SIZE_POW_BITS + HEADER_OFFSET_COMPONENT_BITS * 3;
		}

		if (root.size == 1) {
			ans += COLOR_COMPONENT_BITS * 4;
			ans += NORMAL_COMPONENT_BITS * 4;
		}
		else {
			ans += CHILD_OFFSET_BITS * 8;
			for (int i = 0; i < 8; i++) {
				if (root.children[i] != null && root.children[i].isAirAdjacent) {
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
	 * K + OX + OY + OZ
	 * K, OX, OY, OZ = 32 bit unsigned int
	 * the size of the root is 2^K
	 * 
	 * IF node is leaf, 
	 * color bits + normal bits
	 * color bits = R + G + B + A
	 * R, G, B, A = 8 bit unsigned int
	 * normal bits = X + Y + Z + W
	 * X, Y, Z, W = 8 bit signed int
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
	public boolean[] serialize() {
		assert this.isRoot : "Try calling serialize on the root";

		boolean[] bits = new boolean[countRequiredBits(this)];

		//header
		int sizePow = 0;
		int tmp = 1;
		while (tmp < this.size) {
			tmp *= 2;
			sizePow++;
		}

		int ptr = 0;
		//size pow
		for (int i = 0; i < HEADER_SIZE_POW_BITS; i++) {
			bits[ptr++] = (((sizePow >> (HEADER_SIZE_POW_BITS - 1 - i)) & 1) == 1);
		}

		//offset
		for (int i = 0; i < HEADER_OFFSET_COMPONENT_BITS; i++) {
			bits[ptr++] = (((this.offset.x >> (HEADER_OFFSET_COMPONENT_BITS - 1 - i)) & 1) == 1);
		}
		for (int i = 0; i < HEADER_OFFSET_COMPONENT_BITS; i++) {
			bits[ptr++] = (((this.offset.y >> (HEADER_OFFSET_COMPONENT_BITS - 1 - i)) & 1) == 1);
		}
		for (int i = 0; i < HEADER_OFFSET_COMPONENT_BITS; i++) {
			bits[ptr++] = (((this.offset.z >> (HEADER_OFFSET_COMPONENT_BITS - 1 - i)) & 1) == 1);
		}

		this._serialize(bits, HEADER_SIZE_POW_BITS + HEADER_OFFSET_COMPONENT_BITS * 3);

		return bits;
	}

	/**
	 * Returns the number of bits used to encode the input subtree
	 * @param bits
	 * @param start
	 * @param root
	 * @return
	 */
	private int _serialize(boolean[] bits, int start) {
		int nrBits = 0;
		int ptr = start;
		if (this.size == 1) {
			//leaf node, encode color
			for (int i = 0; i < COLOR_COMPONENT_BITS; i++) {
				bits[ptr++] = (((this.r >> (COLOR_COMPONENT_BITS - 1 - i)) & 1) == 1);
			}
			for (int i = 0; i < COLOR_COMPONENT_BITS; i++) {
				bits[ptr++] = (((this.g >> (COLOR_COMPONENT_BITS - 1 - i)) & 1) == 1);
			}
			for (int i = 0; i < COLOR_COMPONENT_BITS; i++) {
				bits[ptr++] = (((this.b >> (COLOR_COMPONENT_BITS - 1 - i)) & 1) == 1);
			}
			for (int i = 0; i < COLOR_COMPONENT_BITS; i++) {
				bits[ptr++] = (((this.a >> (COLOR_COMPONENT_BITS - 1 - i)) & 1) == 1);
			}
			nrBits += COLOR_COMPONENT_BITS * 4;
			//encode normal
			for (int i = 0; i < NORMAL_COMPONENT_BITS; i++) {
				bits[ptr++] = (((this.nx >> (NORMAL_COMPONENT_BITS - 1 - i)) & 1) == 1);
			}
			for (int i = 0; i < NORMAL_COMPONENT_BITS; i++) {
				bits[ptr++] = (((this.ny >> (NORMAL_COMPONENT_BITS - 1 - i)) & 1) == 1);
			}
			for (int i = 0; i < NORMAL_COMPONENT_BITS; i++) {
				bits[ptr++] = (((this.nz >> (NORMAL_COMPONENT_BITS - 1 - i)) & 1) == 1);
			}
			for (int i = 0; i < NORMAL_COMPONENT_BITS; i++) {
				bits[ptr++] = (((this.nw >> (NORMAL_COMPONENT_BITS - 1 - i)) & 1) == 1);
			}
			nrBits += NORMAL_COMPONENT_BITS * 4;
		}
		else {
			int offset = CHILD_OFFSET_BITS * 8;
			for (int i = 0; i < 8; i++) {
				if (this.children[i] == null || !this.children[i].isAirAdjacent) {
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
					offset += this.children[i]._serialize(bits, start + offset);
				}
			}
			nrBits = offset;
		}
		return nrBits;
	}

	private static int[] adj_dx = { -1, 1, 0, 0, 0, 0 };
	private static int[] adj_dy = { 0, 0, -1, 1, 0, 0 };
	private static int[] adj_dz = { 0, 0, 0, 0, -1, 1 };

	/**
	 * Adds voxel at requested location
	 * Color components are in the range [0, 1]. 
	 * 
	 * @param v
	 * @param color
	 * @param normal
	 */
	public void addVoxel(IVec3 v, Vec3 color, Vec3 normal) {
		//check if is outside range
		if (!this.isInside(v)) {
			//it's outside of the range of the current voxel
			assert this.isRoot;
			return;
		}

		if (this.size == 1) {
			//this is the leaf node, write stuff to it
			this.r = MathUtils.clamp(0, (1 << COLOR_COMPONENT_BITS) - 1, (int) (color.x * (1 << COLOR_COMPONENT_BITS)));
			this.g = MathUtils.clamp(0, (1 << COLOR_COMPONENT_BITS) - 1, (int) (color.y * (1 << COLOR_COMPONENT_BITS)));
			this.b = MathUtils.clamp(0, (1 << COLOR_COMPONENT_BITS) - 1, (int) (color.z * (1 << COLOR_COMPONENT_BITS)));

			normal.normalize();
			this.nx = MathUtils.clamp(-127, 127, (int) (normal.x * (1 << (NORMAL_COMPONENT_BITS - 1))));
			this.ny = MathUtils.clamp(-127, 127, (int) (normal.y * (1 << (NORMAL_COMPONENT_BITS - 1))));
			this.nz = MathUtils.clamp(-127, 127, (int) (normal.z * (1 << (NORMAL_COMPONENT_BITS - 1))));
			this.nx = this.nx < 0 ? Math.abs(this.nx) + (1 << (NORMAL_COMPONENT_BITS - 1)) : this.nx;
			this.ny = this.ny < 0 ? Math.abs(this.ny) + (1 << (NORMAL_COMPONENT_BITS - 1)) : this.ny;
			this.nz = this.nz < 0 ? Math.abs(this.nz) + (1 << (NORMAL_COMPONENT_BITS - 1)) : this.nz;

			//update nr adjacent voxels
			for (int i = 0; i < 6; i++) {
				IVec3 nv = new IVec3(this.relOffset);
				nv.x += adj_dx[i];
				nv.y += adj_dy[i];
				nv.z += adj_dz[i];
				this.nrAdjacentVoxels += this.root.doesVoxelExist(nv) ? 1 : 0;
				this.root.modifyNrAdjacentVoxels(nv, 1);
			}
			this.isAirAdjacent = this.nrAdjacentVoxels != 6;
			return;
		}

		//insert into a child
		int childInd = this.calcChildInd(v);
		IVec3 childCoord = this.calcChildCoord(v);
		if (this.children[childInd] == null) {
			this.nrEmptySubtrees--;
			IVec3 childRelOffset = new IVec3(this.relOffset);
			if (((childInd >> 0) & 1) == 1) {
				childRelOffset.x += this.size / 2;
			}
			if (((childInd >> 1) & 1) == 1) {
				childRelOffset.y += this.size / 2;
			}
			if (((childInd >> 2) & 1) == 1) {
				childRelOffset.z += this.size / 2;
			}
			this.children[childInd] = new VoxelOctreeNode(this, childRelOffset);
		}
		this.children[childInd].addVoxel(childCoord, color, normal);
	}

	/**
	 * Return true if the subtree represented by the node is empty
	 * If a subtree is empty, the parent node will remove the reference to the child.
	 * @param x
	 * @param y
	 * @param z
	 * @return
	 */
	public boolean removeVoxel(IVec3 v) {
		//check if is outside range
		if (!this.isInside(v)) {
			//it's outside of the range of the current voxel
			assert this.isRoot;
			return this.nrEmptySubtrees == 8;
		}

		if (this.size == 1) {
			//we're removing this leaf node

			//update nr adjacent voxels
			for (int i = 0; i < 6; i++) {
				IVec3 nv = new IVec3(this.relOffset);
				nv.x += adj_dx[i];
				nv.y += adj_dy[i];
				nv.z += adj_dz[i];
				this.root.modifyNrAdjacentVoxels(nv, -1);
			}
			return true;
		}

		//remove from child
		int childInd = this.calcChildInd(v);
		IVec3 childCoord = this.calcChildCoord(v);
		if (this.children[childInd] != null) {
			if (this.children[childInd].removeVoxel(childCoord)) {
				this.nrEmptySubtrees++;
				this.children[childInd] = null;
			}
		}
		return this.nrEmptySubtrees == 8;
	}

	private void modifyNrAdjacentVoxels(IVec3 v, int increment) {
		if (!this.isInside(v)) {
			return;
		}

		if (this.size == 1) {
			this.nrAdjacentVoxels += increment;
			this.isAirAdjacent = this.nrAdjacentVoxels != 6;
			return;
		}

		int childInd = this.calcChildInd(v);
		if (this.children[childInd] == null) {
			return;
		}
		IVec3 childCoord = this.calcChildCoord(v);
		this.nrAirAdjacentSubtrees -= this.children[childInd].isAirAdjacent ? 1 : 0;
		this.children[childInd].modifyNrAdjacentVoxels(childCoord, increment);
		this.nrAirAdjacentSubtrees += this.children[childInd].isAirAdjacent ? 1 : 0;
		this.isAirAdjacent = this.nrAirAdjacentSubtrees != 0;
	}

	/**
	 * Given a relative coordinate, returns whether or not a voxel exists at that coordinate. 
	 * @param v
	 * @return
	 */
	private boolean doesVoxelExist(IVec3 v) {
		//check if is outside range
		if (!this.isInside(v)) {
			return false;
		}

		//this is the voxel we're looking for
		if (this.size == 1) {
			return true;
		}

		//check child
		int childInd = this.calcChildInd(v);
		if (this.children[childInd] == null) {
			return false;
		}
		IVec3 childCoord = this.calcChildCoord(v);
		return this.children[childInd].doesVoxelExist(childCoord);
	}

	/**
	 * Given a relative coordinate, returns the VoxelOctreeNode object that represents the leaf at that coordinate,
	 * or if it doesn't exist, returns null. 
	 * @param v
	 */
	private VoxelOctreeNode getLeaf(IVec3 v) {
		if (!this.isInside(v)) {
			return null;
		}

		if (this.size == 1) {
			return this;
		}

		int childInd = this.calcChildInd(v);
		if (this.children[childInd] == null) {
			return null;
		}
		IVec3 childCoord = this.calcChildCoord(v);
		return this.children[childInd].getLeaf(childCoord);
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
			if (this.nx != n.nx || this.ny != n.ny || this.nz != n.nz) {
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
			res.append("(" + this.r + ", " + this.g + ", " + this.b + ")");
			res.append(", ");
			res.append("(" + this.nx + ", " + this.ny + ", " + this.nz + ")");
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
