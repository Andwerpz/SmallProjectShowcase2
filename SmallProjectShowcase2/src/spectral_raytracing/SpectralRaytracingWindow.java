package spectral_raytracing;

import static org.lwjgl.glfw.GLFW.GLFW_KEY_B;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_C;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_P;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_R;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_T;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_V;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_Y;

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
import lwjglengine.ui.UIElement;
import lwjglengine.ui.UISection;
import lwjglengine.window.AdjustableWindow;
import lwjglengine.window.FileCreatorWindow;
import lwjglengine.window.ObjectEditorWindow;
import lwjglengine.window.Window;
import myutils.file.FileUtils;
import myutils.math.Mat4;
import myutils.math.Vec3;
import myutils.math.Vec4;
import raytracing.RaytracingScreen;

public class SpectralRaytracingWindow extends Window {

	private final int RAYTRACING_SCENE = Scene.generateScene();

	private SpectralRaytracingScreen screen;

	private PlayerInputController pic;

	public SpectralRaytracingWindow(int xOffset, int yOffset, int width, int height, Window parentWindow) {
		super(xOffset, yOffset, width, height, parentWindow);
		this.init();
	}

	private void init() {
		this.screen = new SpectralRaytracingScreen();

		this.setContextMenuActions(new String[] { "Open Color Test Window" });
		this.setContextMenuRightClick(true);
		this.setUpdateWhenNotSelected(false);

		//skybox
		BufferedImage[] skyboxSides = new BufferedImage[6];
		String skyboxDir = "/res/skybox/lake/";
		for (int i = 0; i < 6; i++) {
			skyboxSides[i] = FileUtils.loadImageRelative(skyboxDir + Cubemap.CUBEMAP_SIDE_NAMES[i] + ".jpg");
		}
		Cubemap skybox = new Cubemap(skyboxSides);
		Scene.skyboxes.put(RAYTRACING_SCENE, skybox);

		this.screen = new SpectralRaytracingScreen();
		this.screen.setRenderMode(RaytracingScreen.RENDER_MODE_PREVIEW);

		this.pic = new PlayerInputController(new Vec3(0, 0, 102));

		//set up the scene
		Material whiteMaterial = new Material(new Vec3(1, 1, 1));
		Material grayMaterial = new Material(Color.GRAY);
		Material blueMaterial = new Material(Color.BLUE);
		Material greenMaterial = new Material(Color.GREEN);
		Material redMaterial = new Material(Color.RED);
		Material sphereMaterial = new Material(Color.WHITE);

		//		//cornell box
		//		{
		//			Vec3 v0 = new Vec3(-50, -50, -50);
		//			Vec3 v1 = new Vec3(-50, -50, 50);
		//			Vec3 v2 = new Vec3(50, -50, 50);
		//			Vec3 v3 = new Vec3(50, -50, -50);
		//			Vec3 v4 = new Vec3(-50, 50, -50);
		//			Vec3 v5 = new Vec3(-50, 50, 50);
		//			Vec3 v6 = new Vec3(50, 50, 50);
		//			Vec3 v7 = new Vec3(50, 50, -50);
		//
		//			//floor
		//			this.screen.addTriangle(v0, v1, v2, whiteMaterial);
		//			this.screen.addTriangle(v0, v2, v3, whiteMaterial);
		//
		//			//ceiling
		//			this.screen.addTriangle(v5, v4, v6, whiteMaterial);
		//			this.screen.addTriangle(v6, v4, v7, whiteMaterial);
		//
		//			//back wall
		//			this.screen.addTriangle(v0, v3, v7, whiteMaterial);
		//			this.screen.addTriangle(v0, v7, v4, whiteMaterial);
		//
		//			//left wall
		//			this.screen.addTriangle(v1, v0, v4, redMaterial);
		//			this.screen.addTriangle(v1, v4, v5, redMaterial);
		//
		//			//right wall
		//			this.screen.addTriangle(v2, v7, v3, greenMaterial);
		//			this.screen.addTriangle(v2, v6, v7, greenMaterial);
		//
		//			//front wall
		//			//this.screen.addTriangle(v2, v1, v5, whiteMaterial);
		//			//this.screen.addTriangle(v2, v5, v6, whiteMaterial);
		//
		//			//ceiling light
		//			float lightScale = 0.5f;
		//			v4.muli(lightScale);
		//			v5.muli(lightScale);
		//			v6.muli(lightScale);
		//			v7.muli(lightScale);
		//			v4.y = 49.99f;
		//			v5.y = 49.99f;
		//			v6.y = 49.99f;
		//			v7.y = 49.99f;
		//			Material lightMaterial = new Material(new Vec3(1));
		//			lightMaterial.setEmissive(new Vec4(1, 1, 1, 25f));
		//			this.screen.addTriangle(v5, v4, v6, lightMaterial);
		//			this.screen.addTriangle(v6, v4, v7, lightMaterial);
		//		}

		//small floor
		{
			Vec3 v0 = new Vec3(-50, -50, -50);
			Vec3 v1 = new Vec3(-50, -50, 50);
			Vec3 v2 = new Vec3(50, -50, 50);
			Vec3 v3 = new Vec3(50, -50, -50);
			this.screen.addTriangle(v0, v1, v2, whiteMaterial);
			this.screen.addTriangle(v0, v2, v3, whiteMaterial);
		}

		//		//large floor
		//		{
		//			Vec3 v0 = new Vec3(-250, -50, -250);
		//			Vec3 v1 = new Vec3(-250, -50, 250);
		//			Vec3 v2 = new Vec3(250, -50, 250);
		//			Vec3 v3 = new Vec3(250, -50, -250);
		//			this.screen.addTriangle(v0, v1, v2, whiteMaterial);
		//			this.screen.addTriangle(v0, v2, v3, whiteMaterial);
		//		}

		//		//5 glass balls. Touching the floor and arranged in a pyramid shape
		//		{
		//			Material glass_material = Material.defaultMaterial();
		//			glass_material.setRoughness(0);
		//			glass_material.setRefractiveIndex(1.7f);
		//			glass_material.setDispersion(0.05f);
		//
		//			float radius = 16;
		//
		//			Vec3 c0 = new Vec3(radius, radius, radius);
		//			Vec3 c1 = new Vec3(-radius, radius, radius);
		//			Vec3 c2 = new Vec3(radius, radius, -radius);
		//			Vec3 c3 = new Vec3(-radius, radius, -radius);
		//
		//			//compute elevation for stacked ball
		//			float diag = (float) Math.sqrt(radius * radius + radius * radius);
		//			float ascent = radius * 2;
		//			float elevation = (float) Math.sqrt(ascent * ascent - diag * diag);
		//			Vec3 c4 = new Vec3(0, radius + elevation, 0);
		//
		//			//translate down to touch ground
		//			c0.y -= 50;
		//			c1.y -= 50;
		//			c2.y -= 50;
		//			c3.y -= 50;
		//			c4.y -= 50;
		//
		//			this.screen.addSphere(c0, radius, glass_material);
		//			this.screen.addSphere(c1, radius, glass_material);
		//			this.screen.addSphere(c2, radius, glass_material);
		//			this.screen.addSphere(c3, radius, glass_material);
		//			this.screen.addSphere(c4, radius, glass_material);
		//		}

		//		//suzanne
		//		{
		//			Model suzanne = Model.loadModelFile(FileUtils.loadFileRelative("/res/suzanne/suzanne.obj"));
		//
		//			Material monkeyMaterial = Material.defaultMaterial();
		//			//monkeyMaterial.setSpecular(new Vec3(212, 175, 55).mul(1.0f / 255.0f));
		//			monkeyMaterial.setRoughness(0f);
		//			//monkeyMaterial.setMetalness(1f);
		//			monkeyMaterial.setRefractiveIndex(1.5f);
		//			monkeyMaterial.setDispersion(0.00425f);
		//
		//			Mat4 transform = Mat4.identity();
		//			transform.muli(Mat4.scale(30));
		//			transform.muli(Mat4.rotateX((float) Math.toRadians(30)));
		//			transform.muli(Mat4.translate(new Vec3(0, -20, 0)));
		//
		//			this.screen.addModel(suzanne, transform, monkeyMaterial);
		//		}

		//		//diamond
		//		{
		//			Model diamond = Model.loadModelFile(FileUtils.loadFileRelative("/res/diamond/diamond.obj"));
		//
		//			Material material = Material.defaultMaterial();
		//			material.setRoughness(0);
		//			material.setRefractiveIndex(2.3818f);
		//			material.setDispersion(0.0121f);
		//
		//			Mat4 transform = Mat4.identity();
		//			transform.muli(Mat4.scale(4f));
		//			transform.muli(Mat4.rotateY((float) Math.toRadians(15f)));
		//			transform.muli(Mat4.translate(new Vec3(0, -50, 0)));
		//
		//			this.screen.addModel(diamond, transform, material);
		//		}

		//stanford dragon
		{
			Model dragon = Model.loadModelFile(FileUtils.loadFileRelative("/res/stanford_dragon/stanford_dragon.obj"));
			Material dragonMaterial = Material.defaultMaterial();
			dragonMaterial.setRoughness(0);
			dragonMaterial.setMetalness(0);
			dragonMaterial.setRefractiveIndex(1.5f);
			dragonMaterial.setDispersion(0.015f);

			Mat4 transform = Mat4.identity();
			transform.muli(Mat4.rotateY((float) Math.toRadians(-30)));
			transform.muli(Mat4.scale(40));
			transform.muli(Mat4.translate(new Vec3(0, -50, 0)));

			this.screen.addModel(dragon, transform, dragonMaterial);
		}

		//		//big ball
		//		whiteMaterial.setRoughness(0f);
		//		whiteMaterial.setMetalness(0);
		//		whiteMaterial.setDiffuse(new Vec3(1));
		//		whiteMaterial.setRefractiveIndex(1.7387f);
		//		whiteMaterial.setDispersion(0.05f);
		//		whiteMaterial.setSpecular(new Vec3(1));
		//		this.screen.addSphere(new Vec3(0, -20, 0), 30, whiteMaterial);

		this.screen.buildBVHBuffers();

		AdjustableWindow optionsWindow = new AdjustableWindow("Raytracing Options", new ObjectEditorWindow(this.screen.getOptions()), this);

		this._resize();
	}

	@Override
	protected void _kill() {
		this.screen.kill();
		Scene.removeScene(RAYTRACING_SCENE);
	}

	@Override
	protected void _resize() {
		this.screen.setScreenDimensions(this.getWidth(), this.getHeight());
	}

	@Override
	public String getDefaultTitle() {
		return "Spectral Raytracing Window";
	}

	@Override
	protected void _update() {
		this.pic.update();

		this.screen.setCameraPos(this.pic.getPos());
		this.screen.setCameraFacing(this.pic.getFacing());
	}

	@Override
	protected void renderContent(Framebuffer outputBuffer) {
		this.screen.setRaytracingScene(RAYTRACING_SCENE);
		this.screen.render(outputBuffer);
	}

	@Override
	protected void renderOverlay(Framebuffer outputBuffer) {
		// TODO Auto-generated method stub

	}

	@Override
	protected void selected() {
		if (this.screen.getRenderMode() == RaytracingScreen.RENDER_MODE_PREVIEW) {
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
		// TODO Auto-generated method stub

	}

	@Override
	protected void _mouseReleased(int button) {
		// TODO Auto-generated method stub

	}

	@Override
	protected void _mouseScrolled(float wheelOffset, float smoothOffset) {
		// TODO Auto-generated method stub

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
			this.screen.getOptions().setAmbientStrength(0);
			this.screen.getOptions().setSunStrength(0);
			break;

		case GLFW_KEY_P:
			this.screen.setRenderMode(RaytracingScreen.RENDER_MODE_PREVIEW);
			Main.lockCursor();
			break;

		case GLFW_KEY_R:
			this.screen.setRenderMode(RaytracingScreen.RENDER_MODE_RENDER);
			break;

		case GLFW_KEY_T:
			this.screen.setRenderMode(RaytracingScreen.RENDER_MODE_DISPLAY_PREV_RENDER);
			Main.unlockCursor();
			break;

		case GLFW_KEY_Y:
			BufferedImage img = this.screen.getPostprocessHDRMap().toBufferedImage();
			AdjustableWindow adj = new AdjustableWindow("Save Render As", new FileCreatorWindow(img), this);
			break;
		}
	}

	@Override
	protected void _keyReleased(int key) {
		// TODO Auto-generated method stub

	}

	@Override
	public void handleContextMenuAction(String action) {
		switch (action) {
		case "Open Color Test Window": {
			AdjustableWindow window = new AdjustableWindow(new ColorTestWindow(null), this);
			window.setDimensions(800, 600);
			break;
		}
		}
	}

}
