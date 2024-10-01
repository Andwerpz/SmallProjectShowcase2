package csce_vis.hw3;

import static org.lwjgl.opengl.GL11.GL_DEPTH_TEST;
import static org.lwjgl.opengl.GL11.GL_POINTS;
import static org.lwjgl.opengl.GL11.glDisable;
import static org.lwjgl.opengl.GL11.glDrawArrays;
import static org.lwjgl.opengl.GL11.glPointSize;
import static org.lwjgl.opengl.GL11.glViewport;
import static org.lwjgl.opengl.GL15.GL_DYNAMIC_DRAW;
import static org.lwjgl.opengl.GL15.GL_QUERY_RESULT;
import static org.lwjgl.opengl.GL15.glBeginQuery;
import static org.lwjgl.opengl.GL15.glEndQuery;
import static org.lwjgl.opengl.GL15.glGenQueries;
import static org.lwjgl.opengl.GL15.glGetQueryObjectuiv;
import static org.lwjgl.opengl.GL33.GL_TIME_ELAPSED;
import static org.lwjgl.opengl.GL42.glMemoryBarrier;
import static org.lwjgl.opengl.GL43.GL_COMPUTE_SHADER;
import static org.lwjgl.opengl.GL43.GL_SHADER_STORAGE_BARRIER_BIT;
import static org.lwjgl.opengl.GL43.glDispatchCompute;

import java.util.Stack;

import lwjglengine.graphics.Framebuffer;
import lwjglengine.graphics.Shader;
import lwjglengine.graphics.ShaderStorageBuffer;
import lwjglengine.main.Main;
import lwjglengine.player.Camera;
import lwjglengine.util.ShaderUtils;
import lwjglengine.window.Window;
import myutils.math.Mat4;
import myutils.math.MathUtils;
import myutils.math.Vec2;
import myutils.math.Vec3;

public class HW3Aquarium extends Window {

	//TODO
	// - implement sph in 2D
	// - add foam / bubbles
	// - add fish
	
	private boolean printUpdateTimes = false;
	private boolean printRenderTimes = true;
	private boolean doUpdate = true;

	private int timeAvgAmt = 100;
	private Stack<Float> updateTimes = new Stack<>();
	private Stack<Float> renderTimes = new Stack<>();
	
	private static final int NR_PARTICLES_LOG2 = 14; //must be \geq 10 due to bitonic sort
	private static final int NR_PARTICLES = (1 << NR_PARTICLES_LOG2);
	
	//used to sample properties from the point cloud
	private static float smoothingRadius = 1f;
	//(S - r)^3
	private static float densitySmoothingKernelVolume = (float) (Math.PI * Math.pow(smoothingRadius, 5) / 10.0);
	//(S - r)^6
	private static float nearDensitySmoothingKernelVolume = (float) (Math.PI * Math.pow(smoothingRadius, 8) / 28.0);
	//(S^2 - r^2)^3
	private static float viscositySmoothingKernelVolume = (float) (Math.PI * Math.pow(smoothingRadius, 8) / 4.0);
	
	private static int spatialWorkgroupSz = 32;
	
	private Shader waterCompute1;
	private Shader waterCompute21, waterCompute22;
	private Shader waterCompute3;
	private Shader waterCompute4;
	
	private static final int HASH_LUT_SIZE = (1 << 20);
	private static final int HASH_MOD = (int) (1e6 + 7);
	private static final int LUT_P1 = 8443;
	private static final int LUT_P2 = 107;
	private static final int LUT_P3 = 251527;
	private static final int LUT_P4 = 6037;
	private static final int LUT_P5 = 1721;
	
	private ShaderStorageBuffer hashLUTBuffer;
	
	private static final int SIZEOF_PARTICLE = 24;
	private static final int SIZEOF_PARTICLE_INFO = 24;
	
	/*
	// 24 bytes?
	struct Particle {
		vec2 pos;
		vec2 vel;
		int hash;
	};
	
	// 24 bytes
	struct ParticleInfo {
	    vec2 pred_pos;
	    vec2 visc_force;
	    float density;
	    float near_density;
	};
	*/
	class Particle {
		Vec2 pos, vel;
		int hash;

		public Particle(Vec2 _pos) {
			this.pos = new Vec2(_pos);
			this.vel = new Vec2(0);
			this.hash = -1;
		}

		public Particle(int[] buffer, int offset) {
			this.pos = new Vec2(Float.intBitsToFloat(buffer[offset + 0]), Float.intBitsToFloat(buffer[offset + 1]));
			this.vel = new Vec2(Float.intBitsToFloat(buffer[offset + 2]), Float.intBitsToFloat(buffer[offset + 3]));
			this.hash = buffer[offset + 4];
		}

		public void writeToBuffer(int[] buffer, int offset) {
			buffer[offset + 0] = Float.floatToIntBits(pos.x);
			buffer[offset + 1] = Float.floatToIntBits(pos.y);

			buffer[offset + 2] = Float.floatToIntBits(vel.x);
			buffer[offset + 3] = Float.floatToIntBits(vel.y);

			buffer[offset + 4] = hash;
		}
	}
	
	private ShaderStorageBuffer particleBuffer, particleInfoBuffer;
	
	private Vec2 gravity = new Vec2(0, -9.8);
	
	private float viscosityStrength = 1f;
	public float pressureMultiplier = 256f;
	public float nearPressureMultiplier = 0.1f;
	public float targetDensity = 16f;
	
	private int renderScale = 20;	//how many pixels on screen is one unit in particle space
	private Vec2 bbPos, bbDimensions;	//update this in update loop
	
	private Shader particleShader;
	
	public HW3Aquarium(int xOffset, int yOffset, int width, int height, Window parentWindow) {
		super(xOffset, yOffset, width, height, parentWindow);
		this.init();
	}
	
	private void init() {
		this.waterCompute1 = ShaderUtils.createShader("/csce_vis/hw3/aquarium/water_1.compute", GL_COMPUTE_SHADER);
		this.waterCompute21 = ShaderUtils.createShader("/csce_vis/hw3/aquarium/water_2_1.compute", GL_COMPUTE_SHADER);
		this.waterCompute22 = ShaderUtils.createShader("/csce_vis/hw3/aquarium/water_2_2.compute", GL_COMPUTE_SHADER);
		this.waterCompute3 = ShaderUtils.createShader("/csce_vis/hw3/aquarium/water_3.compute", GL_COMPUTE_SHADER);
		this.waterCompute4 = ShaderUtils.createShader("/csce_vis/hw3/aquarium/water_4.compute", GL_COMPUTE_SHADER);
		
		this.particleShader = ShaderUtils.createShader("/csce_vis/hw3/aquarium/particle.vert", "/csce_vis/hw3/aquarium/particle.frag");
	
		this.particleBuffer = new ShaderStorageBuffer(NR_PARTICLES * SIZEOF_PARTICLE);
		this.particleBuffer.setUsage(GL_DYNAMIC_DRAW);

		this.particleInfoBuffer = new ShaderStorageBuffer(NR_PARTICLES * SIZEOF_PARTICLE_INFO);
		this.particleInfoBuffer.setUsage(GL_DYNAMIC_DRAW);

		this.hashLUTBuffer = new ShaderStorageBuffer(HASH_LUT_SIZE * 4);
		this.hashLUTBuffer.setUsage(GL_DYNAMIC_DRAW);

		//initialize every element of LUT to 0
		{
			int[] data = new int[HASH_LUT_SIZE];
			this.hashLUTBuffer.setSubData(data, 0);
		}
		
		this.setBB();
		this.resetParticles();
		
		this._resize();
	}

	@Override
	protected void _kill() {
		this.particleBuffer.kill();
		this.particleInfoBuffer.kill();
		this.hashLUTBuffer.kill();
	}

	@Override
	protected void _resize() {
		
	}

	@Override
	public String getDefaultTitle() {
		return "Homework 3";
	}
	
	private void setBB() {
		this.bbPos = this.getGlobalOffset().mul(1.0 / this.renderScale);
		this.bbDimensions = new Vec2(this.getWidth(), this.getHeight()).mul(1.0 / this.renderScale);
	}
	
	private Mat4 getPrMatrix() {
		float left = this.bbPos.x;
		float right = left + this.bbDimensions.x;
		float bottom = this.bbPos.y;
		float top = bottom + this.bbDimensions.y;
		float near = -1000;
		float far = 1000;
		return Mat4.orthographic(left, right, bottom, top, near, far);
	}
	
	private void resetParticles() {
		int[] data = new int[NR_PARTICLES * SIZEOF_PARTICLE / 4];
		for(int i = 0; i < NR_PARTICLES; i++) {
			Vec2 pos = MathUtils.random(new Vec2(0), this.bbDimensions);
			pos.addi(this.bbPos);
			Particle p = new Particle(pos);
			p.writeToBuffer(data, i * SIZEOF_PARTICLE / 4);
		}
		this.particleBuffer.setSubData(data, 0);
	}
	
	private void waterUpdate() {
		float dt = Main.getDeltaSeconds();
		dt = Math.min(16.0f / 1000.0f, dt);

		// -- PHASE 1 --
		//update position due to velocity, compute hashes
		{
			this.waterCompute1.enable();
			this.waterCompute1.setUniform1f("dt", dt);

			this.waterCompute1.setUniform1f("smoothing_radius", smoothingRadius);
			this.waterCompute1.setUniform1i("hash_mod", HASH_MOD);
			this.waterCompute1.setUniform1i("LUT_P1", LUT_P1);
			this.waterCompute1.setUniform1i("LUT_P2", LUT_P2);
			this.waterCompute1.setUniform1i("LUT_P3", LUT_P3);
			this.waterCompute1.setUniform1i("LUT_P4", LUT_P4);
			this.waterCompute1.setUniform1i("LUT_P5", LUT_P5);

			this.particleBuffer.bindToBase(0);

			glDispatchCompute(NR_PARTICLES / 32, 1, 1);
			glMemoryBarrier(GL_SHADER_STORAGE_BARRIER_BIT);
		}
		
		
		// -- PHASE 2.1 -- 
		//sort particles according to their hashes using bitonic merge sort
		//currently, have a mix of local and global sorting. Local sorting under 1024 elements, and global sorting past that. 
		//shader invocations are bad, but global memory accesses are also bad. Can't increase local sorting due to 
		//constraints on workgroup shared memory. 
		//TODO see if pure local sorting with global memory accesses is better than the current method.
		// - could also sort hash along with pointer to original particle, then go back and rearrange all the particles. 
		//   this could allow for more local sorting / less shader invocations, but more rearranging stuff in memory
		// - however, the rearranging during sorting is greatly reduced, and that's where most of the operations are taking place. 
		// - 8 bytes per element means 4096 in local sorting stage. 4 bytes per element is not feasible, as that limits us to (2 << 16)
		//   particles (if we split the bytes equally between hash and index). 
		{
			this.waterCompute21.enable();

			this.particleBuffer.bindToBase(0);

			//local BMS
			this.waterCompute21.setUniform1i("mode", 0);
			glDispatchCompute(NR_PARTICLES / 1024, 1, 1);
			glMemoryBarrier(GL_SHADER_STORAGE_BARRIER_BIT);

			for (int i = 2048; i <= NR_PARTICLES; i *= 2) {
				//big flip
				this.waterCompute21.setUniform1i("mode", 2);
				this.waterCompute21.setUniform1i("global_op_sz", i);
				glDispatchCompute(NR_PARTICLES / 1024, 1, 1);
				glMemoryBarrier(GL_SHADER_STORAGE_BARRIER_BIT);

				for (int j = i / 2; j > 1; j /= 2) {
					//big disperse
					this.waterCompute21.setUniform1i("mode", 3);
					this.waterCompute21.setUniform1i("global_op_sz", j);
					glDispatchCompute(NR_PARTICLES / 1024, 1, 1);
					glMemoryBarrier(GL_SHADER_STORAGE_BARRIER_BIT);
				}

				//local disperse
				this.waterCompute21.setUniform1i("mode", 1);
				glDispatchCompute(NR_PARTICLES / 1024, 1, 1);
				glMemoryBarrier(GL_SHADER_STORAGE_BARRIER_BIT);
			}
		}
		
		// -- PHASE 2.2 -- 
		//generate hash lookup tables. For each hash, will save index at which particles belonging to that hash start
		//since hashes are sorted, can just see if current hash is unequal to previous hash.
		{
			this.waterCompute22.enable();

			this.particleBuffer.bindToBase(0);
			this.hashLUTBuffer.bindToBase(1);

			glDispatchCompute(NR_PARTICLES, 1, 1);
			glMemoryBarrier(GL_SHADER_STORAGE_BARRIER_BIT);
		}

		// -- PHASE 3 --
		//compute density and viscosity forces per particle
		{
			this.waterCompute3.enable();

			this.particleBuffer.bindToBase(0);
			this.hashLUTBuffer.bindToBase(1);
			this.particleInfoBuffer.bindToBase(2);

			this.waterCompute3.setUniform1f("dt", dt);
			this.waterCompute3.setUniform1i("nr_particles", NR_PARTICLES);

			this.waterCompute3.setUniform1f("smoothing_radius", smoothingRadius);
			this.waterCompute3.setUniform1i("hash_mod", HASH_MOD);
			this.waterCompute3.setUniform1i("LUT_P1", LUT_P1);
			this.waterCompute3.setUniform1i("LUT_P2", LUT_P2);
			this.waterCompute3.setUniform1i("LUT_P3", LUT_P3);
			this.waterCompute3.setUniform1i("LUT_P4", LUT_P4);
			this.waterCompute3.setUniform1i("LUT_P5", LUT_P5);

			this.waterCompute3.setUniform1f("density_smoothing_kernel_volume", densitySmoothingKernelVolume);
			this.waterCompute3.setUniform1f("near_density_smoothing_kernel_volume", nearDensitySmoothingKernelVolume);
			this.waterCompute3.setUniform1f("viscosity_smoothing_kernel_volume", viscositySmoothingKernelVolume);

			this.waterCompute3.setUniform1f("viscosity_strength", this.viscosityStrength);

			glDispatchCompute(NR_PARTICLES / spatialWorkgroupSz, 1, 1);
			glMemoryBarrier(GL_SHADER_STORAGE_BARRIER_BIT);
		}
		
		// -- PHASE 4 --
		//compute pressure forces. apply all forces to particles
		{
			this.waterCompute4.enable();

			this.particleBuffer.bindToBase(0);
			this.hashLUTBuffer.bindToBase(1);
			this.particleInfoBuffer.bindToBase(2);

			this.waterCompute4.setUniform1f("dt", dt);
			this.waterCompute4.setUniform2f("gravity", this.gravity);
			this.waterCompute4.setUniform1i("nr_particles", NR_PARTICLES);

			this.waterCompute4.setUniform2f("bounds_min", this.bbPos);
			this.waterCompute4.setUniform2f("bounds_max", this.bbPos.add(this.bbDimensions));

			this.waterCompute4.setUniform1f("smoothing_radius", smoothingRadius);
			this.waterCompute4.setUniform1i("hash_mod", HASH_MOD);
			this.waterCompute4.setUniform1i("LUT_P1", LUT_P1);
			this.waterCompute4.setUniform1i("LUT_P2", LUT_P2);
			this.waterCompute4.setUniform1i("LUT_P3", LUT_P3);
			this.waterCompute4.setUniform1i("LUT_P4", LUT_P4);
			this.waterCompute4.setUniform1i("LUT_P5", LUT_P5);

			this.waterCompute4.setUniform1f("density_smoothing_kernel_volume", densitySmoothingKernelVolume);
			this.waterCompute4.setUniform1f("near_density_smoothing_kernel_volume", nearDensitySmoothingKernelVolume);
			this.waterCompute4.setUniform1f("viscosity_smoothing_kernel_volume", viscositySmoothingKernelVolume);

			this.waterCompute4.setUniform1f("target_density", this.targetDensity);
			this.waterCompute4.setUniform1f("pressure_multiplier", this.pressureMultiplier);
			this.waterCompute4.setUniform1f("near_pressure_multiplier", this.nearPressureMultiplier);

			glDispatchCompute(NR_PARTICLES / spatialWorkgroupSz, 1, 1);
			glMemoryBarrier(GL_SHADER_STORAGE_BARRIER_BIT);
		}
	}

	@Override
	protected void _update() {
		this.setBB();
		
		int time_query = -1;
		if (this.printUpdateTimes) {
			time_query = glGenQueries();
			glBeginQuery(GL_TIME_ELAPSED, time_query);
		}

		this.waterUpdate();

		if (this.printUpdateTimes) {
			glEndQuery(GL_TIME_ELAPSED);
			int[] time_elapsed_res = new int[1];
			glGetQueryObjectuiv(time_query, GL_QUERY_RESULT, time_elapsed_res);
			this.updateTimes.push((float) (time_elapsed_res[0] / 1000000.0));

			if (this.updateTimes.size() == this.timeAvgAmt) {
				float avg = 0;
				while (this.updateTimes.size() != 0) {
					avg += this.updateTimes.pop();
				}
				avg /= this.timeAvgAmt;
				System.out.println("Average last " + this.timeAvgAmt + " update times : " + avg);
			}
		}
	}
	
	private void waterRenderPipelineV0(Framebuffer outputBuffer) {
		Mat4 pr_matrix = this.getPrMatrix();
		
		outputBuffer.bind();
		
		this.particleShader.enable();
		this.particleShader.setUniformMat4("pr_matrix", pr_matrix);

		this.particleBuffer.bindToBase(0);

		glDisable(GL_DEPTH_TEST);
		glPointSize(2f);
		glViewport(0, 0, this.getWidth(), this.getHeight());
		glDrawArrays(GL_POINTS, 0, NR_PARTICLES);
	}

	@Override
	protected void renderContent(Framebuffer outputBuffer) {
		int time_query = -1;
		if (this.printRenderTimes) {
			time_query = glGenQueries();
			glBeginQuery(GL_TIME_ELAPSED, time_query);
		}

		this.waterRenderPipelineV0(outputBuffer);

		if (this.printRenderTimes) {
			glEndQuery(GL_TIME_ELAPSED);
			int[] time_elapsed_res = new int[1];
			glGetQueryObjectuiv(time_query, GL_QUERY_RESULT, time_elapsed_res);
			this.renderTimes.push((float) (time_elapsed_res[0] / 1000000.0));

			if (this.renderTimes.size() == this.timeAvgAmt) {
				float avg = 0;
				while (this.renderTimes.size() != 0) {
					avg += this.renderTimes.pop();
				}
				avg /= this.timeAvgAmt;
				System.out.println("Average last " + this.timeAvgAmt + " render times : " + avg);
			}
		}
	}

	@Override
	protected void renderOverlay(Framebuffer outputBuffer) {
		// TODO Auto-generated method stub

	}

	@Override
	protected void selected() {
		// TODO Auto-generated method stub

	}

	@Override
	protected void deselected() {
		// TODO Auto-generated method stub

	}

	@Override
	protected void subtreeSelected() {
		// TODO Auto-generated method stub

	}

	@Override
	protected void subtreeDeselected() {
		// TODO Auto-generated method stub

	}

	@Override
	protected void _mousePressed(int button) {
		// TODO Auto-generated method stub

	}

	@Override
	protected void _mouseReleased(int button) {
		// TODO Auto-generated method stub

	}

	@Override
	protected void _mouseScrolled(float wheelOffset, float smoothOffset) {
		// TODO Auto-generated method stub

	}

	@Override
	protected void _keyPressed(int key) {
		// TODO Auto-generated method stub

	}

	@Override
	protected void _keyReleased(int key) {
		// TODO Auto-generated method stub

	}

}
