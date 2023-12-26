package voxel_raytracing;

import java.util.HashMap;

import lwjglengine.graphics.ShaderStorageBuffer;
import myutils.math.IVec3;
import myutils.math.MathUtils;
import myutils.math.Vec3;
import myutils.misc.Triple;

public class VoxelOctreeManager {

	//TODO
	// - figure out how to serialize multiple chunks at once, in a way that can allow the fragment shader to 
	//   quickly render

	//currently worst case is around 700K ints for a 64^3 volume
	//allocate 1M ints for each chunk. 

	private static final int CHUNK_SIZE = 64;

	private HashMap<String, VoxelOctreeNode> chunks;

	private ShaderStorageBuffer svoSSBO;

	private IVec3 playerCurChunkCoord;

	public VoxelOctreeManager(ShaderStorageBuffer svoSSBO) {
		this.chunks = new HashMap<>();

		this.playerCurChunkCoord = null;

		this.svoSSBO = svoSSBO;
	}

	public void updateSVOSSBO(Vec3 playerPos) {
		IVec3 iplayerPos = new IVec3(MathUtils.floor(playerPos.x), MathUtils.floor(playerPos.y), MathUtils.floor(playerPos.z));
		IVec3 chunkCoord = getChunkCoord(iplayerPos);

		if (!chunkCoord.equals(this.playerCurChunkCoord)) {
			System.out.println("Update SVO SSBO : " + chunkCoord);
			if (this.playerCurChunkCoord == null) {
				this.playerCurChunkCoord = new IVec3();
			}
			this.playerCurChunkCoord.set(chunkCoord);
			boolean[] bits = this.serialize(this.playerCurChunkCoord, -1);

			//convert bits to ints 
			int[] data = new int[bits.length / 32];
			for (int i = 0; i < bits.length; i++) {
				int bit_ind = i % 32;
				data[i / 32] = data[i / 32] | ((bits[i] ? 1 : 0) << (31 - bit_ind));
			}

			System.out.println("Serialized length of chunk " + chunkCoord + " : " + data.length);

			this.svoSSBO.setData(data);
		}
	}

	private VoxelOctreeNode generateChunk(IVec3 chunkCoord) {
		System.out.println("Generating chunk : " + chunkCoord);
		VoxelOctreeNode root = new VoxelOctreeNode(this, CHUNK_SIZE, chunkCoord.mul(CHUNK_SIZE));

		//TODO move this to a compute shader
		for (int cx = 0; cx < CHUNK_SIZE; cx++) {
			for (int cy = 0; cy < CHUNK_SIZE; cy++) {
				for (int cz = 0; cz < CHUNK_SIZE; cz++) {
					int x = chunkCoord.x * CHUNK_SIZE + cx;
					int y = chunkCoord.y * CHUNK_SIZE + cy;
					int z = chunkCoord.z * CHUNK_SIZE + cz;
					//y < Math.sin(Math.sqrt(x * x + z * z) / 20f) * 5 + 10
					if (y < Math.sin(Math.sqrt(x * x + z * z) / 20f) * 5 + 10) {
						int col = (int) (Math.random() * 15);
						int r = 79 + col;
						int g = 58 + col;
						int b = 43 + col;
						Vec3 color = new Vec3(r, g, b);
						color.muli(1.0 / 255.0);
						root.addVoxel(new IVec3(cx, cy, cz), color, new Vec3());
					}
				}
			}
		}

		return root;
	}

	/**
	 * Should only serialize all chunks within viewing range of the center coordinate passed in. 
	 * 
	 * @param center
	 * @param chunkViewDist
	 * @return
	 */
	public boolean[] serialize(IVec3 chunkCoord, int chunkViewDist) {
		//for now, just i'm just going to serialize the center coordinate
		VoxelOctreeNode root = this.getChunkRoot(chunkCoord);
		return root.serialize();
	}

	public static IVec3 getChunkCoord(IVec3 tileCoord) {
		IVec3 v = new IVec3(tileCoord);
		int x_off = 0;
		int y_off = 0;
		int z_off = 0;
		if (v.x < 0) {
			x_off = (-v.x) / CHUNK_SIZE + 1;
			v.x += x_off * CHUNK_SIZE;
		}
		if (v.y < 0) {
			y_off = (-v.y) / CHUNK_SIZE + 1;
			v.y += y_off * CHUNK_SIZE;
		}
		if (v.z < 0) {
			z_off = (-v.z) / CHUNK_SIZE + 1;
			v.z += z_off * CHUNK_SIZE;
		}
		int cx = v.x / CHUNK_SIZE - x_off;
		int cy = v.y / CHUNK_SIZE - y_off;
		int cz = v.z / CHUNK_SIZE - z_off;
		return new IVec3(cx, cy, cz);
	}

	public static IVec3 getChunkRelCoord(IVec3 tileCoord) {
		IVec3 chunkCoord = getChunkCoord(tileCoord);
		return tileCoord.sub(chunkCoord.mul(CHUNK_SIZE));
	}

	public static String getChunkKey(IVec3 chunkCoord) {
		return chunkCoord.toString();
	}

	private VoxelOctreeNode getChunkRoot(IVec3 chunkCoord) {
		String key = getChunkKey(chunkCoord);
		if (!this.chunks.containsKey(key)) {
			//generate chunk
			System.out.println("Chunk with key : \"" + key + "\" doesn't exist");
			this.chunks.put(key, this.generateChunk(chunkCoord));
		}
		return this.chunks.get(key);
	}

	public void addVoxel(IVec3 v, Vec3 color) {
		VoxelOctreeNode root = this.getChunkRoot(v);
		IVec3 rel = getChunkRelCoord(v);
		root.addVoxel(rel, color, color);
	}

	public void removeVoxel(IVec3 v) {
		VoxelOctreeNode root = this.getChunkRoot(v);
		IVec3 rel = getChunkRelCoord(v);
		root.removeVoxel(rel);
	}

	public ShaderStorageBuffer getSVOSSBO() {
		return this.svoSSBO;
	}

	public void kill() {
		this.svoSSBO.kill();
	}

}
