package spectral_raytracing;

import static org.lwjgl.opengl.GL11.GL_BLEND;
import static org.lwjgl.opengl.GL11.GL_COLOR_BUFFER_BIT;
import static org.lwjgl.opengl.GL11.GL_CULL_FACE;
import static org.lwjgl.opengl.GL11.GL_DEPTH_TEST;
import static org.lwjgl.opengl.GL11.GL_FLOAT;
import static org.lwjgl.opengl.GL11.GL_ONE;
import static org.lwjgl.opengl.GL11.GL_ONE_MINUS_SRC_ALPHA;
import static org.lwjgl.opengl.GL11.GL_RGBA;
import static org.lwjgl.opengl.GL11.GL_SRC_ALPHA;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_2D;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_WRAP_S;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_WRAP_T;
import static org.lwjgl.opengl.GL11.glBindTexture;
import static org.lwjgl.opengl.GL11.glClear;
import static org.lwjgl.opengl.GL11.glDisable;
import static org.lwjgl.opengl.GL11.glEnable;
import static org.lwjgl.opengl.GL11.glTexParameteri;
import static org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE;
import static org.lwjgl.opengl.GL13.GL_TEXTURE0;
import static org.lwjgl.opengl.GL13.GL_TEXTURE1;
import static org.lwjgl.opengl.GL14.glBlendFuncSeparate;
import static org.lwjgl.opengl.GL30.GL_COLOR_ATTACHMENT0;
import static org.lwjgl.opengl.GL30.GL_RGBA32F;
import static org.lwjgl.opengl.GL30.*;

import java.io.IOException;
import java.util.ArrayList;

import lwjglengine.graphics.Framebuffer;
import lwjglengine.graphics.Material;
import lwjglengine.graphics.Shader;
import lwjglengine.graphics.ShaderStorageBuffer;
import lwjglengine.graphics.Texture;
import lwjglengine.model.Model;
import lwjglengine.model.VertexArray;
import lwjglengine.player.Camera;
import lwjglengine.scene.Scene;
import lwjglengine.screen.Screen;
import lwjglengine.screen.SkyboxCube;
import lwjglengine.util.ShaderUtils;
import myutils.file.FileUtils;
import myutils.file.csv.CSVReader;
import myutils.math.Mat4;
import myutils.math.MathUtils;
import myutils.math.Vec3;
import raytracing.RaytracingScreen.RaytracingOptions;
import raytracing.bvh.*;

public class SpectralRaytracingScreen extends Screen {
	//woah, spectral raytracing, bueno as jonathan would say

	//actually path tracing, but raytracing sounds better. 
	//this time, we're using a more physically accurate spectrum representation of light
	//this allows us to capture phenomenon like diffraction

	//when rendering, should first sample xyz color coefficients, which then we use another shader to convert to whatever 
	//rgb color space we want. 

	//in the shader, should sample several different (fixed?) wavelengths, to figure out xyz color coefficients. 
	//perhaps in the beginning, make it completely random, then can just multiply against xyz sensitivity distributions.

	//preview rendering should maintain rgb sampling? it's much faster than spectrum rendering

	//Preview Mode - camera can move around, and minimal rays are sent
	//Render Mode - camera cannot move around, and previous frames get blended with new frames to create the render
	//Display Prev Render Mode - look at the previous render, and tweak postprocessing stuff.

	public static final int RENDER_MODE_PREVIEW = 0;
	public static final int RENDER_MODE_RENDER = 1;
	public static final int RENDER_MODE_DISPLAY_PREV_RENDER = 2;

	private int renderMode = RENDER_MODE_PREVIEW;

	//raytracing is pretty simple, we only need one buffer for color lol. 
	private Framebuffer renderBuffer;
	private Texture renderColorMap;

	private Framebuffer prevRenderBuffer;
	private Texture prevRenderColorMap;

	//after doing raytracing, saves the hdr image to be postprocessed
	private Framebuffer outputBuffer;
	private Texture outputColorMap;

	private Framebuffer postprocessHDRBuffer;
	private Texture postprocessHDRMap;

	private Framebuffer postprocessBloomBuffer;
	private Texture postprocessBloomMap;

	private Framebuffer postprocessTempBuffer;
	private Texture postprocessTempMap;

	private int raytracingScene;

	private BVHManager bvhManager;

	private int numRenderedFrames;

	private Shader raytracingExtractBloomShader;
	private Shader raytracingHDRShader;
	private Shader raytracingGeometryShader;

	private RaytracingOptions options = new RaytracingOptions();

	private static int nm_min = 360;
	private static int nm_max = 830;
	private static int nm_range = nm_max - nm_min + 1;
	private float cie_y_int;
	private ShaderStorageBuffer RGBC_buffer, XYZ_buffer;

	public SpectralRaytracingScreen() {
		this.raytracingExtractBloomShader = ShaderUtils.createShader("/raytracing/raytracing_extract_bloom.vert", "/raytracing/raytracing_extract_bloom.frag");
		this.raytracingHDRShader = ShaderUtils.createShader("/raytracing/raytracing_hdr.vert", "/raytracing/raytracing_hdr.frag");
		this.raytracingGeometryShader = ShaderUtils.createShader("/spectral_raytracing/spectral_raytracing.vert", "/spectral_raytracing/spectral_raytracing.frag");

		this.raytracingGeometryShader.setUniform1i("render_tex_0", 0);
		this.raytracingGeometryShader.setUniform1i("skybox_tex", 1);

		this.bvhManager = new BVHManager();

		this.numRenderedFrames = 0;

		//read in wavelength related data, and put into SSBOs
		//read in rgb_components data, goes from 360nm to 830nm
		try {
			CSVReader csv = new CSVReader();
			csv.setReadHeader(false);
			csv.setTranspose(true);
			csv.readFileAsCSV(FileUtils.loadFileRelative("/res/spectral_raytracing/rgb_components.csv"));
			float[] RGBC_data = new float[3 * nm_range];
			for (int i = 0; i < 3; i++) {
				for (int j = nm_min; j <= nm_max; j++) {
					RGBC_data[i * nm_range + j - nm_min] = Float.parseFloat(csv.getData()[i][j - nm_min]);
				}
			}

			this.RGBC_buffer = new ShaderStorageBuffer();
			this.RGBC_buffer.setUsage(GL_STATIC_READ);
			this.RGBC_buffer.setData(RGBC_data);
		}
		catch (IOException e) {
			e.printStackTrace();
		}

		//read in cie xyz data, goes from 360nm to 830nm
		try {
			CSVReader csv = new CSVReader();
			csv.setTranspose(true);
			csv.readFileAsCSV(FileUtils.loadFileRelative("/res/spectral_raytracing/cie_xyz.csv"));
			float[] XYZ_data = new float[3 * nm_range];
			for (int i = 0; i < 3; i++) {
				for (int j = nm_min; j <= nm_max; j++) {
					XYZ_data[i * nm_range + j - nm_min] = Float.parseFloat(csv.getData()[i][j - nm_min]);
				}
			}
			this.XYZ_buffer = new ShaderStorageBuffer();
			this.XYZ_buffer.setUsage(GL_STATIC_READ);
			this.XYZ_buffer.setData(XYZ_data);

			//compute cie_y_int
			this.cie_y_int = 0;
			for (int i = nm_min; i <= nm_max; i++) {
				this.cie_y_int += XYZ_data[nm_range + i - nm_min];
			}
		}
		catch (IOException e) {
			e.printStackTrace();
		}
	}

	public RaytracingOptions getOptions() {
		return this.options;
	}

	public void setRaytracingScene(int scene) {
		this.raytracingScene = scene;
	}

	@Override
	protected void _kill() {
		this.renderBuffer.kill();
		this.prevRenderBuffer.kill();
		this.outputBuffer.kill();
		this.postprocessTempBuffer.kill();
		this.postprocessBloomBuffer.kill();
		this.postprocessHDRBuffer.kill();

		this.raytracingExtractBloomShader.kill();
		this.raytracingHDRShader.kill();
		this.raytracingGeometryShader.kill();

		this.RGBC_buffer.kill();
		this.XYZ_buffer.kill();

		this.bvhManager.kill();
	}

	@Override
	public void buildBuffers() {
		if (this.renderBuffer != null) {
			this.renderBuffer.kill();
			this.prevRenderBuffer.kill();
			this.outputBuffer.kill();
			this.postprocessTempBuffer.kill();
			this.postprocessBloomBuffer.kill();
			this.postprocessHDRBuffer.kill();
		}

		this.renderBuffer = new Framebuffer(this.screenWidth, this.screenHeight);
		this.renderColorMap = new Texture(GL_RGBA32F, this.screenWidth, this.screenHeight, GL_RGBA, GL_FLOAT);
		this.renderBuffer.bindTextureToBuffer(GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, this.renderColorMap.getID());
		this.renderBuffer.setDrawBuffers(new int[] { GL_COLOR_ATTACHMENT0 });
		this.renderBuffer.isComplete();

		this.prevRenderBuffer = new Framebuffer(this.screenWidth, this.screenHeight);
		this.prevRenderColorMap = new Texture(GL_RGBA32F, this.screenWidth, this.screenHeight, GL_RGBA, GL_FLOAT);
		this.prevRenderBuffer.bindTextureToBuffer(GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, this.prevRenderColorMap.getID());
		this.prevRenderBuffer.setDrawBuffers(new int[] { GL_COLOR_ATTACHMENT0 });
		this.prevRenderBuffer.isComplete();

		this.outputBuffer = new Framebuffer(this.screenWidth, this.screenHeight);
		this.outputColorMap = new Texture(GL_RGBA32F, this.screenWidth, this.screenHeight, GL_RGBA, GL_FLOAT);
		this.outputBuffer.bindTextureToBuffer(GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, this.outputColorMap.getID());
		this.outputBuffer.setDrawBuffers(new int[] { GL_COLOR_ATTACHMENT0 });
		this.outputBuffer.isComplete();

		this.postprocessHDRBuffer = new Framebuffer(this.screenWidth, this.screenHeight);
		this.postprocessHDRMap = new Texture(GL_RGBA32F, this.screenWidth, this.screenHeight, GL_RGBA, GL_FLOAT);
		this.postprocessHDRBuffer.bindTextureToBuffer(GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, this.postprocessHDRMap.getID());
		this.postprocessHDRBuffer.setDrawBuffers(new int[] { GL_COLOR_ATTACHMENT0 });
		this.postprocessHDRBuffer.isComplete();

		this.postprocessBloomBuffer = new Framebuffer(this.screenWidth, this.screenHeight);
		this.postprocessBloomMap = new Texture(GL_RGBA32F, this.screenWidth, this.screenHeight, GL_RGBA, GL_FLOAT);
		glBindTexture(GL_TEXTURE_2D, this.postprocessBloomMap.getID());
		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
		this.postprocessBloomBuffer.bindTextureToBuffer(GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, this.postprocessBloomMap.getID());
		this.postprocessBloomBuffer.setDrawBuffers(new int[] { GL_COLOR_ATTACHMENT0 });
		this.postprocessBloomBuffer.isComplete();

		this.postprocessTempBuffer = new Framebuffer(this.screenWidth, this.screenHeight);
		this.postprocessTempMap = new Texture(GL_RGBA32F, this.screenWidth, this.screenHeight, GL_RGBA, GL_FLOAT);
		glBindTexture(GL_TEXTURE_2D, this.postprocessTempMap.getID());
		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
		this.postprocessTempBuffer.bindTextureToBuffer(GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, this.postprocessTempMap.getID());
		this.postprocessTempBuffer.setDrawBuffers(new int[] { GL_COLOR_ATTACHMENT0 });
		this.postprocessTempBuffer.isComplete();

		Vec3 cameraPos = new Vec3();
		Vec3 cameraFacing = new Vec3(0, 0, -1);

		if (this.camera != null) {
			cameraPos = this.camera.getPos();
			cameraFacing = this.camera.getFacing();
		}

		this.camera = new Camera((float) Math.toRadians(90f), this.screenWidth, this.screenHeight, 0.1f, 200f);
		this.camera.setPos(cameraPos);
		this.camera.setFacing(cameraFacing);
	}

	public void setCameraPos(Vec3 pos) {
		if (this.renderMode == RENDER_MODE_RENDER) {
			return;
		}
		if (this.camera.getPos().equals(pos)) {
			return;
		}
		this.camera.setPos(pos);
		this.numRenderedFrames = 0;
	}

	public void setCameraFacing(Vec3 facing) {
		if (this.renderMode == RENDER_MODE_RENDER) {
			return;
		}
		if (this.camera.getFacing().equals(facing)) {
			return;
		}
		this.camera.setFacing(facing);
		this.numRenderedFrames = 0;
	}

	public void addBVHInstance(BVH bvh, Mat4 transform) {
		this.bvhManager.addBVHInstance(bvh, transform);
	}

	public void addSphere(Vec3 center, float radius, Material material) {
		this.bvhManager.addSphere(center, radius, material);
	}

	public void addTriangle(Vec3 a, Vec3 b, Vec3 c, Material material) {
		this.bvhManager.addTriangle(a, b, c, material);
	}

	public void addModel(Model model, Mat4 transform, Material material) {
		ArrayList<VertexArray> meshes = model.getMeshes();
		for (VertexArray v : meshes) {
			int[] indices = v.getIndices();
			Vec3[] vertices = new Vec3[v.getVertices().length / 3];
			for (int i = 0; i < vertices.length; i++) {
				float x = v.getVertices()[i * 3 + 0];
				float y = v.getVertices()[i * 3 + 1];
				float z = v.getVertices()[i * 3 + 2];
				vertices[i] = new Vec3(x, y, z);
			}
			for (int i = 0; i < indices.length / 3; i++) {
				Vec3 a = vertices[indices[i * 3 + 0]];
				Vec3 b = vertices[indices[i * 3 + 1]];
				Vec3 c = vertices[indices[i * 3 + 2]];

				//apply model transform
				a = transform.mul(a, 1);
				b = transform.mul(b, 1);
				c = transform.mul(c, 1);

				this.addTriangle(a, b, c, material);
			}
		}
	}

	public void buildBVHBuffers() {
		this.bvhManager.build();
	}

	public Texture getPostprocessHDRMap() {
		return this.postprocessHDRMap;
	}

	private void setRaytracingShaderUniforms() {
		this.camera.setProjectionMatrix(Mat4.perspective((float) Math.toRadians(this.options.fov), this.screenWidth, this.screenHeight, 0.1f, 200f));
		Vec3 cameraRight = this.camera.getFacing().cross(this.camera.getUp());
		Vec3 cameraUp = this.camera.getFacing().cross(cameraRight);

		this.raytracingGeometryShader.enable();
		this.raytracingGeometryShader.setUniformMat4("vw_matrix", this.camera.getViewMatrix());
		this.raytracingGeometryShader.setUniformMat4("pr_matrix", this.camera.getProjectionMatrix());
		this.raytracingGeometryShader.setUniform3f("camera_pos", this.camera.getPos());
		this.raytracingGeometryShader.setUniform1i("max_bounce_count", this.renderMode == RENDER_MODE_PREVIEW ? this.options.previewMaxBounceCount : this.options.renderMaxBounceCount);
		this.raytracingGeometryShader.setUniform1i("num_rays_per_pixel", this.renderMode == RENDER_MODE_PREVIEW ? this.options.previewNumRaysPerPixel : this.options.renderNumRaysPerPixel);
		this.raytracingGeometryShader.setUniform1i("num_rendered_frames", this.numRenderedFrames);
		this.raytracingGeometryShader.setUniform1i("window_width", this.screenWidth);
		this.raytracingGeometryShader.setUniform1i("window_height", this.screenHeight);
		this.raytracingGeometryShader.setUniform1f("blur_strength", this.options.blurStrength); //for antialiasing
		this.raytracingGeometryShader.setUniform1f("defocus_strength", this.options.defocusStrength);
		this.raytracingGeometryShader.setUniform1f("focus_dist", this.options.focusDist);
		this.raytracingGeometryShader.setUniform3f("camera_right", cameraRight);
		this.raytracingGeometryShader.setUniform3f("camera_up", cameraUp);
		this.raytracingGeometryShader.setUniform3f("sun_dir", this.options.sunDir.normalize());
		this.raytracingGeometryShader.setUniform1f("sun_strength", this.options.sunStrength);
		this.raytracingGeometryShader.setUniform1f("ambient_strength", this.options.ambientStrength);
		this.raytracingGeometryShader.setUniform1i("is_preview", this.renderMode == RENDER_MODE_PREVIEW ? 1 : 0);
		this.raytracingGeometryShader.setUniform1f("cie_y_int", this.cie_y_int);
		this.raytracingGeometryShader.setUniform1i("wavelength_nm_interval", MathUtils.clamp(1, nm_range, this.options.wavelengthNmInterval));
	}

	private void bindRaytracingSSBOs() {
		this.bvhManager.getBVHSSBO().bindToBase(1);
		this.bvhManager.getBoundingBoxSSBO().bindToBase(2);
		this.bvhManager.getPrimitiveSSBO().bindToBase(3);
		this.bvhManager.getMaterialSSBO().bindToBase(4);
		this.RGBC_buffer.bindToBase(5);
		this.XYZ_buffer.bindToBase(6);
	}

	@Override
	protected void _render(Framebuffer outputBuffer) {

		//pre-render checks
		if (!Scene.skyboxes.containsKey(this.raytracingScene)) {
			System.err.println("RaytracingScreen : NO SKYBOX ENTRY FOR RAYTRACING SCENE " + this.raytracingScene);
			return;
		}

		//set blend mode
		glBlendFuncSeparate(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA, GL_ONE, GL_ONE_MINUS_SRC_ALPHA);

		// -- RENDER SCENE --
		//at the end, the hdr output should be in prevRenderColorMap
		switch (this.renderMode) {
		case RENDER_MODE_PREVIEW: {
			renderBuffer.bind();
			glDisable(GL_DEPTH_TEST);
			glDisable(GL_CULL_FACE);
			glDisable(GL_BLEND);
			this.bindRaytracingSSBOs();
			this.setRaytracingShaderUniforms();
			this.raytracingGeometryShader.enable();
			this.prevRenderColorMap.bind(GL_TEXTURE0);
			Scene.skyboxes.get(this.raytracingScene).bind(GL_TEXTURE1);
			SkyboxCube.skyboxCube.render();

			this.numRenderedFrames++;

			//render to prev buffer
			prevRenderBuffer.bind();
			glClear(GL_COLOR_BUFFER_BIT);
			glDisable(GL_DEPTH_TEST);
			glEnable(GL_BLEND);
			this.renderColorMap.bind(GL_TEXTURE0);
			Shader.SPLASH.enable();
			Shader.SPLASH.setUniform1f("alpha", 1f);
			screenQuad.render();

			this.outputBuffer.bind();
			glClear(GL_COLOR_BUFFER_BIT);
			glDisable(GL_DEPTH_TEST);
			glEnable(GL_BLEND);
			this.renderColorMap.bind(GL_TEXTURE0);
			Shader.SPLASH.enable();
			Shader.SPLASH.setUniform1f("alpha", 1f);
			screenQuad.render();
			break;
		}

		case RENDER_MODE_RENDER: {
			//render
			renderBuffer.bind();
			glDisable(GL_DEPTH_TEST);
			glDisable(GL_CULL_FACE);
			glDisable(GL_BLEND);
			this.bindRaytracingSSBOs();
			this.setRaytracingShaderUniforms();
			this.raytracingGeometryShader.enable();
			this.prevRenderColorMap.bind(GL_TEXTURE0);
			Scene.skyboxes.get(this.raytracingScene).bind(GL_TEXTURE1);
			SkyboxCube.skyboxCube.render();

			this.numRenderedFrames++;

			//render to prev buffer
			prevRenderBuffer.bind();
			glClear(GL_COLOR_BUFFER_BIT);
			glDisable(GL_DEPTH_TEST);
			glEnable(GL_BLEND);
			this.renderColorMap.bind(GL_TEXTURE0);
			Shader.SPLASH.enable();
			Shader.SPLASH.setUniform1f("alpha", 1f);
			screenQuad.render();

			this.outputBuffer.bind();
			glClear(GL_COLOR_BUFFER_BIT);
			glDisable(GL_DEPTH_TEST);
			glEnable(GL_BLEND);
			this.renderColorMap.bind(GL_TEXTURE0);
			Shader.SPLASH.enable();
			Shader.SPLASH.setUniform1f("alpha", 1f);
			screenQuad.render();
			break;
		}

		case RENDER_MODE_DISPLAY_PREV_RENDER: {
			this.outputBuffer.bind();
			glClear(GL_COLOR_BUFFER_BIT);
			glDisable(GL_DEPTH_TEST);
			glEnable(GL_BLEND);
			this.prevRenderColorMap.bind(GL_TEXTURE0);
			Shader.SPLASH.enable();
			Shader.SPLASH.setUniform1f("alpha", 1f);
			screenQuad.render();
			break;
		}
		}

		// -- RENDER TO OUTPUT --
		//extract bright pixels
		this.postprocessBloomBuffer.bind();
		glClear(GL_COLOR_BUFFER_BIT);
		glDisable(GL_DEPTH_TEST);
		glEnable(GL_BLEND);
		this.outputColorMap.bind(GL_TEXTURE0);
		this.raytracingExtractBloomShader.enable();
		this.raytracingExtractBloomShader.setUniform1f("bloomThreshold", this.options.bloomThreshold);
		screenQuad.render();

		Shader.GAUSSIAN_BLUR.enable();
		glDisable(GL_DEPTH_TEST);
		glEnable(GL_BLEND);
		for (int i = 0; i < 5; i++) {
			//blur horizontally
			this.postprocessTempBuffer.bind();
			this.postprocessBloomMap.bind(GL_TEXTURE0);
			Shader.GAUSSIAN_BLUR.setUniform1i("horizontal", 1);
			screenQuad.render();

			//blur vertically
			this.postprocessBloomBuffer.bind();
			this.postprocessTempMap.bind(GL_TEXTURE0);
			Shader.GAUSSIAN_BLUR.setUniform1i("horizontal", 0);
			screenQuad.render();
		}

		//do postprocessing
		this.postprocessHDRBuffer.bind();
		glClear(GL_COLOR_BUFFER_BIT);
		glDisable(GL_DEPTH_TEST);
		glEnable(GL_BLEND);
		this.outputColorMap.bind(GL_TEXTURE0);
		this.postprocessBloomMap.bind(GL_TEXTURE1);
		this.raytracingHDRShader.enable();
		this.raytracingHDRShader.setUniform1f("exposure", this.options.exposure);
		this.raytracingHDRShader.setUniform1f("gamma", this.options.gamma);
		screenQuad.render();

		//render to output
		outputBuffer.bind();
		glDisable(GL_DEPTH_TEST);
		glEnable(GL_BLEND);
		this.postprocessHDRMap.bind(GL_TEXTURE0);
		Shader.SPLASH.enable();
		Shader.SPLASH.setUniform1f("alpha", 1f);
		screenQuad.render();

	}

	public void setRenderMode(int renderMode) {
		this.renderMode = renderMode;
		this.numRenderedFrames = 0;
	}

	public int getRenderMode() {
		return this.renderMode;
	}

	public class RaytracingOptions {
		private float fov = 90f; //in degrees

		private float blurStrength = 1.5f; //good to keep around 1 to 5 for antialiasing
		private float defocusStrength = 0f;
		private float focusDist = 30f;

		private float ambientStrength = 2;
		private float sunStrength = 10000;
		private Vec3 sunDir = new Vec3(-1, 1, -0.4f);

		//more bounces have drastically diminishing returns along with drastically increasing render times
		private int previewMaxBounceCount = 2;
		private int renderMaxBounceCount = 15;

		//increase number of rays per pixel while rendering to speed it up?
		//downside is lower fps
		private int previewNumRaysPerPixel = 1;
		private int renderNumRaysPerPixel = 1;

		//by default, sample every 5 nanometers in the visible light range. 
		//higher should converge faster (???), but look worse. 
		private int wavelengthNmInterval = 5;

		private float exposure = 1;
		private float gamma = 1;

		//smudging bright areas with gaussian blur
		private float bloomThreshold = 2.5f; //how bright does a pixel have to be to be blurred?

		public float getFov() {
			return fov;
		}

		public void setFov(float fov) {
			this.fov = fov;
		}

		public float getBlurStrength() {
			return blurStrength;
		}

		public void setBlurStrength(float blurStrength) {
			this.blurStrength = blurStrength;
		}

		public float getDefocusStrength() {
			return defocusStrength;
		}

		public void setDefocusStrength(float defocusStrength) {
			this.defocusStrength = defocusStrength;
		}

		public float getFocusDist() {
			return focusDist;
		}

		public void setFocusDist(float focusDist) {
			this.focusDist = focusDist;
		}

		public float getAmbientStrength() {
			return ambientStrength;
		}

		public void setAmbientStrength(float ambientStrength) {
			this.ambientStrength = ambientStrength;
		}

		public float getSunStrength() {
			return sunStrength;
		}

		public void setSunStrength(float sunStrength) {
			this.sunStrength = sunStrength;
		}

		public Vec3 getSunDir() {
			return sunDir;
		}

		public void setSunDir(Vec3 sunDir) {
			this.sunDir = sunDir;
		}

		public int getPreviewMaxBounceCount() {
			return previewMaxBounceCount;
		}

		public void setPreviewMaxBounceCount(int previewMaxBounceCount) {
			this.previewMaxBounceCount = previewMaxBounceCount;
		}

		public int getRenderMaxBounceCount() {
			return renderMaxBounceCount;
		}

		public void setRenderMaxBounceCount(int renderMaxBounceCount) {
			this.renderMaxBounceCount = renderMaxBounceCount;
		}

		public int getPreviewNumRaysPerPixel() {
			return previewNumRaysPerPixel;
		}

		public void setPreviewNumRaysPerPixel(int previewNumRaysPerPixel) {
			this.previewNumRaysPerPixel = previewNumRaysPerPixel;
		}

		public int getRenderNumRaysPerPixel() {
			return renderNumRaysPerPixel;
		}

		public void setRenderNumRaysPerPixel(int renderNumRaysPerPixel) {
			this.renderNumRaysPerPixel = renderNumRaysPerPixel;
		}

		public float getExposure() {
			return exposure;
		}

		public void setExposure(float exposure) {
			this.exposure = exposure;
		}

		public float getGamma() {
			return gamma;
		}

		public void setGamma(float gamma) {
			this.gamma = gamma;
		}

		public float getBloomThreshold() {
			return bloomThreshold;
		}

		public void setBloomThreshold(float bloomThreshold) {
			this.bloomThreshold = bloomThreshold;
		}

		public int getWavelengthNmInterval() {
			return wavelengthNmInterval;
		}

		public void setWavelengthNmInterval(int wavelengthNmInterval) {
			this.wavelengthNmInterval = wavelengthNmInterval;
		}
	}

}
