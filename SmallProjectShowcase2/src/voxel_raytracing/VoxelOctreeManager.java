package voxel_raytracing;

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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Queue;

import com.sun.jna.platform.win32.Wincon.COORD;

import lwjglengine.graphics.ShaderStorageBuffer;
import myutils.math.IVec3;
import myutils.math.MathUtils;
import myutils.math.Vec3;
import myutils.misc.Triple;

public class VoxelOctreeManager {

	//TODO
	// - move generating chunks to the gpu, and do that on another thread. 
	// - move updating ssbo to another thread, or at least make it extremely fast
	// - when generating chunks, we quickly run out of JVM heap space. 
	//   - one idea is to save chunks that are pretty far away in the hard drive, and load them when we need them again.
	//   - another idea is to try to reduce the amount of memory required to store the chunks, but this one is pretty hard.

	//currently worst case is around 700K ints for a 64^3 volume
	//allocate 1M ints for each chunk. 

	//block 0 is used for chunk indexing information. 

	private static final int CHUNK_SIZE = 64;
	private static final long CHUNK_BLOCK_SIZE_INTS = (1 << 20); //allocate 1M ints for each chunk
	private static final long CHUNK_BLOCK_SIZE_BYTES = CHUNK_BLOCK_SIZE_INTS * 4l;
	private static final long NR_BUFFER_BLOCKS = 1000;

	private Queue<Integer> availableBlocks;

	//if a chunk is in the ssbo, then it is present in this map. 
	//we can easily find it's block index. 
	private HashMap<IVec3, Integer> chunkBlockIndexes;

	private HashMap<IVec3, VoxelOctreeNode> chunks;

	private ShaderStorageBuffer ssbo;

	private IVec3 playerCurChunkCoord;
	private int viewDist = 2;

	public VoxelOctreeManager() {
		System.out.println("Creating voxel chunk manager");
		this.chunks = new HashMap<>();

		this.playerCurChunkCoord = null;

		this.ssbo = new ShaderStorageBuffer();
		this.ssbo.setUsage(GL_DYNAMIC_DRAW);
		this.ssbo.setSize(CHUNK_BLOCK_SIZE_BYTES * NR_BUFFER_BLOCKS);

		this.availableBlocks = new ArrayDeque<>();
		for (int i = 1; i < NR_BUFFER_BLOCKS; i++) {
			this.availableBlocks.add(i);
		}

		this.chunkBlockIndexes = new HashMap<>();
	}

	public void updateSSBO(Vec3 playerPos) {
		IVec3 iplayerPos = new IVec3(MathUtils.floor(playerPos.x), MathUtils.floor(playerPos.y), MathUtils.floor(playerPos.z));
		IVec3 chunkCoord = getChunkCoord(iplayerPos);

		if (chunkCoord.equals(this.playerCurChunkCoord)) {
			//no need for updating
			return;
		}

		System.out.println("Update SVO SSBO : " + chunkCoord);
		long startMillis = System.currentTimeMillis();

		if (this.playerCurChunkCoord == null) {
			this.playerCurChunkCoord = new IVec3();
		}
		this.playerCurChunkCoord.set(chunkCoord);

		HashSet<IVec3> inView = new HashSet<>();
		for (int i = chunkCoord.x - this.viewDist; i <= chunkCoord.x + this.viewDist; i++) {
			for (int j = chunkCoord.y - this.viewDist; j <= chunkCoord.y + this.viewDist; j++) {
				for (int k = chunkCoord.z - this.viewDist; k <= chunkCoord.z + this.viewDist; k++) {
					inView.add(new IVec3(i, j, k));
				}
			}
		}

		//remove stuff that is not in view
		System.out.println("Removing stuff in view");
		HashSet<IVec3> toRemove = new HashSet<>();
		for (IVec3 coord : this.chunkBlockIndexes.keySet()) {
			if (inView.contains(coord)) {
				continue;
			}
			toRemove.add(coord);
		}
		for (IVec3 coord : toRemove) {
			this.availableBlocks.add(this.chunkBlockIndexes.get(coord));
			this.chunkBlockIndexes.remove(coord);
		}

		//add stuff that is newly in view
		System.out.println("Adding stuff that is in view");
		for (IVec3 coord : inView) {
			if (this.chunkBlockIndexes.containsKey(coord)) {
				continue;
			}
			if (this.availableBlocks.size() == 0) {
				System.err.println("VoxelOctreeManager : Ran out of blocks in ssbo");
				break;
			}

			int blockIndex = this.availableBlocks.poll();
			this.chunkBlockIndexes.put(coord, blockIndex);

			boolean[] bits = this.getChunkRoot(coord).serialize();
			int[] data = new int[bits.length / 32];
			for (int i = 0; i < bits.length; i++) {
				int bit_ind = i % 32;
				data[i / 32] = data[i / 32] | ((bits[i] ? 1 : 0) << (31 - bit_ind));
			}

			System.out.println("Chunk " + coord + " assigned to block " + blockIndex);
			this.ssbo.setSubData(data, blockIndex * CHUNK_BLOCK_SIZE_BYTES);
		}

		//update indexing
		System.out.println("Updating indexing");
		int[] indexingData = new int[(int) CHUNK_BLOCK_SIZE_INTS];
		int cubeSize = 2 * this.viewDist + 1;
		IVec3 cubeBase = chunkCoord.sub(new IVec3(this.viewDist));
		for (IVec3 coord : inView) {
			IVec3 cubeOffset = coord.sub(cubeBase);
			int index = cubeOffset.x + cubeOffset.y * cubeSize + cubeOffset.z * cubeSize * cubeSize;
			indexingData[index] = this.chunkBlockIndexes.get(coord);
		}

		indexingData[1000000] = CHUNK_SIZE;
		indexingData[1000001] = this.viewDist;

		this.ssbo.setSubData(indexingData, 0);

		System.out.println("Updating buffer took : " + (System.currentTimeMillis() - startMillis) + " millis");
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

	private VoxelOctreeNode getChunkRoot(IVec3 chunkCoord) {
		IVec3 key = new IVec3(chunkCoord);
		if (!this.chunks.containsKey(key)) {
			//generate chunk
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

	public ShaderStorageBuffer getSSBO() {
		return this.ssbo;
	}

	public void kill() {
		this.ssbo.kill();
	}

}
