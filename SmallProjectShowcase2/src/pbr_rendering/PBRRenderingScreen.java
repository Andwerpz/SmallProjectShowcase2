package pbr_rendering;

import static org.lwjgl.opengl.GL13.GL_TEXTURE0;
import static org.lwjgl.opengl.GL13.GL_TEXTURE1;
import static org.lwjgl.opengl.GL13.GL_TEXTURE2;
import static org.lwjgl.opengl.GL13.GL_TEXTURE3;
import static org.lwjgl.opengl.GL13.GL_TEXTURE4;
import static org.lwjgl.opengl.GL13.GL_TEXTURE5;
import static org.lwjgl.opengl.GL13.GL_TEXTURE6;
import static org.lwjgl.opengl.GL13.GL_TEXTURE_CUBE_MAP_POSITIVE_X;
import static org.lwjgl.opengl.GL30.GL_COLOR_ATTACHMENT0;
import static org.lwjgl.opengl.GL30.GL_COLOR_ATTACHMENT1;
import static org.lwjgl.opengl.GL30.GL_COLOR_ATTACHMENT2;
import static org.lwjgl.opengl.GL30.GL_COLOR_ATTACHMENT3;
import static org.lwjgl.opengl.GL30.GL_COLOR_ATTACHMENT4;
import static org.lwjgl.opengl.GL30.GL_DEPTH_ATTACHMENT;
import static org.lwjgl.opengl.GL30.GL_RGBA16F;
import static org.lwjgl.opengl.GL30.GL_RGBA32F;
import static org.lwjgl.opengl.GL11.*;

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
import lwjglengine.screen.SkyboxCube;
import myutils.v10.math.Mat4;
import myutils.v10.math.Vec3;

public class PBRRenderingScreen extends Screen {

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

	private int worldScene;

	private float worldFOV = 90f;

	private Shader geometryShader, lightingShader;

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

	@Override
	protected void _render(Framebuffer outputBuffer) {
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
		if (Scene.skyboxes.containsKey(this.worldScene)) {
			skyboxBuffer.bind();
			glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
			glDisable(GL_CULL_FACE);
			glDisable(GL_BLEND);
			Shader.SKYBOX.enable();
			Shader.SKYBOX.setUniformMat4("vw_matrix", this.camera.getViewMatrix());
			Shader.SKYBOX.setUniformMat4("pr_matrix", this.camera.getProjectionMatrix());
			Scene.skyboxes.get(this.worldScene).bind(GL_TEXTURE0);
			SkyboxCube.skyboxCube.render();
		}
		else {
			System.err.println("PerspectiveScreen : NO SKYBOX ENTRY FOR SCENE " + this.worldScene);
		}

		// -- RENDER TO OUTPUT --
		outputBuffer.bind();
		glDisable(GL_DEPTH_TEST);
		glEnable(GL_BLEND);
		glBlendFunc(GL_ONE, GL_ONE_MINUS_SRC_ALPHA);
		Shader.SPLASH.enable();
		Shader.SPLASH.setUniform1f("alpha", 1f);

		this.skyboxColorMap.bind(GL_TEXTURE0);
		screenQuad.render();

		this.lightingColorMap.bind(GL_TEXTURE0);
		screenQuad.render();
	}

	@Override
	protected void _kill() {
		this.geometryBuffer.kill();
		this.lightingBuffer.kill();
		this.shadowBuffer.kill();
		this.skyboxBuffer.kill();

		this.geometryShader.kill();
		this.lightingShader.kill();
	}

}
