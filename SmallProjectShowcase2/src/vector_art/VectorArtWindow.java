package vector_art;

import java.io.File;
import java.util.ArrayList;

import lwjglengine.graphics.Framebuffer;
import lwjglengine.graphics.Material;
import lwjglengine.input.Button;
import lwjglengine.input.Input;
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
import myutils.file.xml.svg.SVGReader;

public class VectorArtWindow extends Window {

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
		System.err.println("Got file");
		if (files.length != 1) {
			return;
		}

		File f = files[0];
		System.err.println(FileUtils.getFileExtension(f));
		if (FileUtils.getFileExtension(f).equals("svg")) {
			System.err.println("Parsing SVG");
			ArrayList<SVGElement> elements = SVGReader.parseStringAsSVG(FileUtils.readFileToString(f));
			for (SVGElement e : elements) {
				System.out.println(e);
			}
		}
	}

	@Override
	protected void _kill() {
		this.uiScreen.kill();
		this.uiSection.kill();
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
