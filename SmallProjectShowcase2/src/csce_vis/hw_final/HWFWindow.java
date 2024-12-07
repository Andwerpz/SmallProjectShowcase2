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

	private static final int WATER_RESOLUTION = 128;

	private final int WORLD_SCENE = Scene.generateScene();

	private HWFScreen worldScreen;

	private PlayerInputController pic;

	private Texture gaussianNoiseTexture;

	private Shader generateSpectraShader;
	private Texture baseSpectraTexture;
	private Texture waveInfoTexture;

	private Shader evolveSpectraShader;
	private Texture Dx_Dz, Dy_Dxz, Dyx_Dyz, Dxx_Dzz;

	private Shader fftShader, waveTexMergerShader;
	private Texture dispTexture, normalTexture;

	private Options options = new Options();

	private float time = 0;

	public class Options {
		private float waterDepth = 100; //height of water in meters
		private float windSpeed = 0.5f; //avg wind speed (m/s)
		private float fetch = 100000.0f; //fetch, length of area over which wind is acting on water
		private Vec2 windDir = new Vec2(1, 0);
		private float spectraMultiplier = 1f; //hack for debugging
		private float lambda = 0.5f;

		public float getWaterDepth() {
			return waterDepth;
		}

		public void setWaterDepth(float waterDepth) {
			this.waterDepth = waterDepth;
			generateSpectra();
		}

		public float getWindSpeed() {
			return windSpeed;
		}

		public void setWindSpeed(float windSpeed) {
			this.windSpeed = windSpeed;
			generateSpectra();
		}

		public float getFetch() {
			return fetch;
		}

		public void setFetch(float fetch) {
			this.fetch = fetch;
			generateSpectra();
		}

		public Vec2 getWindDir() {
			return windDir;
		}

		public void setWindDir(Vec2 windDir) {
			this.windDir = windDir;
			generateSpectra();
		}

		public float getSpectraMultiplier() {
			return spectraMultiplier;
		}

		public void setSpectraMultiplier(float spectraMultiplier) {
			this.spectraMultiplier = spectraMultiplier;
			generateSpectra();
		}

		public float getLambda() {
			return lambda;
		}

		public void setLambda(float lambda) {
			this.lambda = lambda;
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
		this.worldScreen.renderSkybox(true);

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

		this.gaussianNoiseTexture = this.generateGaussianNoiseTexture();
		glMemoryBarrier(GL_SHADER_IMAGE_ACCESS_BARRIER_BIT);

		this.generateSpectraShader = ShaderUtils.createShader("/csce_vis/hw_final/gen_spectra.compute", GL_COMPUTE_SHADER);
		this.baseSpectraTexture = new Texture(WATER_RESOLUTION, WATER_RESOLUTION, GL_RGBA32F, GL_RGBA, GL_FLOAT, GL_NEAREST);
		this.waveInfoTexture = new Texture(WATER_RESOLUTION, WATER_RESOLUTION, GL_RGBA32F, GL_RGBA, GL_FLOAT, GL_NEAREST);

		this.evolveSpectraShader = ShaderUtils.createShader("/csce_vis/hw_final/evolve_spectra.compute", GL_COMPUTE_SHADER);
		this.Dx_Dz = new Texture(WATER_RESOLUTION, WATER_RESOLUTION, GL_RGBA32F, GL_RGBA, GL_FLOAT, GL_NEAREST);
		this.Dy_Dxz = new Texture(WATER_RESOLUTION, WATER_RESOLUTION, GL_RGBA32F, GL_RGBA, GL_FLOAT, GL_NEAREST);
		this.Dyx_Dyz = new Texture(WATER_RESOLUTION, WATER_RESOLUTION, GL_RGBA32F, GL_RGBA, GL_FLOAT, GL_NEAREST);
		this.Dxx_Dzz = new Texture(WATER_RESOLUTION, WATER_RESOLUTION, GL_RGBA32F, GL_RGBA, GL_FLOAT, GL_NEAREST);

		this.fftShader = ShaderUtils.createShader("/csce_vis/hw_final/fft.compute", GL_COMPUTE_SHADER);
		this.waveTexMergerShader = ShaderUtils.createShader("/csce_vis/hw_final/waves_tex_merger.compute", GL_COMPUTE_SHADER);
		this.dispTexture = new Texture(WATER_RESOLUTION, WATER_RESOLUTION, GL_RGBA32F, GL_RGBA, GL_FLOAT, GL_LINEAR_MIPMAP_LINEAR, GL_LINEAR, 5, null);
		this.normalTexture = new Texture(WATER_RESOLUTION, WATER_RESOLUTION, GL_RGBA32F, GL_RGBA, GL_FLOAT, GL_LINEAR_MIPMAP_LINEAR, GL_LINEAR, 5, null);

		this.generateSpectra();

		this.worldScreen.dispTexture = this.dispTexture;
		this.worldScreen.normalTexture = this.normalTexture;

		//		this.addChildAdjWindow(new TextureViewerWindow(this.gaussianNoiseTexture, "Gaussian Noise"));
		//		this.addChildAdjWindow(new TextureViewerWindow(this.baseSpectraTexture, "Base Spectra"));
		//		this.addChildAdjWindow(new TextureViewerWindow(this.waveInfoTexture, "Wave Info"));
		//
		//		this.addChildAdjWindow(new TextureViewerWindow(this.Dx_Dz, "Dx_Dz"));
		//		this.addChildAdjWindow(new TextureViewerWindow(this.Dyx_Dyz, "Dyx_Dyz"));

		this.addChildAdjWindow(new TextureViewerWindow(this.dispTexture, "Displacement"));
		this.addChildAdjWindow(new TextureViewerWindow(this.normalTexture, "Normals"));

		this.addChildAdjWindow(new ObjectEditorWindow(this.options));

		this._resize();
	}

	private Texture generateGaussianNoiseTexture() {
		float[] data = new float[WATER_RESOLUTION * WATER_RESOLUTION * 4];
		for (int i = 0; i < data.length; i++) {
			float u1 = (float) Math.random();
			float u2 = (float) Math.random();
			data[i] = (float) (Math.sqrt(-2.0 * Math.log(u1)) * Math.cos(Math.PI * 2.0 * u2));
		}

		int textureID = glGenTextures(); //create texture handle
		glBindTexture(GL_TEXTURE_2D, textureID); //set as active texture
		glTexStorage2D(GL_TEXTURE_2D, 1, GL_RGBA32F, WATER_RESOLUTION, WATER_RESOLUTION); //allocate storage for texture
		if (data != null) {
			glTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, WATER_RESOLUTION, WATER_RESOLUTION, GL_RGBA, GL_FLOAT, data); //initialize 0th mipmap layer of texture
		}

		//set interpolation filters
		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
		//set border behaviour
		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_REPEAT);
		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_REPEAT);
		glBindTexture(GL_TEXTURE_2D, 0);

		return new Texture(textureID);
	}

	private void generateSpectra() {
		this.generateSpectraShader.enable();
		this.generateSpectraShader.setUniform1i("spectra_sz", WATER_RESOLUTION);
		this.generateSpectraShader.setUniform2f("wind_dir", this.options.windDir.normalize());
		this.generateSpectraShader.setUniform1f("h", this.options.waterDepth);
		this.generateSpectraShader.setUniform1f("U", this.options.windSpeed);
		this.generateSpectraShader.setUniform1f("F", this.options.fetch);
		this.generateSpectraShader.setUniform1f("omega_p", (float) (22.0 * Math.pow(9.81 * 9.81 / (this.options.windSpeed * this.options.fetch), 1.0 / 3.0)));
		this.generateSpectraShader.setUniform1f("multiplier", this.options.spectraMultiplier);
		glBindImageTexture(0, this.baseSpectraTexture.getID(), 0, false, 0, GL_WRITE_ONLY, GL_RGBA32F);
		glBindImageTexture(1, this.gaussianNoiseTexture.getID(), 0, false, 0, GL_READ_ONLY, GL_RGBA32F);
		glBindImageTexture(2, this.waveInfoTexture.getID(), 0, false, 0, GL_WRITE_ONLY, GL_RGBA32F);
		glDispatchCompute(WATER_RESOLUTION, WATER_RESOLUTION, 1);
		glMemoryBarrier(GL_SHADER_IMAGE_ACCESS_BARRIER_BIT);
	}

	@Override
	protected void _kill() {
		this.worldScreen.kill();

		Scene.removeScene(WORLD_SCENE);

		this.generateSpectraShader.kill();
		this.evolveSpectraShader.kill();
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
		this.time += Main.getDeltaSeconds();

		this.pic.update();

		//update camera position
		this.worldScreen.getCamera().setFacing(this.pic.getFacing());
		this.worldScreen.getCamera().setPos(this.pic.getPos());
	}

	@Override
	protected void renderContent(Framebuffer outputBuffer) {
		// -- generate evolved spectra --
		{
			this.evolveSpectraShader.enable();
			this.evolveSpectraShader.setUniform1i("spectra_sz", WATER_RESOLUTION);
			this.evolveSpectraShader.setUniform1f("t", this.time);
			glBindImageTexture(0, this.baseSpectraTexture.getID(), 0, false, 0, GL_READ_ONLY, GL_RGBA32F);
			glBindImageTexture(1, this.waveInfoTexture.getID(), 0, false, 0, GL_READ_ONLY, GL_RGBA32F);

			glBindImageTexture(2, this.Dx_Dz.getID(), 0, false, 0, GL_WRITE_ONLY, GL_RGBA32F);
			glBindImageTexture(3, this.Dy_Dxz.getID(), 0, false, 0, GL_WRITE_ONLY, GL_RGBA32F);
			glBindImageTexture(4, this.Dyx_Dyz.getID(), 0, false, 0, GL_WRITE_ONLY, GL_RGBA32F);
			glBindImageTexture(5, this.Dxx_Dzz.getID(), 0, false, 0, GL_WRITE_ONLY, GL_RGBA32F);
			glDispatchCompute(WATER_RESOLUTION, WATER_RESOLUTION, 1);
			glMemoryBarrier(GL_SHADER_IMAGE_ACCESS_BARRIER_BIT);
		}

		// -- apply fft --
		if (true) {
			Texture[] to_fft = new Texture[] { this.Dx_Dz, this.Dy_Dxz, this.Dyx_Dyz, this.Dxx_Dzz };
			//			Texture[] to_fft = new Texture[] { this.Dx_Dz };
			this.fftShader.enable();
			this.fftShader.setUniform1i("invert", 1);
			for (Texture t : to_fft) {
				this.apply2DFFT(t, WATER_RESOLUTION);
			}
		}

		// -- compute displacement and normals --
		if (true) {
			this.waveTexMergerShader.enable();
			this.waveTexMergerShader.setUniform1f("lambda", this.options.lambda);
			glBindImageTexture(0, this.Dx_Dz.getID(), 0, false, 0, GL_READ_ONLY, GL_RGBA32F);
			glBindImageTexture(1, this.Dy_Dxz.getID(), 0, false, 0, GL_READ_ONLY, GL_RGBA32F);
			glBindImageTexture(2, this.Dyx_Dyz.getID(), 0, false, 0, GL_READ_ONLY, GL_RGBA32F);
			glBindImageTexture(3, this.Dxx_Dzz.getID(), 0, false, 0, GL_READ_ONLY, GL_RGBA32F);

			glBindImageTexture(4, this.dispTexture.getID(), 0, false, 0, GL_WRITE_ONLY, GL_RGBA32F);
			glBindImageTexture(5, this.normalTexture.getID(), 0, false, 0, GL_WRITE_ONLY, GL_RGBA32F);
			glDispatchCompute(WATER_RESOLUTION, WATER_RESOLUTION, 1);
			glMemoryBarrier(GL_SHADER_IMAGE_ACCESS_BARRIER_BIT);

			//generate mipmaps for normal texture
			this.normalTexture.bind();
			glGenerateMipmap(GL_TEXTURE_2D);
		}

		this.worldScreen.setWorldScene(WORLD_SCENE);
		this.worldScreen.render(outputBuffer);
	}

	private void apply2DFFT(Texture t, int resolution) {
		glBindImageTexture(0, t.getID(), 0, false, 0, GL_READ_WRITE, GL_RGBA32F);
		glDispatchCompute(1, 1, 1);

		this.fftShader.setUniform1i("workRow", 1);
		glDispatchCompute(resolution, 1, 1);
		glMemoryBarrier(GL_SHADER_IMAGE_ACCESS_BARRIER_BIT);
		this.fftShader.setUniform1i("workRow", 0);
		glDispatchCompute(resolution, 1, 1);
		glMemoryBarrier(GL_SHADER_IMAGE_ACCESS_BARRIER_BIT);
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
