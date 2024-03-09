package raytracing;

import static org.lwjgl.glfw.GLFW.*;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.ArrayList;

import lwjglengine.graphics.Cubemap;
import lwjglengine.graphics.Framebuffer;
import lwjglengine.graphics.Material;
import lwjglengine.main.Main;
import lwjglengine.model.Model;
import lwjglengine.model.VertexArray;
import lwjglengine.player.PlayerInputController;
import lwjglengine.scene.Scene;
import lwjglengine.window.AdjustableWindow;
import lwjglengine.window.FileCreatorWindow;
import lwjglengine.window.ObjectEditorWindow;
import lwjglengine.window.Window;
import myutils.file.FileUtils;
import myutils.math.Mat4;
import myutils.math.Vec3;
import myutils.math.Vec4;

public class RaytracingWindow extends Window {

	private final int RAYTRACING_SCENE = Scene.generateScene();

	private RaytracingScreen raytracingScreen;

	private PlayerInputController pic;

	public RaytracingWindow(int xOffset, int yOffset, int contentWidth, int contentHeight, Window parentWindow) {
		super(xOffset, yOffset, contentWidth, contentHeight, parentWindow);
		this.init();
	}

	public RaytracingWindow() {
		super(0, 0, 400, 400, null);
		this.init();
	}

	private void init() {
		this.setUpdateWhenNotSelected(false);

		//skybox
		BufferedImage[] skyboxSides = new BufferedImage[6];
		String skyboxDir = "/res/skybox/lake/";
		for (int i = 0; i < 6; i++) {
			skyboxSides[i] = FileUtils.loadImageRelative(skyboxDir + Cubemap.CUBEMAP_SIDE_NAMES[i] + ".jpg");
		}
		Cubemap skybox = new Cubemap(skyboxSides);
		Scene.skyboxes.put(RAYTRACING_SCENE, skybox);

		this.raytracingScreen = new RaytracingScreen();
		this.raytracingScreen.setRenderMode(RaytracingScreen.RENDER_MODE_PREVIEW);

		this.pic = new PlayerInputController(new Vec3(0, 50, 0));

		//set up the scene
		Material lightMaterial = new Material(new Vec3(10));
		lightMaterial.setEmissive(new Vec4(1, 1, 1, 10f));

		//		int edgeSize = 11;
		//		float radius = 5f;
		//		float gap = 5;
		//
		//		float edgeLength = (radius * 2 + gap) * (edgeSize - 1);
		//
		//		for (int i = 0; i < edgeSize; i++) {
		//			for (int j = 0; j < edgeSize; j++) {
		//				Material m = new Material(new Vec3(Math.random(), Math.random(), Math.random()));
		//				m.setRoughness((1.0f / (edgeSize - 1)) * i);
		//				m.setSpecularProbability((1.0f / (edgeSize - 1)) * j);
		//
		//				this.raytracingScreen.addSphere(new Vec3(i * (radius * 2 + gap) - edgeLength / 2.0f, 5, j * (radius * 2 + gap) - edgeLength / 2.0f), radius, m);
		//			}
		//		}

		//		//add some random balls
		//		for(int i = 0; i < 100; i++) {
		//			Vec3 center = new Vec3(Math.random() * 100f - 50f, Math.random() * 50f, Math.random() * 100f - 50f);
		//			Material m = new Material(new Vec3(Math.random(), Math.random(), Math.random()));
		//			m.setRoughness((float) Math.random());
		//			m.setSpecularProbability((float) Math.random());
		//			this.raytracingScreen.addSphere(center, (float) Math.random() * 5f + 2f, m);
		//		}

		Material whiteMaterial = new Material(new Vec3(1, 1, 1));
		Material grayMaterial = new Material(Color.GRAY);
		Material blueMaterial = new Material(Color.BLUE);
		Material greenMaterial = new Material(Color.GREEN);
		Material redMaterial = new Material(Color.RED);
		Material sphereMaterial = new Material(Color.WHITE);

		Vec3 v0 = new Vec3(-50, -50, -50);
		Vec3 v1 = new Vec3(-50, -50, 50);
		Vec3 v2 = new Vec3(50, -50, 50);
		Vec3 v3 = new Vec3(50, -50, -50);
		Vec3 v4 = new Vec3(-50, 50, -50);
		Vec3 v5 = new Vec3(-50, 50, 50);
		Vec3 v6 = new Vec3(50, 50, 50);
		Vec3 v7 = new Vec3(50, 50, -50);

		//floor
		this.raytracingScreen.addTriangle(v0, v1, v2, whiteMaterial);
		this.raytracingScreen.addTriangle(v0, v2, v3, whiteMaterial);

		//		//ceiling
		//		this.raytracingScreen.addTriangle(v5, v4, v6, whiteMaterial);
		//		this.raytracingScreen.addTriangle(v6, v4, v7, whiteMaterial);
		//
		//		//back wall
		//		this.raytracingScreen.addTriangle(v0, v3, v7, whiteMaterial);
		//		this.raytracingScreen.addTriangle(v0, v7, v4, whiteMaterial);
		//
		//		//left wall
		//		this.raytracingScreen.addTriangle(v1, v0, v4, redMaterial);
		//		this.raytracingScreen.addTriangle(v1, v4, v5, redMaterial);
		//
		//		//right wall
		//		this.raytracingScreen.addTriangle(v2, v7, v3, greenMaterial);
		//		this.raytracingScreen.addTriangle(v2, v6, v7, greenMaterial);

		//front wall
		//this.raytracingScreen.addTriangle(v2, v1, v5, whiteMaterial);
		//this.raytracingScreen.addTriangle(v2, v5, v6, whiteMaterial);

		//ceiling light
		float lightScale = 0.5f;
		v4.muli(lightScale);
		v5.muli(lightScale);
		v6.muli(lightScale);
		v7.muli(lightScale);
		v4.y = 49.99f;
		v5.y = 49.99f;
		v6.y = 49.99f;
		v7.y = 49.99f;
		this.raytracingScreen.addTriangle(v5, v4, v6, lightMaterial);
		this.raytracingScreen.addTriangle(v6, v4, v7, lightMaterial);

		//suzanne
		Model suzanne = Model.loadModelFile(FileUtils.loadFileRelative("/res/suzanne/suzanne.obj"));
		Material monkeyMaterial = Material.defaultMaterial();
		//monkeyMaterial.setSpecular(new Vec3(212, 175, 55).mul(1.0f / 255.0f));
		monkeyMaterial.setRoughness(0f);
		//monkeyMaterial.setMetalness(1f);
		monkeyMaterial.setRefractiveIndex(1.5f);
		{
			Mat4 transform = Mat4.identity();
			transform.muli(Mat4.scale(30));
			transform.muli(Mat4.rotateX((float) Math.toRadians(30)));
			transform.muli(Mat4.translate(new Vec3(0, -20, 0)));
			ArrayList<VertexArray> meshes = suzanne.getMeshes();
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

					this.raytracingScreen.addTriangle(a, b, c, monkeyMaterial);
				}
			}
		}

		//		//stanford dragon
		//		Model dragon = Model.loadModelFile(FileUtils.loadFileRelative("/res/stanford_dragon/stanford_dragon.obj"));
		//		Material dragonMaterial = Material.defaultMaterial();
		//		dragonMaterial.setRoughness(0);
		//		dragonMaterial.setMetalness(0);
		//		dragonMaterial.setRefractiveIndex(1.5f);
		//		{
		//			Mat4 transform = Mat4.identity();
		//			transform.muli(Mat4.scale(30));
		//			transform.muli(Mat4.translate(new Vec3(0, -50, 0)));
		//			ArrayList<VertexArray> meshes = dragon.getMeshes();
		//			for (VertexArray v : meshes) {
		//				int[] indices = v.getIndices();
		//				Vec3[] vertices = new Vec3[v.getVertices().length / 3];
		//				for (int i = 0; i < vertices.length; i++) {
		//					float x = v.getVertices()[i * 3 + 0];
		//					float y = v.getVertices()[i * 3 + 1];
		//					float z = v.getVertices()[i * 3 + 2];
		//					vertices[i] = new Vec3(x, y, z);
		//				}
		//				for (int i = 0; i < indices.length / 3; i++) {
		//					Vec3 a = vertices[indices[i * 3 + 0]];
		//					Vec3 b = vertices[indices[i * 3 + 1]];
		//					Vec3 c = vertices[indices[i * 3 + 2]];
		//
		//					//apply model transform
		//					a = transform.mul(a, 1);
		//					b = transform.mul(b, 1);
		//					c = transform.mul(c, 1);
		//
		//					this.raytracingScreen.addTriangle(a, b, c, dragonMaterial);
		//				}
		//			}
		//		}

		//		//big ball
		//		whiteMaterial.setRoughness(0f);
		//		whiteMaterial.setMetalness(0);
		//		whiteMaterial.setRefractiveIndex(1.5f);
		//		//whiteMaterial.setSpecular(new Vec3(212, 175, 55).mul(1.0f / 255.0f));
		//		this.raytracingScreen.addSphere(new Vec3(0, -20, 0), 30, whiteMaterial);

		this.raytracingScreen.buildBVHBuffers();

		AdjustableWindow optionsWindow = new AdjustableWindow("Raytracing Options", new ObjectEditorWindow(this.raytracingScreen.getOptions()), this);

		this._resize();
	}

	@Override
	public String getDefaultTitle() {
		return "Raytracing Window";
	}

	@Override
	protected void _kill() {
		this.raytracingScreen.kill();
		Scene.removeScene(RAYTRACING_SCENE);
	}

	@Override
	protected void _resize() {
		if (this.raytracingScreen != null) {
			this.raytracingScreen.setScreenDimensions(this.getWidth(), this.getHeight());
		}
	}

	@Override
	protected void _update() {
		this.pic.update();

		this.raytracingScreen.setCameraPos(this.pic.getPos());
		this.raytracingScreen.setCameraFacing(this.pic.getFacing());
	}

	@Override
	protected void renderContent(Framebuffer outputBuffer) {
		this.raytracingScreen.setRaytracingScene(RAYTRACING_SCENE);
		this.raytracingScreen.render(outputBuffer);
	}

	@Override
	protected void renderOverlay(Framebuffer outputBuffer) {

	}

	@Override
	protected void selected() {
		if (this.raytracingScreen.getRenderMode() == RaytracingScreen.RENDER_MODE_PREVIEW) {
			Main.lockCursor();
		}
	}

	@Override
	protected void deselected() {
		Main.unlockCursor();
	}

	@Override
	protected void subtreeSelected() {
		// TODO Auto-generated method stub

	}

	@Override
	protected void subtreeDeselected() {
		// TODO Auto-generated method stub

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
		switch (key) {
		case GLFW_KEY_ESCAPE:
			this.deselect();
			break;

		case GLFW_KEY_C:
			System.out.println(this.pic.getPos());
			System.out.println(this.pic.getCamXRot() + " " + this.pic.getCamYRot());
			break;

		case GLFW_KEY_V:
			//snap camera to some default position
			this.pic.setPos(new Vec3(0, 0, 102));
			this.pic.setCamXRot(0);
			this.pic.setCamYRot(0);
			this.pic.setAcceptPlayerInputs(false);
			break;

		case GLFW_KEY_B:
			//turn off all external lighting
			this.raytracingScreen.getOptions().setAmbientStrength(0);
			this.raytracingScreen.getOptions().setSunStrength(0);
			break;

		case GLFW_KEY_P:
			this.raytracingScreen.setRenderMode(RaytracingScreen.RENDER_MODE_PREVIEW);
			Main.lockCursor();
			break;

		case GLFW_KEY_R:
			this.raytracingScreen.setRenderMode(RaytracingScreen.RENDER_MODE_RENDER);
			break;

		case GLFW_KEY_T:
			this.raytracingScreen.setRenderMode(RaytracingScreen.RENDER_MODE_DISPLAY_PREV_RENDER);
			Main.unlockCursor();
			break;

		case GLFW_KEY_Y:
			BufferedImage img = this.raytracingScreen.getPrevRenderColorMap().toBufferedImage();
			AdjustableWindow adj = new AdjustableWindow("Save Render As", new FileCreatorWindow(img), this);
			break;
		}
	}

	@Override
	protected void _keyReleased(int key) {
		// TODO Auto-generated method stub

	}

}
