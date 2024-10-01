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

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.Stack;

import org.lwjgl.glfw.GLFW;

import lwjglengine.graphics.Cubemap;
import lwjglengine.graphics.Framebuffer;
import lwjglengine.graphics.Shader;
import lwjglengine.graphics.ShaderStorageBuffer;
import lwjglengine.graphics.Texture;
import lwjglengine.main.Main;
import lwjglengine.model.Line;
import lwjglengine.model.Model;
import lwjglengine.model.ModelInstance;
import lwjglengine.model.ModelTransform;
import lwjglengine.model.VertexArray;
import lwjglengine.player.Camera;
import lwjglengine.player.PlayerInputController;
import lwjglengine.scene.DirLight;
import lwjglengine.scene.Light;
import lwjglengine.scene.Scene;
import lwjglengine.screen.PerspectiveScreen;
import lwjglengine.screen.ScreenQuad;
import lwjglengine.screen.SkyboxCube;
import lwjglengine.util.ShaderUtils;
import lwjglengine.window.TextureViewerWindow;
import lwjglengine.window.Window;
import myutils.file.FileUtils;
import myutils.math.Mat4;
import myutils.math.MathUtils;
import myutils.math.Vec3;

public class HW3Window extends Window {

	// - at minimum, implement SPH in 3D
	// - on top of that, get nice water rendering
	//   - can just fake it: https://developer.download.nvidia.com/presentations/2010/gdc/Direct3D_Effects.pdf
	//   - anisotropic kernels. Can run in realtime? https://faculty.cc.gatech.edu/~turk/my_papers/sph_surfaces.pdf
	//   - how to speed up metaball rendering?
	//     - can sample from water density field?
	//     - use SDF to bound, and take small steps once reached SDF: https://facultyweb.cs.wwu.edu/~wehrwes/courses/csci480_22f/lectures/L37/L37_Isosurfaces_Polygonization.pdf
	//     - kinda, look here: http://www.geisswerks.com/ryan/BLOBS/blobs.html
	//   - basic reflections are a must. This includes from skybox and fresnel
	//   - refraction is harder, as we have to trace the ray thru the liquid. 
	//     - Maybe we can fake it and only consider the entry angle
	//       - only considering entry angle is doodoo. Tried it and it looks bad
	//     - or maybe there is screen space refraction techniques. Idk, need to do some research
	// - if time permits, add some fish!

	// - SPH overview : https://cg.informatik.uni-freiburg.de/publications/2014_EG_SPH_STAR.pdf
	// - unified spray, foam, bubbles : https://cg.informatik.uni-freiburg.de/publications/2012_CGI_sprayFoamBubbles.pdf
	//   - 2D simulation might be better for this one. 

	//to speed up, can try to load particles in workgroup into local memory. When looking for a particle that exists in local memory, 
	//just load from local. 

	//however, this only reliably works for particles within the same hash cube. So it's 26 / 27 or 7 / 8 speedup depending on size of hash cube. 
	//somehow manipulate hash to make adjacent cells hash adjacent to eachother. This might increase efficiency. 

	//maybe do 27 or 8 passes (depending on cube size) each focusing on comparing current particle with one of the adjacent hash cubes. 
	//for each pass, can look at workgroup thread 0, and just grab particles starting from proper cell. 
	//try to make workgroup size around equal to amount of particles that can fit in a cell. 
	//too few, and each particle is going to still have to lookup many particles in global memory.
	//too many, and many particles will not be properly represented in local memory. 

	//for each workgroup, we can still ask each thread to load in many particles to shared memory. Ex: 256 threads, but each loads 4 particles
	//to shared. 

	//TODO
	// - velocity color rendering
	// - improve rendering speed
	//   - ok, slow raymarching rendering is in place. It takes around 30ms to render once the water settles. 
	//   - if we can make it 60fps at 16k particles, I'd be happy
	//   - nice idea here : https://jcgt.org/published/0007/01/02/paper-lowres.pdf 
	//   - idea is to speed up raytracing by first rasterizing particle spheres and using that position information as
	//     starting point for the raycasts. 
	// - improve update speed
	//   - experiment to determine what is best spatialWorkgroupSz

	// Time per update after water settles:
	// Simulation Settings:
	// - pipelineV0
	// - 20x20 box centered at origin	
	// - viscosityStrength = 1f;
	// - pressureMultiplier = 512f;
	// - nearPressureMultiplier = 0.1f;
	// - targetDensity = 16f;

	// hash cube size = smoothing_radius
	// - (2 << 12) : ~4ms
	// - (2 << 13) : ~10ms
	// - (2 << 14) : ~18ms
	// - (2 << 15) : ~38ms
	// - (2 << 16) : ~90ms

	// hash cube size = smoothing_radius * 2
	// - (2 << 12) : ~3ms
	// - (2 << 13) : ~9ms
	// - (2 << 14) : ~27ms
	// - (2 << 15) : ~140ms

	// hash cube size = smoothing_radius
	// spatialWorkgroupSz = 32
	// - (2 << 12) : ~0.8ms
	// - (2 << 13) : ~1.2ms
	// - (2 << 14) : ~1.7ms
	// - (2 << 15) : ~2.9ms	(15x15 base)
	// - (2 << 16) : ~6ms	(15x15 base)
	// - (2 << 17) : ~12ms  (15x15 base)
	// - (2 << 18) : ~50ms  (15x15 base)

	private boolean printUpdateTimes = false;
	private boolean printRenderTimes = true;
	private boolean doUpdate = true;

	private int timeAvgAmt = 100;
	private Stack<Float> updateTimes = new Stack<>();
	private Stack<Float> renderTimes = new Stack<>();

	private static final int NR_PARTICLES_LOG2 = 16; //must be \geq 10 due to bitonic sort
	private static final int NR_PARTICLES = (1 << NR_PARTICLES_LOG2);

	//to compute volumes, just have to take spherical integral.
	//something, something, rho, phi, theta, multiply f(r) by rho^2 * sin(phi)

	private static float smoothingRadius = 1f;
	//(S - r)^3
	//https://www.wolframalpha.com/input?i=int+%5B%2F%2Fmath%3Arho%5E2+*+sin%28phi%29+*+%28S+-+rho%29%5E3%2F%2F%5D+%5B%2F%2Fmath%3Adrho+dphi+dtheta%2F%2F%5D+%2C+rho%3D%5B%2F%2Fmath%3A0%2F%2F%5D..%5B%2F%2Fmath%3AS%2F%2F%5D%2C+phi%3D%5B%2F%2Fmath%3A0%2F%2F%5D..%5B%2F%2Fmath%3Api%2F2%2F%2F%5D%2C+theta%3D%5B%2F%2Fmath%3A0%2F%2F%5D..%5B%2F%2Fmath%3A2pi%2F%2F%5D+
	private static float densitySmoothingKernelVolume = (float) (Math.PI * Math.pow(smoothingRadius, 6) / 30.0);
	//(S - r)^6
	//https://www.wolframalpha.com/input?i=int+%5B%2F%2Fmath%3Arho%5E2+*+sin%28phi%29+*+%28S+-+rho%29%5E6%2F%2F%5D+%5B%2F%2Fmath%3Adrho+dphi+dtheta%2F%2F%5D+%2C+rho%3D%5B%2F%2Fmath%3A0%2F%2F%5D..%5B%2F%2Fmath%3AS%2F%2F%5D%2C+phi%3D%5B%2F%2Fmath%3A0%2F%2F%5D..%5B%2F%2Fmath%3Api%2F2%2F%2F%5D%2C+theta%3D%5B%2F%2Fmath%3A0%2F%2F%5D..%5B%2F%2Fmath%3A2pi%2F%2F%5D+
	private static float nearDensitySmoothingKernelVolume = (float) (Math.PI * Math.pow(smoothingRadius, 9) / 126.0);
	//(S^2 - r^2)^3
	//https://www.wolframalpha.com/input?i=int+%5B%2F%2Fmath%3Arho%5E2+*+sin%28phi%29+*+%28S%5E2+-+rho%5E2%29%5E3%2F%2F%5D+%5B%2F%2Fmath%3Adrho+dphi+dtheta%2F%2F%5D+%2C+rho%3D%5B%2F%2Fmath%3A0%2F%2F%5D..%5B%2F%2Fmath%3AS%2F%2F%5D%2C+phi%3D%5B%2F%2Fmath%3A0%2F%2F%5D..%5B%2F%2Fmath%3Api%2F2%2F%2F%5D%2C+theta%3D%5B%2F%2Fmath%3A0%2F%2F%5D..%5B%2F%2Fmath%3A2pi%2F%2F%5D+
	private static float viscositySmoothingKernelVolume = (float) (Math.PI * Math.pow(smoothingRadius, 9) * 32.0 / 315.0);

	//want this to be around equal to the number of particles in a filled hash cell. 
	//TODO do some testing to figure out best value. 
	private static int spatialWorkgroupSz = 32;

	private Shader waterCompute1;
	private Shader waterCompute21, waterCompute22;
	private Shader waterCompute3;
	private Shader waterCompute4;

	private static final int HASH_LUT_SIZE = (1 << 20);
	private static final int HASH_MOD = (int) (1e6 + 7);
	private static final int LUT_P1 = 8443;
	private static final int LUT_P2 = 107;
	private static final int LUT_P3 = 31;
	private static final int LUT_P4 = 251527;
	private static final int LUT_P5 = 6037;
	private static final int LUT_P6 = 417;
	private static final int LUT_P7 = 1721;

	private ShaderStorageBuffer hashLUTBuffer;

	/*
	// 32 bytes
	struct Particle {
		vec3 pos;
		vec3 vel;
		int hash;
	};
	
	//32 bytes
	struct ParticleInfo {
	    vec3 pred_pos;
	    float density;
	    vec3 visc_force;
	    float near_density;
	};
	*/
	private static final int SIZEOF_PARTICLE = 32;
	private static final int SIZEOF_PARTICLE_INFO = 32;

	class Particle {
		Vec3 pos, vel;
		int hash;

		public Particle(Vec3 _pos) {
			this.pos = new Vec3(_pos);
			this.vel = new Vec3(0);
			this.hash = -1;
		}

		public Particle(int[] buffer, int offset) {
			this.pos = new Vec3(Float.intBitsToFloat(buffer[offset + 0]), Float.intBitsToFloat(buffer[offset + 1]), Float.intBitsToFloat(buffer[offset + 2]));
			this.vel = new Vec3(Float.intBitsToFloat(buffer[offset + 4]), Float.intBitsToFloat(buffer[offset + 5]), Float.intBitsToFloat(buffer[offset + 6]));
			this.hash = buffer[offset + 7];
		}

		public void writeToBuffer(int[] buffer, int offset) {
			buffer[offset + 0] = Float.floatToIntBits(pos.x);
			buffer[offset + 1] = Float.floatToIntBits(pos.y);
			buffer[offset + 2] = Float.floatToIntBits(pos.z);

			buffer[offset + 4] = Float.floatToIntBits(vel.x);
			buffer[offset + 5] = Float.floatToIntBits(vel.y);
			buffer[offset + 6] = Float.floatToIntBits(vel.z);

			buffer[offset + 7] = hash;
		}
	}

	private ShaderStorageBuffer particleBuffer, particleInfoBuffer;

	private final int WORLD_SCENE = Scene.generateScene();
	private PerspectiveScreen perspectiveScreen;
	private Shader particleRenderShader, waterRenderShader;
	private Cubemap skybox;

	private Shader sphereRasterShader, isosurfaceExtractShader;
	private Model cubeModel;

	private Framebuffer renderBuffer;
	private Texture renderColorMap;
	private Texture renderPositionMap;
	private TextureViewerWindow colorMapViewer, positionMapViewer;

	//	private Vec3 bbDimensions = new Vec3(10, 15, 15); //box centered at origin
	private Vec3 bbDimensions = new Vec3(20, 30, 20);
	private ModelInstance[] bbLines;
	private Vec3 gravity = new Vec3(0, -9.8, 0);

	private float viscosityStrength = 1f;
	public float pressureMultiplier = 256f;
	public float nearPressureMultiplier = 0.1f;
	public float targetDensity = 16f;

	private PlayerInputController pic;

	public HW3Window(int xOffset, int yOffset, int width, int height, Window parentWindow) {
		super(xOffset, yOffset, width, height, parentWindow);
		this.init();
	}

	private void init() {
		this.setLockCursorOnSelect(true);
		this.setDeselectOnEscPressed(true);
		this.setUnlockCursorOnEscPressed(true);

		this.waterCompute1 = ShaderUtils.createShader("/csce_vis/hw3/water_1.compute", GL_COMPUTE_SHADER);
		this.waterCompute21 = ShaderUtils.createShader("/csce_vis/hw3/water_2_1.compute", GL_COMPUTE_SHADER);
		this.waterCompute22 = ShaderUtils.createShader("/csce_vis/hw3/water_2_2.compute", GL_COMPUTE_SHADER);
		this.waterCompute3 = ShaderUtils.createShader("/csce_vis/hw3/water_3.compute", GL_COMPUTE_SHADER);
		this.waterCompute4 = ShaderUtils.createShader("/csce_vis/hw3/water_4.compute", GL_COMPUTE_SHADER);

		this.particleRenderShader = ShaderUtils.createShader("/csce_vis/hw3/particle.vert", "/csce_vis/hw3/particle.frag");
		this.waterRenderShader = ShaderUtils.createShader("/csce_vis/hw3/water_render.vert", "/csce_vis/hw3/water_render.frag");

		this.sphereRasterShader = ShaderUtils.createShader("/csce_vis/hw3/sphere_raster.vert", "/csce_vis/hw3/sphere_raster.frag");
		this.isosurfaceExtractShader = ShaderUtils.createShader("/csce_vis/hw3/isosurface_extract.vert", "/csce_vis/hw3/isosurface_extract.frag");
		try {
			this.cubeModel = Model.loadModelFileRelative("/res/cube/cube.obj");
		}
		catch (IOException e) {
			e.printStackTrace();
		}

		this.colorMapViewer = new TextureViewerWindow(null);
		this.positionMapViewer = new TextureViewerWindow(null);
		this.addChildAdjWindow(this.colorMapViewer);
		this.addChildAdjWindow(this.positionMapViewer);

		this.perspectiveScreen = new PerspectiveScreen();
		this.perspectiveScreen.setWorldCameraFOV(90f);
		this.perspectiveScreen.setWorldScene(WORLD_SCENE);
		this.perspectiveScreen.renderDecals(false);
		this.perspectiveScreen.renderParticles(false);
		this.perspectiveScreen.renderPlayermodel(false);
		this.perspectiveScreen.renderSkybox(true);

		this.pic = new PlayerInputController(new Vec3(0, 0, 30));
		this.pic.setNoclipSpeed(0.125f);
		this.pic.setDoNoclip(true);
		this.pic.setAcceptPlayerInputs(false);

		this.setBBDimensions(this.bbDimensions);

		BufferedImage[] skyboxSides = new BufferedImage[6];
		String skyboxDir = "/res/skybox/lake/";
		for (int i = 0; i < 6; i++) {
			skyboxSides[i] = FileUtils.loadImageRelative(skyboxDir + Cubemap.CUBEMAP_SIDE_NAMES[i] + ".jpg");
		}
		this.skybox = new Cubemap(skyboxSides);
		Scene.skyboxes.put(WORLD_SCENE, skybox);

		Light light = new DirLight(new Vec3(1, -1, 0.5f), new Vec3(1), 0.25f);
		Light.addLight(WORLD_SCENE, light);

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

		this.resetParticles();

		this._resize();
	}

	@Override
	protected void _kill() {
		this.perspectiveScreen.kill();

		this.particleBuffer.kill();
		this.particleInfoBuffer.kill();
		this.hashLUTBuffer.kill();

		this.waterCompute1.kill();
		this.waterCompute21.kill();
		this.waterCompute22.kill();
		this.waterCompute3.kill();
		this.waterCompute4.kill();

		this.particleRenderShader.kill();
		this.waterRenderShader.kill();
		this.renderBuffer.kill();

		this.sphereRasterShader.kill();
		this.isosurfaceExtractShader.kill();
		this.cubeModel.kill();

		this.skybox.kill();

		Scene.removeScene(WORLD_SCENE);
	}

	@Override
	protected void _resize() {
		if (this.renderBuffer != null) {
			this.renderBuffer.kill();
			this.renderBuffer = null;
		}

		if (this.getWidth() > 0 && this.getHeight() > 0) {
			this.renderBuffer = new Framebuffer(this.getWidth(), this.getHeight());
			this.renderColorMap = new Texture(this.getWidth(), this.getHeight(), GL_RGBA32F, GL_RGBA, GL_FLOAT);
			this.renderPositionMap = new Texture(this.getWidth(), this.getHeight(), GL_RGBA32F, GL_RGBA, GL_FLOAT);
			this.renderBuffer.bindTextureToBuffer(GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, this.renderColorMap.getID());
			this.renderBuffer.bindTextureToBuffer(GL_COLOR_ATTACHMENT1, GL_TEXTURE_2D, this.renderPositionMap.getID());
			this.renderBuffer.addDepthBuffer();
			this.renderBuffer.setDrawBuffers(new int[] { GL_COLOR_ATTACHMENT0, GL_COLOR_ATTACHMENT1 });
			this.renderBuffer.isComplete();

			this.colorMapViewer.setTexture(this.renderColorMap);
			this.positionMapViewer.setTexture(this.renderPositionMap);
		}

		this.perspectiveScreen.setScreenDimensions(this.getWidth(), this.getHeight());
	}

	@Override
	public String getDefaultTitle() {
		return "Homework 3";
	}

	private void resetParticles() {
		Vec3 min_b = bbDimensions.mul(-0.95f);
		Vec3 max_b = bbDimensions.mul(0.95f);

		int[] data = new int[NR_PARTICLES * SIZEOF_PARTICLE / 4];

		int col_height = 32;
		int col_wh = 32;

		int p_ptr = 0;
		for (int y = 0; y < col_height; y++) {
			for (int x = 0; x < col_wh; x++) {
				for (int z = 0; z < col_wh && p_ptr != NR_PARTICLES; z++) {
					Vec3 pos = new Vec3(x, y, z).add(MathUtils.random(new Vec3(-0.05), new Vec3(0.05)));
					pos.muli(0.535f);
					pos.addi(min_b);
					Particle p = new Particle(pos);
					p.writeToBuffer(data, p_ptr * SIZEOF_PARTICLE / 4);
					p_ptr++;
				}
			}
		}

		for (int y = 0; y < col_height; y++) {
			for (int x = 0; x < col_wh; x++) {
				for (int z = 0; z < col_wh && p_ptr != NR_PARTICLES; z++) {
					Vec3 pos = new Vec3(x, y, z).add(MathUtils.random(new Vec3(-0.05), new Vec3(0.05)));
					pos.muli(0.535f);
					pos.addi(min_b);
					pos = Mat4.rotateY((float) Math.toRadians(180f)).mul(pos, 0);
					Particle p = new Particle(pos);
					p.writeToBuffer(data, p_ptr * SIZEOF_PARTICLE / 4);
					p_ptr++;
				}
			}
		}

		for (int y = 0; y < col_height; y++) {
			for (int x = 0; x < col_wh; x++) {
				for (int z = 0; z < col_wh && p_ptr != NR_PARTICLES; z++) {
					Vec3 pos = new Vec3(x, y, z).add(MathUtils.random(new Vec3(-0.05), new Vec3(0.05)));
					pos.muli(0.535f);
					pos.addi(min_b);
					pos = Mat4.rotateY((float) Math.toRadians(90f)).mul(pos, 0);
					Particle p = new Particle(pos);
					p.writeToBuffer(data, p_ptr * SIZEOF_PARTICLE / 4);
					p_ptr++;
				}
			}
		}

		for (int y = 0; y < col_height; y++) {
			for (int x = 0; x < col_wh; x++) {
				for (int z = 0; z < col_wh && p_ptr != NR_PARTICLES; z++) {
					Vec3 pos = new Vec3(x, y, z).add(MathUtils.random(new Vec3(-0.05), new Vec3(0.05)));
					pos.muli(0.535f);
					pos.addi(min_b);
					pos = Mat4.rotateY((float) Math.toRadians(270f)).mul(pos, 0);
					Particle p = new Particle(pos);
					p.writeToBuffer(data, p_ptr * SIZEOF_PARTICLE / 4);
					p_ptr++;
				}
			}
		}

		this.particleBuffer.setSubData(data, 0);
		glMemoryBarrier(GL_SHADER_STORAGE_BARRIER_BIT);
	}

	private void setBBDimensions(Vec3 d) {
		this.bbDimensions = new Vec3(d);

		if (this.bbLines != null) {
			for (ModelInstance m : this.bbLines) {
				m.kill();
			}
			this.bbLines = null;
		}

		this.bbLines = new ModelInstance[12];

		Vec3 c0 = new Vec3(d.x, d.y, d.z);
		Vec3 c1 = new Vec3(d.x, d.y, -d.z);
		Vec3 c2 = new Vec3(d.x, -d.y, d.z);
		Vec3 c3 = new Vec3(d.x, -d.y, -d.z);
		Vec3 c4 = new Vec3(-d.x, d.y, d.z);
		Vec3 c5 = new Vec3(-d.x, d.y, -d.z);
		Vec3 c6 = new Vec3(-d.x, -d.y, d.z);
		Vec3 c7 = new Vec3(-d.x, -d.y, -d.z);

		this.bbLines[0] = Line.addDefaultLine(c0, c1, WORLD_SCENE);
		this.bbLines[1] = Line.addDefaultLine(c0, c2, WORLD_SCENE);
		this.bbLines[2] = Line.addDefaultLine(c3, c1, WORLD_SCENE);
		this.bbLines[3] = Line.addDefaultLine(c3, c2, WORLD_SCENE);
		this.bbLines[4] = Line.addDefaultLine(c4, c5, WORLD_SCENE);
		this.bbLines[5] = Line.addDefaultLine(c4, c6, WORLD_SCENE);
		this.bbLines[6] = Line.addDefaultLine(c7, c5, WORLD_SCENE);
		this.bbLines[7] = Line.addDefaultLine(c7, c6, WORLD_SCENE);
		this.bbLines[8] = Line.addDefaultLine(c0, c4, WORLD_SCENE);
		this.bbLines[9] = Line.addDefaultLine(c1, c5, WORLD_SCENE);
		this.bbLines[10] = Line.addDefaultLine(c2, c6, WORLD_SCENE);
		this.bbLines[11] = Line.addDefaultLine(c3, c7, WORLD_SCENE);
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
			this.waterCompute1.setUniform1i("LUT_P6", LUT_P6);
			this.waterCompute1.setUniform1i("LUT_P7", LUT_P7);

			this.particleBuffer.bindToBase(0);

			glDispatchCompute(NR_PARTICLES, 1, 1);
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
			this.waterCompute3.setUniform1i("LUT_P6", LUT_P6);
			this.waterCompute3.setUniform1i("LUT_P7", LUT_P7);

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
			this.waterCompute4.setUniform3f("gravity", this.gravity);
			this.waterCompute4.setUniform1i("nr_particles", NR_PARTICLES);

			this.waterCompute4.setUniform3f("bounds_min", this.bbDimensions.mul(-1));
			this.waterCompute4.setUniform3f("bounds_max", this.bbDimensions);

			this.waterCompute4.setUniform1f("smoothing_radius", smoothingRadius);
			this.waterCompute4.setUniform1i("hash_mod", HASH_MOD);
			this.waterCompute4.setUniform1i("LUT_P1", LUT_P1);
			this.waterCompute4.setUniform1i("LUT_P2", LUT_P2);
			this.waterCompute4.setUniform1i("LUT_P3", LUT_P3);
			this.waterCompute4.setUniform1i("LUT_P4", LUT_P4);
			this.waterCompute4.setUniform1i("LUT_P5", LUT_P5);
			this.waterCompute4.setUniform1i("LUT_P6", LUT_P6);
			this.waterCompute4.setUniform1i("LUT_P7", LUT_P7);

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
		this.pic.update();

		// query max shader workgroup shared memory in bytes
		// around 49k bytes
		//		int[] res = new int[1];
		//		glGetIntegerv(GL_MAX_COMPUTE_SHARED_MEMORY_SIZE, res);
		//		System.err.println("MAX MEM : " + res[0]);

		if (this.doUpdate) {
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

	}

	private void waterRenderPipelineV2(Framebuffer outputBuffer) {
		Camera camera = new Camera((float) Math.toRadians(90), this.getWidth(), this.getHeight(), 0.1f, 400);
		camera.setPos(this.pic.getPos());
		camera.setFacing(this.pic.getFacing());

		//clear buffer
		{
			this.renderBuffer.bind();
			glClearDepth(1); // maximum value
			glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
		}

		//raster sphere positions to pos buffer
		{
			this.sphereRasterShader.enable();
			this.sphereRasterShader.setUniformMat4("pr_matrix", camera.getProjectionMatrix());
			this.sphereRasterShader.setUniformMat4("vw_matrix", camera.getViewMatrix());
			this.sphereRasterShader.setUniform1f("smoothing_radius", smoothingRadius * 0.5f);
			this.sphereRasterShader.setUniform3f("view_pos", camera.getPos());

			this.particleBuffer.bindToBase(0);

			glViewport(0, 0, this.getWidth(), this.getHeight());
			glEnable(GL_DEPTH_TEST);
			glDepthFunc(GL_LESS);

			glEnable(GL_CULL_FACE);
			glCullFace(GL_BACK);

			VertexArray cube_va = this.cubeModel.getMeshes().get(0);
			cube_va.bind();
			glDrawElementsInstanced(GL_TRIANGLES, 12 * 3, GL_UNSIGNED_INT, 0, NR_PARTICLES);
		}

		//extract isosurface using pos buffer. 
		{
			this.isosurfaceExtractShader.enable();
			this.isosurfaceExtractShader.setUniform1i("pos_tex", 0);

			this.isosurfaceExtractShader.setUniformMat4("pr_matrix", camera.getProjectionMatrix());
			this.isosurfaceExtractShader.setUniformMat4("vw_matrix", camera.getViewMatrix());
			this.isosurfaceExtractShader.setUniform3f("camera_pos", camera.getPos());

			this.isosurfaceExtractShader.setUniform1f("target_density", this.targetDensity);
			this.isosurfaceExtractShader.setUniform1i("nr_particles", NR_PARTICLES);

			this.isosurfaceExtractShader.setUniform1f("smoothing_radius", smoothingRadius);
			this.isosurfaceExtractShader.setUniform1i("hash_mod", HASH_MOD);
			this.isosurfaceExtractShader.setUniform1i("LUT_P1", LUT_P1);
			this.isosurfaceExtractShader.setUniform1i("LUT_P2", LUT_P2);
			this.isosurfaceExtractShader.setUniform1i("LUT_P3", LUT_P3);
			this.isosurfaceExtractShader.setUniform1i("LUT_P4", LUT_P4);
			this.isosurfaceExtractShader.setUniform1i("LUT_P5", LUT_P5);
			this.isosurfaceExtractShader.setUniform1i("LUT_P6", LUT_P6);
			this.isosurfaceExtractShader.setUniform1i("LUT_P7", LUT_P7);

			this.isosurfaceExtractShader.setUniform1f("density_smoothing_kernel_volume", densitySmoothingKernelVolume);

			this.renderPositionMap.bind(GL_TEXTURE0);

			this.particleBuffer.bindToBase(0);
			this.hashLUTBuffer.bindToBase(1);

			glDisable(GL_CULL_FACE);
			glDisable(GL_DEPTH_TEST);

			glEnable(GL_BLEND);
			glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

			glViewport(0, 0, this.getWidth(), this.getHeight());
			SkyboxCube.skyboxCube.render();
		}

		//render back to output buffer
		{
			outputBuffer.bind();
			Shader.SPLASH.enable();
			Shader.SPLASH.setUniform1f("alpha", 1f);
			this.renderColorMap.bind(GL_TEXTURE0);

			glEnable(GL_BLEND);
			glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
			ScreenQuad.screenQuad.render();
		}
	}

	private void waterRenderPipelineV1(Framebuffer outputBuffer) {
		Camera camera = new Camera((float) Math.toRadians(90), this.getWidth(), this.getHeight(), 0.1f, 400);
		camera.setPos(this.pic.getPos());
		camera.setFacing(this.pic.getFacing());

		//clear buffer
		{
			this.renderBuffer.bind();
			glClearDepth(1); // maximum value
			glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
		}

		//render water
		{
			this.renderBuffer.bind();

			this.waterRenderShader.enable();
			this.waterRenderShader.setUniform1i("skybox", 0);

			this.waterRenderShader.setUniformMat4("pr_matrix", camera.getProjectionMatrix());
			this.waterRenderShader.setUniformMat4("vw_matrix", camera.getViewMatrix());
			this.waterRenderShader.setUniform3f("camera_pos", camera.getPos());

			this.waterRenderShader.setUniform1f("target_density", this.targetDensity);
			this.waterRenderShader.setUniform1i("nr_particles", NR_PARTICLES);

			this.waterRenderShader.setUniform1f("smoothing_radius", smoothingRadius);
			this.waterRenderShader.setUniform1i("hash_mod", HASH_MOD);
			this.waterRenderShader.setUniform1i("LUT_P1", LUT_P1);
			this.waterRenderShader.setUniform1i("LUT_P2", LUT_P2);
			this.waterRenderShader.setUniform1i("LUT_P3", LUT_P3);
			this.waterRenderShader.setUniform1i("LUT_P4", LUT_P4);
			this.waterRenderShader.setUniform1i("LUT_P5", LUT_P5);
			this.waterRenderShader.setUniform1i("LUT_P6", LUT_P6);
			this.waterRenderShader.setUniform1i("LUT_P7", LUT_P7);

			this.waterRenderShader.setUniform1f("density_smoothing_kernel_volume", densitySmoothingKernelVolume);

			this.particleBuffer.bindToBase(0);
			this.hashLUTBuffer.bindToBase(1);

			this.skybox.bind(GL_TEXTURE0);

			glDisable(GL_CULL_FACE);
			glDisable(GL_DEPTH_TEST);

			glEnable(GL_BLEND);
			glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

			glViewport(0, 0, this.getWidth(), this.getHeight());
			SkyboxCube.skyboxCube.render();
		}

		//render back to output buffer
		{
			outputBuffer.bind();
			Shader.SPLASH.enable();
			Shader.SPLASH.setUniform1f("alpha", 1f);
			this.renderColorMap.bind(GL_TEXTURE0);

			glEnable(GL_BLEND);
			glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
			ScreenQuad.screenQuad.render();
		}
	}

	private void waterRenderPipelineV0(Framebuffer outputBuffer) {
		Camera camera = new Camera((float) Math.toRadians(90), this.getWidth(), this.getHeight(), 0.1f, 400);
		camera.setPos(this.pic.getPos());
		camera.setFacing(this.pic.getFacing());

		outputBuffer.bind();

		this.particleRenderShader.enable();
		this.particleRenderShader.setUniformMat4("pr_matrix", camera.getProjectionMatrix());
		this.particleRenderShader.setUniformMat4("vw_matrix", camera.getViewMatrix());

		this.particleBuffer.bindToBase(0);

		glDisable(GL_DEPTH_TEST);
		glPointSize(2f);
		glViewport(0, 0, this.getWidth(), this.getHeight());
		glDrawArrays(GL_POINTS, 0, NR_PARTICLES);
	}

	@Override
	protected void renderContent(Framebuffer outputBuffer) {
		Camera camera = new Camera((float) Math.toRadians(90), this.getWidth(), this.getHeight(), 0.1f, 400);
		camera.setPos(this.pic.getPos());
		camera.setFacing(this.pic.getFacing());

		this.perspectiveScreen.setCamera(camera);
		this.perspectiveScreen.render(outputBuffer);

		int time_query = -1;
		if (this.printRenderTimes) {
			time_query = glGenQueries();
			glBeginQuery(GL_TIME_ELAPSED, time_query);
		}

		this.waterRenderPipelineV2(outputBuffer);

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
		this.pic.setAcceptPlayerInputs(true);
	}

	@Override
	protected void deselected() {
		this.pic.setAcceptPlayerInputs(false);
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
		case GLFW.GLFW_KEY_R: {
			this.resetParticles();
			break;
		}
		}
	}

	@Override
	protected void _keyReleased(int key) {
		// TODO Auto-generated method stub

	}

}
