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
import java.nio.FloatBuffer;
import java.util.ArrayList;

import lwjglengine.graphics.*;
import lwjglengine.screen.ScreenQuad;
import lwjglengine.util.BufferUtils;
import lwjglengine.util.ShaderUtils;
import org.lwjgl.glfw.GLFW;

import lwjglengine.model.Model;
import lwjglengine.model.ModelInstance;
import lwjglengine.model.ModelTransform;
import lwjglengine.model.VertexArray;
import lwjglengine.player.PlayerInputController;
import lwjglengine.scene.DirLight;
import lwjglengine.scene.Light;
import lwjglengine.scene.Scene;
import lwjglengine.window.AdjustableWindow;
import lwjglengine.window.ObjectEditorWindow;
import lwjglengine.window.TextureViewerWindow;
import lwjglengine.window.Window;
import myutils.file.FileUtils;
import myutils.math.Vec3;
import myutils.math.Vec4;

public class HWFWindow extends Window {

	private static int waterResolution = 256;
	private Model waterModel;

	private final int WORLD_SCENE = Scene.generateScene();

	//	private HWFScreen worldScreen;

	private PlayerInputController pic;

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
			int[][] vertexIndices = new int[waterResolution][waterResolution];
			int ptr = 0;
			ArrayList<Float> vertices = new ArrayList<>();
			ArrayList<Float> uvs = new ArrayList<>();
			ArrayList<Integer> indices = new ArrayList<>();

			for (int i = 0; i < waterResolution; i++) {
				for (int j = 0; j < waterResolution; j++) {
					vertices.add((float) i - (waterResolution - 1) / 2);
					vertices.add((float) 0);
					vertices.add((float) j - (waterResolution - 1) / 2);
					vertexIndices[i][j] = ptr++;

					uvs.add((float) i / (waterResolution - 1));
					uvs.add((float) j / (waterResolution - 1));
				}
			}

			for (int i = 0; i < waterResolution - 1; i++) {
				for (int j = 0; j < waterResolution - 1; j++) {
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

		//		// INITIALIZE SCREEN
		//		this.worldScreen = new HWFScreen();
		//		this.worldScreen.renderSkybox(true);
		//
		//		this.pic = new PlayerInputController(new Vec3(0, 1, 0));
		//
		//		// SET CAMERA POS
		//		this.worldScreen.getCamera().setFacing(this.pic.getFacing());
		//		this.worldScreen.getCamera().setPos(this.pic.getPos().add(new Vec3(0, 3, 0)));
		//
		//		// ADD SUN & LIGHTS
		//		DirLight sun = new DirLight(new Vec3(0.3, -0.6f, 1), new Vec3(1), 0.4f);
		//		Light.addLight(WORLD_SCENE, sun);
		//		this.worldScreen.setSun(sun);

		// DEBUGGING TOOLS IF DESIRED
		//windows to look at water textures
		//		AdjustableWindow waterHeightViewer = new AdjustableWindow("Water Height Map", new TextureViewerWindow(this.worldScreen.getWaterHeightMap()), this);
		//		AdjustableWindow waterNormalViewer = new AdjustableWindow("Water Normal Map", new TextureViewerWindow(this.worldScreen.getWaterNormalMap()), this);
		//control panel for the water
		//AdjustableWindow waterAttributesPanel = new AdjustableWindow("Water Attributes", new ObjectEditorWindow(this.worldScreen.getWaterAttributes()), this);

		//Texture t = new Texture(256, 256);

		Shader s = ShaderUtils.createShader("/csce_vis/hw_final/fft.compute", GL_COMPUTE_SHADER);

		int resolution = 256;
		float[] data = new float[resolution * resolution * 4];
		//		data[resolution * 4 + 5] = -1;
		data[resolution * 127 * 4 + resolution / 2 * 4 - 64 + 4] = 1;
		//		data[resolution * 4 + 12] = 2;

		int textureID = glGenTextures(); //create texture handle
		glBindTexture(GL_TEXTURE_2D, textureID); //set as active texture
		glTexStorage2D(GL_TEXTURE_2D, 1, GL_RGBA32F, resolution, resolution); //allocate storage for texture
		glTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, resolution, resolution, GL_RGBA, GL_FLOAT, data); //initialize 0th mipmap layer of texture
		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);

		Texture in = new Texture(textureID);
		Texture out = new Texture(resolution, resolution, GL_RGBA32F, GL_RGBA, GL_FLOAT, GL_NEAREST);

		glBindImageTexture(0, in.getID(), 0, false, 0, GL_READ_WRITE, GL_RGBA32F);
		glBindImageTexture(1, out.getID(), 0, false, 0, GL_READ_WRITE, GL_RGBA32F);

		s.enable();
		glDispatchCompute(1, 1, 1);

		//		float[] data2 = new float[8 * 8 * 4];
		//		out.bind();
		//		glGetTexImage(GL_TEXTURE_2D, 0, GL_RGBA, GL_FLOAT, data2);
		//
		//		for (int i = 0; i < 8 * 8; ++i) {
		//			System.out.println(data2[i * 4] + " " + data2[i * 4 + 1]);
		//		}

		this.addChildAdjWindow(new TextureViewerWindow(in));
		this.addChildAdjWindow(new TextureViewerWindow(out));

		//		System.out.println(out.getID() + " " + this.worldScreen.getWaterNormalMap().getID());
		System.out.println("done!");

		/*
		Framebuffer waterBuffer = new Framebuffer(256, 256);
		Texture waterHeightMap = new Texture(256, 256, GL_RGBA32F, GL_RGBA, GL_FLOAT, GL_LINEAR);
		waterHeightMap.setWrapping(GL_CLAMP_TO_EDGE);
		waterBuffer.bindTextureToBuffer(GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, waterHeightMap.getID());
		waterBuffer.setDrawBuffers(new int[] { GL_COLOR_ATTACHMENT0, GL_COLOR_ATTACHMENT1 });
		waterBuffer.isComplete();
		s.setUniform1i("input", 0);
		glBindImageTexture(0, waterHeightMap.getID(), 0, false, 0, GL_READ_WRITE, GL_RGBA32F);
		s.enable();
		glViewport(0, 0, 256, 256);
		glDispatchCompute(1, 1, 1);
		this.addChildAdjWindow(new TextureViewerWindow(waterHeightMap));
		*/
		this._resize();

		//System.exit(0);
	}

	@Override
	protected void _kill() {
		//		this.worldScreen.kill();

		this.waterModel.kill();

		Scene.removeScene(WORLD_SCENE);
	}

	@Override
	protected void _resize() {
		//		this.worldScreen.setScreenDimensions(this.getWidth(), this.getHeight());
	}

	@Override
	public String getDefaultTitle() {
		return "FFT Waves & Buoyancy";
	}

	@Override
	protected void _update() {
		if (this.isSelected()) {
			this.pic.update();

			//update camera position
			//			this.worldScreen.getCamera().setFacing(this.pic.getFacing());
			//			this.worldScreen.getCamera().setPos(this.pic.getPos());
		}
	}

	@Override
	protected void renderContent(Framebuffer outputBuffer) {
		//		this.worldScreen.setWorldScene(WORLD_SCENE);
		//		this.worldScreen.render(outputBuffer);
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
