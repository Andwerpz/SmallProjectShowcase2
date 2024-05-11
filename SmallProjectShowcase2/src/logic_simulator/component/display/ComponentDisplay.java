package logic_simulator.component.display;

import logic_simulator.CircuitSimulatorWindow;
import logic_simulator.component.InputPin;
import logic_simulator.component.LogicComponent;
import logic_simulator.component.OutputPin;
import logic_simulator.component.Wire;
import logic_simulator.component.circuit.LogicCircuit;
import logic_simulator.component.gate.LogicGate;
import logic_simulator.component.instance.InputPinInstance;
import logic_simulator.component.instance.LogicCircuitInstance;
import logic_simulator.component.instance.LogicComponentInstance;
import logic_simulator.component.instance.LogicGateInstance;
import logic_simulator.component.instance.OutputPinInstance;
import logic_simulator.component.instance.WireInstance;
import lwjglengine.graphics.Material;
import lwjglengine.model.Line;
import lwjglengine.model.ModelInstance;
import myutils.math.IVec2;
import myutils.math.Vec2;

public abstract class ComponentDisplay {

	public static final Material ERROR_MATERIAL = CircuitSimulatorWindow.ERROR_MATERIAL;
	public static final Material TRUE_MATERIAL = CircuitSimulatorWindow.TRUE_MATERIAL;
	public static final Material FALSE_MATERIAL = CircuitSimulatorWindow.FALSE_MATERIAL;

	private final int COMPONENT_SELECT_SCENE;
	protected final int COMPONENT_SCENE;
	protected final int WIRE_SCENE;

	private LogicComponentInstance component;
	protected boolean isVisible = false;
	protected IVec2 offset;

	protected boolean isSelected = false;
	protected ModelInstance[] selectLines;

	protected CircuitSimulatorWindow window;

	public ComponentDisplay(LogicComponentInstance component, IVec2 offset, CircuitSimulatorWindow window) {
		this.COMPONENT_SELECT_SCENE = window.getComponentSelectScene();
		this.COMPONENT_SCENE = window.getComponentScene();
		this.WIRE_SCENE = window.getWireScene();
		this.window = window;
		this.component = component;
		this.offset = new IVec2(offset);
		this.selectLines = new ModelInstance[4];
	}

	public static ComponentDisplay createComponentDisplay(LogicComponent component, LogicComponentInstance instance, CircuitSimulatorWindow window) {
		ComponentDisplay display = null;
		if (component instanceof Wire) {
			display = new WireDisplay((WireInstance) instance, component.getOffset(), window);
		}
		else if (component instanceof LogicGate) {
			display = new GateDisplay((LogicGateInstance) instance, component.getOffset(), window);
		}
		else if (component instanceof InputPin) {
			display = new InputPinDisplay((InputPinInstance) instance, component.getOffset(), window);
		}
		else if (component instanceof OutputPin) {
			display = new OutputPinDisplay((OutputPinInstance) instance, component.getOffset(), window);
		}
		else if (component instanceof LogicCircuit) {
			display = new CircuitDisplay((LogicCircuitInstance) instance, component.getOffset(), window);
		}
		else {
			System.err.println("ComponentDisplay : unexpected component type");
		}
		assert display != null;
		return display;
	}

	public static ComponentDisplay createComponentDisplay(LogicComponent component, CircuitSimulatorWindow window) {
		return createComponentDisplay(component, LogicComponentInstance.createLogicComponentInstance(component), window);
	}

	public abstract void update();

	public abstract void kill();

	public abstract void setVisible(boolean b);

	public void refresh() {
		if (this.isVisible) {
			this.setVisible(false);
			this.setVisible(true);
			this.update();
		}
		if (this.isSelected) {
			this.setSelected(false);
			this.setSelected(true);
		}
	}

	public void setOffset(IVec2 offset) {
		if (this.offset.equals(offset)) {
			return;
		}

		this.offset = offset;
		this.refresh();
	}

	public void setSelected(boolean b) {
		if (this.isSelected && !b) {
			for (int i = 0; i < 4; i++) {
				this.selectLines[i].kill();
				this.selectLines[i] = null;
			}
		}
		else if (!this.isSelected && b) {
			Vec2 bl = new Vec2(this.offset);
			Vec2 tr = new Vec2(this.offset.add(this.component.getWidth(), this.component.getHeight()));
			bl.subi(0.5f);
			tr.addi(0.5f);
			Vec2[] corners = { new Vec2(bl.x, bl.y), new Vec2(tr.x, bl.y), new Vec2(tr.x, tr.y), new Vec2(bl.x, tr.y) };
			for (int i = 0; i < 4; i++) {
				this.selectLines[i] = Line.addDefaultLine(corners[i], corners[(i + 1) % 4], COMPONENT_SELECT_SCENE);
				this.selectLines[i].setMaterial(CircuitSimulatorWindow.SELECT_MATERIAL);
			}
		}
		this.isSelected = b;
	}
}
