package csce_vis.buoyancy_test;

import static org.lwjgl.opengl.GL11.GL_BLEND;
import static org.lwjgl.opengl.GL11.GL_COLOR_BUFFER_BIT;
import static org.lwjgl.opengl.GL11.GL_CULL_FACE;
import static org.lwjgl.opengl.GL11.GL_DEPTH_BUFFER_BIT;
import static org.lwjgl.opengl.GL11.GL_DEPTH_TEST;
import static org.lwjgl.opengl.GL11.GL_FLOAT;
import static org.lwjgl.opengl.GL11.GL_RGBA;
import static org.lwjgl.opengl.GL11.GL_TRIANGLES;
import static org.lwjgl.opengl.GL11.glClear;
import static org.lwjgl.opengl.GL11.glDisable;
import static org.lwjgl.opengl.GL11.glEnable;
import static org.lwjgl.opengl.GL11.glViewport;
import static org.lwjgl.opengl.GL13.GL_TEXTURE0;
import static org.lwjgl.opengl.GL13.GL_TEXTURE_CUBE_MAP_POSITIVE_X;
import static org.lwjgl.opengl.GL30.GL_COLOR_ATTACHMENT0;
import static org.lwjgl.opengl.GL30.GL_RGBA16F;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;

import org.lwjgl.glfw.GLFW;

import lwjglengine.graphics.Cubemap;
import lwjglengine.graphics.Framebuffer;
import lwjglengine.graphics.Shader;
import lwjglengine.graphics.Texture;
import lwjglengine.impulse3d.Body;
import lwjglengine.impulse3d.ImpulseScene;
import lwjglengine.impulse3d.shape.Capsule;
import lwjglengine.impulse3d.shape.KDOP;
import lwjglengine.impulse3d.shape.Shape;
import lwjglengine.main.Main;
import lwjglengine.model.Model;
import lwjglengine.model.ModelInstance;
import lwjglengine.model.VertexArray;
import lwjglengine.player.Camera;
import lwjglengine.player.PlayerInputController;
import lwjglengine.scene.DirLight;
import lwjglengine.scene.Light;
import lwjglengine.scene.Scene;
import lwjglengine.screen.PerspectiveScreen;
import lwjglengine.screen.SkyboxCube;
import lwjglengine.util.ShaderUtils;
import lwjglengine.window.ObjectEditorWindow;
import lwjglengine.window.TextureViewerWindow;
import lwjglengine.window.Window;
import myutils.file.FileUtils;
import myutils.math.Mat3;
import myutils.math.Mat4;
import myutils.math.MathUtils;
import myutils.math.Quaternion;
import myutils.math.Vec3;

public class BuoyancyTestWindow extends Window {
	//wrap an oriented bounding box around every body, and just use that for buoyancy calculations
	//for each obb, subdivide it into a bunch of grid cells, and just sample the water height at each grid cell
	//discard grid cell if it's entirely outside of the object

	//learning material
	//https://www.scratchapixel.com/lessons/procedural-generation-virtual-worlds/simulating-sky/simulating-colors-of-the-sky.html

	private final int WORLD_SCENE = Scene.generateScene();
	private ImpulseScene impulse;
	private PerspectiveScreen perspectiveScreen;
	private HashMap<Body, BuoyancyBody> buoyancyBodies;

	private PlayerInputController pic;

	private static final int SKYBOX_RES = 512;
	private Framebuffer skyboxFramebuffer;
	private Shader skyboxShader;
	private Cubemap skybox;
	private float skyboxTime = 0;

	private Cubemap spaceSkybox;

	private Options options = new Options();

	public class Options {
		private Vec3 sunDir = new Vec3(1).normalize();

		public void setSunDir(Vec3 v) {
			this.sunDir.set(v);
		}

		public Vec3 getSunDir() {
			return this.sunDir;
		}
	}

	public BuoyancyTestWindow(int xOffset, int yOffset, int width, int height, Window parentWindow) {
		super(xOffset, yOffset, width, height, parentWindow);
		this.init();
	}

	private void init() {
		this.setLockCursorOnSelect(true);
		this.setDeselectOnEscPressed(true);
		this.setUnlockCursorOnEscPressed(true);

		this.buoyancyBodies = new HashMap<>();

		this.pic = new PlayerInputController(new Vec3(0, 30, 100));
		this.pic.setAcceptPlayerInputs(false);

		this.impulse = new ImpulseScene(WORLD_SCENE);
		this.impulse.setGravity(new Vec3(0, -50, 0));

		DirLight sun = new DirLight(new Vec3(-2, -1.5, -1), new Vec3(1), 0.4f);
		Light.addLight(WORLD_SCENE, sun);

		//load space skybox
		{
			BufferedImage[] skyboxSides = new BufferedImage[6];
			String skyboxDir = "/res/skybox/stars/";
			for (int i = 0; i < 6; i++) {
				skyboxSides[i] = FileUtils.loadImageRelative(skyboxDir + Cubemap.CUBEMAP_SIDE_NAMES[i] + ".png");
			}
			this.spaceSkybox = new Cubemap(skyboxSides);
		}

		this.skyboxShader = ShaderUtils.createShader("/csce_vis/hw_final/gen_skybox.vert", "/csce_vis/hw_final/gen_skybox.frag");
		this.skyboxShader.setUniform1i("spaceSkybox", 0);
		this.skybox = new Cubemap(GL_RGBA16F, GL_RGBA, GL_FLOAT, SKYBOX_RES);
		this.skyboxFramebuffer = new Framebuffer(SKYBOX_RES, SKYBOX_RES);
		Scene.skyboxes.put(WORLD_SCENE, this.skybox);

		this.addChildAdjWindow(new ObjectEditorWindow(this.options));

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

				for (Body b : this.impulse.getBodies()) {
					if (this.buoyancyBodies.get(b) == null) {
						this.buoyancyBodies.put(b, new BuoyancyBody(b));
					}
					this.buoyancyBodies.get(b).applyBuoyancy(dt);
				}

				this.impulse.update(dt);
			}
		}
		this.impulse.updateDisplayBodies();

		this.pic.update();

		this.skyboxTime += Main.getDeltaSeconds();
	}

	private void generateSkybox() {
		Vec3[][] camVectors = new Vec3[][] { { new Vec3(1, 0, 0), new Vec3(0, -1, 0) }, // -x
				{ new Vec3(-1, 0, 0), new Vec3(0, -1, 0) }, // +x
				{ new Vec3(0, 1, 0), new Vec3(0, 0, 1) }, // -y
				{ new Vec3(0, -1, 0), new Vec3(0, 0, -1) }, // +y
				{ new Vec3(0, 0, 1), new Vec3(0, -1, 0) }, // -z
				{ new Vec3(0, 0, -1), new Vec3(0, -1, 0) }, // +z
		};

		glViewport(0, 0, SKYBOX_RES, SKYBOX_RES);
		glEnable(GL_DEPTH_TEST);
		glDisable(GL_BLEND);
		glDisable(GL_CULL_FACE);

		Camera cubemapCamera = new Camera((float) Math.toRadians(90), 1f, 1f, 0.1f, 50f); // aspect ratio of 1
		cubemapCamera.setPos(new Vec3(0));

		for (int i = 0; i < 6; i++) {
			cubemapCamera.setFacing(camVectors[i][0]);
			cubemapCamera.setUp(camVectors[i][1]);
			this.skyboxShader.enable();
			this.skyboxShader.setUniformMat4("pr_matrix", cubemapCamera.getProjectionMatrix());
			this.skyboxShader.setUniformMat4("vw_matrix", cubemapCamera.getViewMatrix());
			this.skyboxShader.setUniform3f("camera_pos", this.pic.getPos());
			this.skyboxShader.setUniform3f("sun_dir", this.options.sunDir.normalize());
			this.skyboxShader.setUniform1f("t", this.skyboxTime);

			this.skyboxFramebuffer.bindTextureToBuffer(GL_COLOR_ATTACHMENT0, GL_TEXTURE_CUBE_MAP_POSITIVE_X + i, this.skybox.getID());
			this.skyboxFramebuffer.bind();
			glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
			this.spaceSkybox.bind(GL_TEXTURE0);
			SkyboxCube.skyboxCube.render();
		}
	}

	@Override
	protected void renderContent(Framebuffer outputBuffer) {
		this.generateSkybox();

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

		case GLFW.GLFW_KEY_P: {
			Body b = this.impulse.addAABB(new Vec3(0, 10, 0), new Vec3(30, 5, 30));
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
	private float waterDensity = 1.5f;
	private float dragCoeff = 0.5f;

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

		//TODO 
		// - more accurately determine displacement amount
		//   - currently, i'm just assuming each voxel is oriented facing upwards even after rotating, and
		//     that the water height is constant all throughout the voxel
		//   - having constant water height is fine, it's just making the voxel always face upwards is bad. 
		// - determine if cells are inside of shape
		// - adjust force based off of water normal if cell is not completely submerged
		// - drag shouldn't be proportional to volume, it should be towards surface area
		public void applyBuoyancy(float dt) {
			Vec3 vdim = bmax.sub(bmin).div(VOXEL_AMT);
			float voxel_vol = vdim.x * vdim.y * vdim.z;
			for (int x = 0; x < VOXEL_AMT; x++) {
				for (int y = 0; y < VOXEL_AMT; y++) {
					for (int z = 0; z < VOXEL_AMT; z++) {
						Vec3 vcenter = bmin.add(vdim.div(2.0f));
						vcenter.x += vdim.x * x;
						vcenter.y += vdim.y * y;
						vcenter.z += vdim.z * z;

						//transform vcenter to world space
						vcenter = MathUtils.quaternionRotateVec3(this.b.orient, vcenter);
						vcenter.addi(this.b.pos);

						//compute buoyancy force
						float water_height = queryWaterHeight(vcenter) - vcenter.y;
						float disp_vol = MathUtils.clamp(0, vdim.y, vdim.y / 2.0f + water_height) * vdim.x * vdim.z;
						float disp_mass = disp_vol * waterDensity;

						Vec3 force = impulse.getGravity().mul(-1);
						force.muli(disp_mass);
						this.b.applyImpulse(vcenter, force.mul(dt));

						//fake a lil drag. Scale drag by how much of voxel is under the water
						Vec3 body_pt_vel = this.b.calcBodyPtVel(vcenter);
						Vec3 drag = body_pt_vel.mul(-dragCoeff * disp_vol);
						this.b.applyImpulse(vcenter, drag.mul(dt));
					}
				}
			}
		}
	}

}
