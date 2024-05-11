package logic_simulator.component.display;

import java.awt.Color;

import logic_simulator.CircuitSimulatorWindow;
import logic_simulator.component.instance.LogicGateInstance;
import lwjglengine.graphics.Material;
import lwjglengine.model.ModelInstance;
import myutils.math.IVec2;
import myutils.math.Vec2;

public class GateDisplay extends ComponentDisplay {
	private LogicGateInstance gate;
	private ModelInstance gateRect;

	public GateDisplay(LogicGateInstance component, IVec2 offset, CircuitSimulatorWindow window) {
		super(component, offset, window);
		this.gate = component;
		this.gateRect = null;
	}

	@Override
	public void update() {
		if (this.isVisible) {
			//nothing D:
		}
	}

	@Override
	public void kill() {
		this.setVisible(false);
	}

	@Override
	public void setVisible(boolean b) {
		if (this.isVisible && !b) {
			this.gateRect.kill();
			this.gateRect = null;
		}
		else if (!this.isVisible && b) {
			Vec2 c_offset = new Vec2(this.offset);
			float width = this.gate.getWidth();
			float height = this.gate.getHeight();
			Vec2 bl = new Vec2(c_offset);
			Vec2 tr = new Vec2(bl.x + width, bl.y + height);

			float extra_height = 0.3f;
			bl.addi(0, -extra_height);
			tr.addi(0, extra_height);
			this.gateRect = this.window.getGateRects().get(this.gate.getType()).addRectangle(bl, tr, 0, COMPONENT_SCENE);
			this.gateRect.setMaterial(new Material(Color.WHITE));
		}
		this.isVisible = b;
	}

}
