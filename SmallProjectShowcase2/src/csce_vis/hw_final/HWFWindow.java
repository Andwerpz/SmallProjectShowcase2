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

import org.lwjgl.glfw.GLFW;

import lwjglengine.graphics.Cubemap;
import lwjglengine.graphics.Framebuffer;
import lwjglengine.graphics.Material;
import lwjglengine.graphics.Shader;
import lwjglengine.graphics.Texture;
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
import myutils.math.Vec2;
import myutils.math.Vec3;
import myutils.math.Vec4;

public class HWFWindow extends Window {

	//TODO 
	// - improve spectra generation 

	//tentative cascade list:
	// - 1
	//   - length_scale = 128
	//   - omega_min = 0.5
	//   - omega_max = 4
	// - 2
	//   - length_scale = 32
	// - 3
	//   - length_scale = 8

	private final int WORLD_SCENE = Scene.generateScene();

	private HWFScreen worldScreen;

	private PlayerInputController pic;

	private WaveCascade cascadeLong, cascadeMed, cascadeShort;

	private Options options = new Options();
	private float time = 0;

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
	}

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
		float omega_2 = 8f;
		float omega_3 = 20f;

		this.cascadeLong = new WaveCascade(100, omega_0, omega_1, this.options);
		this.cascadeMed = new WaveCascade(27, omega_1, omega_2, this.options);
		this.cascadeShort = new WaveCascade(7, omega_2, omega_3, this.options);

		this.worldScreen.options = this.options;

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

		Shader s = ShaderUtils.createShader("/csce_vis/hw_final/fft.compute", GL_COMPUTE_SHADER);

		int resolution = 256;
		float[] data = new float[resolution * resolution * 4];
		data[resolution * 127 * 4 + resolution / 2 * 4 - 64 + 4] = 1;
		int textureID = glGenTextures(); //create texture handle
		glBindTexture(GL_TEXTURE_2D, textureID); //set as active texture
		glTexStorage2D(GL_TEXTURE_2D, 1, GL_RGBA32F, resolution, resolution); //allocate storage for texture
		glTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, resolution, resolution, GL_RGBA, GL_FLOAT, data); //initialize 0th mipmap layer of texture
		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
		Texture in = new Texture(textureID);

		glBindImageTexture(0, in.getID(), 0, false, 0, GL_READ_WRITE, GL_RGBA32F);

		s.enable();
		apply2DFFT(s, in, 256, true);

		this.addChildAdjWindow(new TextureViewerWindow(in));

		//		System.out.println(out.getID() + " " + this.worldScreen.getWaterNormalMap().getID());
		System.out.println("done!");

		this._resize();
	}

	private void apply2DFFT(Shader fftShader, Texture t, int resolution, boolean invert) {
		fftShader.setUniform1i("invert", invert ? 1 : 0);
		glBindImageTexture(0, t.getID(), 0, false, 0, GL_READ_WRITE, GL_RGBA32F);

		fftShader.setUniform1i("workRow", 1);
		glDispatchCompute(resolution, 1, 1);
		glMemoryBarrier(GL_SHADER_IMAGE_ACCESS_BARRIER_BIT);
		fftShader.setUniform1i("workRow", 0);
		glDispatchCompute(resolution, 1, 1);
		glMemoryBarrier(GL_SHADER_IMAGE_ACCESS_BARRIER_BIT);
	}

	private void generateSpectrum() {
		this.cascadeLong.generateSpectrum();
		this.cascadeMed.generateSpectrum();
		this.cascadeShort.generateSpectrum();
	}

	@Override
	protected void _kill() {
		this.worldScreen.kill();

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

	@Override
	protected void _update() {
		if (!this.options.pauseTime) {
			this.time += Main.getDeltaSeconds();
		}

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

		this.worldScreen.setWorldScene(WORLD_SCENE);
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
	}

	@Override
	protected void _keyReleased(int key) {
	}
}
