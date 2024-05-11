package logic_simulator;

import java.io.File;
import java.io.IOException;

import logic_simulator.component.InputPin;
import logic_simulator.component.LogicComponent;
import logic_simulator.component.LogicComponent.ComponentType;
import logic_simulator.component.OutputPin;
import logic_simulator.component.Project;
import logic_simulator.component.gate.GateType;
import logic_simulator.component.gate.LogicGate;
import lwjglengine.graphics.Framebuffer;
import lwjglengine.window.NestedListViewerWindow;
import lwjglengine.window.NestedListViewerWindow.NestedListViewerCallback;
import lwjglengine.window.Window;
import myutils.file.FileUtils;
import myutils.file.xml.XMLNode;
import myutils.file.xml.XMLReader;
import myutils.math.IVec2;

public class CircuitEditorWindow extends Window {
	//this should be able to use a circuit simulator to edit a circuit. 

	private NestedListViewerWindow componentSelectorWindow;
	private CircuitSimulatorWindow circuitSimulatorWindow;

	private int selectorWindowWidth = 150;

	private Project project;

	public CircuitEditorWindow(int xOffset, int yOffset, int width, int height, Window parentWindow) {
		super(xOffset, yOffset, width, height, parentWindow);
		this.init();
	}

	private void init() {
		this.project = new Project(FileUtils.loadFileRelative("/res/logic_simulator/projects/test.xml"));

		this.componentSelectorWindow = new NestedListViewerWindow(0, 0, this.selectorWindowWidth, this.getHeight(), new ComponentSelectorCallback(), this);
		this.componentSelectorWindow.setFillHeight(true);
		this.componentSelectorWindow.setRenderBottomBar(false);
		this.componentSelectorWindow.setRenderTopBar(false);
		this.componentSelectorWindow.setCloseOnSubmit(false);
		this.componentSelectorWindow.setSubmitOnDoubleClick(true);

		this.updateSelectorWindowList();

		this.circuitSimulatorWindow = new CircuitSimulatorWindow(this.selectorWindowWidth, 0, this.getWidth() - this.selectorWindowWidth, this.getHeight(), this);
		this.circuitSimulatorWindow.setFillHeight(true);
		this.circuitSimulatorWindow.setBlueprint(this.project.getMainBlueprint());

		this._resize();
	}

	private void updateSelectorWindowList() {
		XMLNode root = XMLReader.parseFileAsXML(FileUtils.loadFileRelative("/res/logic_simulator/selector_menu.xml"));
		root = root.getChildren().get(0);

		XMLNode circuit_root = root.getChildren().get(0);
		for (String name : this.project.getBlueprintNames()) {
			circuit_root.addChild(new XMLNode(name));
		}

		this.componentSelectorWindow.setList(root);
	}

	private void saveProject() {
		this.circuitSimulatorWindow.saveBlueprint();

		File f = FileUtils.loadFileRelative("/res/logic_simulator/projects/test.xml");
		try {
			this.project.saveToFile(f);
		}
		catch (IOException e) {
			e.printStackTrace();
		}
	}

	@Override
	protected void _kill() {
		this.saveProject();
	}

	@Override
	protected void _resize() {
		this.circuitSimulatorWindow.setWidth(Math.max(10, this.getWidth() - this.selectorWindowWidth));
	}

	@Override
	public String getDefaultTitle() {
		return "Circuit Editor Window";
	}

	@Override
	protected void _update() {
		// TODO Auto-generated method stub

	}

	@Override
	protected void renderContent(Framebuffer outputBuffer) {
		// TODO Auto-generated method stub

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

	class ComponentSelectorCallback implements NestedListViewerCallback {
		@Override
		public void handleCallback(String _path, Object contents) {
			assert contents instanceof XMLNode;
			XMLNode node = (XMLNode) contents;

			String[] path = _path.split("/");
			LogicComponent selected_component = null;
			switch (path[0]) {
			case "circuits": {
				//TODO
				break;
			}

			case "components": {
				switch (path[1]) {
				case "gates": {
					GateType type = GateType.valueOf(node.getContent().get(0));
					selected_component = LogicGate.createGate(type, new IVec2(0));
					break;
				}
				default: {
					ComponentType type = ComponentType.valueOf(node.getContent().get(0));
					switch (type) {
					case INPUT_PIN:
						selected_component = new InputPin(new IVec2(0));
						break;
					case OUTPUT_PIN:
						selected_component = new OutputPin(new IVec2(0));
						break;
					default:
						System.err.println("CircuitEditorWindow : unexpected selector type; " + type);
						assert false;
						break;
					}
					break;
				}
				}
			}
			}
			circuitSimulatorWindow.setPlaceComponent(selected_component);
		}
	}

}
