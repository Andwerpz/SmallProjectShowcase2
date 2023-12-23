package voxel_raytracing;

import java.util.ArrayList;

import myutils.file.SystemUtils;
import myutils.math.IVec3;
import myutils.math.MathUtils;
import myutils.math.Vec3;
import myutils.math.Vec4;

public class VoxelOctreeNode {

	//TODO 
	// - optimize so that the serialized form only stores voxels that are adjacent to air
	// - store per vertex per face AO information
	//   - 6 faces
	//   - each face needs to store 4 vertices
	//   - each vertex should have 2 bits
	//   - total of 6 * 4 * 2 = 48 bits

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

	private int r, g, b, a;  //[0, 255], color data
	private int nx, ny, nz, nw;  //[-127, 127], normal data

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
	
	public boolean isInside(IVec3 v) {
		return !(v.x < 0 || v.y < 0 || v.z < 0 || v.x >= this.size || v.y >= this.size || v.z >= this.size);
	}
	
	private int calcChildInd(IVec3 v) {
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
	
//	private int calcChildInd(int x, int y, int z) {
//		//make sure coords are inside node
//		assert !(x < 0 || y < 0 || z < 0 || x >= this.size || y >= this.size || z >= this.size);
//		
//		//calculate index
//		int ind = 0;
//		if (x >= this.size / 2) {
//			x -= this.size / 2;
//			ind += (1 << 0);
//		}
//		if (y >= this.size / 2) {
//			y -= this.size / 2;
//			ind += (1 << 1);
//		}
//		if (z >= this.size / 2) {
//			z -= this.size / 2;
//			ind += (1 << 2);
//		}
//		return ind;
//	}
	
	private IVec3 calcChildCoord(IVec3 coord) {
		assert this.size != 1;
		return coord.mod(this.size / 2);
	}
	
//	private IVec3 calcChildCoord(int x, int y, int z) {
//		return this.calcChildCoord(new IVec3(x, y, z));
//	}

	private static final int HEADER_SIZE_POW_BITS = 32;
	private static final int COLOR_COMPONENT_BITS = 8;
	private static final int NORMAL_COMPONENT_BITS = 8;
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
			ans += COLOR_COMPONENT_BITS * 4;
			ans += NORMAL_COMPONENT_BITS * 4;
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
		assert this.isRoot;

		boolean[] bits = new boolean[countRequiredBits(this)];

		//header
		int sizePow = 0;
		int tmp = 1;
		while (tmp < this.size) {
			tmp *= 2;
			sizePow++;
		}
		for (int i = 0; i < HEADER_SIZE_POW_BITS; i++) {
			bits[i] = (((sizePow >> (HEADER_SIZE_POW_BITS - 1 - i)) & 1) == 1);
		}
		
		this._serialize(bits, HEADER_SIZE_POW_BITS);

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
			for(int i = 0; i < NORMAL_COMPONENT_BITS; i++) {
				bits[ptr++] = (((this.nx >> (NORMAL_COMPONENT_BITS - 1 - i)) & 1) == 1);
			}
			for(int i = 0; i < NORMAL_COMPONENT_BITS; i++) {
				bits[ptr++] = (((this.ny >> (NORMAL_COMPONENT_BITS - 1 - i)) & 1) == 1);
			}
			for(int i = 0; i < NORMAL_COMPONENT_BITS; i++) {
				bits[ptr++] = (((this.nz >> (NORMAL_COMPONENT_BITS - 1 - i)) & 1) == 1);
			}
			for(int i = 0; i < NORMAL_COMPONENT_BITS; i++) {
				bits[ptr++] = (((this.nw >> (NORMAL_COMPONENT_BITS - 1 - i)) & 1) == 1);
			}
			nrBits += NORMAL_COMPONENT_BITS * 4;
		}
		else {
			int offset = CHILD_OFFSET_BITS * 8;
			for (int i = 0; i < 8; i++) {
				if (this.children[i] == null) {
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
			for (int i = 0; i < COLOR_COMPONENT_BITS; i++) {
				root.r = (root.r << 1) + (bits[ptr++] ? 1 : 0);
			}
			for (int i = 0; i < COLOR_COMPONENT_BITS; i++) {
				root.g = (root.g << 1) + (bits[ptr++] ? 1 : 0);
			}
			for (int i = 0; i < COLOR_COMPONENT_BITS; i++) {
				root.b = (root.b << 1) + (bits[ptr++] ? 1 : 0);
			}
			for (int i = 0; i < COLOR_COMPONENT_BITS; i++) {
				root.a = (root.a << 1) + (bits[ptr++] ? 1 : 0);
			}
			root.nx = 0;
			root.ny = 0;
			root.nz = 0;
			root.nw = 0;
			for(int i = 0; i < NORMAL_COMPONENT_BITS; i++) {
				root.nx = (root.nx << 1) + (bits[ptr++] ? 1 : 0);
			}
			for(int i = 0; i < NORMAL_COMPONENT_BITS; i++) {
				root.ny = (root.ny << 1) + (bits[ptr++] ? 1 : 0);
			}
			for(int i = 0; i < NORMAL_COMPONENT_BITS; i++) {
				root.nz = (root.nz << 1) + (bits[ptr++] ? 1 : 0);
			}
			for(int i = 0; i < NORMAL_COMPONENT_BITS; i++) {
				root.nw = (root.nw << 1) + (bits[ptr++] ? 1 : 0);
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

//	public void addVoxel(int x, int y, int z, Vec3 color, Vec3 normal) {
//		//check if is outside range
//		if (x < 0 || y < 0 || z < 0 || x >= this.size || y >= this.size || z >= this.size) {
//			//it's outside of the range of the current voxel
//			assert this.isRoot;
//			return;
//		}
//
//		if (this.size == 1) {
//			//this is the leaf node
//			this.r = MathUtils.clamp(0, (1 << COLOR_COMPONENT_BITS) - 1, (int) (color.x * (1 << COLOR_COMPONENT_BITS)));
//			this.g = MathUtils.clamp(0, (1 << COLOR_COMPONENT_BITS) - 1, (int) (color.y * (1 << COLOR_COMPONENT_BITS)));
//			this.b = MathUtils.clamp(0, (1 << COLOR_COMPONENT_BITS) - 1, (int) (color.z * (1 << COLOR_COMPONENT_BITS)));
//			
//			normal.normalize();
//			this.nx = MathUtils.clamp(-127, 127, (int) (normal.x * (1 << (NORMAL_COMPONENT_BITS - 1))));
//			this.ny = MathUtils.clamp(-127, 127, (int) (normal.y * (1 << (NORMAL_COMPONENT_BITS - 1))));
//			this.nz = MathUtils.clamp(-127, 127, (int) (normal.z * (1 << (NORMAL_COMPONENT_BITS - 1))));
//			this.nx = this.nx < 0? Math.abs(this.nx) + (1 << (NORMAL_COMPONENT_BITS - 1)) : this.nx;
//			this.ny = this.ny < 0? Math.abs(this.ny) + (1 << (NORMAL_COMPONENT_BITS - 1)) : this.ny;
//			this.nz = this.nz < 0? Math.abs(this.nz) + (1 << (NORMAL_COMPONENT_BITS - 1)) : this.nz;
//			return;
//		}
//
//		//insert into a child
//		int ind = this.calcChildInd(x, y, z);
//		IVec3 childCoord = this.calcChildCoord(x, y, z);
//		if (this.children[ind] == null) {
//			this.nrEmptySubtrees--;
//			this.children[ind] = new VoxelOctreeNode(this);
//		}
//		this.children[ind].addVoxel(childCoord, color, normal);
//	}
	
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
			this.nx = this.nx < 0? Math.abs(this.nx) + (1 << (NORMAL_COMPONENT_BITS - 1)) : this.nx;
			this.ny = this.ny < 0? Math.abs(this.ny) + (1 << (NORMAL_COMPONENT_BITS - 1)) : this.ny;
			this.nz = this.nz < 0? Math.abs(this.nz) + (1 << (NORMAL_COMPONENT_BITS - 1)) : this.nz;
			return;
		}

		//insert into a child
		int childInd = this.calcChildInd(v);
		IVec3 childCoord = this.calcChildCoord(v);
		if (this.children[childInd] == null) {
			this.nrEmptySubtrees--;
			this.children[childInd] = new VoxelOctreeNode(this);
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
//	public boolean removeVoxel(int x, int y, int z) {
//		//check if is outside range
//		if (x < 0 || y < 0 || z < 0 || x >= this.size || y >= this.size || z >= this.size) {
//			//it's outside of the range of the current voxel
//			assert this.isRoot;
//			return this.nrEmptySubtrees == 8;
//		}
//
//		if (this.size == 1) {
//			//we're removing this leaf node
//			return true;
//		}
//
//		//remove from child
//		int ind = this.calcChildInd(x, y, z);
//		IVec3 childCoord = this.calcChildCoord(x, y, z);
//		if (this.children[ind] != null) {
//			if (this.children[ind].removeVoxel(childCoord)) {
//				this.nrEmptySubtrees++;
//				this.children[ind] = null;
//			}
//		}
//		return this.nrEmptySubtrees == 8;
//	}
	
	public boolean removeVoxel(IVec3 v) {
		//check if is outside range
		if (!this.isInside(v)) {
			//it's outside of the range of the current voxel
			assert this.isRoot;
			return this.nrEmptySubtrees == 8;
		}

		if (this.size == 1) {
			//we're removing this leaf node
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
	
//	public boolean doesVoxelExist(int x, int y, int z) {
//		//check if is outside range
//		if(x < 0 || y < 0 || z < 0 || x >= this.size || y >= this.size || z >= this.size) {
//			return false;
//		}
//		
//		//this is the voxel we're looking for
//		if(this.size == 1) {
//			return true;
//		}
//		
//		//check child
//		int ind = this.calcChildInd(x, y, z);
//		if(this.children[ind] == null) {
//			return false;
//		}
//		IVec3 childCoord = this.calcChildCoord(x, y, z);
//		return this.children[ind].doesVoxelExist(childCoord);
//	}

	public boolean doesVoxelExist(IVec3 v) {
		//check if is outside range
		if(!this.isInside(v)) {
			return false;
		}
		
		//this is the voxel we're looking for
		if(this.size == 1) {
			return true;
		}
		
		//check child
		int childInd = this.calcChildInd(v);
		if(this.children[childInd] == null) {
			return false;
		}
		IVec3 childCoord = this.calcChildCoord(v);
		return this.children[childInd].doesVoxelExist(childCoord);
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
			if(this.nx != n.nx || this.ny != n.ny || this.nz != n.nz) {
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
