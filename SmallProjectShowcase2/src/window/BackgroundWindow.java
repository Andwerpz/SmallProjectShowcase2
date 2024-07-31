package window;

import java.awt.Color;
import java.awt.Font;

import genetic_image_builder.GeneticImageBuilder;
import hydraulic_terrain.HydraulicTerrainWindow;
import logic_simulator.CircuitEditorWindow;
import logic_simulator.CircuitSimulatorWindow;
import lwjglengine.graphics.Framebuffer;
import lwjglengine.graphics.Material;
import lwjglengine.main.Main;
import lwjglengine.scene.Scene;
import lwjglengine.screen.UIScreen;
import lwjglengine.ui.Text;
import lwjglengine.ui.UIElement;
import lwjglengine.ui.UIFilledRectangle;
import lwjglengine.window.AdjustableWindow;
import lwjglengine.window.FileCreatorWindow;
import lwjglengine.window.ListViewerWindow.ListViewerCallback;
import lwjglengine.window.TextEditorWindow;
import lwjglengine.window.Window;
import pbr_rendering.PBRRenderingWindow;
import procedural_trees.ProceduralTreesWindow;
import ray_marching.RayMarchingWindow;
import raytracing.RaytracingWindow;
import spectral_raytracing.SpectralRaytracingWindow;
import sph_water.SPHWaterWindow;
import sum_of_sines_water.SumOfSinesWaterWindow;
import terrain_shadow_casting.TerrainShadowCastingWindow;
import vector_art.VectorArtWindow;
import volumetric_clouds.VolumetricCloudsWindow;
import voxel_raytracing.VoxelRaytracingWindow;

public class BackgroundWindow extends Window implements ListViewerCallback {
	//for now, this window should just be to open a context menu. 
	//perhaps we can modify the background later. 

	//PROJECT IDEAS:
	// - Actual FFT Water
	// - Dynamic Skybox Shader
	// - 3D Model Animations
	// - Doom style level editor
	// - Gaussian Splatting
	//   - this one is a stretch lol, but as i'm doing research in this area, maybe??
	// - Tile based bread board
	// - Wave Function Collapse
	// - 3D mesh rigid and softbody physics
	//   - softbody tutorial:
	//     - https://www.youtube.com/watch?v=Noo5sfGGWe0
	//   - rigidbody tutorial: 
	//     - Part 1: https://www.youtube.com/watch?v=4r_EvmPKOvY
	//     - Part 2: https://www.youtube.com/watch?v=GYc99lMdcFE
	// - Vertex Painting
	//   - https://www.youtube.com/watch?v=On64nNkjJpQ
	// - expression calculator
	// - Ray Marching Game
	//   - https://www.youtube.com/watch?v=QhvzmskRiCk&t=14s
	// - Bundle Adjustment
	//   - get a live camera feed from a phone or prerecorded video, do feature detection and matching, and perform 
	//     bundle adjustment to retrieve relative locations of camera positions and feature points in 3D space
	// - better? fluid simulator
	//   - Part 1: https://www.youtube.com/watch?v=MXs_vkc8hpY
	//   - Part 2: https://www.youtube.com/watch?v=4b80sR-joNY
	//   - Part 3: https://www.youtube.com/watch?v=sSJmUmCHAJY
	//	 - another related video: https://www.youtube.com/watch?v=iKAVRgIrUOU
	//   - look into the lattice boltzmann (algorithm?) we can do a grid based simulation, and then just advect the particle locations. 
	//   - perhaps able to do 2D drag simulations. 
	// - Procedural 2D Dungeon Generation
	//   - http://pcg.wikidot.com/pcg-algorithm:dungeon-generation
	//     - many links to other stuff
	//     - half of which are dead D:
	//   - https://donjon.bin.sh/d20/dungeon/
	//     - just a cool dungeon generator, probably for dnd
	//   - https://www.gamedeveloper.com/programming/procedural-dungeon-generation-algorithm
	//		- first, place a bunch of overlapping rooms, then spread them out using physics.
	//      - pick some subset of rooms, then connect them using delaunay triangulation, create minimum spanning tree, but add back some edges. 
	//      - along the mst edges, use the rejected rooms as the corridors. 
	//   - https://journal.stuffwithstuff.com/2014/12/21/rooms-and-mazes/
	//      - first, place rooms on 2D grid. Then, wherever you can, use a maze generation algorithm to fill in the rest of the empty space
	//      - slowly connect all the components, until everything is connected
	//      - remove all 'dead ends'. A tile is a dead end if it is surrounded by walls on 3 sides. 

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
			projectPicker.addToList("Genetic Image Builder");
			projectPicker.addToList("Logic Simulator");
			projectPicker.addToList("Spectral Raytracing");
			projectPicker.addToList("Terrain Shadow Casting");
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

	@Override
	public void handleListViewerCallback(Object[] contents) {
		String whichProject = (String) contents[0];
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

		case "Genetic Image Builder": {
			AdjustableWindow window = new AdjustableWindow(new GeneticImageBuilder(x, y, width, height, null), this);
			break;
		}

		case "Logic Simulator": {
			AdjustableWindow window = new AdjustableWindow(new CircuitEditorWindow(x, y, width, height, null), this);
			break;
		}

		case "Spectral Raytracing": {
			AdjustableWindow window = new AdjustableWindow(new SpectralRaytracingWindow(x, y, width, height, null), this);
			break;
		}

		case "Terrain Shadow Casting": {
			AdjustableWindow window = new AdjustableWindow(new TerrainShadowCastingWindow(x, y, width, height, null), this);
			window.setResizeContentWindowWhenEdgeGrabbed(false);
			break;
		}
		}
	}

}
