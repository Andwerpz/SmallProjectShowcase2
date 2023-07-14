package window;

import lwjglengine.graphics.Framebuffer;
import lwjglengine.scene.Scene;
import lwjglengine.screen.UIScreen;
import lwjglengine.ui.UIElement;
import lwjglengine.ui.UIFilledRectangle;
import lwjglengine.window.Window;

public class BackgroundWindow extends Window {
	//for now, this window should just be to open a context menu. 
	//perhaps we can modify the background later. 

	private final int BACKGROUND_SCENE = Scene.generateScene();

	private UIScreen uiScreen;

	private UIFilledRectangle testRect;

	public BackgroundWindow(int xOffset, int yOffset, int width, int height, Window parentWindow) {
		super(xOffset, yOffset, width, height, parentWindow);
		this.init();
	}

	private void init() {
		this.setContextMenuRightClick(true);
		this.setContextMenuActions(new String[] { "Open Project Picker" });

		this.setFillWidth(true);
		this.setFillHeight(true);

		this.uiScreen = new UIScreen();

		this.testRect = new UIFilledRectangle(0, 0, 0, 100, 100, BACKGROUND_SCENE);
		this.testRect.setContentAlignmentStyle(UIElement.ALIGN_CENTER, UIElement.ALIGN_CENTER);

		this._resize();
	}

	@Override
	public void handleContextMenuAction(String action) {
		switch (action) {
		case "Open Project Picker": {
			break;
		}
		}
	}

	@Override
	protected void _kill() {
		Scene.removeScene(BACKGROUND_SCENE);
	}

	@Override
	protected void _resize() {
		this.uiScreen.setScreenDimensions(this.getWidth(), this.getHeight());
	}

	@Override
	protected void _update() {
		this.testRect.setFrameAlignmentOffset(this.getWindowMousePos().x, this.getWindowMousePos().y);
	}

	@Override
	protected void renderContent(Framebuffer outputBuffer) {
		this.uiScreen.setUIScene(BACKGROUND_SCENE);
		this.uiScreen.render(outputBuffer);
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
