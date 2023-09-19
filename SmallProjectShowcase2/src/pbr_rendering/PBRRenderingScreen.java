package pbr_rendering;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL30.*;
import static org.lwjgl.opengl.GL32.GL_TEXTURE_CUBE_MAP_SEAMLESS;
import static org.lwjgl.opengl.GL13.*;
import static org.lwjgl.opengl.GL12.*;

import java.nio.FloatBuffer;
import java.util.ArrayList;

import lwjglengine.graphics.Cubemap;
import lwjglengine.graphics.Framebuffer;
import lwjglengine.graphics.Shader;
import lwjglengine.graphics.Texture;
import lwjglengine.model.Model;
import lwjglengine.player.Camera;
import lwjglengine.scene.Light;
import lwjglengine.scene.Scene;
import lwjglengine.screen.PerspectiveScreen;
import lwjglengine.screen.Screen;
import lwjglengine.screen.ScreenQuad;
import lwjglengine.screen.SkyboxCube;
import myutils.v10.math.Mat4;
import myutils.v10.math.Vec3;

public class PBRRenderingScreen extends Screen {
	//TODO 
	// - figure out how to make actually good shadows. 

	//i'm just going to reinterpret blinn-phong textures into the pbr pipeline
	//note that ambient occlusion is missing, i'm planning to just do screen space AO. 
	//PHONG     -> PBR
	//diffuse   -> albedo
	//specular  -> roughness
	//shininess -> metalness
	//normal    -> normal

	private static final float NEAR = 0.1f;
	private static final float FAR = 400.0f;

	private static final int SHADOW_MAP_NR_CASCADES = 7;
	private static float[] shadowCascades = new float[] { NEAR, 1, 3, 7, 15, 30, 100, FAR };

	private Framebuffer geometryBuffer;
	private Framebuffer lightingBuffer;
	private Framebuffer shadowBuffer;
	private Framebuffer skyboxBuffer;
	private Framebuffer postprocessBuffer;

	private Texture geometryPositionMap; // RGB: pos, A: normalized depth; 0 - 1
	private Texture geometryNormalMap; // RGB: normal
	private Texture geometryAttrMap; // R: roughness, G: metallic
	private Texture geometryColorMap; // RGB: color, A: alpha
	private Texture geometryColorIDMap; // RGB: colorID

	private Texture lightingColorMap; // RGB: HDR color

	private Texture shadowDepthMap; // R: depth
	private Texture shadowBackfaceMap; // R: isBackface
	private Cubemap shadowCubemap; // R: depth

	private Texture skyboxColorMap; // RGB: color

	private Texture postprocessColorMap; //RGB: color

	//we'll kill this when we kill the screen. 
	//this should be an hdr skybox. 
	private Cubemap skyboxCubemap = null;
	private Cubemap irradianceCubemap = null;
	private Cubemap prefilterCubemap = null;

	private Texture brdfTexture = null;

	private int worldScene;

	private float worldFOV = 90f;

	private Shader geometryShader, lightingShader, ambientShader, postprocessShader;

	private PBRRenderSettings renderSettings;

	public PBRRenderingScreen() {
		Vec3 cameraPos = new Vec3();
		Vec3 cameraFacing = new Vec3(0, 0, -1);
		this.camera = new Camera((float) Math.toRadians(this.worldFOV), this.screenWidth, this.screenHeight, NEAR, FAR);
		this.camera.setPos(cameraPos);
		this.camera.setFacing(cameraFacing);

		this.geometryShader = new Shader("/pbr_rendering/geometry.vert", "/pbr_rendering/geometry.frag");
		this.geometryShader.setUniform1i("tex_diffuse", 0);
		this.geometryShader.setUniform1i("tex_roughness", 1);
		this.geometryShader.setUniform1i("tex_metalness", 2);
		this.geometryShader.setUniform1i("tex_normal", 3);
		this.geometryShader.setUniform1i("tex_displacement", 4);
		this.geometryShader.setUniform1i("enableParallaxMapping", 0);
		this.geometryShader.setUniform1i("enableTexScaling", 1);

		this.lightingShader = new Shader("/pbr_rendering/lighting.vert", "/pbr_rendering/lighting.frag");
		this.lightingShader.setUniform1i("tex_position", 0);
		this.lightingShader.setUniform1i("tex_normal", 1);
		this.lightingShader.setUniform1i("tex_diffuse", 2);
		this.lightingShader.setUniform1i("tex_attr", 3);
		this.lightingShader.setUniform1i("shadowMap", 4);
		this.lightingShader.setUniform1i("shadowBackfaceMap", 5);
		this.lightingShader.setUniform1i("shadowCubemap", 6);

		this.ambientShader = new Shader("/pbr_rendering/ambient.vert", "/pbr_rendering/ambient.frag");
		this.ambientShader.setUniform1i("tex_position", 0);
		this.ambientShader.setUniform1i("tex_normal", 1);
		this.ambientShader.setUniform1i("tex_diffuse", 2);
		this.ambientShader.setUniform1i("tex_attr", 3);
		this.ambientShader.setUniform1i("shadowMap", 4);
		this.ambientShader.setUniform1i("shadowBackfaceMap", 5);
		this.ambientShader.setUniform1i("shadowCubemap", 6);
		this.ambientShader.setUniform1i("tex_irradiance", 7);
		this.ambientShader.setUniform1i("tex_prefilter", 8);
		this.ambientShader.setUniform1i("brdfLUT", 9);
		this.ambientShader.setUniform1i("tex_lit_color", 10);

		this.postprocessShader = new Shader("/pbr_rendering/postprocess.vert", "/pbr_rendering/postprocess.frag");
		this.postprocessShader.setUniform1i("tex_color", 0);

		this.brdfTexture = generateBRDF();

		this.renderSettings = new PBRRenderSettings();
	}

	@Override
	public void buildBuffers() {
		if (this.geometryBuffer != null) {
			this.geometryBuffer.kill();
		}
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
		if (this.postprocessBuffer != null) {
			this.postprocessBuffer.kill();
		}

		this.geometryBuffer = new Framebuffer(this.screenWidth, this.screenHeight);
		this.geometryPositionMap = new Texture(GL_RGBA32F, this.screenWidth, this.screenHeight, GL_RGBA, GL_FLOAT);
		this.geometryNormalMap = new Texture(GL_RGBA32F, this.screenWidth, this.screenHeight, GL_RGBA, GL_FLOAT);
		this.geometryAttrMap = new Texture(GL_RGBA32F, this.screenWidth, this.screenHeight, GL_RGBA, GL_FLOAT);
		this.geometryColorMap = new Texture(GL_RGBA, this.screenWidth, this.screenHeight, GL_RGBA, GL_FLOAT);
		this.geometryColorIDMap = new Texture(GL_RGBA, this.screenWidth, this.screenHeight, GL_RGBA, GL_FLOAT);
		this.geometryBuffer.bindTextureToBuffer(GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, this.geometryPositionMap.getID());
		this.geometryBuffer.bindTextureToBuffer(GL_COLOR_ATTACHMENT1, GL_TEXTURE_2D, this.geometryNormalMap.getID());
		this.geometryBuffer.bindTextureToBuffer(GL_COLOR_ATTACHMENT2, GL_TEXTURE_2D, this.geometryAttrMap.getID());
		this.geometryBuffer.bindTextureToBuffer(GL_COLOR_ATTACHMENT3, GL_TEXTURE_2D, this.geometryColorMap.getID());
		this.geometryBuffer.bindTextureToBuffer(GL_COLOR_ATTACHMENT4, GL_TEXTURE_2D, this.geometryColorIDMap.getID());
		this.geometryBuffer.addDepthBuffer();
		this.geometryBuffer.setDrawBuffers(new int[] { GL_COLOR_ATTACHMENT0, GL_COLOR_ATTACHMENT1, GL_COLOR_ATTACHMENT2, GL_COLOR_ATTACHMENT3, GL_COLOR_ATTACHMENT4 });
		this.geometryBuffer.isComplete();

		this.lightingBuffer = new Framebuffer(this.screenWidth, this.screenHeight);
		this.lightingColorMap = new Texture(GL_RGBA, this.screenWidth, this.screenHeight, GL_RGBA, GL_UNSIGNED_BYTE);
		this.lightingBuffer.bindTextureToBuffer(GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, this.lightingColorMap.getID());
		this.lightingBuffer.setDrawBuffers(new int[] { GL_COLOR_ATTACHMENT0 });
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

		this.postprocessBuffer = new Framebuffer(this.screenWidth, this.screenHeight);
		this.postprocessColorMap = new Texture(GL_RGBA32F, this.screenWidth, this.screenHeight, GL_RGBA, GL_FLOAT);
		this.postprocessBuffer.bindTextureToBuffer(GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, this.postprocessColorMap.getID());
		this.postprocessBuffer.setDrawBuffers(new int[] { GL_COLOR_ATTACHMENT0 });
		this.postprocessBuffer.isComplete();
	}

	public class PBRRenderSettings {
		private float exposure = 2f;
		private float gamma = 1f;

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
	}

	public PBRRenderSettings getRenderSettings() {
		return this.renderSettings;
	}

	private void setCameraFOV(float degrees) {
		float cameraFOV = degrees;
		Vec3 cameraPos = this.camera.getPos();
		Vec3 cameraFacing = this.camera.getFacing();
		this.camera = new Camera((float) Math.toRadians(cameraFOV), this.screenWidth, this.screenHeight, NEAR, FAR);
		this.camera.setPos(cameraPos);
		this.camera.setFacing(cameraFacing);
	}

	public void setShaderCameraUniforms(Shader shader, Camera camera) {
		shader.setUniformMat4("pr_matrix", camera.getProjectionMatrix());
		shader.setUniformMat4("vw_matrix", camera.getViewMatrix());
		shader.setUniform3f("view_pos", camera.getPos());
	}

	public void setWorldCameraFOV(float degrees) {
		this.worldFOV = degrees;
	}

	public void setWorldScene(int scene) {
		this.worldScene = scene;
	}

	public static Texture generateBRDF() {
		Shader brdfShader = new Shader("/pbr_rendering/brdf.vert", "/pbr_rendering/brdf.frag");
		int resolution = 512;

		int texID = glGenTextures();
		glBindTexture(GL_TEXTURE_2D, texID);
		glTexImage2D(GL_TEXTURE_2D, 0, GL_RG16F, resolution, resolution, 0, GL_RG, GL_FLOAT, (FloatBuffer) null);
		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);

		Framebuffer captureFBO = new Framebuffer(resolution, resolution);
		captureFBO.bindTextureToBuffer(GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, texID);

		glViewport(0, 0, resolution, resolution);
		glEnable(GL_DEPTH_TEST);
		glDisable(GL_BLEND);
		glDisable(GL_CULL_FACE);

		captureFBO.bind();
		brdfShader.enable();
		ScreenQuad.screenQuad.render();

		captureFBO.unbindTextureAtBuffer(GL_COLOR_ATTACHMENT0);
		captureFBO.kill();
		brdfShader.kill();

		return new Texture(texID);
	}

	public static Cubemap convertEquirectangularToCubemap(Texture equirectangularMap) {
		Shader mappingShader = new Shader("/pbr_rendering/equirectangular_mapping.vert", "/pbr_rendering/equirectangular_mapping.frag");
		mappingShader.setUniform1i("equirectangularMap", 0);

		int cubemapRes = 512;
		Cubemap cubemap = new Cubemap(GL_RGBA16F, GL_RGBA, GL_FLOAT, cubemapRes);
		Framebuffer captureFBO = new Framebuffer(cubemapRes, cubemapRes);

		Vec3[][] camVectors = new Vec3[][] { { new Vec3(1, 0, 0), new Vec3(0, -1, 0) }, // -x
				{ new Vec3(-1, 0, 0), new Vec3(0, -1, 0) }, // +x
				{ new Vec3(0, 1, 0), new Vec3(0, 0, 1) }, // -y
				{ new Vec3(0, -1, 0), new Vec3(0, 0, -1) }, // +y
				{ new Vec3(0, 0, 1), new Vec3(0, -1, 0) }, // -z
				{ new Vec3(0, 0, -1), new Vec3(0, -1, 0) }, // +z
		};

		Camera cubemapCamera = new Camera((float) Math.toRadians(90), 1f, 1f, 0.1f, 50f); // aspect ratio of 1
		cubemapCamera.setPos(new Vec3(0));

		glViewport(0, 0, cubemapRes, cubemapRes);
		glEnable(GL_DEPTH_TEST);
		glDisable(GL_BLEND);
		glDisable(GL_CULL_FACE);
		for (int i = 0; i < 6; i++) {
			cubemapCamera.setFacing(camVectors[i][0]);
			cubemapCamera.setUp(camVectors[i][1]);
			mappingShader.enable();
			mappingShader.setUniformMat4("pr_matrix", cubemapCamera.getProjectionMatrix());
			mappingShader.setUniformMat4("vw_matrix", cubemapCamera.getViewMatrix());

			captureFBO.bindTextureToBuffer(GL_COLOR_ATTACHMENT0, GL_TEXTURE_CUBE_MAP_POSITIVE_X + i, cubemap.getID());
			captureFBO.bind();
			glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
			equirectangularMap.bind(GL_TEXTURE0);
			SkyboxCube.skyboxCube.render();
		}

		captureFBO.unbindTextureAtBuffer(GL_COLOR_ATTACHMENT0);
		captureFBO.kill();
		mappingShader.kill();

		return cubemap;
	}

	public static Cubemap generateIrradianceMap(Cubemap environmentMap) {
		Shader mappingShader = new Shader("/pbr_rendering/irradiance_mapping.vert", "/pbr_rendering/irradiance_mapping.frag");
		mappingShader.setUniform1i("environmentMap", 0);

		int cubemapRes = 32;
		Cubemap irradianceCubemap = new Cubemap(GL_RGBA16F, GL_RGBA, GL_FLOAT, cubemapRes);
		Framebuffer captureFBO = new Framebuffer(cubemapRes, cubemapRes);

		Vec3[][] camVectors = new Vec3[][] { { new Vec3(1, 0, 0), new Vec3(0, -1, 0) }, // -x
				{ new Vec3(-1, 0, 0), new Vec3(0, -1, 0) }, // +x
				{ new Vec3(0, 1, 0), new Vec3(0, 0, 1) }, // -y
				{ new Vec3(0, -1, 0), new Vec3(0, 0, -1) }, // +y
				{ new Vec3(0, 0, 1), new Vec3(0, -1, 0) }, // -z
				{ new Vec3(0, 0, -1), new Vec3(0, -1, 0) }, // +z
		};

		Camera cubemapCamera = new Camera((float) Math.toRadians(90), 1f, 1f, 0.1f, 50f); // aspect ratio of 1
		cubemapCamera.setPos(new Vec3(0));

		glViewport(0, 0, cubemapRes, cubemapRes);
		glEnable(GL_DEPTH_TEST);
		glDisable(GL_BLEND);
		glDisable(GL_CULL_FACE);
		for (int i = 0; i < 6; i++) {
			cubemapCamera.setFacing(camVectors[i][0]);
			cubemapCamera.setUp(camVectors[i][1]);
			mappingShader.enable();
			mappingShader.setUniformMat4("pr_matrix", cubemapCamera.getProjectionMatrix());
			mappingShader.setUniformMat4("vw_matrix", cubemapCamera.getViewMatrix());

			captureFBO.bindTextureToBuffer(GL_COLOR_ATTACHMENT0, GL_TEXTURE_CUBE_MAP_POSITIVE_X + i, irradianceCubemap.getID());
			captureFBO.bind();
			glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
			environmentMap.bind(GL_TEXTURE0);
			SkyboxCube.skyboxCube.render();
		}

		captureFBO.unbindTextureAtBuffer(GL_COLOR_ATTACHMENT0);
		captureFBO.kill();
		mappingShader.kill();

		return irradianceCubemap;
	}

	public static Cubemap generatePrefilterMap(Cubemap environmentMap) {
		Shader prefilterShader = new Shader("/pbr_rendering/prefiltering.vert", "/pbr_rendering/prefiltering.frag");
		prefilterShader.setUniform1i("environmentMap", 0);

		//generate custom cubemap with mipmaps
		int cubemapRes = 128; //this has to be a power of 2
		Cubemap prefilterMap = null;
		{
			int prefilterMapID = glGenTextures();
			glBindTexture(GL_TEXTURE_CUBE_MAP, prefilterMapID);
			for (int i = 0; i < 6; i++) {
				glTexImage2D(GL_TEXTURE_CUBE_MAP_POSITIVE_X + i, 0, GL_RGBA16F, cubemapRes, cubemapRes, 0, GL_RGBA, GL_FLOAT, (FloatBuffer) null);
			}
			glTexParameteri(GL_TEXTURE_CUBE_MAP, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
			glTexParameteri(GL_TEXTURE_CUBE_MAP, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
			glTexParameteri(GL_TEXTURE_CUBE_MAP, GL_TEXTURE_WRAP_R, GL_CLAMP_TO_EDGE);
			glTexParameteri(GL_TEXTURE_CUBE_MAP, GL_TEXTURE_MIN_FILTER, GL_LINEAR_MIPMAP_LINEAR);
			glTexParameteri(GL_TEXTURE_CUBE_MAP, GL_TEXTURE_MAG_FILTER, GL_LINEAR);

			glGenerateMipmap(GL_TEXTURE_CUBE_MAP);

			prefilterMap = new Cubemap(prefilterMapID);
		}

		//render to prefilter map
		Vec3[][] camVectors = new Vec3[][] { { new Vec3(1, 0, 0), new Vec3(0, -1, 0) }, // -x
				{ new Vec3(-1, 0, 0), new Vec3(0, -1, 0) }, // +x
				{ new Vec3(0, 1, 0), new Vec3(0, 0, 1) }, // -y
				{ new Vec3(0, -1, 0), new Vec3(0, 0, -1) }, // +y
				{ new Vec3(0, 0, 1), new Vec3(0, -1, 0) }, // -z
				{ new Vec3(0, 0, -1), new Vec3(0, -1, 0) }, // +z
		};

		Camera cubemapCamera = new Camera((float) Math.toRadians(90), 1f, 1f, 0.1f, 50f); // aspect ratio of 1
		cubemapCamera.setPos(new Vec3(0));

		int maxMipLevels = 5;
		for (int mip = 0; mip < maxMipLevels; mip++) {
			// reisze framebuffer according to mip-level size.
			int mipRes = (cubemapRes >> mip);

			glViewport(0, 0, mipRes, mipRes);
			glEnable(GL_DEPTH_TEST);
			glDisable(GL_BLEND);
			glDisable(GL_CULL_FACE);

			Framebuffer captureFBO = new Framebuffer(mipRes, mipRes);

			float roughness = (float) mip / (float) (maxMipLevels - 1);
			prefilterShader.setUniform1f("roughness", roughness);

			//render out the sides of the current mip level. 
			for (int i = 0; i < 6; i++) {
				cubemapCamera.setFacing(camVectors[i][0]);
				cubemapCamera.setUp(camVectors[i][1]);
				prefilterShader.enable();
				prefilterShader.setUniformMat4("pr_matrix", cubemapCamera.getProjectionMatrix());
				prefilterShader.setUniformMat4("vw_matrix", cubemapCamera.getViewMatrix());

				captureFBO.bindTextureToBuffer(GL_COLOR_ATTACHMENT0, GL_TEXTURE_CUBE_MAP_POSITIVE_X + i, prefilterMap.getID(), mip);
				captureFBO.bind();
				glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
				environmentMap.bind(GL_TEXTURE0);
				SkyboxCube.skyboxCube.render();
			}

			captureFBO.unbindTextureAtBuffer(GL_COLOR_ATTACHMENT0);
			captureFBO.kill();
		}

		prefilterShader.kill();

		return prefilterMap;
	}

	public void setSkybox(Texture hdrEquirectangularMap) {
		Cubemap environmentMap = convertEquirectangularToCubemap(hdrEquirectangularMap);
		this.setSkybox(environmentMap);
	}

	public void setSkybox(Cubemap skybox) {
		if (this.skyboxCubemap != null) {
			this.skyboxCubemap.kill();
			this.irradianceCubemap.kill();
			this.prefilterCubemap.kill();
			this.skyboxCubemap = null;
			this.irradianceCubemap = null;
			this.prefilterCubemap = null;
		}

		this.skyboxCubemap = skybox;
		this.irradianceCubemap = generateIrradianceMap(skybox);
		this.prefilterCubemap = generatePrefilterMap(skybox);
	}

	@Override
	protected void _render(Framebuffer outputBuffer) {
		if (this.skyboxCubemap == null) {
			System.err.println("PBRRenderingScreen : Must have skybox to render");
			return;
		}

		// -- GEOMETRY -- : render 3d perspective to geometry buffer
		geometryBuffer.bind();
		glEnable(GL_DEPTH_TEST);
		glEnable(GL_TEXTURE_CUBE_MAP_SEAMLESS);
		glDepthFunc(GL_LESS);
		glEnable(GL_CULL_FACE);
		glCullFace(GL_BACK);
		glPolygonMode(GL_FRONT, GL_FILL);
		glDisable(GL_BLEND);
		glClearDepth(1); // maximum value
		glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
		Texture.bindingEnabled = true;

		this.geometryShader.enable();
		this.setCameraFOV(this.worldFOV);
		this.setShaderCameraUniforms(this.geometryShader, this.camera);
		Model.renderModels(this.worldScene);

		// -- LIGHTING -- : using information from the geometry buffer, calculate lighting.
		this.lightingBuffer.bind();
		this.lightingShader.enable();
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
		this.geometryAttrMap.bind(GL_TEXTURE3);
		this.shadowDepthMap.bind(GL_TEXTURE4);
		this.shadowBackfaceMap.bind(GL_TEXTURE5);
		this.shadowCubemap.bind(GL_TEXTURE6);

		this.lightingShader.setUniform3f("view_pos", this.camera.getPos());

		// disable to prevent overwriting geometry buffer textures
		// don't forget to re-enable
		Texture.bindingEnabled = false;

		// backfaces should also be able to cast shadows
		glDisable(GL_CULL_FACE);

		// calculate lighting with each light seperately
		ArrayList<Light> lights = Light.lights.get(this.worldScene);
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
				for (int cascade = 0; cascade < PBRRenderingScreen.SHADOW_MAP_NR_CASCADES; cascade++) {
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
					this.lightingShader.setUniform1f("shadowMapNear", near);
					this.lightingShader.setUniform1f("shadowMapFar", far);

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

					this.lightingShader.setUniformMat4("lightSpace_matrix", lightMat.mul(lightCamera.getProjectionMatrix()));
					Shader.DEPTH.enable();

					this.setShaderCameraUniforms(Shader.DEPTH, lightCamera);
					Model.renderModels(this.worldScene);

					// render portion of lit scene
					this.lightingBuffer.bind();
					glDisable(GL_DEPTH_TEST);
					glEnable(GL_BLEND);
					glDisable(GL_CULL_FACE);
					glCullFace(GL_BACK);
					this.lightingShader.enable();
					lights.get(i).bind(this.lightingShader, i);
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
					Model.renderModels(this.worldScene);
				}

				// render lit scene
				lightingBuffer.bind();
				glViewport(0, 0, this.screenWidth, this.screenHeight);
				glDisable(GL_DEPTH_TEST);
				glEnable(GL_BLEND);

				this.lightingShader.enable();
				this.lightingShader.setUniform1f("shadowCubemapFar", far);

				lights.get(i).bind(Shader.LIGHTING, i);
				screenQuad.render();
			}
		}

		Texture.bindingEnabled = true;

		// -- SKYBOX --
		if (this.skyboxCubemap != null) {
			skyboxBuffer.bind();
			glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
			glDisable(GL_CULL_FACE);
			glDisable(GL_BLEND);
			Shader.SKYBOX.enable();
			Shader.SKYBOX.setUniformMat4("vw_matrix", this.camera.getViewMatrix());
			Shader.SKYBOX.setUniformMat4("pr_matrix", this.camera.getProjectionMatrix());
			this.skyboxCubemap.bind(GL_TEXTURE0);
			SkyboxCube.skyboxCube.render();
		}

		// -- COMPOSITING --
		{
			this.postprocessBuffer.bind();
			glClear(GL_COLOR_BUFFER_BIT);
			glDisable(GL_DEPTH_TEST);
			glEnable(GL_BLEND);
			glBlendFunc(GL_ONE, GL_ONE_MINUS_SRC_ALPHA);

			//transfer skybox
			Shader.SPLASH.enable();
			Shader.SPLASH.setUniform1f("alpha", 1f);
			this.skyboxColorMap.bind(GL_TEXTURE0);
			screenQuad.render();

			//transfer lit models
			this.ambientShader.enable();
			this.ambientShader.setUniform3f("view_pos", this.camera.getPos());
			this.geometryPositionMap.bind(GL_TEXTURE0);
			this.geometryNormalMap.bind(GL_TEXTURE1);
			this.geometryColorMap.bind(GL_TEXTURE2);
			this.geometryAttrMap.bind(GL_TEXTURE3);
			this.shadowDepthMap.bind(GL_TEXTURE4);
			this.shadowBackfaceMap.bind(GL_TEXTURE5);
			this.shadowCubemap.bind(GL_TEXTURE6);
			this.irradianceCubemap.bind(GL_TEXTURE7);
			this.prefilterCubemap.bind(GL_TEXTURE8);
			this.brdfTexture.bind(GL_TEXTURE9);
			this.lightingColorMap.bind(GL_TEXTURE10);
			screenQuad.render();
		}

		// -- POSTPROCESS AND RENDER TO OUTPUT -- : hdr tonemapping and gamma correction
		outputBuffer.bind();
		glDisable(GL_DEPTH_TEST);
		glEnable(GL_BLEND);
		glBlendFunc(GL_ONE, GL_ONE_MINUS_SRC_ALPHA);
		this.postprocessShader.enable();
		this.postprocessShader.setUniform1f("gamma", this.renderSettings.getGamma());
		this.postprocessShader.setUniform1f("exposure", this.renderSettings.getExposure());
		this.postprocessColorMap.bind(GL_TEXTURE0);
		screenQuad.render();
	}

	@Override
	protected void _kill() {
		this.geometryBuffer.kill();
		this.lightingBuffer.kill();
		this.shadowBuffer.kill();
		this.skyboxBuffer.kill();
		this.postprocessBuffer.kill();

		if (this.skyboxCubemap != null) {
			this.skyboxCubemap.kill();
		}
		if (this.irradianceCubemap != null) {
			this.irradianceCubemap.kill();
		}
		if (this.prefilterCubemap != null) {
			this.prefilterCubemap.kill();
		}

		this.brdfTexture.kill();

		this.geometryShader.kill();
		this.lightingShader.kill();
		this.ambientShader.kill();
		this.postprocessShader.kill();
	}

}
