package raytracing;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL13.*;
import static org.lwjgl.opengl.GL14.glBlendFuncSeparate;
import static org.lwjgl.opengl.GL30.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL21.*;
import static org.lwjgl.opengl.GL40.*;
import static org.lwjgl.opengl.GL41.*;
import static org.lwjgl.opengl.GL42.*;
import static org.lwjgl.opengl.GL43.*;

import static org.lwjgl.opengl.GL44.*;
import static org.lwjgl.opengl.GL45.*;
import static org.lwjgl.opengl.GL46.*;

import java.util.ArrayList;

import lwjglengine.graphics.Cubemap;
import lwjglengine.graphics.Framebuffer;
import lwjglengine.graphics.Material;
import lwjglengine.graphics.Shader;
import lwjglengine.graphics.Texture;
import lwjglengine.player.Camera;
import lwjglengine.scene.Scene;
import lwjglengine.screen.Screen;
import lwjglengine.screen.SkyboxCube;
import lwjglengine.util.BufferUtils;
import lwjglengine.util.ShaderUtils;
import myutils.math.Mat4;
import myutils.math.Vec3;
import raytracing.bvh.BVH;
import raytracing.bvh.BVHManager;

public class RaytracingScreen extends Screen {
	//raytracing, wowee, very nice

	//TODO 
	// - read triangles in from attached raytracing scene. 
	// - construct BVH so that ray collisions are fast

	//Preview Mode - camera can move around, and minimal rays are sebnt
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

	public RaytracingScreen() {
		this.raytracingExtractBloomShader = ShaderUtils.createShader("/raytracing/raytracing_extract_bloom.vert", "/raytracing/raytracing_extract_bloom.frag");
		this.raytracingHDRShader = ShaderUtils.createShader("/raytracing/raytracing_hdr.vert", "/raytracing/raytracing_hdr.frag");
		this.raytracingGeometryShader = ShaderUtils.createShader("/raytracing/raytracing.vert", "/raytracing/raytracing.frag");

		this.raytracingGeometryShader.setUniform1i("render_tex_0", 0);
		this.raytracingGeometryShader.setUniform1i("skybox_tex", 1);

		this.bvhManager = new BVHManager();

		this.numRenderedFrames = 0;
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
		this.camera.setPos(pos);
	}

	public void setCameraFacing(Vec3 facing) {
		if (this.renderMode == RENDER_MODE_RENDER) {
			return;
		}
		this.camera.setFacing(facing);
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

	public void buildBVHBuffers() {
		this.bvhManager.build();
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
			this.numRenderedFrames = 0;

			//render
			renderBuffer.bind();
			glDisable(GL_DEPTH_TEST);
			glDisable(GL_CULL_FACE);
			glDisable(GL_BLEND);
			this.bvhManager.getBVHSSBO().bindToBase(1);
			this.bvhManager.getBoundingBoxSSBO().bindToBase(2);
			this.bvhManager.getPrimitiveSSBO().bindToBase(3);
			this.bvhManager.getMaterialSSBO().bindToBase(4);
			this.setRaytracingShaderUniforms();
			this.raytracingGeometryShader.enable();
			this.prevRenderColorMap.bind(GL_TEXTURE0);
			Scene.skyboxes.get(this.raytracingScene).bind(GL_TEXTURE1);
			SkyboxCube.skyboxCube.render();

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
			this.bvhManager.getBVHSSBO().bindToBase(1);
			this.bvhManager.getBoundingBoxSSBO().bindToBase(2);
			this.bvhManager.getPrimitiveSSBO().bindToBase(3);
			this.bvhManager.getMaterialSSBO().bindToBase(4);
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
		private float sunStrength = 1000;
		private Vec3 sunDir = new Vec3(1, 1, 0.4f);

		//more bounces have drastically diminishing returns along with drastically increasing render times
		private int previewMaxBounceCount = 5;
		private int renderMaxBounceCount = 10;

		//increase number of rays per pixel while rendering to speed it up?
		//downside is lower fps
		private int previewNumRaysPerPixel = 1;
		private int renderNumRaysPerPixel = 20;

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
	}

}
