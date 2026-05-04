package gaussian_splatting;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL13.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL21.*;
import static org.lwjgl.opengl.GL30.*;
import static org.lwjgl.opengl.GL31.*;
import static org.lwjgl.opengl.GL32.*;
import static org.lwjgl.opengl.GL33.*;
import static org.lwjgl.opengl.GL42.glMemoryBarrier;
import static org.lwjgl.opengl.GL43.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;
import static org.lwjgl.opengl.GL31.*;
import static org.lwjgl.opengl.GL33.*;

import java.util.ArrayList;
import java.util.List;

import lwjglengine.graphics.Framebuffer;
import lwjglengine.graphics.Shader;
import lwjglengine.graphics.ShaderStorageBuffer;
import lwjglengine.graphics.Texture;
import lwjglengine.player.Camera;
import lwjglengine.screen.Screen;
import lwjglengine.screen.SkyboxCube;
import lwjglengine.util.ShaderUtils;
import myutils.math.Mat4;
import myutils.math.MathUtils;
import myutils.math.Vec3;

public class GaussianSplattingScreen extends Screen {
	
	//SH approximation of global illumination
	// https://www.cse.chalmers.se/~uffe/xjobb/Readings/GlobalIllumination/Spherical%20Harmonic%20Lighting%20-%20the%20gritty%20details.pdf
	
	//gaussian rendering pipeline. screen partitioned into 16x16 pixel tiles. 
	//buffers :
	// - gaussian buffer : raw gaussians
	// - tile buffer : tile-gaussian pairs
	// - gaussian start buffer : start offset of each gaussian into tile buffer
	// - tile start buffer : start offset of each tile into sorted tile buffer
	
	//pipeline : 
	// - (1) for each gaussian, compute how many tiles it meaningfully contributes to. 
	// - (2) for each gaussian, compute the start offset within the tile buffer
	//   - this is just computing the prefix sum array
	//   - https://developer.nvidia.com/gpugems/gpugems3/part-vi-gpu-computing/chapter-39-parallel-prefix-sum-scan-cuda
	// - allocate tile buffer big enough to store all tile-gaussian pairings. 
	//   - buffer should be re-used between frames
	//   - can dynamically resize this buffer ig
	// - (3) for each gaussian, write all its pairings into the tile buffer
	// - (4) sort tile buffer, sort by tile ID first then gaussian depth
	//   - bitonic merge sort is annoying to use with arbitrarily sized lists
	//   - radix sort is faster and works better with arbitrarily sized lists. 
	// - (5) for each tile, compute its start offset within the tile buffer
	//   - launch one thread per tile, binary search for start offset within tile buffer
	// - (6) for each pixel, loop through all gaussians contributing to its tile. 
	//   - for each tile, should have computed start location in buffer. 
	//   - use shader local storage, each thread in tile should fetch one gaussian from buffer into local memory
	// - (7) render rasterized pixels to output buffer
	
	// TODO
	// - right now if too many gaussians end up in one tile, then one fragment shader has to iterate through all of them
	//   maybe put a hard cap on how many gaussians one fragment shader can iterate across?
	//   - placed a hard cap, but it leads to some nasty artifacts when rendering scenes with lots of gaussians. 
	// - ok, optimized phase (4). Now, phases (1), (4), (6) are taking roughly equal amounts of time for large scenes. 
	//   perhaps there is a way to optimize the math done in phase (1)
	
/*
colmap feature_extractor \
  --database_path ./captures/myscene/database.db \
  --image_path ./captures/myscene/images

colmap exhaustive_matcher \
  --database_path ./captures/myscene/database.db

colmap mapper \
  --database_path ./captures/myscene/database.db \
  --image_path ./captures/myscene/images \
  --output_path ./captures/myscene/sparse

opensplat /path/to/project -n <itercnt> 
*/
	
	private Shader splattingShader;
	
	static final int NR_PHASES = 7;
	
	static final int SIZEOF_GAUSSIAN = 240;
	static final int SIZEOF_TILE = 8;
	static final int SIZEOF_GAUSSIAN_INFO = 64;
	
	private int nrGaussians;	//number of gaussians in the gaussian buffer
	private int nrScreenTiles;	//number of 16x16 pixel tiles on the screen
	private int maxTiles;		//maximum amount of tiles that the tile buffers can currently handle
	
	private boolean reflectY = true;
	private float renderScale = 20.0f;
	
	static final int TILE_SIZE = 16;
	
	static final int RADIX_NR_PHASES = 3;
	static final int RADIX_NR_ITERATIONS = 8;
	static final int RADIX_ELEMENTS_PER_THREAD = 8;
	static final int RADIX_THREADS_PER_BLOCK = 32;
	static final int RADIX_ELEMENTS_PER_BLOCK = RADIX_ELEMENTS_PER_THREAD * RADIX_THREADS_PER_BLOCK;
	static final int RADIX_K = 4;
	static final int RADIX_PREFIX_SCAN_ELEMENTS_PER_BLOCK = 64;
	
	static final int BLELLOCH_BLOCK_SIZE = 1 << 10;
	
	private ShaderStorageBuffer gaussianSSBO;
	private ShaderStorageBuffer gaussianStartSSBO;
	private ShaderStorageBuffer tileSSBO1;
	private ShaderStorageBuffer tileSSBO2;
	private ShaderStorageBuffer blockHistogramSSBO;
	private ShaderStorageBuffer tileStartSSBO;
	private ShaderStorageBuffer renderSSBO;
	private ShaderStorageBuffer prefixBlockSSBO;
	private ShaderStorageBuffer gaussianInfoSSBO;
	private ShaderStorageBuffer blockHistogramPrefixSSBO;
	
	private Shader pipeline1;
	private Shader pipeline2;
	private Shader pipeline3;
	private Shader pipeline4;
	private Shader pipeline5;
	private Shader pipeline6;
	private Shader pipeline7;
	
	static final int TIMING_PRINT_INTERVAL = 120;
	private boolean timingEnabled = true;
	private int timingFrameCounter;
	private int[] timingQueries;
	private double[] timingPhaseMS;
	
	private int[][] radixTimingQueries;
	private double[][] radixTimingMS;
	
	public GaussianSplattingScreen() {
		super();
		this.splattingShader = ShaderUtils.createShader("/gaussian_splatting/splatting.vert", "/gaussian_splatting/splatting.frag");
		
		this.pipeline1 = ShaderUtils.createShader("/gaussian_splatting/pipeline1.compute", GL_COMPUTE_SHADER);
		this.pipeline2 = ShaderUtils.createShader("/gaussian_splatting/pipeline2.compute", GL_COMPUTE_SHADER);
		this.pipeline3 = ShaderUtils.createShader("/gaussian_splatting/pipeline3.compute", GL_COMPUTE_SHADER);
		this.pipeline4 = ShaderUtils.createShader("/gaussian_splatting/pipeline4.compute", GL_COMPUTE_SHADER);
		this.pipeline5 = ShaderUtils.createShader("/gaussian_splatting/pipeline5.compute", GL_COMPUTE_SHADER);
		this.pipeline6 = ShaderUtils.createShader("/gaussian_splatting/pipeline6.compute", GL_COMPUTE_SHADER);
		this.pipeline7 = ShaderUtils.createShader("/gaussian_splatting/pipeline7.vert", "/gaussian_splatting/pipeline7.frag");
		
		this.gaussianSSBO = new ShaderStorageBuffer();
		this.gaussianSSBO.setUsage(GL_STATIC_DRAW);
		this.gaussianStartSSBO = new ShaderStorageBuffer();
		this.gaussianStartSSBO.setUsage(GL_DYNAMIC_DRAW);
		
		this.tileSSBO1 = new ShaderStorageBuffer();
		this.tileSSBO1.setUsage(GL_DYNAMIC_DRAW);
		this.tileSSBO2 = new ShaderStorageBuffer();
		this.tileSSBO2.setUsage(GL_DYNAMIC_DRAW);
		this.blockHistogramSSBO = new ShaderStorageBuffer();
		this.blockHistogramSSBO.setUsage(GL_DYNAMIC_DRAW);
		this.tileStartSSBO = new ShaderStorageBuffer();
		this.tileStartSSBO.setUsage(GL_DYNAMIC_DRAW);
		this.renderSSBO = new ShaderStorageBuffer();
		this.renderSSBO.setUsage(GL_DYNAMIC_DRAW);
		this.prefixBlockSSBO = new ShaderStorageBuffer();
		this.prefixBlockSSBO.setUsage(GL_DYNAMIC_DRAW);
		this.gaussianInfoSSBO = new ShaderStorageBuffer();
		this.gaussianInfoSSBO.setUsage(GL_DYNAMIC_DRAW);
		this.blockHistogramPrefixSSBO = new ShaderStorageBuffer();
		this.blockHistogramPrefixSSBO.setUsage(GL_DYNAMIC_DRAW);
		
		this.nrScreenTiles = 
			((this.getScreenWidth() + TILE_SIZE - 1) / TILE_SIZE) * 
			((this.getScreenHeight() + TILE_SIZE - 1) / TILE_SIZE)
		;
		this.tileStartSSBO.setSize((this.nrScreenTiles + 1) * 4);
		this.renderSSBO.setSize(this.getScreenWidth() * this.getScreenHeight() * 4 * 4);
		
		this.maxTiles = 0;
		
		this.timingFrameCounter = 0;
		this.timingQueries = new int[NR_PHASES];
		this.timingPhaseMS = new double[NR_PHASES];
		for(int i = 0; i < NR_PHASES; i++) {
			this.timingQueries[i] = glGenQueries();
			this.timingPhaseMS[i] = 0;
		}
		
		this.radixTimingQueries = new int[RADIX_NR_ITERATIONS][RADIX_NR_PHASES];
		this.radixTimingMS = new double[RADIX_NR_ITERATIONS][RADIX_NR_PHASES];
		for(int i = 0; i < RADIX_NR_ITERATIONS; i++) {
			for(int j = 0; j < RADIX_NR_PHASES; j++) {
				this.radixTimingQueries[i][j] = glGenQueries();
				this.radixTimingMS[i][j] = 0;
			}
		}
		
		int maxBlockSize = glGetInteger(GL_MAX_SHADER_STORAGE_BLOCK_SIZE);
		int maxBindings  = glGetInteger(GL_MAX_SHADER_STORAGE_BUFFER_BINDINGS);

		System.out.println("Max SSBO block size: " + maxBlockSize + " bytes");
		System.out.println("Max SSBO bindings: " + maxBindings);
	}
	
	public void setReflectY(boolean b) {
		this.reflectY = b;
	}
	
	public void setRenderScale(float f) {
		this.renderScale = Math.max(0.05f, f);
	}
	
	public void setTimingEnabled(boolean b) {
		this.timingEnabled = b;
	}
	
	public void setCameraPos(Vec3 _pos) {
		this.camera.setPos(_pos);
	}

	public void setCameraFacing(Vec3 _facing) {
		this.camera.setFacing(_facing);
	}
	
	public void setGaussians(Gaussian[] gaussians) {
		float[] data = new float[gaussians.length * SIZEOF_GAUSSIAN / 4];
		
		for(int i = 0; i < gaussians.length; i++) {
			Gaussian g = gaussians[i];
			data[i * SIZEOF_GAUSSIAN / 4 + 0] = g.center.x;		// center
			data[i * SIZEOF_GAUSSIAN / 4 + 1] = g.center.y;
			data[i * SIZEOF_GAUSSIAN / 4 + 2] = g.center.z;
			data[i * SIZEOF_GAUSSIAN / 4 + 3] = 0;				// pad0
			data[i * SIZEOF_GAUSSIAN / 4 + 4] = g.orient.s;		// orient
			data[i * SIZEOF_GAUSSIAN / 4 + 5] = g.orient.i;		
			data[i * SIZEOF_GAUSSIAN / 4 + 6] = g.orient.j;		
			data[i * SIZEOF_GAUSSIAN / 4 + 7] = g.orient.k;		
			data[i * SIZEOF_GAUSSIAN / 4 + 8] = g.scale.x;		// scale
			data[i * SIZEOF_GAUSSIAN / 4 + 9] = g.scale.y;
			data[i * SIZEOF_GAUSSIAN / 4 + 10] = g.scale.z;	
			data[i * SIZEOF_GAUSSIAN / 4 + 11] = g.alpha;		// alpha
			
			for(int j = 0; j < 16; j++) {
				data[i * SIZEOF_GAUSSIAN / 4 + 12 + j] = g.r_coeff[j];
				data[i * SIZEOF_GAUSSIAN / 4 + 28 + j] = g.g_coeff[j];
				data[i * SIZEOF_GAUSSIAN / 4 + 44 + j] = g.b_coeff[j];
			}
		}
		
		this.nrGaussians = gaussians.length;
		this.gaussianSSBO.setData(data);
		this.gaussianStartSSBO.setSize((gaussians.length + 1 + BLELLOCH_BLOCK_SIZE) * 4);
		this.prefixBlockSSBO.setSize((gaussians.length + 1 + BLELLOCH_BLOCK_SIZE) * 4);
		this.gaussianInfoSSBO.setSize(SIZEOF_GAUSSIAN_INFO * this.nrGaussians);
	}
	
	@Override
	public void buildBuffers() {		
		Vec3 cameraPos = new Vec3();
		Vec3 cameraFacing = new Vec3(0, 0, -1);

		if (this.camera != null) {
			cameraPos = this.camera.getPos();
			cameraFacing = this.camera.getFacing();
		}

		this.camera = new Camera((float) Math.toRadians(90f), this.screenWidth, this.screenHeight, 0.1f, 200f);
		this.camera.setPos(cameraPos);
		this.camera.setFacing(cameraFacing);
		
		if(this.tileStartSSBO != null) {
			this.nrScreenTiles = 
				((this.getScreenWidth() + TILE_SIZE - 1) / TILE_SIZE) * 
				((this.getScreenHeight() + TILE_SIZE - 1) / TILE_SIZE)
			;
			this.tileStartSSBO.setSize((this.nrScreenTiles + 1) * 4);
			this.renderSSBO.setSize(this.getScreenWidth() * this.getScreenHeight() * 4 * 4);
		}
	}

	@Override
	protected void _render(Framebuffer outputBuffer) {
		// -- PHASE 1 --
		// - for each gaussian figure out how many tiles it intersects
		// - write this to the gaussian start buffer
		{
			glBeginQuery(GL_TIME_ELAPSED, this.timingQueries[0]);
			this.pipeline1.enable();
			
			this.pipeline1.setUniformMat4("pr_matrix", this.camera.getProjectionMatrix());
			this.pipeline1.setUniformMat4("vw_matrix", this.camera.getViewMatrix());
			this.pipeline1.setUniform3f("cameraPos", this.camera.getPos());
			
			this.pipeline1.setUniform1i("reflectY", this.reflectY ? 1 : 0);
			this.pipeline1.setUniform1f("renderScale", this.renderScale);
			
			this.pipeline1.setUniform1i("screenWidth", this.getScreenWidth());
			this.pipeline1.setUniform1i("screenHeight", this.getScreenHeight());
			this.pipeline1.setUniform1f("verticalFOV", this.camera.getVerticalFOV());
			this.pipeline1.setUniform1i("tileSize", TILE_SIZE);
			
			this.pipeline1.setUniform1i("nrGaussians", this.nrGaussians);
			
			this.gaussianSSBO.bindToBase(0);
			this.gaussianStartSSBO.bindToBase(1);
			this.gaussianInfoSSBO.bindToBase(7);
			
			glDispatchCompute((this.nrGaussians + 32 - 1) / 32, 1, 1);
			glMemoryBarrier(GL_SHADER_STORAGE_BARRIER_BIT);
			glEndQuery(GL_TIME_ELAPSED);
		}
		
		// -- PHASE 2 -- 
		// - take exclusive prefix sum of gaussian start buffer
		{
			glBeginQuery(GL_TIME_ELAPSED, this.timingQueries[1]);
			int nr_blocks = (this.nrGaussians / BLELLOCH_BLOCK_SIZE) + 1;

			this.pipeline2.enable();
			
			this.pipeline2.setUniform1i("nrBlocks", nr_blocks);

			this.gaussianStartSSBO.bindToBase(1);
			this.prefixBlockSSBO.bindToBase(2);
			
			for(int phase = 1; phase <= 3; phase++) {
				this.pipeline2.setUniform1i("scanPhase", phase);
				
				glDispatchCompute(phase == 2? 1 : nr_blocks, 1, 1);
				glMemoryBarrier(GL_SHADER_STORAGE_BARRIER_BIT);
			}
			glEndQuery(GL_TIME_ELAPSED);
		}
		
		int total_tiles = 0;
		int radix_blocks = 0;
		{
			// - read back size of tile buffer
			total_tiles = -1;
			{
				int[] buf = new int[1];
				this.gaussianStartSSBO.getSubData(buf, this.nrGaussians * 4);
				total_tiles = buf[0];
			}
			radix_blocks = (total_tiles + RADIX_ELEMENTS_PER_BLOCK - 1) / RADIX_ELEMENTS_PER_BLOCK;
			
			// - resize tile buffers if needed
			if(total_tiles > this.maxTiles) {
				if(this.maxTiles == 0) this.maxTiles = 1;
				while(this.maxTiles < total_tiles) this.maxTiles *= 2;
				
				int max_super_blocks = 
						(this.maxTiles + RADIX_PREFIX_SCAN_ELEMENTS_PER_BLOCK * RADIX_ELEMENTS_PER_BLOCK) / 
						(RADIX_PREFIX_SCAN_ELEMENTS_PER_BLOCK * RADIX_ELEMENTS_PER_BLOCK);
				max_super_blocks += 10;
				int max_blocks = max_super_blocks * RADIX_PREFIX_SCAN_ELEMENTS_PER_BLOCK;
				
				this.tileSSBO1.setSize(this.maxTiles * SIZEOF_TILE);
				this.tileSSBO2.setSize(this.maxTiles * SIZEOF_TILE);
				this.blockHistogramSSBO.setSize(max_blocks * 4 * (1 << RADIX_K));
				this.blockHistogramPrefixSSBO.setSize(max_super_blocks * 4 * (1 << RADIX_K));
			}
		}

		// -- PHASE 3 -- 
		// - write tile-gaussian pairs into tile buffer
		{
			glBeginQuery(GL_TIME_ELAPSED, this.timingQueries[2]);
			this.pipeline3.enable();
			
			this.pipeline3.setUniform1i("screenWidth", this.getScreenWidth());
			this.pipeline3.setUniform1i("screenHeight", this.getScreenHeight());
			this.pipeline3.setUniform1i("tileSize", TILE_SIZE);
			
			this.pipeline3.setUniform1i("nrGaussians", this.nrGaussians);
			
			this.gaussianStartSSBO.bindToBase(1);
			this.tileSSBO1.bindToBase(2);
			this.gaussianInfoSSBO.bindToBase(7);
			
			glDispatchCompute((this.nrGaussians + 32 - 1) / 32, 1, 1);
			glMemoryBarrier(GL_SHADER_STORAGE_BARRIER_BIT);
			glEndQuery(GL_TIME_ELAPSED);
		}
		
		// -- PHASE 4 -- 
		// - sort tile buffer using radix sort
		// - every iteration, the tiles in layout 2 get permuted into the buffer in layout 3
		{			
			this.pipeline4.enable();
			
			this.pipeline4.setUniform1i("nrGaussians", this.nrGaussians);
			this.pipeline4.setUniform1i("nrTiles", total_tiles);
			
			this.blockHistogramSSBO.bindToBase(4);
			this.blockHistogramPrefixSSBO.bindToBase(5);
			
			int prefix_sweep_workgroups = radix_blocks / RADIX_PREFIX_SCAN_ELEMENTS_PER_BLOCK + 1;
			
			for(int iteration = 0; iteration < RADIX_NR_ITERATIONS; iteration++) {
				if((iteration % 2) == 0) {
					this.tileSSBO1.bindToBase(2);
					this.tileSSBO2.bindToBase(3);
				}
				else {
					this.tileSSBO1.bindToBase(3);
					this.tileSSBO2.bindToBase(2);
				}
				
				this.pipeline4.setUniform1i("sortIteration", iteration);
				this.pipeline4.setUniform1i("sortPhase", 0);
				glBeginQuery(GL_TIME_ELAPSED, this.radixTimingQueries[iteration][0]);
				glDispatchCompute(radix_blocks, 1, 1);
				glMemoryBarrier(GL_SHADER_STORAGE_BARRIER_BIT);
				glEndQuery(GL_TIME_ELAPSED);
				
				this.pipeline4.setUniform1i("sortPhase", 1);
				glBeginQuery(GL_TIME_ELAPSED, this.radixTimingQueries[iteration][1]);
				for(int sweep_phase = 0; sweep_phase < 3; sweep_phase++) {
					this.pipeline4.setUniform1i("sweepPhase", sweep_phase);	
					glDispatchCompute(sweep_phase == 1? 1 : prefix_sweep_workgroups, 1, 1);
					glMemoryBarrier(GL_SHADER_STORAGE_BARRIER_BIT);
				}
				glEndQuery(GL_TIME_ELAPSED);

				this.pipeline4.setUniform1i("sortPhase", 2);
				glBeginQuery(GL_TIME_ELAPSED, this.radixTimingQueries[iteration][2]);
				glDispatchCompute(radix_blocks, 1, 1);
				glMemoryBarrier(GL_SHADER_STORAGE_BARRIER_BIT);
				glEndQuery(GL_TIME_ELAPSED);
			}
		}
		
		// -- PHASE 5 --
		// - compute per-tile start offsets within tile buffer
		{
			glBeginQuery(GL_TIME_ELAPSED, this.timingQueries[4]);
			this.pipeline5.enable();
			
			this.pipeline5.setUniform1i("nrGaussians", this.nrGaussians);
			this.pipeline5.setUniform1i("nrScreenTiles", this.nrScreenTiles);
			
			this.gaussianStartSSBO.bindToBase(1);
			this.tileSSBO1.bindToBase(2);
			this.tileStartSSBO.bindToBase(5);
			
			glDispatchCompute((this.nrScreenTiles + 32 - 1) / 32, 1, 1);
			glMemoryBarrier(GL_SHADER_STORAGE_BARRIER_BIT);
			glEndQuery(GL_TIME_ELAPSED);
		}
		
		// -- PHASE 6 --
		// - raster gaussians in each tile
		{
			glBeginQuery(GL_TIME_ELAPSED, this.timingQueries[5]);
			this.pipeline6.enable();
			
			this.pipeline6.setUniform1i("screenWidth", this.getScreenWidth());
			this.pipeline6.setUniform1i("screenHeight", this.getScreenHeight());
			this.pipeline6.setUniform1i("tileSize", TILE_SIZE);
			
			this.gaussianStartSSBO.bindToBase(1);
			this.tileSSBO1.bindToBase(2);
			this.tileStartSSBO.bindToBase(5);
			this.renderSSBO.bindToBase(6);
			this.gaussianInfoSSBO.bindToBase(7);
			
			glDispatchCompute(this.nrScreenTiles, 1, 1);
			glMemoryBarrier(GL_SHADER_STORAGE_BARRIER_BIT);
			glEndQuery(GL_TIME_ELAPSED);
		}
		
		// -- PHASE 7 -- 
		// - render renderSSBO back to output buffer
		{
			glBeginQuery(GL_TIME_ELAPSED, this.timingQueries[6]);
			outputBuffer.bind();
			this.pipeline7.enable();
			
			this.pipeline7.setUniform1i("screenWidth", this.getScreenWidth());
			this.pipeline7.setUniform1i("screenHeight", this.getScreenHeight());
			
			this.renderSSBO.bindToBase(6);
			
			glDisable(GL_DEPTH_TEST);
			glEnable(GL_BLEND);
			screenQuad.render();
			glEndQuery(GL_TIME_ELAPSED);
		}
		
		// print timing information
		if(this.timingEnabled) {
			this.timingFrameCounter ++;
			for(int i = 0; i < RADIX_NR_ITERATIONS; i++) {
				for(int j = 0; j < RADIX_NR_PHASES; j++) {
					long phase_ns = glGetQueryObjectui64(this.radixTimingQueries[i][j], GL_QUERY_RESULT);
					this.radixTimingMS[i][j] += phase_ns / 1_000_000.0;
				}
			}
			for(int i = 0; i < NR_PHASES; i++) {
				if(i != 3) {	//radix sort handled separately
					long phase_ns = glGetQueryObjectui64(this.timingQueries[i], GL_QUERY_RESULT);
					this.timingPhaseMS[i] += phase_ns / 1_000_000.0;
				}
			}

			if(this.timingFrameCounter >= TIMING_PRINT_INTERVAL) {
				System.out.println("Tile Buffer Size : " + total_tiles);
				System.out.println("Radix Sort Blocks : " + radix_blocks);
				
				double total_ms = 0.0;
				for(int i = 0; i < RADIX_NR_ITERATIONS; i++) {
					for(int j = 0; j < RADIX_NR_PHASES; j++) {
						this.timingPhaseMS[3] += this.radixTimingMS[i][j];
					}
				}
				for(int i = 0; i < NR_PHASES; i++) {
					this.timingPhaseMS[i] /= TIMING_PRINT_INTERVAL;
					total_ms += this.timingPhaseMS[i];
				}
				System.out.printf("Average total GPU time: %.4f ms%n", total_ms);
				for(int i = 0; i < NR_PHASES; i++) {
					double percent = total_ms == 0.0 ? 0.0 : (this.timingPhaseMS[i] / total_ms) * 100.0;
					System.out.printf(
						"Phase %d average GPU time: %.4f ms (%.2f%%)%n",
						i + 1,
						this.timingPhaseMS[i],
						percent
					);
				}
				
				double radix_total_ms = 0;
				for(int i = 0; i < RADIX_NR_ITERATIONS; i++) {
					for(int j = 0; j < RADIX_NR_PHASES; j++) {
						this.radixTimingMS[i][j] /= TIMING_PRINT_INTERVAL;
						radix_total_ms += this.radixTimingMS[i][j];
					}
				}
				System.out.printf("Average radix sort GPU time: %.4f ms%n", radix_total_ms);
				for(int i = 0; i < RADIX_NR_PHASES; i++) {
					double phase_ms = 0;
					for(int j = 0; j < RADIX_NR_ITERATIONS; j++) {
						phase_ms += this.radixTimingMS[j][i];
					}
					double percent = radix_total_ms == 0.0 ? 0.0 : (phase_ms / radix_total_ms) * 100.0;
					System.out.printf(
						"Radix phase %d average GPU time: %.4f ms (%.2f%%)%n",
						i + 1,
						phase_ms,
						percent
					);
				}

				this.timingFrameCounter = 0;
				for(int i = 0; i < NR_PHASES; i++) {
					this.timingPhaseMS[i] = 0;
				}
				for(int i = 0; i < RADIX_NR_ITERATIONS; i++) {
					for(int j = 0; j < RADIX_NR_PHASES; j++) {
						this.radixTimingMS[i][j] = 0;
					}
				}
			}
		}
		
		// basic rendering
		if(false) {
			outputBuffer.bind();
			this.splattingShader.enable();
			this.splattingShader.setUniformMat4("pr_matrix", this.camera.getProjectionMatrix());
			this.splattingShader.setUniformMat4("vw_matrix", this.camera.getViewMatrix());
			this.splattingShader.setUniform3f("cameraPos", this.camera.getPos());
			
			this.splattingShader.setUniform1i("screenWidth", this.getScreenWidth());
			this.splattingShader.setUniform1i("screenHeight", this.getScreenHeight());
			this.splattingShader.setUniform1f("verticalFOV", this.camera.getVerticalFOV());
			
			this.splattingShader.setUniform1i("nrGaussians", this.nrGaussians);
			this.gaussianSSBO.bindToBase(0);
			
			glDisable(GL_DEPTH_TEST);
			glDisable(GL_CULL_FACE);
			glDisable(GL_BLEND);
			SkyboxCube.skyboxCube.render();
		}
	}

	@Override
	protected void _kill() {
		this.splattingShader.kill();
		
		this.pipeline1.kill();
		this.pipeline2.kill();
		this.pipeline3.kill();
		this.pipeline4.kill();
		this.pipeline5.kill();
		this.pipeline6.kill();
		this.pipeline7.kill();
		
		this.gaussianSSBO.kill();
		this.gaussianStartSSBO.kill();
		this.tileSSBO1.kill();
		this.tileSSBO2.kill();
		this.blockHistogramSSBO.kill();
		this.tileStartSSBO.kill();
		this.renderSSBO.kill();
		this.prefixBlockSSBO.kill();
		this.gaussianInfoSSBO.kill();
		
		for(int i = 0; i < NR_PHASES; i++) {
			glDeleteQueries(this.timingQueries[i]);
		}
		for(int i = 0; i < RADIX_NR_PHASES; i++) {
			glDeleteQueries(this.radixTimingQueries[i]);
		}
	}

}
