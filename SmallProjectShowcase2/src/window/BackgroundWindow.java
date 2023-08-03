package window;

import hydraulic_terrain.HydraulicTerrainWindow;
import lwjglengine.graphics.Framebuffer;
import lwjglengine.scene.Scene;
import lwjglengine.screen.UIScreen;
import lwjglengine.ui.UIElement;
import lwjglengine.ui.UIFilledRectangle;
import lwjglengine.window.AdjustableWindow;
import lwjglengine.window.Window;
import procedural_trees.ProceduralTreesWindow;
import volumetric_clouds.VolumetricCloudsWindow;

public class BackgroundWindow extends Window {
	//for now, this window should just be to open a context menu. 
	//perhaps we can modify the background later. 

	private final int BACKGROUND_SCENE = Scene.generateScene();

	private UIScreen uiScreen;

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

		this._resize();
	}

	@Override
	public String getDefaultTitle() {
		return "Background Window";
	}

	@Override
	public void handleContextMenuAction(String action) {
		switch (action) {
		case "Open Project Picker": {
			int width = 400;
			int height = 400;
			int x = (int) this.getWindowMousePos().x;
			int y = (int) this.getWindowMousePos().y - height;
			ProjectPickerWindow projectPicker = new ProjectPickerWindow(x, y, width, height, this, null);
			AdjustableWindow adjWindow = new AdjustableWindow("Project Picker", projectPicker, this);

			projectPicker.addToList("Volumetric Clouds");
			projectPicker.addToList("Hydraulic Terrain Generation");
			projectPicker.addToList("Procedural Trees");
			break;
		}
		}
	}

	@Override
	public void handleObjects(Object[] o) {
		String whichProject = (String) o[0];
		int width = 800;
		int height = 600;
		int x = (int) this.getWindowMousePos().x;
		int y = (int) this.getWindowMousePos().y - height;
		switch (whichProject) {
		case "Volumetric Clouds": {
			AdjustableWindow window = new AdjustableWindow(new VolumetricCloudsWindow(x, y, width, height, null), this);
			break;
		}

		case "Hydraulic Terrain Generation": {
			AdjustableWindow window = new AdjustableWindow(new HydraulicTerrainWindow(x, y, width, height, null), this);
			break;
		}

		case "Procedural Trees": {
			AdjustableWindow window = new AdjustableWindow(new ProceduralTreesWindow(x, y, width, height, null), this);
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
