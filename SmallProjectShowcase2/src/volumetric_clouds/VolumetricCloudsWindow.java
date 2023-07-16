package volumetric_clouds;

import static org.lwjgl.opengl.GL11.glViewport;

import java.awt.image.BufferedImage;

import lwjglengine.graphics.Cubemap;
import lwjglengine.graphics.Framebuffer;
import lwjglengine.graphics.Shader;
import lwjglengine.main.Main;
import lwjglengine.model.Line;
import lwjglengine.model.ModelInstance;
import lwjglengine.player.PlayerInputController;
import lwjglengine.scene.DirLight;
import lwjglengine.scene.Light;
import lwjglengine.scene.Scene;
import lwjglengine.screen.PerspectiveScreen;
import lwjglengine.screen.SkyboxCube;
import lwjglengine.util.ShaderUtils;
import lwjglengine.window.Window;
import myutils.v10.math.Vec2;
import myutils.v10.math.Vec3;
import myutils.v11.file.JarUtils;

public class VolumetricCloudsWindow extends Window {

	private final int WORLD_SCENE = Scene.generateScene();

	private PerspectiveScreen perspectiveScreen;

	private PlayerInputController pic;

	private Shader cloudsShader;

	private CloudBoundingBox cloudBox;

	public VolumetricCloudsWindow(int xOffset, int yOffset, int width, int height, Window parentWindow) {
		super(xOffset, yOffset, width, height, parentWindow);
		this.init();
	}

	private void init() {
		this.setLockCursorOnSelect(true);
		this.setUnlockCursorOnEscPressed(true);
		this.setDeselectOnEscPressed(true);

		this.perspectiveScreen = new PerspectiveScreen();
		this.perspectiveScreen.renderDecals(false);
		this.perspectiveScreen.renderParticles(false);
		this.perspectiveScreen.renderPlayermodel(false);
		this.perspectiveScreen.renderSkybox(true);

		BufferedImage[] skyboxSides = new BufferedImage[6];
		String skyboxDir = "/skybox/lake/";
		for (int i = 0; i < 6; i++) {
			skyboxSides[i] = JarUtils.loadImage(skyboxDir + Cubemap.CUBEMAP_SIDE_NAMES[i] + ".jpg");
		}
		Cubemap skybox = new Cubemap(skyboxSides);
		Scene.skyboxes.put(WORLD_SCENE, skybox);

		Light dirLight = new DirLight(new Vec3(1), new Vec3(1), 0.3f);
		Light.addLight(WORLD_SCENE, dirLight);

		this.pic = new PlayerInputController(new Vec3(0));

		this.cloudsShader = new Shader("/volumetric_clouds/clouds.vert", "/volumetric_clouds/clouds.frag");

		this.cloudBox = new CloudBoundingBox(new Vec3(0, 0, -20), new Vec3(5, 1, 5));
		this.cloudBox.setDrawBoundingLines(true);

		this._resize();
	}

	@Override
	protected void _kill() {
		this.perspectiveScreen.kill();

		Scene.removeScene(WORLD_SCENE);

		this.cloudsShader.kill();
	}

	@Override
	protected void _resize() {
		this.perspectiveScreen.setScreenDimensions(this.getWidth(), this.getHeight());
	}

	@Override
	protected void _update() {
		if (this.isSelected()) {
			this.pic.update();

			//update camera position
			this.perspectiveScreen.getCamera().setFacing(this.pic.getFacing());
			this.perspectiveScreen.getCamera().setPos(this.pic.getPos());
		}
	}

	@Override
	protected void renderContent(Framebuffer outputBuffer) {
		this.perspectiveScreen.setWorldScene(WORLD_SCENE);
		this.perspectiveScreen.render(outputBuffer);

		glViewport(0, 0, this.getWidth(), this.getHeight());

		//render volumetric clouds

		//probably have to first extract the direction vector for each pixel into a buffer, 
		//so that we can compute the intersection between the view ray and the cloud bounding box. 
		this.cloudsShader.enable();
		this.cloudsShader.setUniformMat4("pr_matrix", this.perspectiveScreen.getCamera().getProjectionMatrix());
		this.cloudsShader.setUniformMat4("vw_matrix", this.perspectiveScreen.getCamera().getViewMatrix());
		this.cloudsShader.setUniform3f("camera_pos", this.perspectiveScreen.getCamera().getPos());
		this.cloudsShader.setUniform3f("cloud_pos", this.cloudBox.pos);
		this.cloudsShader.setUniform3f("cloud_scale", this.cloudBox.scale);
		outputBuffer.bind();
		SkyboxCube.skyboxCube.render();

		glViewport(0, 0, Main.windowWidth, Main.windowHeight);

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
		// TODO Auto-generated method stub

	}

	@Override
	protected void _keyReleased(int key) {
		// TODO Auto-generated method stub

	}

	class CloudBoundingBox {

		public Vec3[] baseCorners = new Vec3[] { new Vec3(-0.5f, -0.5f, -0.5f), new Vec3(-0.5f, -0.5f, 0.5f), new Vec3(0.5f, -0.5f, 0.5f), new Vec3(0.5f, -0.5f, -0.5f), new Vec3(-0.5f, 0.5f, -0.5f), new Vec3(-0.5f, 0.5f, 0.5f), new Vec3(0.5f, 0.5f, 0.5f), new Vec3(0.5f, 0.5f, -0.5f), };

		public boolean drawBoundingLines = false;
		public ModelInstance[] boundingLines = null;

		public Vec3 pos; //refers to the center of the box. 
		public Vec3 scale; //individual scaling of x y z

		public CloudBoundingBox() {
			this.init();
		}

		public CloudBoundingBox(Vec3 pos, Vec3 scale) {
			this.init();
			this.setPos(pos);
			this.setScale(scale);
		}

		private void init() {
			this.pos = new Vec3(0);
			this.scale = new Vec3(1);
		}

		public void setPos(Vec3 pos) {
			this.pos.set(pos);
			this.updateBoundingLineInstances();
		}

		public void setScale(Vec3 scale) {
			this.scale.set(scale);
			this.updateBoundingLineInstances();
		}

		public void setDrawBoundingLines(boolean b) {
			this.drawBoundingLines = b;

			if (this.drawBoundingLines) {
				this.updateBoundingLineInstances();
			}
			else {
				//kill bounding lines
				if (this.boundingLines != null) {
					for (int i = 0; i < this.boundingLines.length; i++) {
						this.boundingLines[i].kill();
					}
				}
				this.boundingLines = null;
			}
		}

		private void updateBoundingLineInstances() {
			if (!this.drawBoundingLines) {
				return;
			}

			if (this.boundingLines == null) {
				this.boundingLines = new ModelInstance[12];
				for (int i = 0; i < this.boundingLines.length; i++) {
					this.boundingLines[i] = Line.addLine(new Vec3(0), new Vec3(0), WORLD_SCENE);
				}
			}

			//enumerate all the corners. 
			Vec3[] corners = new Vec3[8];
			for (int i = 0; i < 8; i++) {
				corners[i] = this.baseCorners[i].mul(this.scale.x, this.scale.y, this.scale.z).add(this.pos);
			}

			//bottom ring
			this.boundingLines[0].setModelTransform(Line.generateLineModelTransform(corners[0], corners[1]));
			this.boundingLines[1].setModelTransform(Line.generateLineModelTransform(corners[1], corners[2]));
			this.boundingLines[2].setModelTransform(Line.generateLineModelTransform(corners[2], corners[3]));
			this.boundingLines[3].setModelTransform(Line.generateLineModelTransform(corners[3], corners[0]));

			//top ring
			this.boundingLines[4].setModelTransform(Line.generateLineModelTransform(corners[4], corners[5]));
			this.boundingLines[5].setModelTransform(Line.generateLineModelTransform(corners[5], corners[6]));
			this.boundingLines[6].setModelTransform(Line.generateLineModelTransform(corners[6], corners[7]));
			this.boundingLines[7].setModelTransform(Line.generateLineModelTransform(corners[7], corners[4]));

			//connecting edges
			this.boundingLines[8].setModelTransform(Line.generateLineModelTransform(corners[0], corners[4]));
			this.boundingLines[9].setModelTransform(Line.generateLineModelTransform(corners[1], corners[5]));
			this.boundingLines[10].setModelTransform(Line.generateLineModelTransform(corners[2], corners[6]));
			this.boundingLines[11].setModelTransform(Line.generateLineModelTransform(corners[3], corners[7]));
		}

	}

}
