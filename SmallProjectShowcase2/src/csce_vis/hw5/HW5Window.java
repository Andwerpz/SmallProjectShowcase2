package csce_vis.hw5;

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
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;

import org.lwjgl.glfw.GLFW;

import lwjglengine.impulse3d.ImpulseScene;
import lwjglengine.impulse3d.Body;
import lwjglengine.graphics.Cubemap;
import lwjglengine.graphics.Framebuffer;
import lwjglengine.graphics.Material;
import lwjglengine.model.Cube;
import lwjglengine.model.CubeSphere;
import lwjglengine.model.Cylinder;
import lwjglengine.model.Line;
import lwjglengine.model.Model;
import lwjglengine.model.ModelInstance;
import lwjglengine.model.ModelTransform;
import lwjglengine.model.Triangle;
import lwjglengine.model.VertexArray;
import lwjglengine.player.Camera;
import lwjglengine.player.PlayerInputController;
import lwjglengine.scene.DirLight;
import lwjglengine.scene.Light;
import lwjglengine.scene.Scene;
import lwjglengine.screen.PerspectiveScreen;
import lwjglengine.window.ObjectEditorWindow;
import lwjglengine.window.Window;
import myutils.file.FileUtils;
import myutils.math.Mat3;
import myutils.math.Mat4;
import myutils.math.MathUtils;
import myutils.math.Quaternion;
import myutils.math.Vec2;
import myutils.math.Vec3;

public class HW5Window extends Window {

	//finally, rigidbodies!!
	//look here: https://box2d.org/files/ErinCatto_IterativeDynamics_GDC2005.pdf

	//TODO
	// - figure out tetrahedron moment of inertia
	// - properly solve for friction in Manifold. 
	// - still some weird stuff going on with moment of inertia of thin objects
	// - optimize kdop-kdop collision narrow phase

	private ImpulseScene impulse;
	private Model suzanne = null;
	private Model burrito = null;

	private final int WORLD_SCENE = Scene.generateScene();

	private PerspectiveScreen perspectiveScreen;
	private PlayerInputController pic;

	private SimulationOptions options = new SimulationOptions();

	public class SimulationOptions {
		private boolean pausePhysics = false;
		private boolean pauseOnCollide = false;
		private boolean generateWireframes = false;
		private int demosceneNumber = 0;

		public int getDemosceneNumber() {
			return demosceneNumber;
		}

		public void setDemosceneNumber(int demosceneNumber) {
			this.demosceneNumber = demosceneNumber;
		}

		public boolean getPausePhysics() {
			return pausePhysics;
		}

		public void setPausePhysics(boolean pausePhysics) {
			this.pausePhysics = pausePhysics;
		}

		public boolean getPauseOnCollide() {
			return pauseOnCollide;
		}

		public void setPauseOnCollide(boolean pauseOnCollide) {
			this.pauseOnCollide = pauseOnCollide;
		}

		public boolean getGenerateWireframes() {
			return generateWireframes;
		}

		public void setGenerateWireframes(boolean generateWireframes) {
			this.generateWireframes = generateWireframes;
		}
	}

	public HW5Window(int xOffset, int yOffset, int width, int height, Window parentWindow) {
		super(xOffset, yOffset, width, height, parentWindow);
		this.init();
	}

	private void init() {
		this.setLockCursorOnSelect(true);
		this.setDeselectOnEscPressed(true);
		this.setUnlockCursorOnEscPressed(true);

		this.impulse = new ImpulseScene(WORLD_SCENE);
		this.impulse.setGravity(new Vec3(0, -50, 0));

		try {
			this.suzanne = Model.loadModelFileRelative("/res/suzanne/suzanne.obj");
			this.burrito = Model.loadModelFileRelative("/res/burrito/burrito.obj");
		}
		catch (IOException e) {
			e.printStackTrace();
		}

		this.pic = new PlayerInputController(new Vec3(0, 30, 100));
		this.pic.setAcceptPlayerInputs(false);

		this.perspectiveScreen = new PerspectiveScreen();
		this.perspectiveScreen.setWorldCameraFOV(90f);
		this.perspectiveScreen.setWorldScene(WORLD_SCENE);
		this.perspectiveScreen.renderSkybox(true);
		this.perspectiveScreen.renderDecals(false);
		this.perspectiveScreen.renderPlayermodel(false);
		this.perspectiveScreen.renderParticles(false);

		DirLight sun = new DirLight(new Vec3(-2, -1.5, -1), new Vec3(1), 0.4f);
		Light.addLight(WORLD_SCENE, sun);

		BufferedImage[] skyboxSides = new BufferedImage[6];
		String skyboxDir = "/res/skybox/lake/";
		for (int i = 0; i < 6; i++) {
			skyboxSides[i] = FileUtils.loadImageRelative(skyboxDir + Cubemap.CUBEMAP_SIDE_NAMES[i] + ".jpg");
		}
		Cubemap skybox = new Cubemap(skyboxSides);
		Scene.skyboxes.put(WORLD_SCENE, skybox);

		//ground
		{
			float tile_size = 10;

			Vec3 g0 = new Vec3(-tile_size, 0, tile_size);
			Vec3 g1 = new Vec3(tile_size, 0, tile_size);
			Vec3 g2 = new Vec3(tile_size, 0, -tile_size);
			Vec3 g3 = new Vec3(-tile_size, 0, -tile_size);

			Material light_mat = new Material(Color.WHITE);
			Material dark_mat = new Material(new Vec3(0.6f));

			light_mat.setSpecular(new Vec3(0));
			dark_mat.setSpecular(new Vec3(0));

			int tile_amt = 10;
			for (int i = -tile_amt; i <= tile_amt; i++) {
				for (int j = -tile_amt; j <= tile_amt; j++) {
					Vec3 offset = new Vec3(i * tile_amt * 2, 0, j * tile_amt * 2);
					Vec3 v0 = g0.add(offset);
					Vec3 v1 = g1.add(offset);
					Vec3 v2 = g2.add(offset);
					Vec3 v3 = g3.add(offset);

					ModelInstance t0 = Triangle.addTriangle(v0, v1, v2, WORLD_SCENE);
					ModelInstance t1 = Triangle.addTriangle(v2, v3, v0, WORLD_SCENE);

					if (Math.abs(i + j) % 2 == 0) {
						t0.setMaterial(light_mat);
						t1.setMaterial(light_mat);
					}
					else {
						t0.setMaterial(dark_mat);
						t1.setMaterial(dark_mat);
					}
				}
			}
		}

		this.addChildAdjWindow(new ObjectEditorWindow(this.options));

		this.resetImpulseScene();

		this._resize();
	}

	private void resetImpulseScene() {
		this.impulse.clearScene();
		options.pausePhysics = false;

		//ground
		{
			Body b = this.impulse.addAABB(new Vec3(0, -210 - 0.1, 0), new Vec3(420));
			b.setStatic();
		}

		switch (options.demosceneNumber) {
		//short wide box
		case 0: {
			Body b = this.impulse.addAABB(new Vec3(0, 5, 0), new Vec3(50, 10, 50));
			b.setStatic();
			break;
		}

		//short wide box resting on short wide box
		case 1: {
			{
				Body b = this.impulse.addAABB(new Vec3(0, 5, 0), new Vec3(50, 10, 50));
				b.setStatic();
			}
			{
				Body b = this.impulse.addAABB(new Vec3(3, 17.5, 0), new Vec3(20, 5, 20));
			}
			break;
		}

		//suzanne
		case 2: {
			Body b = this.impulse.addKDOP(new Vec3(0, 20, 0), this.suzanne, Mat4.scale(5));
			b.angvel = new Vec3(3, 0, 0);
			break;
		}

		//burrito
		case 3: {
			Body b = this.impulse.addKDOP(new Vec3(0, 20, 0), this.burrito, Mat4.scale(10));
			break;
		}

		//table
		case 4: {
			Body bl = this.impulse.addAABB(new Vec3(-10, 10, -10), new Vec3(2, 20, 2));
			Body br = this.impulse.addAABB(new Vec3(10, 10, -10), new Vec3(2, 20, 2));
			Body tl = this.impulse.addAABB(new Vec3(-10, 10, 10), new Vec3(2, 20, 2));
			Body tr = this.impulse.addAABB(new Vec3(10, 10, 10), new Vec3(2, 20, 2));

			Body top = this.impulse.addAABB(new Vec3(0, 22, 0), new Vec3(25, 2, 25));
			Body burrito = this.impulse.addKDOP(new Vec3(0, 30, 0), this.burrito, Mat4.scale(10));
			break;
		}

		//cube triangle
		case 5: {
			int layer_amt = 14;
			float cube_sz = 3;
			float yptr = cube_sz / 2;
			for (int i = layer_amt; i >= 1; i--) {
				float xptr = -(cube_sz * i) / 2;
				xptr += cube_sz / 2;
				for (int j = 0; j < i; j++) {
					Body b = this.impulse.addAABB(new Vec3(xptr, yptr, 0), new Vec3(cube_sz));
					xptr += cube_sz;
				}
				yptr += cube_sz;
			}
			break;
		}

		//cube tower
		case 6: {
			int cube_amt = 3;
			float cube_sz = 3;
			float yptr = cube_sz / 2;
			for (int i = cube_amt; i >= 1; i--) {
				Body b = this.impulse.addAABB(new Vec3(0, yptr, 0), new Vec3(cube_sz));
				yptr += cube_sz;
			}
			break;
		}

		//static capsule
		case 7: {
			Body c1 = this.impulse.addCapsule(new Vec3(0, 10, 0), 3, 10);
			c1.setStatic();

			Body c2 = this.impulse.addCapsule(new Vec3(0, 30, 0), 3, 10);
			c2.angvel.set(new Vec3(0, 2, 0));
			break;
		}

		//capsule vs aabb pen correct test
		case 8: {
			Body c = this.impulse.addCapsule(new Vec3(0, 20, 0), 2, 20);
			c.setStatic();
			break;
		}
		}

	}

	@Override
	protected void _kill() {
		this.impulse.kill();
		Scene.removeScene(WORLD_SCENE);

		this.perspectiveScreen.kill();

		this.burrito.kill();
		this.suzanne.kill();
	}

	@Override
	protected void _resize() {
		this.perspectiveScreen.setScreenDimensions(this.getWidth(), this.getHeight());
	}

	@Override
	public String getDefaultTitle() {
		return "Homework 5";
	}

	@Override
	protected void _update() {
		if (!options.pausePhysics) {
			int itercnt = 4;
			for (int i = 0; i < itercnt; i++) {
				this.impulse.update(1.0f / (60.0f * itercnt));
				if (this.impulse.getCollisionOccurred() && options.pauseOnCollide) {
					options.pausePhysics = true;
				}
			}
		}
		this.impulse.updateDisplayBodies();

		this.pic.update();
	}

	@Override
	protected void renderContent(Framebuffer outputBuffer) {
		Camera camera = new Camera((float) Math.toRadians(90), this.getWidth(), this.getHeight(), 0.1f, 400);
		camera.setPos(this.pic.getTop());
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
		case GLFW.GLFW_KEY_R:
			this.resetImpulseScene();
			break;

		case GLFW.GLFW_KEY_Q: {
			Body b = this.impulse.addAABB(new Vec3(0, 20, 0), MathUtils.random(new Vec3(3), new Vec3(10)));
			b.angvel = MathUtils.randomUnitDir3D();
			break;
		}

		case GLFW.GLFW_KEY_E: {
			Body b = this.impulse.addAABB(this.pic.getPos().add(this.pic.getFacing().mul(5)), new Vec3(3));
			b.angvel = MathUtils.randomUnitDir3D().mul(10);
			b.vel = this.pic.getFacing().mul(50);
			break;
		}

		case GLFW.GLFW_KEY_B: {
			Body b = this.impulse.addKDOP(this.pic.getPos().add(this.pic.getFacing().mul(5)), this.burrito, Mat4.scale(10));
			b.angvel = MathUtils.randomUnitDir3D().mul(10);
			b.vel = this.pic.getFacing().mul(50);
			break;
		}

		case GLFW.GLFW_KEY_C: {
			Body b = this.impulse.addCapsule(this.pic.getPos().add(this.pic.getFacing().mul(5)), 3, 6);
			b.angvel = MathUtils.randomUnitDir3D().mul(10);
			b.vel = this.pic.getFacing().mul(50);
			break;
		}

		case GLFW.GLFW_KEY_Z: {
			Body b = this.impulse.addSphere(this.pic.getPos().add(this.pic.getFacing().mul(5)), 3);
			b.vel = this.pic.getFacing().mul(50);
			break;
		}
		}
	}

	@Override
	protected void _keyReleased(int key) {
		// TODO Auto-generated method stub

	}

}
