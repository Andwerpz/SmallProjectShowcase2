package csce_vis.hw5;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;

import org.lwjgl.glfw.GLFW;

import lwjglengine.graphics.Cubemap;
import lwjglengine.graphics.Framebuffer;
import lwjglengine.graphics.Material;
import lwjglengine.model.Model;
import lwjglengine.model.ModelInstance;
import lwjglengine.model.ModelTransform;
import lwjglengine.model.Triangle;
import lwjglengine.player.Camera;
import lwjglengine.player.PlayerInputController;
import lwjglengine.scene.DirLight;
import lwjglengine.scene.Light;
import lwjglengine.scene.Scene;
import lwjglengine.screen.PerspectiveScreen;
import lwjglengine.window.Window;
import myutils.file.FileUtils;
import myutils.math.Mat3;
import myutils.math.Mat4;
import myutils.math.MathUtils;
import myutils.math.Quaternion;
import myutils.math.Vec3;

public class HW5Window extends Window {

	//finally, rigidbodies!!

	//ok, start experimenting with colliding AABBs, just have to do separating axis test. 
	//question: collision normal is given by separating axis, but how do i find collision position?
	// if some vertices are intersecting, then hopefully they're all on one face. 
	// In that case, take the centroid of the face of the participating vertices
	// if they are not all on one face, then we got some weird stuff going on, prolly just average all the vertices. 
	// if no vertices are intersecting, then it's purely an edge-edge collision. There must be exactly 1 offending edge from both sides. 

	//look here: https://box2d.org/files/ErinCatto_IterativeDynamics_GDC2005.pdf

	private ImpulseScene impulse;
	private ArrayList<ModelInstance> mi_arr;
	private Model cubeModel = null;

	private final int WORLD_SCENE = Scene.generateScene();

	private PerspectiveScreen perspectiveScreen;
	private PlayerInputController pic;

	private boolean pausePhysics = false;
	private boolean pauseOnCollide = false;

	public HW5Window(int xOffset, int yOffset, int width, int height, Window parentWindow) {
		super(xOffset, yOffset, width, height, parentWindow);
		this.init();
	}

	private void init() {
		this.setLockCursorOnSelect(true);
		this.setDeselectOnEscPressed(true);
		this.setUnlockCursorOnEscPressed(true);

		this.impulse = new ImpulseScene();
		this.mi_arr = new ArrayList<>();

		try {
			this.cubeModel = Model.loadModelFileRelative("/res/cube/cube.obj");
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

		this.resetImpulseScene();

		this._resize();
	}

	private void resetImpulseScene() {
		for (ModelInstance m : this.mi_arr) {
			m.kill();
		}
		this.mi_arr.clear();

		this.impulse.clearScene();

		this.pausePhysics = false;

		//ground
		{
			Shape s = new AABB(new Vec3(420));
			Body b = new Body(s, new Vec3(0, -210 - 0.1, 0));
			b.setStatic();
			this.addBody(b);
		}

		//short wide box
		if (true) {
			Shape s = new AABB(new Vec3(20, 10, 20));
			Body b = new Body(s, new Vec3(10, 5, 0));
			b.setStatic();
			this.addBody(b);
		}

		//long box
		if (true) {
			Shape s = new AABB(new Vec3(20, 5, 5));
			Body b = new Body(s, new Vec3(3, 20, 0));
//						b.angvel = new Vec3(0, 0, 0.2f);
			this.addBody(b);
		}

		//random box
		if (false) {
			Shape s = new AABB(MathUtils.random(new Vec3(3), new Vec3(10)));
			Body b = new Body(s, new Vec3(10));
			b.vel = MathUtils.randomUnitDir3D().mul(10);
			b.angvel = MathUtils.randomUnitDir3D();
			this.addBody(b);
		}
	}

	private void addBody(Body b) {
		this.impulse.addBody(b);

		Shape s = b.shape;
		ModelInstance m = null;
		switch (s.type) {
		case AABB:
			m = new ModelInstance(this.cubeModel, WORLD_SCENE);
			break;
		}

		this.mi_arr.add(m);
	}

	@Override
	protected void _kill() {
		for (ModelInstance m : this.mi_arr) {
			m.kill();
		}

		this.cubeModel.kill();

		Scene.removeScene(WORLD_SCENE);

		this.perspectiveScreen.kill();
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
		if (!this.pausePhysics) {
			for (int i = 0; i < 10; i++) {
				this.impulse.update(1.0f / 600.0f);
				if (this.impulse.getCollisionOccurred() && this.pauseOnCollide) {
					this.pausePhysics = true;
				}
			}
		}

		//update physics model transforms
		for (int i = 0; i < this.mi_arr.size(); i++) {
			Mat4 transform = Mat4.identity();

			//apply per shape scaling stuff
			Body b = this.impulse.getBodies().get(i);
			Shape s = b.shape;
			switch (s.type) {
			case AABB:
				AABB a = (AABB) s;
				transform.muli(Mat4.scale(a.half_dim));
				break;
			}

			//apply general orientation and translation transforms
			Mat4 rot_transform = MathUtils.quaternionToRotationMat4(b.orient);
			transform.muli(rot_transform);

			transform.muli(Mat4.translate(b.pos));

			this.mi_arr.get(i).setModelTransform(new ModelTransform(transform));
		}

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
		}
	}

	@Override
	protected void _keyReleased(int key) {
		// TODO Auto-generated method stub

	}

}
