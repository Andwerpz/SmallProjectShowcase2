package voxel_raytracing;

import java.util.HashMap;

import myutils.math.IVec3;
import myutils.math.Vec3;
import myutils.misc.Triple;

public class VoxelOctreeManager {
	
	private static final int CHUNK_SIZE = 64;
	
	private HashMap<String, VoxelOctreeNode> chunks;
	
	public VoxelOctreeManager() {
		this.chunks = new HashMap<>();
	}
	
	public boolean[] serialize(Vec3 center, int chunkViewDist) {
		//TODO
		return null;
	}
	
	public static String getChunkKey(IVec3 v) {
		int x_off = 0;
		int y_off = 0;
		int z_off = 0;
		if(v.x < 0) {
			x_off = (-v.x) / CHUNK_SIZE + 1;
			v.x += x_off * CHUNK_SIZE;
		}
		if(v.y < 0) {
			y_off = (-v.y) / CHUNK_SIZE + 1;
			v.y += y_off * CHUNK_SIZE;
		}
		if(v.z < 0) {
			z_off = (-v.z) / CHUNK_SIZE + 1;
			v.z += z_off * CHUNK_SIZE;
		}
		int cx = v.x / CHUNK_SIZE - x_off;
		int cy = v.y / CHUNK_SIZE - y_off;
		int cz = v.z / CHUNK_SIZE - z_off;
		return cx + " " + cy + " " + cz;
	}
	
	public static IVec3 getChunkRelCoord(IVec3 v) {
		if(v.x < 0) {
			v.x += ((-v.x) / CHUNK_SIZE + 1) * CHUNK_SIZE;
		}
		if(v.y < 0) {
			v.y += ((-v.y) / CHUNK_SIZE + 1) * CHUNK_SIZE;
		}
		if(v.z < 0) {
			v.z += ((-v.z) / CHUNK_SIZE + 1) * CHUNK_SIZE;
		}
		int cx = v.x % CHUNK_SIZE;
		int cy = v.y % CHUNK_SIZE;
		int cz = v.z % CHUNK_SIZE;
		return new IVec3(cx, cy, cz);
	}
	
	private VoxelOctreeNode getChunkRoot(IVec3 v) {
		String key = getChunkKey(v);
		return this.chunks.get(key);
	}
	
	public void addVoxel(IVec3 v, Vec3 color) {
		VoxelOctreeNode root = this.getChunkRoot(v);
		if(root == null) {
			return;
		}
		IVec3 rel = getChunkRelCoord(v);
		root.addVoxel(rel, color, color);
	}
	
	public void removeVoxel(IVec3 v) {
		VoxelOctreeNode root = this.getChunkRoot(v);
		if(root == null) {
			return;
		}
		IVec3 rel = getChunkRelCoord(v);
		root.removeVoxel(rel);
	}
	
	public boolean doesVoxelExist(IVec3 v) {
		VoxelOctreeNode root = this.getChunkRoot(v);
		if(root == null) {
			return false;
		}
		IVec3 rel = getChunkRelCoord(v);
		return root.doesVoxelExist(rel);
	}
	
}
