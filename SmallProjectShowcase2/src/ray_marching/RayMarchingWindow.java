package ray_marching;

import static org.lwjgl.opengl.GL11.GL_BLEND;
import static org.lwjgl.opengl.GL11.GL_COLOR_BUFFER_BIT;
import static org.lwjgl.opengl.GL11.GL_CULL_FACE;
import static org.lwjgl.opengl.GL11.GL_DEPTH_BUFFER_BIT;
import static org.lwjgl.opengl.GL11.glClear;
import static org.lwjgl.opengl.GL11.glDisable;
import static org.lwjgl.opengl.GL11.glViewport;
import static org.lwjgl.opengl.GL13.GL_TEXTURE0;

import java.awt.image.BufferedImage;

import lwjglengine.graphics.Cubemap;
import lwjglengine.graphics.Framebuffer;
import lwjglengine.graphics.Shader;
import lwjglengine.main.Main;
import lwjglengine.player.Camera;
import lwjglengine.player.PlayerInputController;
import lwjglengine.scene.Scene;
import lwjglengine.screen.SkyboxCube;
import lwjglengine.window.Window;
import myutils.file.FileUtils;
import myutils.math.Mat4;
import myutils.math.Vec3;

public class RayMarchingWindow extends Window {

	private static final float FOV = (float) Math.toRadians(90f); //vertical fov
	private static final float NEAR = 0.1f;
	private static final float FAR = 400f;

	private PlayerInputController pc;
	private Camera camera;

	private Cubemap skybox;

	private Shader shader;

	private Vec3 lightDir;

	public RayMarchingWindow(int x, int y, int width, int height, Window parentWindow) {
		super(x, y, width, height, parentWindow);
		this.init();
	}

	private void init() {
		this.setLockCursorOnSelect(true);
		this.setDeselectOnEscPressed(true);

		this.pc = new PlayerInputController(new Vec3(0, 0, 0));
		this.camera = new Camera(FOV, this.getWidth(), this.getHeight(), NEAR, FAR);

		BufferedImage[] skyboxSides = new BufferedImage[6];
		String skyboxDir = "/res/skybox/lake/";
		for (int i = 0; i < 6; i++) {
			skyboxSides[i] = FileUtils.loadImageRelative(skyboxDir + Cubemap.CUBEMAP_SIDE_NAMES[i] + ".jpg");
		}
		this.skybox = new Cubemap(skyboxSides);

		this.shader = new Shader("/ray_marching/skybox.vert", "/ray_marching/skybox.frag");

		this.lightDir = new Vec3(1, 1, 1);
		this.lightDir.normalize();
	}

	@Override
	protected void _kill() {
		this.skybox.kill();

		this.shader.kill();
	}

	@Override
	protected void _resize() {
		this.camera.setProjectionMatrix(Mat4.perspective(FOV, this.getWidth(), this.getHeight(), NEAR, FAR));
	}

	@Override
	public String getDefaultTitle() {
		return "Ray Marching Window";
	}

	@Override
	protected void _update() {
		if (this.isSelected()) {
			this.pc.update();

			this.camera.setPos(this.pc.getPos().mul(0.1f));
			this.camera.setFacing(this.pc.getFacing());
		}
	}

	@Override
	protected void renderContent(Framebuffer outputBuffer) {
		glViewport(0, 0, this.getWidth(), this.getHeight());

		outputBuffer.bind();
		glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
		glDisable(GL_CULL_FACE);
		glDisable(GL_BLEND);
		this.shader.enable();
		this.shader.setUniformMat4("vw_matrix", this.camera.getViewMatrix());
		this.shader.setUniformMat4("pr_matrix", this.camera.getProjectionMatrix());
		this.shader.setUniform3f("camera_pos", this.camera.getPos());
		this.shader.setUniform3f("light_dir", this.lightDir);
		this.skybox.bind(GL_TEXTURE0);
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

}
