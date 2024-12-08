package csce_vis.hw_final;

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
import java.util.ArrayList;
import java.util.HashMap;

import org.lwjgl.glfw.GLFW;

import lwjglengine.graphics.Cubemap;
import lwjglengine.graphics.Framebuffer;
import lwjglengine.graphics.Material;
import lwjglengine.graphics.Shader;
import lwjglengine.graphics.ShaderStorageBuffer;
import lwjglengine.graphics.Texture;
import lwjglengine.impulse3d.Body;
import lwjglengine.impulse3d.ImpulseScene;
import lwjglengine.impulse3d.shape.Shape;
import lwjglengine.main.Main;
import lwjglengine.model.Model;
import lwjglengine.model.ModelInstance;
import lwjglengine.model.ModelTransform;
import lwjglengine.model.VertexArray;
import lwjglengine.player.PlayerInputController;
import lwjglengine.scene.DirLight;
import lwjglengine.scene.Light;
import lwjglengine.scene.Scene;
import lwjglengine.util.ShaderUtils;
import lwjglengine.window.AdjustableWindow;
import lwjglengine.window.ObjectEditorWindow;
import lwjglengine.window.TextureViewerWindow;
import lwjglengine.window.Window;
import myutils.file.FileUtils;
import myutils.math.MathUtils;
import myutils.math.Quaternion;
import myutils.math.Vec2;
import myutils.math.Vec3;
import myutils.math.Vec4;

public class HWFWindow extends Window {

	//TODO 
	// - improve spectra generation 

	private final int WORLD_SCENE = Scene.generateScene();

	private HWFScreen worldScreen;

	private PlayerInputController pic;

	private WaveCascade cascadeLong, cascadeMed, cascadeShort;

	private Options options = new Options();
	private float time = 0;

	private ImpulseScene impulse;
	private ArrayList<BuoyancyBody> buoyancyBodies;

	private static final int MAX_BUOYANCY_BODIES = 100;

	//when querying, should have vec3s with stride of 4 floats. 
	//result will be of form height, {nx, ny, nz}
	private ShaderStorageBuffer heightQuerySSBO;
	private Shader queryHeightShader;

	public HWFWindow(int xOffset, int yOffset, int width, int height, Window parentWindow) {
		super(xOffset, yOffset, width, height, parentWindow);
		this.init();
	}

	private void init() {
		// USER CONTROLS
		this.setLockCursorOnSelect(true);
		this.setUnlockCursorOnEscPressed(true);
		this.setDeselectOnEscPressed(true);

		// SKYBOX INITIALIZATION
		BufferedImage[] skyboxSides = new BufferedImage[6];
		String skyboxDir = "/res/skybox/lake/";
		for (int i = 0; i < 6; i++) {
			skyboxSides[i] = FileUtils.loadImageRelative(skyboxDir + Cubemap.CUBEMAP_SIDE_NAMES[i] + ".jpg");
		}
		Cubemap skybox = new Cubemap(skyboxSides);
		Scene.skyboxes.put(WORLD_SCENE, skybox);

		// INITIALIZE SCREEN
		this.worldScreen = new HWFScreen();
		this.worldScreen.setWorldScene(WORLD_SCENE);

		this.pic = new PlayerInputController(new Vec3(0, 1, 0));
		this.pic.setAcceptPlayerInputs(false);

		// SET CAMERA POS
		this.worldScreen.getCamera().setFacing(this.pic.getFacing());
		this.worldScreen.getCamera().setPos(this.pic.getPos().add(new Vec3(0, 3, 0)));

		// ADD SUN & LIGHTS
		DirLight sun = new DirLight(new Vec3(0.3, -0.6f, 1), new Vec3(1), 0.4f);
		Light.addLight(WORLD_SCENE, sun);
		//		this.worldScreen.setSun(sun);

		// DEBUGGING TOOLS IF DESIRED
		//windows to look at water textures
		//AdjustableWindow waterHeightViewer = new AdjustableWindow("Water Height Map", new TextureViewerWindow(this.worldScreen.getWaterHeightMap()), this);
		//AdjustableWindow waterNormalViewer = new AdjustableWindow("Water Normal Map", new TextureViewerWindow(this.worldScreen.getWaterNormalMap()), this);
		//control panel for the water
		//AdjustableWindow waterAttributesPanel = new AdjustableWindow("Water Attributes", new ObjectEditorWindow(this.worldScreen.getWaterAttributes()), this);

		float omega_0 = 0.5f;
		float omega_1 = 4f;
		float omega_2 = 12f;
		float omega_3 = 25f;

		this.cascadeLong = new WaveCascade(247, omega_0, omega_1, this.options);
		this.cascadeMed = new WaveCascade(37, omega_1, omega_2, this.options);
		this.cascadeShort = new WaveCascade(5, omega_2, omega_3, this.options);

		this.worldScreen.options = this.options;
		this.worldScreen.generateSkybox();

		this.worldScreen.cascadeLong = this.cascadeLong;
		this.worldScreen.cascadeMed = this.cascadeMed;
		this.worldScreen.cascadeShort = this.cascadeShort;

		//		this.addChildAdjWindow(new TextureViewerWindow(this.cascadeLong.gaussianNoiseTexture, "Gaussian Noise"));
		//		this.addChildAdjWindow(new TextureViewerWindow(this.baseSpectraTexture, "Base Spectra"));
		//		this.addChildAdjWindow(new TextureViewerWindow(this.waveInfoTexture, "Wave Info"));
		//
		//		this.addChildAdjWindow(new TextureViewerWindow(this.Dx_Dz, "Dx_Dz"));
		//		this.addChildAdjWindow(new TextureViewerWindow(this.Dyx_Dyz, "Dyx_Dyz"));

		//		this.addChildAdjWindow(new TextureViewerWindow(this.cascadeLong.dispTexture, "Displacement Long"));
		//		this.addChildAdjWindow(new TextureViewerWindow(this.cascadeLong.derivativeTexture, "Derivatives Long"));

		this.addChildAdjWindow(new ObjectEditorWindow(this.options));

		this.impulse = new ImpulseScene(WORLD_SCENE);
		this.buoyancyBodies = new ArrayList<>();

		this.heightQuerySSBO = new ShaderStorageBuffer();
		this.heightQuerySSBO.setUsage(GL_DYNAMIC_DRAW);
		this.heightQuerySSBO.setSize(MAX_BUOYANCY_BODIES * VOXEL_AMT * VOXEL_AMT * VOXEL_AMT * 4 * 4);

		this.queryHeightShader = ShaderUtils.createShader("/csce_vis/hw_final/query_height.compute", GL_COMPUTE_SHADER);
		this.queryHeightShader.setUniform1i("dispTextureLong", 0);
		this.queryHeightShader.setUniform1i("derivativeTextureLong", 1);
		this.queryHeightShader.setUniform1i("dispTextureMed", 2);
		this.queryHeightShader.setUniform1i("derivativeTextureMed", 3);
		this.queryHeightShader.setUniform1i("dispTextureShort", 4);
		this.queryHeightShader.setUniform1i("derivativeTextureShort", 5);

		this._resize();
	}

	private void generateSpectrum() {
		this.cascadeLong.generateSpectrum();
		this.cascadeMed.generateSpectrum();
		this.cascadeShort.generateSpectrum();
	}

	private void resetImpulseScene() {
		this.impulse.clearScene();
	}

	private void addBuoyancyBody(Body b) {
		this.buoyancyBodies.add(new BuoyancyBody(b));
	}

	@Override
	protected void _kill() {
		this.worldScreen.kill();
		this.impulse.kill();
		Scene.removeScene(WORLD_SCENE);

		this.cascadeLong.kill();
		this.cascadeMed.kill();
		this.cascadeShort.kill();
	}

	@Override
	protected void _resize() {
		this.worldScreen.setScreenDimensions(this.getWidth(), this.getHeight());
	}

	@Override
	public String getDefaultTitle() {
		return "FFT Waves & Buoyancy";
	}

	private void queryWaterHeight() {
		//write query positions into buffer
		{
			int ptr = 0;
			float[] buf = new float[MAX_BUOYANCY_BODIES * VOXEL_AMT * VOXEL_AMT * VOXEL_AMT * 4];
			for (int i = 0; i < this.buoyancyBodies.size(); i++) {
				BuoyancyBody b = this.buoyancyBodies.get(i);
				b.writeQueryToBuffer(buf, ptr);
				ptr += VOXEL_AMT * VOXEL_AMT * VOXEL_AMT * 4;
			}
			this.heightQuerySSBO.setSubData(buf, 0);
		}

		//run query
		{
			this.queryHeightShader.enable();
			this.queryHeightShader.setUniform1f("length_scale_long", this.cascadeLong.lengthScale);
			this.queryHeightShader.setUniform1f("length_scale_med", this.cascadeMed.lengthScale);
			this.queryHeightShader.setUniform1f("length_scale_short", this.cascadeShort.lengthScale);

			this.queryHeightShader.setUniform1f("cascadeScale0", this.options.getCascadeScale0());
			this.queryHeightShader.setUniform1f("cascadeScale1", this.options.getCascadeScale1());
			this.queryHeightShader.setUniform1f("cascadeScale2", this.options.getCascadeScale2());

			this.cascadeLong.dispTexture.bind(GL_TEXTURE0);
			this.cascadeLong.derivativeTexture.bind(GL_TEXTURE1);
			this.cascadeMed.dispTexture.bind(GL_TEXTURE2);
			this.cascadeMed.derivativeTexture.bind(GL_TEXTURE3);
			this.cascadeShort.dispTexture.bind(GL_TEXTURE4);
			this.cascadeShort.derivativeTexture.bind(GL_TEXTURE5);

			this.heightQuerySSBO.bindToBase(0);
			glDispatchCompute(MAX_BUOYANCY_BODIES * VOXEL_AMT * VOXEL_AMT * VOXEL_AMT, 1, 1);
		}

		//read result back out
		{
			int ptr = 0;
			float[] buf = new float[MAX_BUOYANCY_BODIES * VOXEL_AMT * VOXEL_AMT * VOXEL_AMT * 4];
			this.heightQuerySSBO.getSubData(buf, 0);
			for (int i = 0; i < this.buoyancyBodies.size(); i++) {
				BuoyancyBody b = this.buoyancyBodies.get(i);
				b.readResultFromBuffer(buf, ptr);
				ptr += VOXEL_AMT * VOXEL_AMT * VOXEL_AMT * 4;
			}
		}
	}

	@Override
	protected void _update() {
		if (!this.options.pauseTime) {
			float dt = Main.getDeltaSeconds();
			this.time += Main.getDeltaSeconds();

			//query relevant information
			this.queryWaterHeight();

			int itercnt = 4;
			for (int i = 0; i < itercnt; i++) {
				for (BuoyancyBody b : this.buoyancyBodies) {
					b.applyBuoyancy(dt / itercnt);
				}
				this.impulse.update(dt / itercnt);
			}
		}
		this.impulse.updateDisplayBodies();

		this.pic.update();

		//update camera position
		this.worldScreen.getCamera().setFacing(this.pic.getFacing());
		this.worldScreen.getCamera().setPos(this.pic.getPos());
	}

	@Override
	protected void renderContent(Framebuffer outputBuffer) {
		if (!this.options.pauseTime) {
			this.cascadeLong.update(this.time);
			this.cascadeMed.update(this.time);
			this.cascadeShort.update(this.time);
		}

		this.worldScreen.render(outputBuffer);
	}

	@Override
	protected void renderOverlay(Framebuffer outputBuffer) {
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
	}

	@Override
	protected void subtreeDeselected() {
	}

	@Override
	protected void _mousePressed(int button) {
	}

	@Override
	protected void _mouseReleased(int button) {
	}

	@Override
	protected void _mouseScrolled(float wheelOffset, float smoothOffset) {
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
			this.addBuoyancyBody(b);
			break;
		}

		case GLFW.GLFW_KEY_E: {
			Body b = this.impulse.addAABB(this.pic.getPos().add(this.pic.getFacing().mul(5)), new Vec3(3));
			b.angvel = MathUtils.randomUnitDir3D().mul(10);
			b.vel = this.pic.getFacing().mul(50);
			this.addBuoyancyBody(b);
			break;
		}

		case GLFW.GLFW_KEY_C: {
			Body b = this.impulse.addCapsule(this.pic.getPos().add(this.pic.getFacing().mul(5)), 3, 6);
			b.angvel = MathUtils.randomUnitDir3D().mul(10);
			b.vel = this.pic.getFacing().mul(50);
			this.addBuoyancyBody(b);
			break;
		}

		case GLFW.GLFW_KEY_Z: {
			Body b = this.impulse.addSphere(this.pic.getPos().add(this.pic.getFacing().mul(5)), 3);
			b.vel = this.pic.getFacing().mul(50);
			this.addBuoyancyBody(b);
			break;
		}

		case GLFW.GLFW_KEY_P: {
			Body b = this.impulse.addAABB(new Vec3(0, 10, 0), new Vec3(30, 5, 30));
			this.addBuoyancyBody(b);
			break;
		}
		}
	}

	@Override
	protected void _keyReleased(int key) {
	}

	private static final int VOXEL_AMT = 5;
	private float waterDensity = 2f;
	private float dragCoeff = 0.5f;

	class BuoyancyBody {
		public Body b;
		public Vec3 bmin, bmax; //bounding box dimensions
		public float[][][] queryHeights;

		public BuoyancyBody(Body _b) {
			this.b = _b;

			//figure out bounding box
			Shape s = this.b.shape;
			lwjglengine.impulse3d.bvh.KDOP bb = s.calcBoundingBox(Quaternion.identity(), new Vec3(0));
			this.bmin = new Vec3(bb.bmin[0], bb.bmin[1], bb.bmin[2]);
			this.bmax = new Vec3(bb.bmax[0], bb.bmax[1], bb.bmax[2]);
			this.queryHeights = new float[VOXEL_AMT][VOXEL_AMT][VOXEL_AMT];
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
						float water_height = queryHeights[x][y][z] - vcenter.y;
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

		public void writeQueryToBuffer(float[] buf, int ptr) {
			Vec3 vdim = bmax.sub(bmin).div(VOXEL_AMT);
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

						buf[ptr++] = vcenter.x;
						buf[ptr++] = vcenter.y;
						buf[ptr++] = vcenter.z;
						buf[ptr++] = 0;
					}
				}
			}
		}

		public void readResultFromBuffer(float[] buf, int ptr) {
			for (int x = 0; x < VOXEL_AMT; x++) {
				for (int y = 0; y < VOXEL_AMT; y++) {
					for (int z = 0; z < VOXEL_AMT; z++) {
						this.queryHeights[x][y][z] = buf[ptr++];
						ptr++;
						ptr++;
						ptr++;
					}
				}
			}
		}
	}

	public class Options {
		private float waterDepth = 100; //height of water in meters
		private float windSpeed = 0.5f; //avg wind speed (m/s)
		private float fetch = 100000.0f; //fetch, length of area over which wind is acting on water
		private Vec2 windDir = new Vec2(1, 0.6).normalize();
		private float spectraMultiplier = 1f; //hack for debugging
		private float lambda = 1f;

		private float cascadeScale0 = 1.2f;
		private float cascadeScale1 = 0.6f;
		private float cascadeScale2 = 0.3f;

		private boolean pauseTime = false;
		private boolean renderNormals = false;
		private boolean renderReflection = false;

		private float sunIrradianceMult = 0.3f;
		private float environmentLightStrength = 0.5f;

		private Vec3 sunDir = new Vec3(1).normalize();
		private Vec3 sunIrradianceBase = new Vec3(1.0f, 0.7f, 0.4f);
		private Vec3 scatterColor = new Vec3(0.016, 0.0736, 0.16);
		private Vec3 bubbleColor = new Vec3(0, 0.02, 0.05);
		private float bubbleDensity = 10;
		private float wavePeakScatterStrength = 5;
		private float scatterStrength = 10;
		private float scatterShadowStrength = 5;
		private float heightModifier = 10;

		private float swell = 2.0f;
		private float spreadBlend = 0.9f;

		public float getWaterDepth() {
			return waterDepth;
		}

		public void setWaterDepth(float waterDepth) {
			this.waterDepth = waterDepth;
			generateSpectrum();
		}

		public float getWindSpeed() {
			return windSpeed;
		}

		public void setWindSpeed(float windSpeed) {
			this.windSpeed = windSpeed;
			generateSpectrum();
		}

		public float getFetch() {
			return fetch;
		}

		public void setFetch(float fetch) {
			this.fetch = fetch;
			generateSpectrum();
		}

		public Vec2 getWindDir() {
			return windDir;
		}

		public void setWindDir(Vec2 windDir) {
			this.windDir = windDir;
			generateSpectrum();
		}

		public float getSpectraMultiplier() {
			return spectraMultiplier;
		}

		public void setSpectraMultiplier(float spectraMultiplier) {
			this.spectraMultiplier = spectraMultiplier;
			generateSpectrum();
		}

		public float getLambda() {
			return lambda;
		}

		public void setLambda(float lambda) {
			this.lambda = lambda;
		}

		public float getCascadeScale0() {
			return cascadeScale0;
		}

		public void setCascadeScale0(float cascadeScale0) {
			this.cascadeScale0 = cascadeScale0;
		}

		public float getCascadeScale1() {
			return cascadeScale1;
		}

		public void setCascadeScale1(float cascadeScale1) {
			this.cascadeScale1 = cascadeScale1;
		}

		public float getCascadeScale2() {
			return cascadeScale2;
		}

		public void setCascadeScale2(float cascadeScale2) {
			this.cascadeScale2 = cascadeScale2;
		}

		public boolean getPauseTime() {
			return pauseTime;
		}

		public void setPauseTime(boolean pauseTime) {
			this.pauseTime = pauseTime;
		}

		public boolean getRenderNormals() {
			return renderNormals;
		}

		public void setRenderNormals(boolean renderNormals) {
			this.renderNormals = renderNormals;
		}

		public float getSunIrradianceMult() {
			return sunIrradianceMult;
		}

		public void setSunIrradianceMult(float sunIrradianceMult) {
			this.sunIrradianceMult = sunIrradianceMult;
		}

		public float getEnvironmentLightStrength() {
			return environmentLightStrength;
		}

		public void setEnvironmentLightStrength(float environmentLightStrength) {
			this.environmentLightStrength = environmentLightStrength;
		}

		public boolean getRenderReflection() {
			return renderReflection;
		}

		public void setRenderReflection(boolean renderReflection) {
			this.renderReflection = renderReflection;
		}

		public Vec3 getSunIrradianceBase() {
			return sunIrradianceBase;
		}

		public void setSunIrradianceBase(Vec3 sunIrradianceBase) {
			this.sunIrradianceBase = sunIrradianceBase;
		}

		public Vec3 getScatterColor() {
			return scatterColor;
		}

		public void setScatterColor(Vec3 scatterColor) {
			this.scatterColor = scatterColor;
		}

		public Vec3 getBubbleColor() {
			return bubbleColor;
		}

		public void setBubbleColor(Vec3 bubbleColor) {
			this.bubbleColor = bubbleColor;
		}

		public float getBubbleDensity() {
			return bubbleDensity;
		}

		public void setBubbleDensity(float bubbleDensity) {
			this.bubbleDensity = bubbleDensity;
		}

		public float getWavePeakScatterStrength() {
			return wavePeakScatterStrength;
		}

		public void setWavePeakScatterStrength(float wavePeakScatterStrength) {
			this.wavePeakScatterStrength = wavePeakScatterStrength;
		}

		public float getScatterStrength() {
			return scatterStrength;
		}

		public void setScatterStrength(float scatterStrength) {
			this.scatterStrength = scatterStrength;
		}

		public float getScatterShadowStrength() {
			return scatterShadowStrength;
		}

		public void setScatterShadowStrength(float scatterShadowStrength) {
			this.scatterShadowStrength = scatterShadowStrength;
		}

		public float getHeightModifier() {
			return heightModifier;
		}

		public void setHeightModifier(float heightModifier) {
			this.heightModifier = heightModifier;
		}

		public Vec3 getSunDir() {
			return sunDir;
		}

		public void setSunDir(Vec3 sunDir) {
			this.sunDir = sunDir;
			worldScreen.generateSkybox();
		}

		public float getSwell() {
			return swell;
		}

		public void setSwell(float swell) {
			this.swell = swell;
			generateSpectrum();
		}

		public float getSpreadBlend() {
			return spreadBlend;
		}

		public void setSpreadBlend(float spreadBlend) {
			this.spreadBlend = spreadBlend;
			generateSpectrum();
		}
	}
}
