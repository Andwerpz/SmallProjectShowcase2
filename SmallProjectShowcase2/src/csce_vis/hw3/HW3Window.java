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

import org.lwjgl.glfw.GLFW;

import lwjglengine.graphics.Cubemap;
import lwjglengine.graphics.Framebuffer;
import lwjglengine.graphics.Shader;
import lwjglengine.graphics.ShaderStorageBuffer;
import lwjglengine.main.Main;
import lwjglengine.model.Model;
import lwjglengine.model.ModelInstance;
import lwjglengine.model.ModelTransform;
import lwjglengine.player.Camera;
import lwjglengine.player.PlayerInputController;
import lwjglengine.scene.DirLight;
import lwjglengine.scene.Light;
import lwjglengine.scene.Scene;
import lwjglengine.screen.PerspectiveScreen;
import lwjglengine.util.ShaderUtils;
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
	//     - or maybe there is screen space refraction techniques. Idk, need to do some research
	// - if time permits, add some fish!

	//TODO
	// - get SPH working
	// - create custom rendering pipeline so we can keep particle data in the GPU

	private static final int NR_PARTICLES = (1 << 13);

	//to compute volumes, just have to take spherical integral.
	//something, something, rho, phi, theta, multiply f(r) by rho^2 * sin(phi)

	private static float smoothingRadius = 1;
	//(S - r)^3
	//https://www.wolframalpha.com/input?i=int+%5B%2F%2Fmath%3Arho%5E2+*+sin%28phi%29+*+%28S+-+rho%29%5E3%2F%2F%5D+%5B%2F%2Fmath%3Adrho+dphi+dtheta%2F%2F%5D+%2C+rho%3D%5B%2F%2Fmath%3A0%2F%2F%5D..%5B%2F%2Fmath%3AS%2F%2F%5D%2C+phi%3D%5B%2F%2Fmath%3A0%2F%2F%5D..%5B%2F%2Fmath%3Api%2F2%2F%2F%5D%2C+theta%3D%5B%2F%2Fmath%3A0%2F%2F%5D..%5B%2F%2Fmath%3A2pi%2F%2F%5D+
	private static float densitySmoothingKernelVolume = (float) (Math.PI * Math.pow(smoothingRadius, 6) / 30.0);
	//(S - r)^6
	//https://www.wolframalpha.com/input?i=int+%5B%2F%2Fmath%3Arho%5E2+*+sin%28phi%29+*+%28S+-+rho%29%5E6%2F%2F%5D+%5B%2F%2Fmath%3Adrho+dphi+dtheta%2F%2F%5D+%2C+rho%3D%5B%2F%2Fmath%3A0%2F%2F%5D..%5B%2F%2Fmath%3AS%2F%2F%5D%2C+phi%3D%5B%2F%2Fmath%3A0%2F%2F%5D..%5B%2F%2Fmath%3Api%2F2%2F%2F%5D%2C+theta%3D%5B%2F%2Fmath%3A0%2F%2F%5D..%5B%2F%2Fmath%3A2pi%2F%2F%5D+
	private static float nearDensitySmoothingKernelVolume = (float) (Math.PI * Math.pow(smoothingRadius, 9) / 126.0);
	//(S^2 - r^2)^3
	//https://www.wolframalpha.com/input?i=int+%5B%2F%2Fmath%3Arho%5E2+*+sin%28phi%29+*+%28S%5E2+-+rho%5E2%29%5E3%2F%2F%5D+%5B%2F%2Fmath%3Adrho+dphi+dtheta%2F%2F%5D+%2C+rho%3D%5B%2F%2Fmath%3A0%2F%2F%5D..%5B%2F%2Fmath%3AS%2F%2F%5D%2C+phi%3D%5B%2F%2Fmath%3A0%2F%2F%5D..%5B%2F%2Fmath%3Api%2F2%2F%2F%5D%2C+theta%3D%5B%2F%2Fmath%3A0%2F%2F%5D..%5B%2F%2Fmath%3A2pi%2F%2F%5D+
	private static float viscositySmoothingKernelVolume = (float) (Math.PI * Math.pow(smoothingRadius, 9) * 32.0 / 315.0);

	private Shader waterCompute1;
	
	private static final int HASH_LUT_SIZE = (1 << 20);
	private static final int HASH_MOD = (int) (1e6 + 7);
	private static final int LUT_P1 = 8443;
	private static final int LUT_P2 = 107;
	private static final int LUT_P3 = 31;
	private static final int LUT_P4 = 251527;
	private static final int LUT_P5 = 6037;
	private static final int LUT_P6 = 417;

	private ShaderStorageBuffer hashLUTBuffer;
	
/*
// 80 bytes
struct Particle {
	vec3 pos;
	vec3 vel;
	vec3 pred_pos;
	vec3 visc_force;
	int hash;
	int hash_start_ind;
	float density;
	float near_density;
};
*/
	private static final int SIZEOF_PARTICLE = 80;
	class Particle {
		Vec3 pos, vel, pred_pos, visc_force;
		int hash, hash_start_ind;
		float density, near_density;
		
		public Particle(Vec3 _pos) {
			this.pos = new Vec3(_pos);
			this.vel = new Vec3(0);
			this.pred_pos = new Vec3(0);
			this.visc_force = new Vec3(0);
			this.hash = -1;
			this.hash_start_ind = -1;
			this.density = 0;
			this.near_density = 0;
		}
		
		public Particle(int[] buffer, int offset) {
			this.pos = new Vec3(Float.intBitsToFloat(buffer[offset + 0]), Float.intBitsToFloat(buffer[offset + 1]), Float.intBitsToFloat(buffer[offset + 2]));
			this.vel = new Vec3(Float.intBitsToFloat(buffer[offset + 4]), Float.intBitsToFloat(buffer[offset + 5]), Float.intBitsToFloat(buffer[offset + 6]));
			this.pred_pos = new Vec3(Float.intBitsToFloat(buffer[offset + 8]), Float.intBitsToFloat(buffer[offset + 9]), Float.intBitsToFloat(buffer[offset + 10]));
			this.visc_force = new Vec3(Float.intBitsToFloat(buffer[offset + 12]), Float.intBitsToFloat(buffer[offset + 13]), Float.intBitsToFloat(buffer[offset + 14]));
			this.hash = buffer[offset + 16];
			this.hash_start_ind = buffer[offset + 17];
			this.density = Float.intBitsToFloat(buffer[offset + 18]);
			this.near_density = Float.intBitsToFloat(buffer[offset + 19]);
		}
		
		public void writeToBuffer(int[] buffer, int offset) {
			buffer[offset + 0] = Float.floatToIntBits(pos.x);
			buffer[offset + 1] = Float.floatToIntBits(pos.y);
			buffer[offset + 2] = Float.floatToIntBits(pos.z);
			
			buffer[offset + 4] = Float.floatToIntBits(vel.x);
			buffer[offset + 5] = Float.floatToIntBits(vel.y);
			buffer[offset + 6] = Float.floatToIntBits(vel.z);
			
			buffer[offset + 8] = Float.floatToIntBits(pred_pos.x);
			buffer[offset + 9] = Float.floatToIntBits(pred_pos.y);
			buffer[offset + 10] = Float.floatToIntBits(pred_pos.z);
			
			buffer[offset + 12] = Float.floatToIntBits(visc_force.x);
			buffer[offset + 13] = Float.floatToIntBits(visc_force.y);
			buffer[offset + 14] = Float.floatToIntBits(visc_force.z);
			
			buffer[offset + 16] = hash;
			buffer[offset + 17] = hash_start_ind;
			buffer[offset + 18] = Float.floatToIntBits(density);
			buffer[offset + 19] = Float.floatToIntBits(near_density);
		}
	}
	
	private ShaderStorageBuffer particleBuffer;
	
	private final int WORLD_SCENE = Scene.generateScene();
	private PerspectiveScreen perspectiveScreen;

	private Model cubeModel;
	private ModelInstance[] cubeInstances;
	private float cubeScale = 0.1f;

	private Vec3 bbDimensions = new Vec3(10, 10, 10); //box centered at origin
	private float boundaryRestitution = 0.5f;
	private Vec3 gravity = new Vec3(0, -9.8, 0);

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

		this.perspectiveScreen = new PerspectiveScreen();
		this.perspectiveScreen.setWorldScene(WORLD_SCENE);
		this.perspectiveScreen.renderDecals(false);
		this.perspectiveScreen.renderParticles(false);
		this.perspectiveScreen.renderPlayermodel(false);
		this.perspectiveScreen.renderSkybox(true);

		this.pic = new PlayerInputController(new Vec3(0, 0, 30));
		this.pic.setNoclipSpeed(0.125f);
		this.pic.setDoNoclip(true);
		this.pic.setAcceptPlayerInputs(false);

		BufferedImage[] skyboxSides = new BufferedImage[6];
		String skyboxDir = "/res/skybox/lake/";
		for (int i = 0; i < 6; i++) {
			skyboxSides[i] = FileUtils.loadImageRelative(skyboxDir + Cubemap.CUBEMAP_SIDE_NAMES[i] + ".jpg");
		}
		Cubemap skybox = new Cubemap(skyboxSides);
		Scene.skyboxes.put(WORLD_SCENE, skybox);

		Light light = new DirLight(new Vec3(1, -1, 0.5f), new Vec3(1), 0.25f);
		Light.addLight(WORLD_SCENE, light);

		//initialize all particles
		try {
			this.cubeModel = Model.loadModelFileRelative("/res/cube/cube.obj");
		}
		catch (IOException e) {
			e.printStackTrace();
		}
		this.cubeInstances = new ModelInstance[NR_PARTICLES];
		for (int i = 0; i < NR_PARTICLES; i++) {
			ModelInstance m = new ModelInstance(this.cubeModel, WORLD_SCENE);
			this.cubeInstances[i] = m;
		}

		this.particleBuffer = new ShaderStorageBuffer();
		this.particleBuffer.setSize(NR_PARTICLES * SIZEOF_PARTICLE);
		this.particleBuffer.setUsage(GL_DYNAMIC_DRAW);
		
		this.hashLUTBuffer = new ShaderStorageBuffer();
		this.hashLUTBuffer.setSize(HASH_LUT_SIZE * 4);
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
		this.cubeModel.kill();
		this.perspectiveScreen.kill();

		this.particleBuffer.kill();
		this.hashLUTBuffer.kill();

		this.waterCompute1.kill();

		Scene.removeScene(WORLD_SCENE);
	}

	@Override
	protected void _resize() {
		this.perspectiveScreen.setScreenDimensions(this.getWidth(), this.getHeight());
	}

	@Override
	public String getDefaultTitle() {
		return "Homework 3";
	}

	private void resetParticles() {
		int[] data = new int[NR_PARTICLES * SIZEOF_PARTICLE / 4];
		for (int i = 0; i < NR_PARTICLES; i++) {
			Vec3 pos = MathUtils.random(bbDimensions.mul(-1), bbDimensions);
			Particle p = new Particle(pos);
			p.writeToBuffer(data, i * SIZEOF_PARTICLE / 4);
		}
		this.particleBuffer.setSubData(data, 0);
	}

	@Override
	protected void _update() {
		this.pic.update();

		float dt = Main.getDeltaSeconds();

		// -- PHASE 1 --
		//update position due to velocity, compute hashes
		{
			this.waterCompute1.enable();
			this.waterCompute1.setUniform1f("dt", dt);
			this.waterCompute1.setUniform3f("bounds_min", this.bbDimensions.mul(-1));
			this.waterCompute1.setUniform3f("bounds_max", this.bbDimensions);
			this.waterCompute1.setUniform1f("boundary_restitution", this.boundaryRestitution);
			this.waterCompute1.setUniform3f("gravity", this.gravity);
			
			this.waterCompute1.setUniform1f("smoothing_radius", smoothingRadius);
			this.waterCompute1.setUniform1i("hash_mod", HASH_MOD);
			this.waterCompute1.setUniform1i("LUT_P1", LUT_P1);
			this.waterCompute1.setUniform1i("LUT_P2", LUT_P2);
			this.waterCompute1.setUniform1i("LUT_P3", LUT_P3);
			this.waterCompute1.setUniform1i("LUT_P4", LUT_P4);
			this.waterCompute1.setUniform1i("LUT_P5", LUT_P5);
			this.waterCompute1.setUniform1i("LUT_P6", LUT_P6);

			this.particleBuffer.bindToBase(0);

			glDispatchCompute(NR_PARTICLES, 1, 1);
		}

		// -- PHASE 2.1 -- 
		//sort particles according to their hashes
		{
			//bitonic merge sort
			
		}
		
		// -- PHASE 2.2 -- 
		//generate hash lookup tables. For each hash, will save index at which particles belonging to that hash start
		
		// -- PHASE 3 --
		//compute density and viscosity forces per particle
		
		// -- PHASE 4 --
		//compute pressure forces. apply all forces to particles
		
		//update cube model instances
		{
			int[] data = new int[NR_PARTICLES * SIZEOF_PARTICLE / 4];
			this.particleBuffer.getSubData(data, 0);
			for (int i = 0; i < NR_PARTICLES; i++) {
				Particle p = new Particle(data, i * SIZEOF_PARTICLE / 4);

				Mat4 transform = Mat4.scale(this.cubeScale);
				transform.muli(Mat4.translate(p.pos));
				this.cubeInstances[i].setModelTransform(new ModelTransform(transform));
			}
		}
	}

	@Override
	protected void renderContent(Framebuffer outputBuffer) {
		Camera camera = new Camera((float) Math.toRadians(90), this.getWidth(), this.getHeight(), 0.1f, 400);
		camera.setPos(this.pic.getPos());
		camera.setFacing(this.pic.getFacing());

		this.perspectiveScreen.setCamera(camera);
		this.perspectiveScreen.render(outputBuffer);
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
