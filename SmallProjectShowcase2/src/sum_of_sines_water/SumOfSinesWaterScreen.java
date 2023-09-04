package sum_of_sines_water;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL12.*;
import static org.lwjgl.opengl.GL14.*;
import static org.lwjgl.opengl.GL13.*;
import static org.lwjgl.opengl.GL30.*;

import java.util.ArrayList;

import lwjglengine.graphics.Cubemap;
import lwjglengine.graphics.Framebuffer;
import lwjglengine.graphics.Shader;
import lwjglengine.graphics.Texture;
import lwjglengine.main.Main;
import lwjglengine.model.Model;
import lwjglengine.player.Camera;
import lwjglengine.scene.DirLight;
import lwjglengine.scene.Light;
import lwjglengine.scene.Scene;
import lwjglengine.screen.Screen;
import lwjglengine.screen.SkyboxCube;
import myutils.v10.math.Mat4;
import myutils.v10.math.MathUtils;
import myutils.v10.math.Vec3;

public class SumOfSinesWaterScreen extends Screen {
	// renders the scene with a perspective projection matrix.

	private static final float NEAR = 0.1f;
	private static final float FAR = 400.0f;

	private int world_scene;

	private int particle_scene;
	private boolean renderParticles = false;

	private static final int SHADOW_MAP_NR_CASCADES = 7;
	private static float[] shadowCascades = new float[] { NEAR, 1, 3, 7, 15, 30, 100, FAR };

	private float worldFOV;

	private Framebuffer geometryBuffer;
	private Framebuffer lightingBuffer;
	private Framebuffer shadowBuffer;
	private Framebuffer skyboxBuffer;

	private Texture geometryPositionMap; // RGB: pos, A: normalized depth; 0 - 1
	private Texture geometryNormalMap; // RGB: normal
	private Texture geometrySpecularMap; // RGB: specular, A: shininess
	private Texture geometryColorMap; // RGB: color, A: alpha
	private Texture geometryColorIDMap; // RGB: colorID

	private Texture lightingColorMap; // RGB: color
	private Texture lightingBrightnessMap; // R: brightness

	private Texture shadowDepthMap; // R: depth
	private Texture shadowBackfaceMap; // R: isBackface
	private Cubemap shadowCubemap; // R: depth

	private Texture skyboxColorMap; // RGB: color

	private boolean renderSkybox = false;

	private Shader waterGeometryShader;

	private float waterTime = 0f;
	private Shader waterTextureShader; //responsible for creating the water texture. 

	private static int nrSumsMax = 128;
	private static int waterTextureResolution = 1024; //resolution of the water texture should remain fixed. 
	private Framebuffer waterBuffer; //height and normal of the water. 
	private Texture waterHeightMap; //R: water height
	private Texture waterNormalMap; //RGB: normal

	private WaterAttributes waterAttributes;

	private Shader waterAtmosphereShader;

	public SumOfSinesWaterScreen() {
		this.waterAttributes = new WaterAttributes();

		this.waterTextureShader = new Shader("/sum_of_sines_water/water_texture.vert", "/sum_of_sines_water/water_texture.frag");

		{
			float speed = 0.5f;
			for (int i = 0; i < nrSumsMax; i++) {
				float theta = (float) (Math.random() * Math.PI * 2);
				this.waterTextureShader.setUniform1f("theta[" + i + "]", theta);
				this.waterTextureShader.setUniform1f("speed[" + i + "]", speed);
				speed *= 1.07;
			}
		}

		this.waterBuffer = new Framebuffer(waterTextureResolution, waterTextureResolution);
		this.waterHeightMap = new Texture(GL_RGBA32F, waterTextureResolution, waterTextureResolution, GL_RGBA, GL_FLOAT);
		this.waterNormalMap = new Texture(GL_RGBA32F, waterTextureResolution, waterTextureResolution, GL_RGBA, GL_FLOAT);
		this.waterHeightMap.setWrapping(GL_CLAMP_TO_EDGE);
		this.waterNormalMap.setWrapping(GL_CLAMP_TO_EDGE);
		this.waterBuffer.bindTextureToBuffer(GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, this.waterHeightMap.getID());
		this.waterBuffer.bindTextureToBuffer(GL_COLOR_ATTACHMENT1, GL_TEXTURE_2D, this.waterNormalMap.getID());
		this.waterBuffer.setDrawBuffers(new int[] { GL_COLOR_ATTACHMENT0, GL_COLOR_ATTACHMENT1 });
		this.waterBuffer.isComplete();

		this.waterGeometryShader = new Shader("/sum_of_sines_water/water.vert", "/sum_of_sines_water/water.frag");
		this.waterGeometryShader.setUniform1i("tex_diffuse", 0);
		this.waterGeometryShader.setUniform1i("tex_specular", 1);
		this.waterGeometryShader.setUniform1i("tex_normal", 2);
		this.waterGeometryShader.setUniform1i("tex_displacement", 3);
		this.waterGeometryShader.setUniform1i("skybox", 4);
		this.waterGeometryShader.setUniform1i("tex_water_height", 5);
		this.waterGeometryShader.setUniform1i("tex_water_normal", 6);

		this.waterGeometryShader.setUniform1i("enableParallaxMapping", 0);
		this.waterGeometryShader.setUniform1i("enableTexScaling", 1);

		this.waterGeometryShader.setUniform1f("water_scale", 512);

		this.waterAtmosphereShader = new Shader("/sum_of_sines_water/water_atmosphere.vert", "/sum_of_sines_water/water_atmosphere.frag");
	}

	public class WaterAttributes {
		public float u_amplitude = 0.004f;
		public float u_period = 0.05f;
		public int nr_sums = 128;

		public float period_mult = 0.877f;
		public float amplitude_mult = 0.82f;

		public float domain_warp_coeff = 0.06f;

		private float water_depth = 10;

		public float getU_amplitude() {
			return u_amplitude;
		}

		public void setU_amplitude(float u_amplitude) {
			this.u_amplitude = u_amplitude;
		}

		public float getU_period() {
			return u_period;
		}

		public void setU_period(float u_period) {
			this.u_period = u_period;
		}

		public int getNr_sums() {
			return nr_sums;
		}

		public void setNr_sums(int nr_sums) {
			this.nr_sums = nr_sums;
		}

		public float getPeriod_mult() {
			return period_mult;
		}

		public void setPeriod_mult(float period_mult) {
			this.period_mult = period_mult;
		}

		public float getAmplitude_mult() {
			return amplitude_mult;
		}

		public void setAmplitude_mult(float amplitude_mult) {
			this.amplitude_mult = amplitude_mult;
		}

		public float getDomain_warp_coeff() {
			return domain_warp_coeff;
		}

		public void setDomain_warp_coeff(float domain_warp_coeff) {
			this.domain_warp_coeff = domain_warp_coeff;
		}

		public float getWater_depth() {
			return water_depth;
		}

		public void setWater_depth(float water_depth) {
			this.water_depth = water_depth;
		}

	}

	public void setSun(DirLight sun) {
		this.waterGeometryShader.setUniform3f("sun_dir", sun.dir);
		this.waterAtmosphereShader.setUniform3f("sun_dir", sun.dir);
	}

	public WaterAttributes getWaterAttributes() {
		return this.waterAttributes;
	}

	public Texture getWaterHeightMap() {
		return this.waterHeightMap;
	}

	public Texture getWaterNormalMap() {
		return this.waterNormalMap;
	}

	public void generateTheta() {
		for (int i = 0; i < nrSumsMax; i++) {
			float theta = (float) (Math.random() * Math.PI * 2);
			this.waterTextureShader.enable();
			this.waterTextureShader.setUniform1f("theta[" + i + "]", theta);
		}
	}

	@Override
	protected void _kill() {
		this.geometryBuffer.kill();
		this.lightingBuffer.kill();
		this.shadowBuffer.kill();
		this.skyboxBuffer.kill();

		this.waterBuffer.kill();

		this.waterTextureShader.kill();
		this.waterGeometryShader.kill();
		this.waterAtmosphereShader.kill();
	}

	@Override
	public void buildBuffers() {
		if (this.geometryBuffer != null) {
			this.geometryBuffer.kill();
		}
		if (this.lightingBuffer != null) {
			this.lightingBuffer.kill();
		}
		if (this.skyboxBuffer != null) {
			this.skyboxBuffer.kill();
		}
		if (this.shadowBuffer != null) {
			this.shadowBuffer.kill();
		}

		this.geometryBuffer = new Framebuffer(this.screenWidth, this.screenHeight);
		this.geometryPositionMap = new Texture(GL_RGBA32F, this.screenWidth, this.screenHeight, GL_RGBA, GL_FLOAT);
		this.geometryNormalMap = new Texture(GL_RGBA32F, this.screenWidth, this.screenHeight, GL_RGBA, GL_FLOAT);
		this.geometrySpecularMap = new Texture(GL_RGBA32F, this.screenWidth, this.screenHeight, GL_RGBA, GL_FLOAT);
		this.geometryColorMap = new Texture(GL_RGBA, this.screenWidth, this.screenHeight, GL_RGBA, GL_FLOAT);
		this.geometryColorIDMap = new Texture(GL_RGBA, this.screenWidth, this.screenHeight, GL_RGBA, GL_FLOAT);
		this.geometryBuffer.bindTextureToBuffer(GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, this.geometryPositionMap.getID());
		this.geometryBuffer.bindTextureToBuffer(GL_COLOR_ATTACHMENT1, GL_TEXTURE_2D, this.geometryNormalMap.getID());
		this.geometryBuffer.bindTextureToBuffer(GL_COLOR_ATTACHMENT2, GL_TEXTURE_2D, this.geometrySpecularMap.getID());
		this.geometryBuffer.bindTextureToBuffer(GL_COLOR_ATTACHMENT3, GL_TEXTURE_2D, this.geometryColorMap.getID());
		this.geometryBuffer.bindTextureToBuffer(GL_COLOR_ATTACHMENT4, GL_TEXTURE_2D, this.geometryColorIDMap.getID());
		this.geometryBuffer.addDepthBuffer();
		this.geometryBuffer.setDrawBuffers(new int[] { GL_COLOR_ATTACHMENT0, GL_COLOR_ATTACHMENT1, GL_COLOR_ATTACHMENT2, GL_COLOR_ATTACHMENT3, GL_COLOR_ATTACHMENT4 });
		this.geometryBuffer.isComplete();

		this.lightingBuffer = new Framebuffer(this.screenWidth, this.screenHeight);
		this.lightingColorMap = new Texture(GL_RGBA, this.screenWidth, this.screenHeight, GL_RGBA, GL_UNSIGNED_BYTE);
		this.lightingBrightnessMap = new Texture(GL_RGBA16F, this.screenWidth, this.screenHeight, GL_RGBA, GL_FLOAT);
		this.lightingBuffer.bindTextureToBuffer(GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, this.lightingColorMap.getID());
		this.lightingBuffer.bindTextureToBuffer(GL_COLOR_ATTACHMENT1, GL_TEXTURE_2D, this.lightingBrightnessMap.getID());
		this.lightingBuffer.setDrawBuffers(new int[] { GL_COLOR_ATTACHMENT0, GL_COLOR_ATTACHMENT1 });
		this.lightingBuffer.isComplete();

		this.shadowBuffer = new Framebuffer(this.screenWidth, this.screenHeight);
		this.shadowDepthMap = new Texture(GL_DEPTH_COMPONENT, this.screenWidth, this.screenHeight, GL_DEPTH_COMPONENT, GL_FLOAT);
		this.shadowBackfaceMap = new Texture(GL_RGBA16F, this.screenWidth, this.screenHeight, GL_RGBA, GL_FLOAT);
		this.shadowBuffer.bindTextureToBuffer(GL_DEPTH_ATTACHMENT, GL_TEXTURE_2D, this.shadowDepthMap.getID());
		this.shadowBuffer.bindTextureToBuffer(GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, this.shadowBackfaceMap.getID());
		this.shadowBuffer.setDrawBuffers(new int[] { GL_COLOR_ATTACHMENT0 });
		this.shadowBuffer.isComplete();
		this.shadowCubemap = new Cubemap(GL_DEPTH_COMPONENT, GL_DEPTH_COMPONENT, GL_FLOAT);

		this.skyboxBuffer = new Framebuffer(this.screenWidth, this.screenHeight);
		this.skyboxColorMap = new Texture(GL_RGBA, this.screenWidth, this.screenHeight, GL_RGBA, GL_FLOAT);
		this.skyboxBuffer.bindTextureToBuffer(GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, this.skyboxColorMap.getID());
		this.skyboxBuffer.setDrawBuffers(new int[] { GL_COLOR_ATTACHMENT0 });
		this.skyboxBuffer.isComplete();

		this.worldFOV = 90f;

		Vec3 cameraPos = new Vec3();
		Vec3 cameraFacing = new Vec3(0, 0, -1);

		if (this.camera != null) {
			cameraPos = this.camera.getPos();
			cameraFacing = this.camera.getFacing();
		}

		this.camera = new Camera((float) Math.toRadians(this.worldFOV), this.screenWidth, this.screenHeight, NEAR, FAR);
		this.camera.setPos(cameraPos);
		this.camera.setFacing(cameraFacing);
	}

	private void setCameraFOV(float degrees) {
		float cameraFOV = degrees;
		Vec3 cameraPos = this.camera.getPos();
		Vec3 cameraFacing = this.camera.getFacing();
		this.camera = new Camera((float) Math.toRadians(cameraFOV), this.screenWidth, this.screenHeight, NEAR, FAR);
		this.camera.setPos(cameraPos);
		this.camera.setFacing(cameraFacing);
	}

	public void setWorldCameraFOV(float degrees) {
		this.worldFOV = degrees;
	}

	public void setWorldScene(int scene) {
		this.world_scene = scene;
	}

	public void setParticleScene(int scene) {
		this.particle_scene = scene;
	}

	public void setShaderCameraUniforms(Shader shader, Camera camera) {
		shader.setUniformMat4("pr_matrix", camera.getProjectionMatrix());
		shader.setUniformMat4("vw_matrix", camera.getViewMatrix());
		shader.setUniform3f("view_pos", camera.getPos());
	}

	public void renderSkybox(boolean b) {
		this.renderSkybox = b;
	}

	public void renderParticles(boolean b) {
		this.renderParticles = b;
	}

	@Override
	protected void _render(Framebuffer outputBuffer) {
		// -- WATER TEXTURE -- : render out water properties to texture
		glViewport(0, 0, waterTextureResolution, waterTextureResolution);
		glEnable(GL_DEPTH_TEST);
		glDepthFunc(GL_LESS);
		glEnable(GL_CULL_FACE);
		glCullFace(GL_BACK);
		glPolygonMode(GL_FRONT, GL_FILL);
		glDisable(GL_BLEND);
		glClearDepth(1); // maximum value
		glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
		this.waterBuffer.bind();
		this.waterTextureShader.enable();
		this.waterTime += Main.getDeltaSeconds();
		this.waterTextureShader.setUniform1f("time", this.waterTime);

		this.waterTextureShader.setUniform1f("u_amplitude", this.waterAttributes.u_amplitude);
		this.waterTextureShader.setUniform1f("u_period", this.waterAttributes.u_period);
		this.waterTextureShader.setUniform1i("nr_sums", this.waterAttributes.nr_sums);

		this.waterTextureShader.setUniform1f("period_mult", this.waterAttributes.period_mult);
		this.waterTextureShader.setUniform1f("amplitude_mult", this.waterAttributes.amplitude_mult);
		this.waterTextureShader.setUniform1f("domain_warp_coeff", this.waterAttributes.domain_warp_coeff);

		screenQuad.render();
		glViewport(0, 0, this.screenWidth, this.screenHeight);

		// -- GEOMETRY -- : render 3d perspective to geometry buffer
		geometryBuffer.bind();
		glEnable(GL_DEPTH_TEST);
		glDepthFunc(GL_LESS);
		glEnable(GL_CULL_FACE);
		glCullFace(GL_BACK);
		glPolygonMode(GL_FRONT, GL_FILL);
		glDisable(GL_BLEND);
		glClearDepth(1); // maximum value
		glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
		Texture.bindingEnabled = true;

		this.waterGeometryShader.enable();
		this.waterGeometryShader.setUniform1f("water_depth", this.waterAttributes.water_depth);
		Scene.skyboxes.get(this.world_scene).bind(GL_TEXTURE4);
		this.waterHeightMap.bind(GL_TEXTURE5);
		this.waterNormalMap.bind(GL_TEXTURE6);

		this.setCameraFOV(this.worldFOV);
		this.setShaderCameraUniforms(this.waterGeometryShader, this.camera);
		Model.renderModels(this.world_scene);

		// -- LIGHTING -- : using information from the geometry buffer, calculate lighting.
		lightingBuffer.bind();
		Shader.LIGHTING.enable();
		glClear(GL_COLOR_BUFFER_BIT);
		glDisable(GL_DEPTH_TEST);
		glDepthMask(true);
		glEnable(GL_BLEND);
		glPolygonMode(GL_FRONT, GL_FILL);
		glBlendFunc(GL_ONE, GL_ONE);

		// TODO split lighting shader into directional and cubemap lighting
		this.geometryPositionMap.bind(GL_TEXTURE0);
		this.geometryNormalMap.bind(GL_TEXTURE1);
		this.geometryColorMap.bind(GL_TEXTURE2);
		this.geometrySpecularMap.bind(GL_TEXTURE3);
		this.shadowDepthMap.bind(GL_TEXTURE4);
		this.shadowBackfaceMap.bind(GL_TEXTURE5);
		this.shadowCubemap.bind(GL_TEXTURE6);

		Shader.LIGHTING.setUniform3f("view_pos", this.camera.getPos());

		// disable to prevent overwriting geometry buffer textures
		// don't forget to re-enable
		Texture.bindingEnabled = false;

		// backfaces should also be able to cast shadows
		glDisable(GL_CULL_FACE);

		// calculate lighting with each light seperately
		ArrayList<Light> lights = Light.lights.get(this.world_scene);
		if (lights == null || lights.size() == 0) {
			System.err.println("PerspectiveScreen : Must have at least 1 light in world scene to render");
			return;
		}
		for (int i = 0; i < lights.size(); i++) {
			// generate depth map for light
			if (lights.get(i).type == Light.DIR_LIGHT) {

				Vec3 lightDir = new Vec3(lights.get(i).dir).normalize();
				Mat4 lightMat = Mat4.lookAt(new Vec3(0), lightDir, new Vec3(0, 1, 0));

				// re-bind directional depth map texture as depth map
				shadowBuffer.bindTextureToBuffer(GL_DEPTH_ATTACHMENT, GL_TEXTURE_2D, this.shadowDepthMap.getID());

				// do this for each cascade near / far plane
				for (int cascade = 0; cascade < SumOfSinesWaterScreen.SHADOW_MAP_NR_CASCADES; cascade++) {
					// calculate orthographic projection matrix
					// generate perspective frustum corners in camera space
					float near = shadowCascades[cascade];
					float far = shadowCascades[cascade + 1];
					float y1 = near * (float) Math.tan(this.camera.getVerticalFOV() / 2f);
					float y2 = far * (float) Math.tan(this.camera.getVerticalFOV() / 2f);

					float aspectRatio = (float) this.screenWidth / (float) this.screenHeight;
					float x1 = y1 * aspectRatio;
					float x2 = y2 * aspectRatio;
					Vec3[] corners = new Vec3[] { new Vec3(x1, y1, -near), new Vec3(-x1, y1, -near), new Vec3(-x1, -y1, -near), new Vec3(x1, -y1, -near), new Vec3(x2, y2, -far), new Vec3(-x2, y2, -far), new Vec3(-x2, -y2, -far), new Vec3(x2, -y2, -far), };

					//we have to normalize the near and far coordinates
					near = (1f / near - 1f / NEAR) / (1f / FAR - 1f / NEAR);
					far = (1f / far - 1f / NEAR) / (1f / FAR - 1f / NEAR);
					Shader.LIGHTING.setUniform1f("shadowMapNear", near);
					Shader.LIGHTING.setUniform1f("shadowMapFar", far);

					// transform frustum corners from camera space to light space
					Mat4 transformMatrix = new Mat4(this.camera.getInvViewMatrix()); // from camera to world space
					transformMatrix.muli(lightMat); // apply rotation to align light dir with -z axis
					for (int j = 0; j < corners.length; j++) {
						corners[j] = transformMatrix.mul(corners[j], 1f);
					}

					// generate the AABB that bounds the corners in light space
					float left = corners[0].x;
					float right = corners[0].x;
					float bottom = corners[0].y;
					float top = corners[0].y;
					near = corners[0].z;
					far = corners[0].z;

					for (Vec3 v : corners) {
						left = Math.min(left, v.x);
						right = Math.max(right, v.x);
						bottom = Math.min(bottom, v.y);
						top = Math.max(top, v.y);
						near = Math.min(near, v.z);
						far = Math.max(far, v.z);
					}

					// construct orthographic projection matrix
					//it's important that all geometry is captured inside this orthographic frustum, so we have to consider
					//stuff that's behind the camera as well. 
					float diff = FAR - NEAR;
					Camera lightCamera = new Camera(left, right, bottom, top, near - diff, far + diff);
					lightCamera.setFacing(lightDir);

					// render shadow map
					shadowBuffer.bind();
					glViewport(0, 0, this.screenWidth, this.screenHeight);
					glEnable(GL_DEPTH_TEST);
					glClear(GL_DEPTH_BUFFER_BIT | GL_COLOR_BUFFER_BIT);
					glDisable(GL_BLEND);
					glEnable(GL_CULL_FACE);
					glCullFace(GL_FRONT);

					Shader.LIGHTING.setUniformMat4("lightSpace_matrix", lightMat.mul(lightCamera.getProjectionMatrix()));
					Shader.DEPTH.enable();

					this.setShaderCameraUniforms(Shader.DEPTH, lightCamera);
					Model.renderModels(this.world_scene);

					// render portion of lit scene
					lightingBuffer.bind();
					glDisable(GL_DEPTH_TEST);
					glEnable(GL_BLEND);
					glDisable(GL_CULL_FACE);
					glCullFace(GL_BACK);
					Shader.LIGHTING.enable();
					lights.get(i).bind(Shader.LIGHTING, i);
					screenQuad.render();
				}
			}
			else {
				Light light = lights.get(i);

				// generate cubemap
				shadowBuffer.bind();
				Shader.CUBE_DEPTH.enable();
				float near = 0.1f;
				float far = 50f;

				Shader.CUBE_DEPTH.setUniform1f("far", far);

				Vec3[][] camVectors = new Vec3[][] { { new Vec3(1, 0, 0), new Vec3(0, -1, 0) }, // -x
						{ new Vec3(-1, 0, 0), new Vec3(0, -1, 0) }, // +x
						{ new Vec3(0, 1, 0), new Vec3(0, 0, 1) }, // -y
						{ new Vec3(0, -1, 0), new Vec3(0, 0, -1) }, // +y
						{ new Vec3(0, 0, 1), new Vec3(0, -1, 0) }, // -z
						{ new Vec3(0, 0, -1), new Vec3(0, -1, 0) }, // +z
				};

				Camera cubemapCamera = new Camera((float) Math.toRadians(90), 1f, 1f, near, far); // aspect ratio of 1
				cubemapCamera.setPos(light.pos);

				glViewport(0, 0, shadowCubemap.getSize(), shadowCubemap.getSize());
				glEnable(GL_DEPTH_TEST);
				glDisable(GL_BLEND);
				glClear(GL_DEPTH_BUFFER_BIT);

				// render each side of cubemap separately
				for (int j = 0; j < 6; j++) {
					cubemapCamera.setFacing(camVectors[j][0]);
					cubemapCamera.setUp(camVectors[j][1]);

					int face = GL_TEXTURE_CUBE_MAP_POSITIVE_X + j;
					shadowBuffer.bindTextureToBuffer(GL_DEPTH_ATTACHMENT, face, shadowCubemap.getID());
					shadowBuffer.bind();
					glClear(GL_DEPTH_BUFFER_BIT);
					// world.render(Shader.CUBE_DEPTH, cubemapCamera);
					this.setShaderCameraUniforms(Shader.CUBE_DEPTH, cubemapCamera);
					Shader.CUBE_DEPTH.enable();
					Model.renderModels(this.world_scene);
				}

				// render lit scene
				lightingBuffer.bind();
				glViewport(0, 0, this.screenWidth, this.screenHeight);
				glDisable(GL_DEPTH_TEST);
				glEnable(GL_BLEND);

				Shader.LIGHTING.enable();
				Shader.LIGHTING.setUniform1f("shadowCubemapFar", far);

				lights.get(i).bind(Shader.LIGHTING, i);
				screenQuad.render();
			}
		}

		Texture.bindingEnabled = true;

		// -- PARTICLES -- : front facing rectangular billboards
		//this renders after lighting, so we can do transparency.
		if (this.renderParticles) {
			lightingBuffer.bind();
			glEnable(GL_DEPTH_TEST);
			glDepthFunc(GL_LESS);
			glEnable(GL_CULL_FACE);
			glCullFace(GL_BACK);
			glPolygonMode(GL_FRONT, GL_FILL);
			glEnable(GL_BLEND);
			glBlendFunc(GL_ONE_MINUS_SRC_ALPHA, GL_ONE);

			this.geometryPositionMap.bind(GL_TEXTURE4);

			Shader.PARTICLE.enable();
			this.setCameraFOV(this.worldFOV);
			this.setShaderCameraUniforms(Shader.PARTICLE, this.camera);
			Model.renderModels(this.particle_scene);
		}

		// -- SKYBOX -- : we'll use this texture in the post-processing step
		if (this.renderSkybox) {
			skyboxBuffer.bind();
			glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
			glDisable(GL_CULL_FACE);
			glDisable(GL_BLEND);
			this.waterAtmosphereShader.enable();
			this.waterAtmosphereShader.setUniformMat4("vw_matrix", this.camera.getViewMatrix());
			this.waterAtmosphereShader.setUniformMat4("pr_matrix", this.camera.getProjectionMatrix());
			SkyboxCube.skyboxCube.render();
		}

		// -- RENDER TO OUTPUT --
		outputBuffer.bind();
		glDisable(GL_DEPTH_TEST);
		glEnable(GL_BLEND);
		glBlendFunc(GL_ONE, GL_ONE_MINUS_SRC_ALPHA);
		Shader.SPLASH.enable();
		Shader.SPLASH.setUniform1f("alpha", 1f);

		if (this.renderSkybox) {
			this.skyboxColorMap.bind(GL_TEXTURE0);
			screenQuad.render();
		}

		this.lightingColorMap.bind(GL_TEXTURE0);
		//this.geometryPositionMap.bind(GL_TEXTURE0);
		screenQuad.render();
	}

}
