package vector_art;

import java.awt.Color;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

import lwjglengine.graphics.Framebuffer;
import lwjglengine.graphics.Material;
import lwjglengine.input.Button;
import lwjglengine.input.Input;
import lwjglengine.model.Line;
import lwjglengine.model.ModelInstance;
import lwjglengine.scene.Scene;
import lwjglengine.screen.UIScreen;
import lwjglengine.ui.UIElement;
import lwjglengine.ui.UIFilledRectangle;
import lwjglengine.ui.UISection;
import lwjglengine.window.AdjustableWindow;
import lwjglengine.window.FileExplorerWindow;
import lwjglengine.window.TextEditorWindow;
import lwjglengine.window.Window;
import myutils.file.FileUtils;
import myutils.file.xml.XMLNode;
import myutils.file.xml.XMLReader;
import myutils.file.xml.svg.SVGElement;
import myutils.file.xml.svg.SVGPath;
import myutils.file.xml.svg.SVGReader;
import myutils.math.Vec2;

public class VectorArtWindow extends Window {

	private final int VECTOR_SCENE = Scene.generateScene();

	private UIScreen uiScreen;
	private UISection uiSection;

	public VectorArtWindow(int xOffset, int yOffset, int width, int height, Window parentWindow) {
		super(xOffset, yOffset, width, height, parentWindow);
		this.init();
	}

	private void init() {
		this.uiScreen = new UIScreen();
		this.uiSection = new UISection(0, 0, this.getWidth(), this.getHeight(), this.uiScreen);
		UIFilledRectangle backgroundRect = this.uiSection.getBackgroundRect();
		backgroundRect.bind(this.rootUIElement);
		backgroundRect.setFrameAlignmentStyle(UIElement.FROM_LEFT, UIElement.FROM_TOP);
		backgroundRect.setContentAlignmentStyle(UIElement.ALIGN_LEFT, UIElement.ALIGN_TOP);
		backgroundRect.setMaterial(Material.transparent());
		backgroundRect.setFillWidth(true);
		backgroundRect.setFillHeight(true);

		this.setContextMenuRightClick(true);
		this.setContextMenuActions(new String[] { "Load svg" });

		this._resize();
	}

	@Override
	public void handleContextMenuAction(String action) {
		switch (action) {
		case "Load svg": {
			FileExplorerWindow fileExplorer = new FileExplorerWindow(this);
			fileExplorer.setSingleEntrySelection(true);
			AdjustableWindow adjWindow = new AdjustableWindow(fileExplorer, this);
			break;
		}
		}
	}

	@Override
	public void handleFiles(File[] files) {
		if (files.length != 1) {
			return;
		}

		File f = files[0];
		System.err.println(FileUtils.getFileExtension(f));
		if (FileUtils.getFileExtension(f).equals("svg")) {
			Scene.clearScene(VECTOR_SCENE);
			System.out.println("Parsing SVG");
			ArrayList<SVGElement> elements = SVGReader.parseStringAsSVG(FileUtils.readFileToString(f));

			//just draw all the bezier curves
			for (SVGElement e : elements) {
				System.out.println("Printing Path");
				System.out.println(e);
				SVGPath p = (SVGPath) e;
				List<Vec2[]> cubicCurves = p.getCubicCurves();
				for (Vec2[] v : cubicCurves) {
					for (int i = 0; i < 4; i++) {
						Vec2 v0 = v[i];
						Vec2 v1 = v[(i + 1) % 4];
						ModelInstance line = Line.addLine(v0, v1, VECTOR_SCENE);
						line.setMaterial(new Material(Color.WHITE));
					}
				}
			}

		}
	}

	@Override
	protected void _kill() {
		this.uiScreen.kill();
		this.uiSection.kill();

		Scene.removeScene(VECTOR_SCENE);
	}

	@Override
	protected void _resize() {

	}

	@Override
	public String getDefaultTitle() {
		return "Vector Art";
	}

	@Override
	protected void _update() {
		this.uiSection.update();
	}

	@Override
	protected void renderContent(Framebuffer outputBuffer) {
		this.uiScreen.setUIScene(VECTOR_SCENE);
		this.uiScreen.render(outputBuffer);

		this.uiSection.render(outputBuffer, this.getWindowMousePos());
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
		this.uiSection.mousePressed(button);
	}

	@Override
	protected void _mouseReleased(int button) {
		this.uiSection.mouseReleased(button);

		String which = Input.getClicked(this.uiSection.getSelectionScene());
		switch (which) {

		}
	}

	@Override
	protected void _mouseScrolled(float wheelOffset, float smoothOffset) {
		this.uiSection.mouseScrolled(wheelOffset, smoothOffset);
	}

	@Override
	protected void _keyPressed(int key) {
		this.uiSection.keyPressed(key);
	}

	@Override
	protected void _keyReleased(int key) {
		this.uiSection.keyReleased(key);
	}

}
