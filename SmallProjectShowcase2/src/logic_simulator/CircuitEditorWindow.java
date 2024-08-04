package logic_simulator;

import java.io.File;
import java.io.IOException;

import logic_simulator.component.InputPin;
import logic_simulator.component.LogicComponent;
import logic_simulator.component.LogicComponent.ComponentType;
import logic_simulator.component.OutputPin;
import logic_simulator.component.Project;
import logic_simulator.component.circuit.LogicCircuitBlueprint;
import logic_simulator.component.gate.GateType;
import logic_simulator.component.gate.LogicGate;
import lwjglengine.graphics.Framebuffer;
import lwjglengine.graphics.Material;
import lwjglengine.input.Button;
import lwjglengine.input.Input;
import lwjglengine.input.Input.InputCallback;
import lwjglengine.ui.UIElement;
import lwjglengine.ui.UISection;
import lwjglengine.window.AdjustableWindow;
import lwjglengine.window.NestedListViewerWindow;
import lwjglengine.window.NestedListViewerWindow.NestedListViewerCallback;
import lwjglengine.window.TextEntryWindow;
import lwjglengine.window.TextEntryWindow.TextEntryWindowCallback;
import lwjglengine.window.Window;
import myutils.file.FileUtils;
import myutils.file.xml.XMLNode;
import myutils.file.xml.XMLReader;
import myutils.math.IVec2;

public class CircuitEditorWindow extends Window implements InputCallback {
	//this should be able to use a circuit simulator to edit a circuit. 

	private UISection topBarSection;
	private static int topBarHeightPx = 20;

	private Button newCircuitButton;

	private NestedListViewerWindow componentSelectorWindow;
	private CircuitSimulatorWindow circuitSimulatorWindow;

	private int selectorWindowWidth = 150;

	private Project project;

	public CircuitEditorWindow(int xOffset, int yOffset, int width, int height, Window parentWindow) {
		super(xOffset, yOffset, width, height, parentWindow);
		this.init();
	}

	private void init() {
		this.topBarSection = new UISection();
		this.topBarSection.getBackgroundRect().setFrameAlignmentStyle(UIElement.FROM_LEFT, UIElement.FROM_TOP);
		this.topBarSection.getBackgroundRect().setContentAlignmentStyle(UIElement.ALIGN_LEFT, UIElement.ALIGN_TOP);
		this.topBarSection.getBackgroundRect().setFillWidth(true);
		this.topBarSection.getBackgroundRect().setHeight(topBarHeightPx);
		this.topBarSection.getBackgroundRect().setMaterial(Material.TOP_BAR_DEFAULT_MATERIAL);
		this.topBarSection.getBackgroundRect().bind(this.rootUIElement);

		this.newCircuitButton = new Button(0, 0, 80, 20, "btn_new_circuit", "New Circuit", this, this.topBarSection.getSelectionScene(), this.topBarSection.getTextScene());
		this.newCircuitButton.setFrameAlignmentStyle(UIElement.FROM_LEFT, UIElement.FROM_CENTER_TOP);
		this.newCircuitButton.setContentAlignmentStyle(UIElement.ALIGN_LEFT, UIElement.ALIGN_CENTER);
		this.newCircuitButton.bind(this.topBarSection.getBackgroundRect());

		this.project = new Project(FileUtils.loadFileRelative("/res/logic_simulator/projects/test.xml"));

		this.componentSelectorWindow = new NestedListViewerWindow(0, 0, this.selectorWindowWidth, this.getHeight(), new ComponentSelectorCallback(), this);
		this.componentSelectorWindow.setAlignmentStyle(Window.FROM_LEFT, Window.FROM_TOP);
		this.componentSelectorWindow.setRenderBottomBar(false);
		this.componentSelectorWindow.setRenderTopBar(false);
		this.componentSelectorWindow.setCloseOnSubmit(false);
		this.componentSelectorWindow.setSubmitOnDoubleClick(true);

		this.updateSelectorWindowList();

		this.circuitSimulatorWindow = new CircuitSimulatorWindow(this.selectorWindowWidth, 0, this.getWidth() - this.selectorWindowWidth, this.getHeight(), this);
		this.circuitSimulatorWindow.setAlignmentStyle(Window.FROM_LEFT, Window.FROM_TOP);
		this.setEditorBlueprint(this.project.getMainBlueprint());

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

	private void setEditorBlueprint(LogicCircuitBlueprint b) {
		this.circuitSimulatorWindow.setBlueprint(b);
	}

	private void createNewBlueprint(String name) {
		LogicCircuitBlueprint new_blueprint = this.project.createNewBlueprint(name);
		if (new_blueprint != null) {
			this.setEditorBlueprint(new_blueprint);
		}
		this.updateSelectorWindowList();
	}

	@Override
	protected void _kill() {
		this.saveProject();
	}

	@Override
	protected void _resize() {
		this.componentSelectorWindow.setOffset(0, topBarHeightPx);
		this.componentSelectorWindow.setHeight(this.getHeight() - topBarHeightPx);

		this.circuitSimulatorWindow.setOffset(this.selectorWindowWidth, topBarHeightPx);
		this.circuitSimulatorWindow.setWidth(Math.max(10, this.getWidth() - this.selectorWindowWidth));
		this.circuitSimulatorWindow.setHeight(this.getHeight() - topBarHeightPx);
	}

	@Override
	public String getDefaultTitle() {
		return "Circuit Editor Window";
	}

	@Override
	protected void _update() {
		this.topBarSection.update();
	}

	@Override
	protected void renderContent(Framebuffer outputBuffer) {
		this.topBarSection.render(outputBuffer, this.getWindowMousePos());
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
		this.topBarSection.mousePressed(button);
	}

	@Override
	protected void _mouseReleased(int button) {
		this.topBarSection.mouseReleased(button);
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
	public void inputClicked(String sID) {
		switch (sID) {
		case "btn_new_circuit": {
			TextEntryWindow te_window = new TextEntryWindow(new NewCircuitCallback(), "New Circuit Name");
			AdjustableWindow adj_window = new AdjustableWindow(te_window, this);
			break;
		}
		}
	}

	@Override
	public void inputChanged(String sID) {

	}

	class ComponentSelectorCallback implements NestedListViewerCallback {

		private String lastCircuitSelected = null;

		@Override
		public void handleCallback(String _path, Object contents) {
			assert contents instanceof XMLNode;
			XMLNode node = (XMLNode) contents;

			String[] path = _path.split("/");
			LogicComponent selected_component = null;
			switch (path[0]) {
			case "circuits": {
				String circuit_name = node.getName();
				if (circuit_name.equals(this.lastCircuitSelected)) {
					setEditorBlueprint(project.getBlueprint(circuit_name));
				}
				else {
					this.lastCircuitSelected = circuit_name;
					//TODO set selected_component
				}

				break;
			}

			case "components": {
				this.lastCircuitSelected = null;
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

	class NewCircuitCallback implements TextEntryWindowCallback {
		@Override
		public void handleCallback(String text) {
			createNewBlueprint(text);
		}
	}

}
