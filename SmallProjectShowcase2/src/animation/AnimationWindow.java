package animation;

import java.awt.image.BufferedImage;

import lwjglengine.graphics.Cubemap;
import lwjglengine.graphics.Framebuffer;
import lwjglengine.player.PlayerInputController;
import lwjglengine.scene.DirLight;
import lwjglengine.scene.Light;
import lwjglengine.scene.Scene;
import lwjglengine.screen.PerspectiveScreen;
import lwjglengine.window.Window;
import myutils.file.FileUtils;
import myutils.math.Vec3;

public class AnimationWindow extends Window {
	
	private final int WORLD_SCENE = Scene.generateScene();
	private PerspectiveScreen perspective;
	
	private PlayerInputController pic;

	public AnimationWindow(int xOffset, int yOffset, int width, int height, Window parentWindow) {
		super(xOffset, yOffset, width, height, parentWindow);
		this.init();
	}
	
	private void init() {
		this.perspective = new PerspectiveScreen();
		this.perspective.renderDecals(false);
		this.perspective.renderParticles(false);
		this.perspective.renderPlayermodel(false);
		this.perspective.renderSkybox(true);
		
		BufferedImage[] skyboxSides = new BufferedImage[6];
		String skyboxDir = "/res/skybox/lake/";
		for (int i = 0; i < 6; i++) {
			skyboxSides[i] = FileUtils.loadImageRelative(skyboxDir + Cubemap.CUBEMAP_SIDE_NAMES[i] + ".jpg");
		}
		Cubemap skybox = new Cubemap(skyboxSides);
		Scene.skyboxes.put(WORLD_SCENE, skybox);

		Light sun = new DirLight(new Vec3(1), new Vec3(1), 0.3f);
		Light.addLight(WORLD_SCENE, sun);

		this.pic = new PlayerInputController(new Vec3(0));
		this.pic.setAcceptPlayerInputs(false);
		
		this._resize();
	}

	@Override
	protected void _kill() {
		Scene.removeScene(WORLD_SCENE);
		this.perspective.kill();
	}

	@Override
	protected void _resize() {
		this.perspective.setScreenDimensions(this.getWidth(), this.getHeight());
	}

	@Override
	public String getDefaultTitle() {
		return "Animation";
	}

	@Override
	protected void _update() {
		this.pic.update();
	}

	@Override
	protected void renderContent(Framebuffer outputBuffer) {
		this.perspective.render(outputBuffer);
	}

	@Override
	protected void renderOverlay(Framebuffer outputBuffer) {
		// TODO Auto-generated method stub
		
	}

	@Override
	protected void selected() {
		this.pic.setAcceptPlayerInputs(true);
	}

	@Override
	protected void deselected() {
		this.pic.setAcceptPlayerInputs(false);
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
