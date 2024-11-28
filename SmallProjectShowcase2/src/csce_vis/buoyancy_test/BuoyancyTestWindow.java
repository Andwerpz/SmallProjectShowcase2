package csce_vis.buoyancy_test;

import java.awt.image.BufferedImage;
import java.util.ArrayList;

import org.lwjgl.glfw.GLFW;

import lwjglengine.graphics.Cubemap;
import lwjglengine.graphics.Framebuffer;
import lwjglengine.impulse3d.Body;
import lwjglengine.impulse3d.ImpulseScene;
import lwjglengine.impulse3d.shape.Capsule;
import lwjglengine.impulse3d.shape.KDOP;
import lwjglengine.impulse3d.shape.Shape;
import lwjglengine.player.Camera;
import lwjglengine.player.PlayerInputController;
import lwjglengine.scene.DirLight;
import lwjglengine.scene.Light;
import lwjglengine.scene.Scene;
import lwjglengine.screen.PerspectiveScreen;
import lwjglengine.window.Window;
import myutils.file.FileUtils;
import myutils.math.Mat4;
import myutils.math.MathUtils;
import myutils.math.Quaternion;
import myutils.math.Vec3;

public class BuoyancyTestWindow extends Window {
	//wrap an oriented bounding box around every body, and just use that for buoyancy calculations
	//for each obb, subdivide it into a bunch of grid cells, and just sample the water height at each grid cell
	//discard grid cell if it's entirely outside of the object

	private final int WORLD_SCENE = Scene.generateScene();
	private ImpulseScene impulse;
	private PerspectiveScreen perspectiveScreen;
	private ArrayList<BuoyancyBody> buoyancyBodies;

	private PlayerInputController pic;

	public BuoyancyTestWindow(int xOffset, int yOffset, int width, int height, Window parentWindow) {
		super(xOffset, yOffset, width, height, parentWindow);
		this.init();
	}

	private void init() {
		this.setLockCursorOnSelect(true);
		this.setDeselectOnEscPressed(true);
		this.setUnlockCursorOnEscPressed(true);

		this.buoyancyBodies = new ArrayList<>();

		this.pic = new PlayerInputController(new Vec3(0, 30, 100));
		this.pic.setAcceptPlayerInputs(false);

		this.impulse = new ImpulseScene(WORLD_SCENE);
		this.impulse.setGravity(new Vec3(0, -50, 0));

		DirLight sun = new DirLight(new Vec3(-2, -1.5, -1), new Vec3(1), 0.4f);
		Light.addLight(WORLD_SCENE, sun);

		BufferedImage[] skyboxSides = new BufferedImage[6];
		String skyboxDir = "/res/skybox/lake/";
		for (int i = 0; i < 6; i++) {
			skyboxSides[i] = FileUtils.loadImageRelative(skyboxDir + Cubemap.CUBEMAP_SIDE_NAMES[i] + ".jpg");
		}
		Cubemap skybox = new Cubemap(skyboxSides);
		Scene.skyboxes.put(WORLD_SCENE, skybox);

		this.perspectiveScreen = new PerspectiveScreen();
		this.perspectiveScreen.setWorldCameraFOV(90f);
		this.perspectiveScreen.setWorldScene(WORLD_SCENE);
		this.perspectiveScreen.renderSkybox(true);
		this.perspectiveScreen.renderDecals(false);
		this.perspectiveScreen.renderPlayermodel(false);
		this.perspectiveScreen.renderParticles(false);

		this._resize();
	}

	private void resetImpulseScene() {
		this.impulse.clearScene();
	}

	@Override
	protected void _kill() {
		this.impulse.kill();
		this.perspectiveScreen.kill();

		Scene.removeScene(WORLD_SCENE);
	}

	@Override
	protected void _resize() {
		this.perspectiveScreen.setScreenDimensions(this.getWidth(), this.getHeight());
	}

	@Override
	public String getDefaultTitle() {
		return "Buoyancy Test";
	}

	@Override
	protected void _update() {
		{
			int itercnt = 4;
			for (int i = 0; i < itercnt; i++) {
				float dt = 1.0f / (60.0f * itercnt);
				for (BuoyancyBody b : this.buoyancyBodies) {
					b.applyBuoyancy(dt);
				}
				this.impulse.update(dt);
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

	private float queryWaterHeight(Vec3 pos) {
		return 0;
	}

	private static final int VOXEL_AMT = 5;

	class BuoyancyBody {
		public Body b;
		public Vec3 bmin, bmax; //bounding box dimensions

		public BuoyancyBody(Body _b) {
			this.b = _b;

			//figure out bounding box
			Shape s = this.b.shape;
			lwjglengine.impulse3d.bvh.KDOP bb = s.calcBoundingBox(Quaternion.identity(), new Vec3(0));
			this.bmin = new Vec3(bb.bmin[0], bb.bmin[1], bb.bmin[2]);
			this.bmax = new Vec3(bb.bmax[0], bb.bmax[1], bb.bmax[2]);
		}

		public void applyBuoyancy(float dt) {
			//TODO
		}
	}

}
