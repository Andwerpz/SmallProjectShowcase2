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

	private static final int WATER_RESOLUTION = 256;
	private Model waterModel;

	private final int WORLD_SCENE = Scene.generateScene();

	private HWFScreen worldScreen;

	private PlayerInputController pic;

	private Texture gaussianNoiseTexture;

	private Shader generateSpectraShader;
	private Texture baseSpectraTexture;
	private Texture waveInfoTexture;

	private Shader evolveSpectraShader;
	private Texture dispX, dispY, dispZ;
	private Texture dispX_dx, dispY_dx, dispZ_dx;
	private Texture dispX_dz, dispY_dz, dispZ_dz;

	private Shader fftShader;

	private Texture evolvedSpectraTexture;

	private Options options = new Options();

	private float time = 0;

	public class Options {
		private float waterDepth = 1000; //height of water in meters
		private float windSpeed = 25.0f; //avg wind speed (m/s)
		private float fetch = 250.0f; //fetch, length of area over which wind is acting on water
		private Vec2 windDir = new Vec2(1, 0);
		private float spectraMultiplier = 1f; //hack for debugging

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

		// CREATE WATER MODEL (TRIANGLE INITIALIZATION: THE POINTS & THE INDICES OF THE POINTS THAT MAKE UP EACH TRIANGLE)
		{
			int[][] vertexIndices = new int[WATER_RESOLUTION][WATER_RESOLUTION];
			int ptr = 0;
			ArrayList<Float> vertices = new ArrayList<>();
			ArrayList<Float> uvs = new ArrayList<>();
			ArrayList<Integer> indices = new ArrayList<>();

			for (int i = 0; i < WATER_RESOLUTION; i++) {
				for (int j = 0; j < WATER_RESOLUTION; j++) {
					vertices.add((float) i - (WATER_RESOLUTION - 1) / 2);
					vertices.add((float) 0);
					vertices.add((float) j - (WATER_RESOLUTION - 1) / 2);
					vertexIndices[i][j] = ptr++;

					uvs.add((float) i / (WATER_RESOLUTION - 1));
					uvs.add((float) j / (WATER_RESOLUTION - 1));
				}
			}

			for (int i = 0; i < WATER_RESOLUTION - 1; i++) {
				for (int j = 0; j < WATER_RESOLUTION - 1; j++) {
					indices.add(vertexIndices[i][j]);
					indices.add(vertexIndices[i + 1][j + 1]);
					indices.add(vertexIndices[i + 1][j]);

					indices.add(vertexIndices[i][j]);
					indices.add(vertexIndices[i][j + 1]);
					indices.add(vertexIndices[i + 1][j + 1]);
				}
			}

			VertexArray va = new VertexArray(vertices, uvs, indices);
			this.waterModel = new Model(va);
		}

		ModelInstance waterInstance = new ModelInstance(this.waterModel, WORLD_SCENE);

		ModelTransform waterTransform = new ModelTransform();
		waterTransform.setTranslation(new Vec3(0.01f));
		waterInstance.setModelTransform(waterTransform);

		Material waterMaterial = new Material(new Vec3(6, 66, 115).mul(1.0f / 255.0f));
		waterMaterial.setSpecular(new Vec3(0.7f));
		waterMaterial.setSpecularExponent(256);
		waterInstance.setMaterial(waterMaterial);

		// INITIALIZE SCREEN
		this.worldScreen = new HWFScreen();
		this.worldScreen.renderSkybox(true);

		this.pic = new PlayerInputController(new Vec3(0, 1, 0));

		// SET CAMERA POS
		this.worldScreen.getCamera().setFacing(this.pic.getFacing());
		this.worldScreen.getCamera().setPos(this.pic.getPos().add(new Vec3(0, 3, 0)));

		// ADD SUN & LIGHTS
		DirLight sun = new DirLight(new Vec3(0.3, -0.6f, 1), new Vec3(1), 0.4f);
		Light.addLight(WORLD_SCENE, sun);
		this.worldScreen.setSun(sun);

		// DEBUGGING TOOLS IF DESIRED
		//windows to look at water textures
		//AdjustableWindow waterHeightViewer = new AdjustableWindow("Water Height Map", new TextureViewerWindow(this.worldScreen.getWaterHeightMap()), this);
		//AdjustableWindow waterNormalViewer = new AdjustableWindow("Water Normal Map", new TextureViewerWindow(this.worldScreen.getWaterNormalMap()), this);
		//control panel for the water
		//AdjustableWindow waterAttributesPanel = new AdjustableWindow("Water Attributes", new ObjectEditorWindow(this.worldScreen.getWaterAttributes()), this);

		this.baseSpectraTexture = new Texture(WATER_RESOLUTION, WATER_RESOLUTION, GL_RGBA32F, GL_RGBA, GL_FLOAT, GL_NEAREST);
		this.waveInfoTexture = new Texture(WATER_RESOLUTION, WATER_RESOLUTION, GL_RGBA32F, GL_RGBA, GL_FLOAT, GL_NEAREST);
		this.evolvedSpectraTexture = new Texture(WATER_RESOLUTION, WATER_RESOLUTION, GL_RGBA32F, GL_RGBA, GL_FLOAT, GL_NEAREST);
		this.gaussianNoiseTexture = this.generateGaussianNoiseTexture();
		this.generateSpectraShader = ShaderUtils.createShader("/csce_vis/hw_final/gen_spectra.compute", GL_COMPUTE_SHADER);
		this.evolveSpectraShader = ShaderUtils.createShader("/csce_vis/hw_final/evolve_spectra.compute", GL_COMPUTE_SHADER);

		this.dispX = new Texture(WATER_RESOLUTION, WATER_RESOLUTION, GL_RGBA32F, GL_RGBA, GL_FLOAT, GL_NEAREST);
		this.dispY = new Texture(WATER_RESOLUTION, WATER_RESOLUTION, GL_RGBA32F, GL_RGBA, GL_FLOAT, GL_NEAREST);
		this.dispZ = new Texture(WATER_RESOLUTION, WATER_RESOLUTION, GL_RGBA32F, GL_RGBA, GL_FLOAT, GL_NEAREST);

		this.dispX_dx = new Texture(WATER_RESOLUTION, WATER_RESOLUTION, GL_RGBA32F, GL_RGBA, GL_FLOAT, GL_NEAREST);
		this.dispY_dx = new Texture(WATER_RESOLUTION, WATER_RESOLUTION, GL_RGBA32F, GL_RGBA, GL_FLOAT, GL_NEAREST);
		this.dispZ_dx = new Texture(WATER_RESOLUTION, WATER_RESOLUTION, GL_RGBA32F, GL_RGBA, GL_FLOAT, GL_NEAREST);

		this.dispX_dz = new Texture(WATER_RESOLUTION, WATER_RESOLUTION, GL_RGBA32F, GL_RGBA, GL_FLOAT, GL_NEAREST);
		this.dispY_dz = new Texture(WATER_RESOLUTION, WATER_RESOLUTION, GL_RGBA32F, GL_RGBA, GL_FLOAT, GL_NEAREST);
		this.dispZ_dz = new Texture(WATER_RESOLUTION, WATER_RESOLUTION, GL_RGBA32F, GL_RGBA, GL_FLOAT, GL_NEAREST);

		this.fftShader = ShaderUtils.createShader("/csce_vis/hw_final/fft.compute", GL_COMPUTE_SHADER);

		this.generateSpectra();

		this.addChildAdjWindow(new TextureViewerWindow(this.gaussianNoiseTexture));
		this.addChildAdjWindow(new TextureViewerWindow(this.baseSpectraTexture));
		this.addChildAdjWindow(new TextureViewerWindow(this.waveInfoTexture));
		this.addChildAdjWindow(new TextureViewerWindow(this.evolvedSpectraTexture));
		this.addChildAdjWindow(new TextureViewerWindow(this.dispY));

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
		this.generateSpectraShader.setUniform1f("multiplier", this.options.multiplier);
		glBindImageTexture(0, this.baseSpectraTexture.getID(), 0, false, 0, GL_WRITE_ONLY, GL_RGBA32F);
		glBindImageTexture(1, this.gaussianNoiseTexture.getID(), 0, false, 0, GL_READ_ONLY, GL_RGBA32F);
		glBindImageTexture(2, this.waveInfoTexture.getID(), 0, false, 0, GL_WRITE_ONLY, GL_RGBA32F);
		glDispatchCompute(WATER_RESOLUTION, WATER_RESOLUTION, 1);
	}

	//with built in LODs 
	private Model createWaterMesh() {
		float interval = 0.25f;
		int lod_cnt = 4;
		int lod_sz = 128;

		int[][] igrid = new int[lod_sz * 2 + 1][lod_sz * 2 + 1];
		int iptr = 0;

		ArrayList<Vec3> vertices = new ArrayList<>();
		ArrayList<Integer> indices = new ArrayList<>();

		//create base lod
		for (int i = 0; i < igrid.length; i++) {
			for (int j = 0; j < igrid.length; j++) {
				vertices.add(new Vec3((i - lod_sz) * interval, 0, (j - lod_sz) * interval));
				igrid[i][j] = iptr++;
			}
		}
		for (int i = 0; i < igrid.length - 1; i++) {
			for (int j = 0; j < igrid.length - 1; j++) {
				indices.add(igrid[i][j]);
				indices.add(igrid[i + 1][j + 1]);
				indices.add(igrid[i + 1][j]);

				indices.add(igrid[i][j]);
				indices.add(igrid[i][j + 1]);
				indices.add(igrid[i + 1][j + 1]);
			}
		}

		//create remaining lods
		for (int lod = 0; lod < lod_cnt; lod++) {
			interval *= 2;

			//generate next LOD indices
			int[][] n_igrid = new int[lod_sz * 2 + 1][lod_sz * 2 + 1];
			for (int i = 0; i < igrid.length; i++) {
				for (int j = 0; j < igrid.length; j++) {
					if (Math.abs(i - lod_sz) < lod_sz / 2 && Math.abs(j - lod_sz) < lod_sz / 2) {
						continue;
					}
					vertices.add(new Vec3((i - lod_sz) * interval, 0, (j - lod_sz) * interval));
					n_igrid[i][j] = iptr++;
				}
			}

			//generate triangles
			for (int i = 0; i < igrid.length - 1; i++) {
				for (int j = 0; j < igrid.length - 1; j++) {
					int z = i - lod_sz;
					int x = j - lod_sz;
					if (x >= -lod_sz / 2 && z >= -lod_sz / 2 && x < lod_sz / 2 && z < lod_sz / 2) {
						//covered by previous LOD
						continue;
					}
					if (x == -lod_sz / 2 - 1 && z >= -lod_sz / 2 && z < lod_sz / 2) {
						//left
						indices.add(n_igrid[i][j]);
						indices.add(igrid[lod_sz + z * 2][0]);
						indices.add(igrid[lod_sz + z * 2 + 1][0]);

						indices.add(n_igrid[i][j]);
						indices.add(igrid[lod_sz + z * 2 + 1][0]);
						indices.add(n_igrid[i + 1][j]);

						indices.add(n_igrid[i + 1][j]);
						indices.add(igrid[lod_sz + z * 2 + 1][0]);
						indices.add(igrid[lod_sz + z * 2 + 2][0]);

					}
					else if (x == lod_sz / 2 && z >= -lod_sz / 2 && z < lod_sz / 2) {
						//right
						indices.add(igrid[lod_sz + z * 2][lod_sz * 2]);
						indices.add(n_igrid[i][j + 1]);
						indices.add(igrid[lod_sz + z * 2 + 1][lod_sz * 2]);

						indices.add(igrid[lod_sz + z * 2 + 1][lod_sz * 2]);
						indices.add(n_igrid[i][j + 1]);
						indices.add(n_igrid[i + 1][j + 1]);

						indices.add(igrid[lod_sz + z * 2 + 1][lod_sz * 2]);
						indices.add(n_igrid[i + 1][j + 1]);
						indices.add(igrid[lod_sz + z * 2 + 2][lod_sz * 2]);

					}
					else if (z == -lod_sz / 2 - 1 && x >= -lod_sz / 2 && x < lod_sz / 2) {
						//bottom
						indices.add(n_igrid[i][j]);
						indices.add(igrid[0][lod_sz + x * 2 + 1]);
						indices.add(igrid[0][lod_sz + x * 2]);

						indices.add(n_igrid[i][j]);
						indices.add(n_igrid[i][j + 1]);
						indices.add(igrid[0][lod_sz + x * 2 + 1]);

						indices.add(n_igrid[i][j + 1]);
						indices.add(igrid[0][lod_sz + x * 2 + 2]);
						indices.add(igrid[0][lod_sz + x * 2 + 1]);
					}
					else if (z == lod_sz / 2 && x >= -lod_sz / 2 && x < lod_sz / 2) {
						//top
						indices.add(igrid[lod_sz * 2][lod_sz + x * 2]);
						indices.add(igrid[lod_sz * 2][lod_sz + x * 2 + 1]);
						indices.add(n_igrid[i + 1][j]);

						indices.add(igrid[lod_sz * 2][lod_sz + x * 2 + 1]);
						indices.add(n_igrid[i + 1][j + 1]);
						indices.add(n_igrid[i + 1][j]);

						indices.add(igrid[lod_sz * 2][lod_sz + x * 2 + 1]);
						indices.add(igrid[lod_sz * 2][lod_sz + x * 2 + 2]);
						indices.add(n_igrid[i + 1][j + 1]);
					}
					else {
						//normal case
						indices.add(n_igrid[i][j]);
						indices.add(n_igrid[i + 1][j + 1]);
						indices.add(n_igrid[i + 1][j]);

						indices.add(n_igrid[i][j]);
						indices.add(n_igrid[i][j + 1]);
						indices.add(n_igrid[i + 1][j + 1]);
					}
				}
			}

			igrid = n_igrid;
		}

		VertexArray va = new VertexArray(vertices, indices, GL_TRIANGLES);
		return new Model(va);
	}

	@Override
	protected void _kill() {
		this.worldScreen.kill();

		this.waterModel.kill();

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

		if (this.isSelected()) {
			this.pic.update();

			//update camera position
			this.worldScreen.getCamera().setFacing(this.pic.getFacing());
			this.worldScreen.getCamera().setPos(this.pic.getPos());
		}
	}

	@Override
	protected void renderContent(Framebuffer outputBuffer) {
		//generate evolved spectra
		{
			this.evolveSpectraShader.enable();
			this.evolveSpectraShader.setUniform1i("spectra_sz", WATER_RESOLUTION);
			this.evolveSpectraShader.setUniform1f("t", this.time);
			glBindImageTexture(0, this.baseSpectraTexture.getID(), 0, false, 0, GL_READ_ONLY, GL_RGBA32F);
			glBindImageTexture(1, this.evolvedSpectraTexture.getID(), 0, false, 0, GL_WRITE_ONLY, GL_RGBA32F);
			glBindImageTexture(2, this.waveInfoTexture.getID(), 0, false, 0, GL_READ_ONLY, GL_RGBA32F);

			glBindImageTexture(3, this.dispX.getID(), 0, false, 0, GL_WRITE_ONLY, GL_RGBA32F);
			glBindImageTexture(4, this.dispY.getID(), 0, false, 0, GL_WRITE_ONLY, GL_RGBA32F);
			glBindImageTexture(5, this.dispZ.getID(), 0, false, 0, GL_WRITE_ONLY, GL_RGBA32F);
			glDispatchCompute(WATER_RESOLUTION, WATER_RESOLUTION, 1);
		}

		//apply fft
		{
			this.fftShader.enable();
			this.fftShader.setUniform1i("invert", 1);
			glBindImageTexture(0, this.dispY.getID(), 0, false, 0, GL_READ_WRITE, GL_RGBA32F);
			glDispatchCompute(1, 1, 1);
		}

		this.worldScreen.setWorldScene(WORLD_SCENE);
		this.worldScreen.render(outputBuffer);
	}

	@Override
	protected void renderOverlay(Framebuffer outputBuffer) {
	}

	@Override
	protected void selected() {
	}

	@Override
	protected void deselected() {
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
