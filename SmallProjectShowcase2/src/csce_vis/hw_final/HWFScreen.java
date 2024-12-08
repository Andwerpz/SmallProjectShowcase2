package csce_vis.hw_final;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL12.*;
import static org.lwjgl.opengl.GL14.*;
import static org.lwjgl.opengl.GL13.*;
import static org.lwjgl.opengl.GL30.*;

import java.awt.image.BufferedImage;
import java.nio.FloatBuffer;
import java.util.ArrayList;

import lwjglengine.graphics.Cubemap;
import lwjglengine.graphics.Framebuffer;
import lwjglengine.graphics.Material;
import lwjglengine.graphics.Shader;
import lwjglengine.graphics.Texture;
import lwjglengine.model.Model;
import lwjglengine.model.ModelInstance;
import lwjglengine.model.ModelTransform;
import lwjglengine.model.VertexArray;
import lwjglengine.player.Camera;
import lwjglengine.scene.Light;
import lwjglengine.scene.Scene;
import lwjglengine.screen.Screen;
import lwjglengine.screen.SkyboxCube;
import lwjglengine.util.ShaderUtils;
import myutils.file.FileUtils;
import myutils.math.Mat4;
import myutils.math.Vec2;
import myutils.math.Vec3;

public class HWFScreen extends Screen {

	private static final float NEAR = 0.1f;
	private static final float FAR = 40000.0f;

	private static final int SKYBOX_RES = 1024;

	private final int WATER_SCENE = Scene.generateScene();
	private int[] world_scenes;

	private float worldFOV = 90f;

	private Framebuffer waterBuffer;
	private Framebuffer geometryBuffer;
	private Framebuffer skyboxBuffer;

	public Texture waterPositionMap; // RGB: pos, A: normalized depth; 0 - 1
	public Texture waterNormalMap; // RGB: normal
	public Texture waterSpecularMap; // RGB: specular, A: shininess
	public Texture waterColorMap; // RGB: color, A: alpha
	public Texture waterColorIDMap; // RGB: colorID

	public Texture geometryColorMap;

	public Texture skyboxColorMap; // RGB: color

	private Cubemap skyboxCubemap, spaceSkybox;
	private Shader skyboxShader;
	private Framebuffer skyboxFramebuffer;

	private Shader waterGeometryShader;
	private Model waterModel;
	private ModelInstance waterInst;
	public WaveCascade cascadeLong, cascadeMed, cascadeShort;

	private Shader worldGeometryShader;

	public HWFWindow.Options options;

	public HWFScreen() {
		Vec3 cameraPos = new Vec3();
		Vec3 cameraFacing = new Vec3(0, 0, -1);

		this.camera = new Camera((float) Math.toRadians(this.worldFOV), this.screenWidth, this.screenHeight, NEAR, FAR);
		this.camera.setPos(cameraPos);
		this.camera.setFacing(cameraFacing);

		this.waterGeometryShader = ShaderUtils.createShader("/csce_vis/hw_final/water.vert", "/csce_vis/hw_final/water.frag");
		this.waterGeometryShader.setUniform1i("tex_diffuse", 0);
		this.waterGeometryShader.setUniform1i("tex_specular", 1);
		this.waterGeometryShader.setUniform1i("tex_shininess", 2);
		this.waterGeometryShader.setUniform1i("tex_normal", 3);
		this.waterGeometryShader.setUniform1i("tex_displacement", 4);
		this.waterGeometryShader.setUniform1i("skyboxCubemap", 5);
		this.waterGeometryShader.setUniform1i("dispTextureLong", 6);
		this.waterGeometryShader.setUniform1i("derivativeTextureLong", 7);
		this.waterGeometryShader.setUniform1i("dispTextureMed", 8);
		this.waterGeometryShader.setUniform1i("derivativeTextureMed", 9);
		this.waterGeometryShader.setUniform1i("dispTextureShort", 10);
		this.waterGeometryShader.setUniform1i("derivativeTextureShort", 11);

		this.waterModel = this.createWaterMesh();
		this.waterInst = new ModelInstance(this.waterModel, WATER_SCENE);

		this.worldGeometryShader = ShaderUtils.createShader("/csce_vis/hw_final/geometry.vert", "/csce_vis/hw_final/geometry.frag");
		this.worldGeometryShader.setUniform1i("tex_diffuse", 0);
		this.worldGeometryShader.setUniform1i("tex_specular", 1);
		this.worldGeometryShader.setUniform1i("tex_shininess", 2);
		this.worldGeometryShader.setUniform1i("tex_normal", 3);
		this.worldGeometryShader.setUniform1i("tex_displacement", 4);
		this.worldGeometryShader.setUniform1i("waterPositionTexture", 5);
		this.worldGeometryShader.setUniform1i("waterColorTexture", 6);

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

		//		this.skyboxShader = ShaderUtils.createShader("/csce_vis/hw_final/water_atmosphere.vert", "/csce_vis/hw_final/water_atmosphere.frag");
		this.skyboxCubemap = new Cubemap(GL_RGBA16F, GL_RGBA, GL_FLOAT, SKYBOX_RES);
		this.skyboxFramebuffer = new Framebuffer(SKYBOX_RES, SKYBOX_RES);
	}

	@Override
	protected void _kill() {
		this.waterBuffer.kill();
		this.geometryBuffer.kill();
		this.skyboxBuffer.kill();

		Scene.removeScene(WATER_SCENE);
		this.waterModel.kill();
		this.waterGeometryShader.kill();
		this.worldGeometryShader.kill();

		this.skyboxShader.kill();
		this.skyboxCubemap.kill();
	}

	@Override
	public void buildBuffers() {
		if (this.waterBuffer != null) {
			this.waterBuffer.kill();
		}
		if (this.geometryBuffer != null) {
			this.geometryBuffer.kill();
		}
		if (this.skyboxBuffer != null) {
			this.skyboxBuffer.kill();
		}

		this.waterBuffer = new Framebuffer(this.screenWidth, this.screenHeight);
		this.waterPositionMap = new Texture(this.screenWidth, this.screenHeight, GL_RGBA32F, GL_RGBA, GL_FLOAT);
		this.waterNormalMap = new Texture(this.screenWidth, this.screenHeight, GL_RGBA32F, GL_RGBA, GL_FLOAT);
		this.waterSpecularMap = new Texture(this.screenWidth, this.screenHeight, GL_RGBA32F, GL_RGBA, GL_FLOAT);
		this.waterColorMap = new Texture(this.screenWidth, this.screenHeight, GL_RGBA32F, GL_RGBA, GL_FLOAT);
		this.waterColorIDMap = new Texture(this.screenWidth, this.screenHeight, GL_RGBA8, GL_RGBA, GL_FLOAT);
		this.waterBuffer.bindTextureToBuffer(GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, this.waterPositionMap.getID());
		this.waterBuffer.bindTextureToBuffer(GL_COLOR_ATTACHMENT1, GL_TEXTURE_2D, this.waterNormalMap.getID());
		this.waterBuffer.bindTextureToBuffer(GL_COLOR_ATTACHMENT2, GL_TEXTURE_2D, this.waterSpecularMap.getID());
		this.waterBuffer.bindTextureToBuffer(GL_COLOR_ATTACHMENT3, GL_TEXTURE_2D, this.waterColorMap.getID());
		this.waterBuffer.bindTextureToBuffer(GL_COLOR_ATTACHMENT4, GL_TEXTURE_2D, this.waterColorIDMap.getID());
		this.waterBuffer.addDepthBuffer();
		this.waterBuffer.setDrawBuffers(new int[] { GL_COLOR_ATTACHMENT0, GL_COLOR_ATTACHMENT1, GL_COLOR_ATTACHMENT2, GL_COLOR_ATTACHMENT3, GL_COLOR_ATTACHMENT4 });
		this.waterBuffer.isComplete();

		this.geometryBuffer = new Framebuffer(this.screenWidth, this.screenHeight);
		this.geometryColorMap = new Texture(this.screenWidth, this.screenHeight, GL_RGBA32F, GL_RGBA, GL_FLOAT);
		this.geometryBuffer.bindTextureToBuffer(GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, this.geometryColorMap.getID());
		this.geometryBuffer.addDepthBuffer();
		this.geometryBuffer.setDrawBuffers(new int[] { GL_COLOR_ATTACHMENT0 });
		this.geometryBuffer.isComplete();

		this.skyboxBuffer = new Framebuffer(this.screenWidth, this.screenHeight);
		this.skyboxColorMap = new Texture(this.screenWidth, this.screenHeight, GL_RGBA32F, GL_RGBA, GL_FLOAT);
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

	public void setWorldCameraFOV(float degrees) {
		this.worldFOV = degrees;
	}

	public void setWorldScene(int scene) {
		this.world_scenes = new int[] { scene };
	}

	public void setWorldScenes(int[] scenes) {
		this.world_scenes = scenes;
	}

	public void setShaderCameraUniforms(Shader shader, Camera camera) {
		shader.setUniformMat4("pr_matrix", camera.getProjectionMatrix());
		shader.setUniformMat4("vw_matrix", camera.getViewMatrix());
		shader.setUniform3f("view_pos", camera.getPos());
	}

	@Override
	protected void _render(Framebuffer outputBuffer) {
		Texture.bindingEnabled = true;

		// -- SKYBOX -- : we'll use this texture in the post-processing step
		{
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

		// -- WATER -- 
		{
			this.waterBuffer.bind();

			//set water position to be centered under the player
			Mat4 transform = Mat4.translate(new Vec3(this.camera.getPos().x, 0, this.camera.getPos().z));
			this.waterInst.setModelTransform(new ModelTransform(transform));

			glEnable(GL_DEPTH_TEST);
			glDepthFunc(GL_LESS);
			glEnable(GL_CULL_FACE);
			glCullFace(GL_BACK);
			glPolygonMode(GL_FRONT, GL_FILL);
			glDisable(GL_BLEND);
			glClearDepth(1); // maximum value
			glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

			this.waterGeometryShader.enable();
			this.waterGeometryShader.setUniform3f("sun_dir", this.options.getSunDir().normalize());
			this.waterGeometryShader.setUniform3f("view_pos", this.camera.getPos());
			this.waterGeometryShader.setUniform1f("length_scale_long", this.cascadeLong.lengthScale);
			this.waterGeometryShader.setUniform1f("length_scale_med", this.cascadeMed.lengthScale);
			this.waterGeometryShader.setUniform1f("length_scale_short", this.cascadeShort.lengthScale);

			this.waterGeometryShader.setUniform1f("cascadeScale0", this.options.getCascadeScale0());
			this.waterGeometryShader.setUniform1f("cascadeScale1", this.options.getCascadeScale1());
			this.waterGeometryShader.setUniform1f("cascadeScale2", this.options.getCascadeScale2());

			this.waterGeometryShader.setUniform1i("render_normals", this.options.getRenderNormals() ? 1 : 0);
			this.waterGeometryShader.setUniform1i("render_reflection", this.options.getRenderReflection() ? 1 : 0);
			this.waterGeometryShader.setUniform1f("sun_irradiance_mult", this.options.getSunIrradianceMult());
			this.waterGeometryShader.setUniform1f("environment_light_strength", this.options.getEnvironmentLightStrength());

			this.waterGeometryShader.setUniform3f("sun_irradiance_base", this.options.getSunIrradianceBase());
			this.waterGeometryShader.setUniform3f("_scatter_color", this.options.getScatterColor());
			this.waterGeometryShader.setUniform3f("_bubble_color", this.options.getBubbleColor());
			this.waterGeometryShader.setUniform1f("bubble_density", this.options.getBubbleDensity());
			this.waterGeometryShader.setUniform1f("wave_peak_scatter_strength", this.options.getWavePeakScatterStrength());
			this.waterGeometryShader.setUniform1f("scatter_strength", this.options.getScatterStrength());
			this.waterGeometryShader.setUniform1f("scatter_shadow_strength", this.options.getScatterShadowStrength());
			this.waterGeometryShader.setUniform1f("height_modifier", this.options.getHeightModifier());

			this.skyboxCubemap.bind(GL_TEXTURE5);
			this.cascadeLong.dispTexture.bind(GL_TEXTURE6);
			this.cascadeLong.derivativeTexture.bind(GL_TEXTURE7);
			this.cascadeMed.dispTexture.bind(GL_TEXTURE8);
			this.cascadeMed.derivativeTexture.bind(GL_TEXTURE9);
			this.cascadeShort.dispTexture.bind(GL_TEXTURE10);
			this.cascadeShort.derivativeTexture.bind(GL_TEXTURE11);
			this.setCameraFOV(this.worldFOV);
			this.setShaderCameraUniforms(this.waterGeometryShader, this.camera);
			Model.renderModels(WATER_SCENE);
		}

		// -- GEOMETRY -- : render 3d perspective to geometry buffer
		if (true) {
			this.geometryBuffer.bind();

			glEnable(GL_DEPTH_TEST);
			glDepthFunc(GL_LESS);
			glEnable(GL_CULL_FACE);
			glCullFace(GL_BACK);
			glPolygonMode(GL_FRONT, GL_FILL);
			glDisable(GL_BLEND);
			glClearDepth(1); // maximum value
			glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

			this.worldGeometryShader.enable();
			this.worldGeometryShader.setUniform3f("sun_dir", this.options.getSunDir().normalize());
			this.worldGeometryShader.setUniform2f("screen_size", new Vec2(this.getScreenWidth(), this.getScreenHeight()));

			this.waterPositionMap.bind(GL_TEXTURE5);
			this.waterColorMap.bind(GL_TEXTURE6);
			this.setCameraFOV(this.worldFOV);
			this.setShaderCameraUniforms(this.worldGeometryShader, this.camera);
			for (int scene : this.world_scenes) {
				Model.renderModels(scene);
			}
		}

		// -- RENDER TO OUTPUT --
		{
			outputBuffer.bind();
			glDisable(GL_DEPTH_TEST);
			glEnable(GL_BLEND);
			glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
			Shader.SPLASH.enable();
			Shader.SPLASH.setUniform1f("alpha", 1f);

			this.skyboxColorMap.bind(GL_TEXTURE0);
			screenQuad.render();

			this.waterColorMap.bind(GL_TEXTURE0);
			//		this.geometryPositionMap.bind(GL_TEXTURE0);
			//		this.geometryColorMap.bind(GL_TEXTURE0);
			//		this.geometryNormalMap.bind(GL_TEXTURE0);
			screenQuad.render();

			this.geometryColorMap.bind(GL_TEXTURE0);
			screenQuad.render();
		}
	}

	public void generateSkybox() {
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
			this.skyboxShader.setUniform3f("sun_dir", this.options.getSunDir().normalize());

			this.skyboxFramebuffer.bindTextureToBuffer(GL_COLOR_ATTACHMENT0, GL_TEXTURE_CUBE_MAP_POSITIVE_X + i, this.skyboxCubemap.getID());
			this.skyboxFramebuffer.bind();
			this.spaceSkybox.bind(GL_TEXTURE0);
			glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
			SkyboxCube.skyboxCube.render();
		}
		glViewport(0, 0, this.getScreenWidth(), this.getScreenHeight());
	}

	//with built in LODs 
	private Model createWaterMesh() {
		float interval = 0.025f;
		int lod_cnt = 12;
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

}
