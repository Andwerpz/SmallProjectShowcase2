package csce_vis.hw3;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL12.*;
import static org.lwjgl.opengl.GL13.*;
import static org.lwjgl.opengl.GL14.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL21.*;
import static org.lwjgl.opengl.GL30.*;
import static org.lwjgl.opengl.GL31.*;
import static org.lwjgl.opengl.GL32.*;
import static org.lwjgl.opengl.GL33.*;
import static org.lwjgl.opengl.GL40.*;
import static org.lwjgl.opengl.GL41.*;
import static org.lwjgl.opengl.GL42.*;
import static org.lwjgl.opengl.GL43.*;
import static org.lwjgl.opengl.GL44.*;
import static org.lwjgl.opengl.GL45.*;
import static org.lwjgl.opengl.GL46.*;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Stack;

import org.lwjgl.glfw.GLFW;

import lwjglengine.graphics.Framebuffer;
import lwjglengine.graphics.Material;
import lwjglengine.graphics.Shader;
import lwjglengine.graphics.ShaderStorageBuffer;
import lwjglengine.graphics.Texture;
import lwjglengine.main.Main;
import lwjglengine.model.ModelInstance;
import lwjglengine.model.VertexArray;
import lwjglengine.player.Camera;
import lwjglengine.scene.Scene;
import lwjglengine.screen.ScreenQuad;
import lwjglengine.screen.UIScreen;
import lwjglengine.ui.UIFilledRectangle;
import lwjglengine.util.ShaderUtils;
import lwjglengine.window.ObjectEditorWindow;
import lwjglengine.window.Window;
import myutils.math.Mat4;
import myutils.math.MathUtils;
import myutils.math.Vec2;
import myutils.math.Vec3;

public class HW3Aquarium extends Window {

	//TODO
	// - better water rendering
	//   - foam / bubbles : https://cg.informatik.uni-freiburg.de/publications/2012_CGI_sprayFoamBubbles.pdf
	//   - don't really want to add diffuse particles, perhaps just fake it by giving each water particle a 'foam' attribute
	//     and let the foam advect around that way. 

	// perhaps just compute a buffer holding density, viscosity, etc information about every pixel. If we can compute this quickly, 
	//then it'll trivialize rendering, and it might even speed up updates. 
	// refer to ideas about speeding up density portion of rendering. 
	// downside is that it'll make the simulation more unstable if we decide to use it for updates. 

	//ideas to speed up density portion of rendering:
	// - can use compute shader to group batches of pixels and do local memory trick. 
	// - can make these workgroup relatively large, maybe 32x32 pixels. Therefore, can load many particles into local memory. 
	// - can also ensure that all pixels in a workgroup belong to same hash cell. Need to set renderScale accordingly. 

	//ideas to improve water attenuation:
	// - current attenutation looks bad because light usually scatters in water
	// - i shortened it, it looks fine now
	// - maybe apply a gaussian blur to it?

	private boolean printUpdateTimes = false;
	private boolean printRenderTimes = false;

	private int timeAvgAmt = 100;
	private Stack<Float> updateTimes = new Stack<>();
	private Stack<Float> renderTimes = new Stack<>();

	private static final int NR_PARTICLES_LOG2 = 10; //must be \geq 10 due to bitonic sort
	private static final int NR_PARTICLES = (1 << NR_PARTICLES_LOG2);

	//used to sample properties from the point cloud
	private static float smoothingRadius = 3f;
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

	private static final int SIZEOF_PARTICLE = 32;
	private static final int SIZEOF_PARTICLE_INFO = 16;

	/*
	// 32 bytes
	struct Particle {
		vec2 pos;
		vec2 pred_pos;
		vec2 vel;
		int hash;
	};
	
	// 16 bytes
	struct ParticleInfo {
	    vec2 visc_force;
	    float density;
	    float near_density;
	};
	*/
	class Particle {
		Vec2 pos, pred_pos, vel;
		int hash;

		public Particle(Vec2 _pos) {
			this.pos = new Vec2(_pos);
			this.pred_pos = new Vec2(0);
			this.vel = new Vec2(0);
			this.hash = -1;
		}

		public void writeToBuffer(int[] buffer, int offset) {
			buffer[offset + 0] = Float.floatToIntBits(pos.x);
			buffer[offset + 1] = Float.floatToIntBits(pos.y);

			buffer[offset + 2] = Float.floatToIntBits(pred_pos.x);
			buffer[offset + 3] = Float.floatToIntBits(pred_pos.y);

			buffer[offset + 4] = Float.floatToIntBits(vel.x);
			buffer[offset + 5] = Float.floatToIntBits(vel.y);

			buffer[offset + 6] = hash;
		}
	}

	private ShaderStorageBuffer particleBuffer, particleInfoBuffer;
	
	private static final int SIZEOF_OBSTACLE = 16;
	
	/*
	// 16 bytes
	struct Obstacle {
		vec2 offset;
		vec2 dimensions;
	};
	*/
	class Obstacle {
		Vec2 offset, dimensions;
		UIFilledRectangle rect;
		
		public Obstacle(Vec2 _offset, Vec2 _dimensions) {
			this.offset = new Vec2(_offset);
			this.dimensions = new Vec2(_dimensions);
			this.rect = new UIFilledRectangle(offset.x * renderScale, offset.y * renderScale, 0, dimensions.x * renderScale, dimensions.y * renderScale, OBSTACLE_RENDER_SCENE);
			this.rect.setMaterial(new Material(Color.WHITE));
		}
		
		public void setOffset(Vec2 _offset) {
			this.offset = new Vec2(_offset);
			this.rect.setFrameAlignmentOffset(this.offset.x * renderScale, this.offset.y * renderScale);
		}
		
		public void setDimensions(Vec2 _dimensions) {
			this.dimensions = new Vec2(_dimensions);
			this.rect.setDimensions(this.dimensions.x * renderScale, this.dimensions.y * renderScale);
		}
		
		public void writeToBuffer(int[] buffer, int offset) {
			buffer[offset + 0] = Float.floatToIntBits(this.offset.x);
			buffer[offset + 1] = Float.floatToIntBits(this.offset.y);
			
			buffer[offset + 2] = Float.floatToIntBits(this.dimensions.x);
			buffer[offset + 3] = Float.floatToIntBits(this.dimensions.y);
		}
		
		public void kill() {
			this.rect.kill();
		}
	}
	
	private ShaderStorageBuffer obstacleBuffer;
	private ArrayList<Obstacle> obstacles;
	
	private final int OBSTACLE_RENDER_SCENE = Scene.generateScene();
	private final int UI_SCENE = Scene.generateScene();
	private UIScreen uiScreen;

	private int renderScale = 5; //how many pixels on screen is one unit in particle space
	private Vec2 bbPos, bbDimensions; //update this in update loop

	private Shader particleShader, densityShader, waterColorShader;
	private Shader shadowShader, gaussianShader, backgroundShader;
	
	private Shader obstacleNormalShader;

	private float timeDebt = 0;

	private Framebuffer densityBuffer;
	private Texture densityMap;
	private Texture normalMap;
	
	private Framebuffer obstacleRenderBuffer;
	private Texture obstacleMap;
	
	private Framebuffer obstacleNormalBuffer;
	private Texture obstacleNormalMap;

	private Framebuffer shadowBuffer;
	private Texture shadowMap; //higher value is more shadows

	private Framebuffer gaussianBlurBuffer;
	private Texture gaussianBlurMap;

	public SimulationSettings settings = new SimulationSettings();

	public class SimulationSettings {
		public Vec2 gravity = new Vec2(0, -20);
		public float viscosityStrength = 1f;
		public float pressureMultiplier = 50f;
		public float nearPressureMultiplier = 0.1f;
		public float targetDensity = 2f;
		public boolean renderParticles = false;

		private float predictDeltaTime = 16.0f / 1000.0f;

		public Vec2 getGravity() {
			return gravity;
		}

		public void setGravity(Vec2 gravity) {
			this.gravity = gravity;
		}

		public float getViscosityStrength() {
			return viscosityStrength;
		}

		public void setViscosityStrength(float viscosityStrength) {
			this.viscosityStrength = viscosityStrength;
		}

		public float getPressureMultiplier() {
			return pressureMultiplier;
		}

		public void setPressureMultiplier(float pressureMultiplier) {
			this.pressureMultiplier = pressureMultiplier;
		}

		public float getNearPressureMultiplier() {
			return nearPressureMultiplier;
		}

		public void setNearPressureMultiplier(float nearPressureMultiplier) {
			this.nearPressureMultiplier = nearPressureMultiplier;
		}

		public float getTargetDensity() {
			return targetDensity;
		}

		public void setTargetDensity(float targetDensity) {
			this.targetDensity = targetDensity;
		}

		public boolean getRenderParticles() {
			return this.renderParticles;
		}

		public void setRenderParticles(boolean b) {
			this.renderParticles = b;
		}
	}

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
		this.densityShader = ShaderUtils.createShader("/csce_vis/hw3/aquarium/density.vert", "/csce_vis/hw3/aquarium/density.frag");
		this.waterColorShader = ShaderUtils.createShader("/csce_vis/hw3/aquarium/water_color.vert", "/csce_vis/hw3/aquarium/water_color.frag");
		this.shadowShader = ShaderUtils.createShader("/csce_vis/hw3/aquarium/shadow.vert", "/csce_vis/hw3/aquarium/shadow.frag");
		this.gaussianShader = ShaderUtils.createShader("/csce_vis/hw3/aquarium/gaussian.vert", "/csce_vis/hw3/aquarium/gaussian.frag");
		this.backgroundShader = ShaderUtils.createShader("/csce_vis/hw3/aquarium/background.vert", "/csce_vis/hw3/aquarium/background.frag");
		this.obstacleNormalShader = ShaderUtils.createShader("/csce_vis/hw3/aquarium/obstacle_normal.vert", "/csce_vis/hw3/aquarium/obstacle_normal.frag");
		
		this.gaussianShader.setUniform1i("color_map", 0);
		
		this.obstacleNormalShader.setUniform1i("obstacle_map", 0);

		this.shadowShader.setUniform1i("density_map", 0);
		this.shadowShader.setUniform1i("obstacle_map", 1);

		this.waterColorShader.setUniform1i("density_map", 0);
		this.waterColorShader.setUniform1i("normal_map", 1);
		this.waterColorShader.setUniform1i("obstacle_map", 2);
		this.waterColorShader.setUniform1i("obstacle_normal_map", 3);

		this.backgroundShader.setUniform1i("shadow_map", 0);

		this.setBB();

		// - particle buffers
		this.particleBuffer = new ShaderStorageBuffer(NR_PARTICLES * SIZEOF_PARTICLE);
		this.particleBuffer.setUsage(GL_DYNAMIC_DRAW);

		this.particleInfoBuffer = new ShaderStorageBuffer(NR_PARTICLES * SIZEOF_PARTICLE_INFO);
		this.particleInfoBuffer.setUsage(GL_DYNAMIC_DRAW);

		this.hashLUTBuffer = new ShaderStorageBuffer(HASH_LUT_SIZE * 4);
		this.hashLUTBuffer.setUsage(GL_DYNAMIC_DRAW);

		ObjectEditorWindow settings_window = new ObjectEditorWindow(this.settings);
		this.addChildAdjWindow(settings_window);

		//initialize every element of LUT to 0
		{
			int[] data = new int[HASH_LUT_SIZE];
			this.hashLUTBuffer.setSubData(data, 0);
		}

		this.resetParticles();
		
		// - obstacle buffers
		this.obstacleBuffer = new ShaderStorageBuffer(0);
		this.obstacleBuffer.setUsage(GL_STATIC_DRAW);
		
		this.obstacles = new ArrayList<>();
		
		this.uiScreen = new UIScreen();
		
		this.addObstacle(new Obstacle(new Vec2(10, 10), new Vec2(50, 50)));

		this._resize();
	}

	@Override
	protected void _kill() {
		this.particleBuffer.kill();
		this.particleInfoBuffer.kill();
		this.hashLUTBuffer.kill();
		
		this.obstacleBuffer.kill();

		this.waterCompute1.kill();
		this.waterCompute21.kill();
		this.waterCompute22.kill();
		this.waterCompute3.kill();
		this.waterCompute4.kill();

		this.particleShader.kill();
		this.densityShader.kill();
		this.waterColorShader.kill();
		this.shadowShader.kill();
		this.gaussianShader.kill();
		this.backgroundShader.kill();
		this.obstacleNormalShader.kill();

		this.densityBuffer.kill();
		this.obstacleRenderBuffer.kill();
		this.shadowBuffer.kill();
		this.gaussianBlurBuffer.kill();
		
		for(Obstacle o : this.obstacles) {
			o.kill();
		}
		
		this.uiScreen.kill();
		Scene.removeScene(OBSTACLE_RENDER_SCENE);
		Scene.removeScene(UI_SCENE);
	}

	@Override
	protected void _resize() {
		if (this.densityBuffer != null) {
			this.densityBuffer.kill();
			this.densityBuffer = null;
		}
		
		if(this.obstacleRenderBuffer != null) {
			this.obstacleRenderBuffer.kill();
			this.obstacleRenderBuffer = null;
		}

		if (this.shadowBuffer != null) {
			this.shadowBuffer.kill();
			this.shadowBuffer = null;
		}

		if (this.gaussianBlurBuffer != null) {
			this.gaussianBlurBuffer.kill();
			this.gaussianBlurBuffer = null;
		}

		if (this.getWidth() > 0 && this.getHeight() > 0) {
			this.densityBuffer = new Framebuffer(this.getWidth(), this.getHeight());
			this.densityMap = new Texture(this.getWidth(), this.getHeight(), GL_RGBA32F, GL_RGBA, GL_FLOAT);
			this.normalMap = new Texture(this.getWidth(), this.getHeight(), GL_RGBA32F, GL_RGBA, GL_FLOAT);
			this.densityBuffer.bindTextureToBuffer(GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, this.densityMap.getID());
			this.densityBuffer.bindTextureToBuffer(GL_COLOR_ATTACHMENT1, GL_TEXTURE_2D, this.normalMap.getID());
			this.densityBuffer.setDrawBuffers(new int[] { GL_COLOR_ATTACHMENT0, GL_COLOR_ATTACHMENT1 });
			this.densityBuffer.isComplete();
			
			this.obstacleRenderBuffer = new Framebuffer(this.getWidth(), this.getHeight());
			this.obstacleMap = new Texture(this.getWidth(), this.getHeight(), GL_RGBA32F, GL_RGBA, GL_FLOAT);
			this.obstacleRenderBuffer.bindTextureToBuffer(GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, this.obstacleMap.getID());
			this.obstacleRenderBuffer.setDrawBuffers(new int[] {GL_COLOR_ATTACHMENT0});
			this.obstacleRenderBuffer.isComplete();
			
			this.obstacleNormalBuffer = new Framebuffer(this.getWidth(), this.getHeight());
			this.obstacleNormalMap = new Texture(this.getWidth(), this.getHeight(), GL_RGBA32F, GL_RGBA, GL_FLOAT);
			this.obstacleNormalBuffer.bindTextureToBuffer(GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, this.obstacleNormalMap.getID());
			this.obstacleNormalBuffer.setDrawBuffers(new int[] {GL_COLOR_ATTACHMENT0});
			this.obstacleNormalBuffer.isComplete();

			this.shadowBuffer = new Framebuffer(this.getWidth(), this.getHeight());
			this.shadowMap = new Texture(this.getWidth(), this.getHeight(), GL_RGBA32F, GL_RGBA, GL_FLOAT);
			this.shadowBuffer.bindTextureToBuffer(GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, this.shadowMap.getID());
			this.shadowBuffer.setDrawBuffers(new int[] { GL_COLOR_ATTACHMENT0 });
			this.shadowBuffer.isComplete();

			this.gaussianBlurBuffer = new Framebuffer(this.getWidth(), this.getHeight());
			this.gaussianBlurMap = new Texture(this.getWidth(), this.getHeight(), GL_RGBA32F, GL_RGBA, GL_FLOAT);
			this.gaussianBlurBuffer.bindTextureToBuffer(GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, this.gaussianBlurMap.getID());
			this.gaussianBlurBuffer.setDrawBuffers(new int[] { GL_COLOR_ATTACHMENT0 });
			this.gaussianBlurBuffer.isComplete();
		}
		
		this.uiScreen.setScreenDimensions(this.getWidth(), this.getHeight());
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
		Vec2 bb = new Vec2(this.bbDimensions);
		bb.y *= 0.5;
		for (int i = 0; i < NR_PARTICLES; i++) {
			Vec2 pos = MathUtils.random(new Vec2(0), bb);
			pos.addi(this.bbPos);
			Particle p = new Particle(pos);
			p.writeToBuffer(data, i * SIZEOF_PARTICLE / 4);
		}
		this.particleBuffer.setSubData(data, 0);
	}
	
	private void addObstacle(Obstacle o) {
		this.obstacles.add(o);
		this.updateObstacleBuffers();
	}
	
	private void updateObstacleBuffers() {
		int nr_obstacles = this.obstacles.size();
		int[] data = new int[nr_obstacles * SIZEOF_OBSTACLE / 4];
		for(int i = 0; i < nr_obstacles; i++) {
			this.obstacles.get(i).writeToBuffer(data, i * SIZEOF_OBSTACLE / 4);
		}
		this.obstacleBuffer.setData(data);
	}

	private void waterUpdate(float dt) {
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

			this.waterCompute1.setUniform1f("predict_delta_time", settings.predictDeltaTime);

			this.particleBuffer.bindToBase(0);

			glDispatchCompute(NR_PARTICLES / 32, 1, 1);
			glMemoryBarrier(GL_SHADER_STORAGE_BARRIER_BIT);
		}

		// -- PHASE 2.1 -- 
		//sort particles according to their hashes using bitonic merge sort
		//currently, have a mix of local and global sorting. Local sorting under 1024 elements, and global sorting past that. 
		//shader invocations are bad, but global memory accesses are very bad. Can't increase local sorting due to 
		//constraints on workgroup shared memory. 
		//TODO see if sorting an array consisting of hash and index is much better than sorting the actual array. 
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

			glDispatchCompute(NR_PARTICLES / 32, 1, 1);
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

			this.waterCompute3.setUniform1f("viscosity_strength", settings.viscosityStrength);

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
			this.obstacleBuffer.bindToBase(3);

			this.waterCompute4.setUniform1f("dt", dt);
			this.waterCompute4.setUniform2f("gravity", settings.gravity);
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

			this.waterCompute4.setUniform1f("target_density", settings.targetDensity);
			this.waterCompute4.setUniform1f("pressure_multiplier", settings.pressureMultiplier);
			this.waterCompute4.setUniform1f("near_pressure_multiplier", settings.nearPressureMultiplier);
			
			this.waterCompute4.setUniform1i("nr_obstacles", this.obstacles.size());

			glDispatchCompute(NR_PARTICLES / spatialWorkgroupSz, 1, 1);
			glMemoryBarrier(GL_SHADER_STORAGE_BARRIER_BIT);
		}
	}

	@Override
	protected void _update() {
		this.setBB();

		float dt = 8.0f / 1000.0f;
		this.timeDebt += Main.getDeltaSeconds() * 2;

		this.timeDebt = Math.min(this.timeDebt, 100.0f / 1000.0f);

		while (this.timeDebt > dt) {
			this.timeDebt -= dt;

			int time_query = -1;
			if (this.printUpdateTimes) {
				time_query = glGenQueries();
				glBeginQuery(GL_TIME_ELAPSED, time_query);
			}

			this.waterUpdate(dt);

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
	}

	private void gaussianBlur5x5(Texture t) {
		this.gaussianBlurBuffer.bind();
		glClear(GL_COLOR_BUFFER_BIT);

		this.gaussianShader.enable();
		this.gaussianShader.setUniform1f("window_width", this.getWidth());
		this.gaussianShader.setUniform1f("window_height", this.getHeight());

		t.bind(GL_TEXTURE0);

		glViewport(0, 0, this.getWidth(), this.getHeight());
		ScreenQuad.screenQuad.render();
	}

	private void waterRenderPipelineV1(Framebuffer outputBuffer) {
		//clear buffer
		{
			this.densityBuffer.bind();
			glClear(GL_COLOR_BUFFER_BIT);

			this.obstacleRenderBuffer.bind();
			glClear(GL_COLOR_BUFFER_BIT);
			
			this.shadowBuffer.bind();
			glClear(GL_COLOR_BUFFER_BIT);
		}
		
		//render obstacles
		{
			//render base map
			this.uiScreen.setUIScene(OBSTACLE_RENDER_SCENE);
			this.uiScreen.render(this.obstacleRenderBuffer);
			
			//render normals
			this.obstacleNormalBuffer.bind();
			
			this.obstacleNormalShader.enable();
			
			this.obstacleNormalShader.setUniform1f("window_width", this.getWidth());
			this.obstacleNormalShader.setUniform1f("window_height", this.getHeight());
			
			this.obstacleMap.bind(GL_TEXTURE0);
			
			glViewport(0, 0, this.getWidth(), this.getHeight());
			ScreenQuad.screenQuad.render();
		}
		
		
		//render water density
		{
			this.densityBuffer.bind();

			this.particleBuffer.bindToBase(0);
			this.hashLUTBuffer.bindToBase(1);

			this.densityShader.enable();
			this.densityShader.setUniform1i("nr_particles", NR_PARTICLES);
			this.densityShader.setUniform1f("smoothing_radius", smoothingRadius);
			this.densityShader.setUniform1i("hash_mod", HASH_MOD);
			this.densityShader.setUniform1i("LUT_P1", LUT_P1);
			this.densityShader.setUniform1i("LUT_P2", LUT_P2);
			this.densityShader.setUniform1i("LUT_P3", LUT_P3);
			this.densityShader.setUniform1i("LUT_P4", LUT_P4);
			this.densityShader.setUniform1i("LUT_P5", LUT_P5);

			this.densityShader.setUniform1f("window_width", this.getWidth());
			this.densityShader.setUniform1f("window_height", this.getHeight());
			this.densityShader.setUniform1f("render_scale", this.renderScale);
			this.densityShader.setUniform2f("window_bl_pos", this.bbPos);

			this.densityShader.setUniform1f("density_threshold", settings.targetDensity);

			this.densityShader.setUniform1f("density_smoothing_kernel_volume", densitySmoothingKernelVolume);
			this.densityShader.setUniform1f("near_density_smoothing_kernel_volume", nearDensitySmoothingKernelVolume);

			glViewport(0, 0, this.getWidth(), this.getHeight());
			ScreenQuad.screenQuad.render();
		}

		//render shadows
		{
			this.shadowBuffer.bind();

			this.shadowShader.enable();
			this.shadowShader.setUniform1f("window_width", this.getWidth());
			this.shadowShader.setUniform1f("window_height", this.getHeight());

			this.densityMap.bind(GL_TEXTURE0);
			this.obstacleMap.bind(GL_TEXTURE1);

			glViewport(0, 0, this.getWidth(), this.getHeight());
			ScreenQuad.screenQuad.render();

			//gaussian blur the shadows
			this.gaussianBlur5x5(this.shadowMap);
		}

		//render background
		{
			outputBuffer.bind();

			this.backgroundShader.enable();
			this.backgroundShader.setUniform1f("window_width", this.getWidth());
			this.backgroundShader.setUniform1f("window_height", this.getHeight());

			this.gaussianBlurMap.bind(GL_TEXTURE0);

			glViewport(0, 0, this.getWidth(), this.getHeight());
			ScreenQuad.screenQuad.render();
		}

		//render water color + attenuation due to water
		{
			outputBuffer.bind();

			this.waterColorShader.enable();
			this.waterColorShader.setUniform1f("window_width", this.getWidth());
			this.waterColorShader.setUniform1f("window_height", this.getHeight());

			this.densityMap.bind(GL_TEXTURE0);
			this.normalMap.bind(GL_TEXTURE1);
			this.obstacleMap.bind(GL_TEXTURE2);
			this.obstacleNormalMap.bind(GL_TEXTURE3);

			glViewport(0, 0, this.getWidth(), this.getHeight());
			ScreenQuad.screenQuad.render();
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

		if (settings.renderParticles) {
			this.waterRenderPipelineV0(outputBuffer);
		}
		else {
			this.waterRenderPipelineV1(outputBuffer);
		}

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
		switch (key) {
		case GLFW.GLFW_KEY_R:
			this.resetParticles();
			break;
		}
	}

	@Override
	protected void _keyReleased(int key) {
		// TODO Auto-generated method stub

	}
}
