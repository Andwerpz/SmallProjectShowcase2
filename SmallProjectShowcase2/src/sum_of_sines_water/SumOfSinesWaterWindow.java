package sum_of_sines_water;

import java.awt.image.BufferedImage;
import java.util.ArrayList;

import org.lwjgl.glfw.GLFW;

import lwjglengine.graphics.Cubemap;
import lwjglengine.graphics.Framebuffer;
import lwjglengine.graphics.Material;
import lwjglengine.model.Model;
import lwjglengine.model.ModelInstance;
import lwjglengine.model.ModelTransform;
import lwjglengine.model.VertexArray;
import lwjglengine.player.PlayerInputController;
import lwjglengine.scene.DirLight;
import lwjglengine.scene.Light;
import lwjglengine.scene.Scene;
import lwjglengine.window.Window;
import myutils.v10.math.Vec3;
import myutils.v10.math.Vec4;
import myutils.v11.file.FileUtils;

public class SumOfSinesWaterWindow extends Window {

	private static int waterResolution = 256;
	private Model waterModel;

	private final int WORLD_SCENE = Scene.generateScene();

	private SumOfSinesWaterScreen worldScreen;

	private PlayerInputController pic;

	public SumOfSinesWaterWindow(int xOffset, int yOffset, int width, int height, Window parentWindow) {
		super(xOffset, yOffset, width, height, parentWindow);
		this.init();
	}

	private void init() {
		this.setLockCursorOnSelect(true);
		this.setUnlockCursorOnEscPressed(true);
		this.setDeselectOnEscPressed(true);

		BufferedImage[] skyboxSides = new BufferedImage[6];
		String skyboxDir = "/res/skybox/lake/";
		for (int i = 0; i < 6; i++) {
			skyboxSides[i] = FileUtils.loadImageRelative(skyboxDir + Cubemap.CUBEMAP_SIDE_NAMES[i] + ".jpg");
		}
		Cubemap skybox = new Cubemap(skyboxSides);
		Scene.skyboxes.put(WORLD_SCENE, skybox);

		//create water model grid
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

					uvs.add((float) i);
					uvs.add((float) j);
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

		Material waterMaterial = new Material(new Vec3(0, 84, 147).mul(1.0f / 255.0f));
		waterMaterial.setSpecular(new Vec3(0.7f));
		waterMaterial.setShininess(256);
		waterInstance.setMaterial(waterMaterial);

		this.worldScreen = new SumOfSinesWaterScreen();
		this.worldScreen.renderSkybox(true);

		this.pic = new PlayerInputController(new Vec3(0, 1, 0));

		//update camera position
		this.worldScreen.getCamera().setFacing(this.pic.getFacing());
		this.worldScreen.getCamera().setPos(this.pic.getPos());

		DirLight sun = new DirLight(new Vec3(0, -0.3f, 1), new Vec3(1), 0.4f);
		Light.addLight(WORLD_SCENE, sun);

		this._resize();
	}

	@Override
	protected void _kill() {
		this.waterModel.kill();

		Scene.removeScene(WORLD_SCENE);
	}

	@Override
	protected void _resize() {
		this.worldScreen.setScreenDimensions(this.getWidth(), this.getHeight());
	}

	@Override
	public String getDefaultTitle() {
		return "Sum of Sines Water";
	}

	@Override
	protected void _update() {
		if (this.isSelected()) {
			this.pic.update();

			//update camera position
			this.worldScreen.getCamera().setFacing(this.pic.getFacing());
			this.worldScreen.getCamera().setPos(this.pic.getPos());
		}
	}

	@Override
	protected void renderContent(Framebuffer outputBuffer) {
		this.worldScreen.setWorldScene(WORLD_SCENE);
		this.worldScreen.render(outputBuffer);
	}

	@Override
	protected void renderOverlay(Framebuffer outputBuffer) {
		// TODO Auto-generated method stub

	}

	@Override
	protected void selected() {
		// TODO Auto-generated method stub

	}

	@Override
	protected void deselected() {
		// TODO Auto-generated method stub

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
		case GLFW.GLFW_KEY_Z: {
			this.worldScreen.setNrSums(this.worldScreen.getNrSums() - 1);
			break;
		}

		case GLFW.GLFW_KEY_X: {
			this.worldScreen.setNrSums(this.worldScreen.getNrSums() + 1);
			break;
		}

		case GLFW.GLFW_KEY_R: {
			this.worldScreen.generateTheta();
			break;
		}
		}
	}

	@Override
	protected void _keyReleased(int key) {
		// TODO Auto-generated method stub

	}

}
