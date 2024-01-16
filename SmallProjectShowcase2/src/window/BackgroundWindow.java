package window;

import java.awt.Color;
import java.awt.Font;

import hydraulic_terrain.HydraulicTerrainWindow;
import lwjglengine.graphics.Framebuffer;
import lwjglengine.graphics.Material;
import lwjglengine.main.Main;
import lwjglengine.scene.Scene;
import lwjglengine.screen.UIScreen;
import lwjglengine.ui.Text;
import lwjglengine.ui.UIElement;
import lwjglengine.ui.UIFilledRectangle;
import lwjglengine.window.AdjustableWindow;
import lwjglengine.window.TextEditorWindow;
import lwjglengine.window.Window;
import pbr_rendering.PBRRenderingWindow;
import procedural_trees.ProceduralTreesWindow;
import ray_marching.RayMarchingWindow;
import raytracing.RaytracingWindow;
import sph_water.SPHWaterWindow;
import sum_of_sines_water.SumOfSinesWaterWindow;
import vector_art.VectorArtWindow;
import volumetric_clouds.VolumetricCloudsWindow;
import voxel_raytracing.VoxelRaytracingWindow;

public class BackgroundWindow extends Window {
	//for now, this window should just be to open a context menu. 
	//perhaps we can modify the background later. 

	//PROJECT IDEAS:
	// - Actual FFT Water
	// - Dynamic Skybox Shader
	// - 3D Model Animations
	// - Doom style level editor
	// - Gaussian Splatting
	//   - this one is a stretch lol
	// - Tile based bread board
	// - Wave Function Collapse
	// - 3D mesh rigid and softbody physics
	// - 2D Terrain Shadow Casting
	//   - https://www.youtube.com/watch?v=bMTeCqNkId8
	// - Layered Material Painting? 
	//   - need to find the proper name for this
	//   - https://www.youtube.com/watch?v=On64nNkjJpQ
	// - Recreating images but with limited tools
	//   - https://www.youtube.com/watch?v=6aXx6RA1IK4

	private final int BACKGROUND_SCENE = Scene.generateScene();
	private final int TEXT_SCENE = Scene.generateScene();

	private UIScreen uiScreen;

	public BackgroundWindow(int xOffset, int yOffset, int width, int height, Window parentWindow) {
		super(xOffset, yOffset, width, height, parentWindow);
		this.init();
	}

	private void init() {
		this.setContextMenuRightClick(true);
		this.setContextMenuActions(new String[] { "Open Project Picker", "Readme" });

		this.setFillWidth(true);
		this.setFillHeight(true);

		UIFilledRectangle backgroundRect = new UIFilledRectangle(0, 0, 0, this.getWidth(), this.getHeight(), BACKGROUND_SCENE);
		backgroundRect.setFillWidth(true);
		backgroundRect.setFillHeight(true);
		backgroundRect.setMaterial(new Material(Color.BLACK));
		backgroundRect.bind(this.rootUIElement);

		Text promptText = new Text(0, 0, "Right click to open the context menu", new Font("Consolas", Font.PLAIN, 36), this.contentDefaultMaterial, TEXT_SCENE);
		promptText.setFrameAlignmentStyle(UIElement.FROM_CENTER_LEFT, UIElement.FROM_CENTER_TOP);
		promptText.setContentAlignmentStyle(UIElement.ALIGN_CENTER, UIElement.ALIGN_CENTER);
		promptText.setBackgroundColor(Color.BLACK);
		promptText.setDrawBackground(true);
		promptText.bind(backgroundRect);

		this.uiScreen = new UIScreen();

		this._resize();
	}

	@Override
	public String getDefaultTitle() {
		return "Background Window";
	}

	@Override
	public void handleContextMenuAction(String action) {
		int width = 400;
		int height = 300;
		int x = (int) this.getWindowMousePos().x;
		int y = (int) (Main.windowHeight - this.getWindowMousePos().y);

		switch (action) {
		case "Open Project Picker": {
			ProjectPickerWindow projectPicker = new ProjectPickerWindow(x, y, width, height, this, null);
			AdjustableWindow adjWindow = new AdjustableWindow("Project Picker", projectPicker, this);

			projectPicker.addToList("Volumetric Clouds");
			projectPicker.addToList("Hydraulic Terrain Generation");
			projectPicker.addToList("Procedural Trees");
			projectPicker.addToList("Sum of Sines Water");
			projectPicker.addToList("Ray Marching");
			projectPicker.addToList("Raytracing");
			projectPicker.addToList("PBR Rendering");
			projectPicker.addToList("Vector Art");
			projectPicker.addToList("SPH Water");
			projectPicker.addToList("Voxel Raytracing");
			break;
		}

		case "Readme": {
			String readmeText = "This is a collection of small projects that may or may not be finished.\nTo open a project, double click it in the project picker.\nWindows can be nested and un-nested by holding Control while dragging them.\nWindows can be resized by dragging close to the outside of their edges.\nIf a window locks your cursor, you can usually press Escape to unlock it.";
			TextEditorWindow textEditor = new TextEditorWindow(x, y, 800, height, null);
			textEditor.appendTextAtCursor(readmeText);
			AdjustableWindow adjWindow = new AdjustableWindow("Readme", textEditor, this);
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
		int y = (int) (Main.windowHeight - this.getWindowMousePos().y);
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

		case "Sum of Sines Water": {
			AdjustableWindow window = new AdjustableWindow(new SumOfSinesWaterWindow(x, y, width, height, null), this);
			break;
		}

		case "Ray Marching": {
			AdjustableWindow window = new AdjustableWindow(new RayMarchingWindow(x, y, width, height, null), this);
			break;
		}

		case "Raytracing": {
			AdjustableWindow window = new AdjustableWindow(new RaytracingWindow(x, y, width, height, null), this);
			break;
		}

		case "PBR Rendering": {
			AdjustableWindow window = new AdjustableWindow(new PBRRenderingWindow(x, y, width, height, null), this);
			break;
		}

		case "Vector Art": {
			AdjustableWindow window = new AdjustableWindow(new VectorArtWindow(x, y, width, height, null), this);
			break;
		}

		case "SPH Water": {
			AdjustableWindow window = new AdjustableWindow(new SPHWaterWindow(x, y, width, height, null), this);
			break;
		}

		case "Voxel Raytracing": {
			AdjustableWindow window = new AdjustableWindow(new VoxelRaytracingWindow(x, y, width, height, null), this);
			break;
		}
		}
	}

	@Override
	protected void _kill() {
		Scene.removeScene(BACKGROUND_SCENE);
		Scene.removeScene(TEXT_SCENE);

		this.uiScreen.kill();
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
		this.uiScreen.setUIScene(TEXT_SCENE);
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
