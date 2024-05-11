package logic_simulator.component.display;

import logic_simulator.CircuitSimulatorWindow;
import logic_simulator.component.TruthValue;
import logic_simulator.component.instance.InputPinInstance;
import lwjglengine.model.FilledRectangle;
import lwjglengine.model.ModelInstance;
import myutils.math.IVec2;
import myutils.math.Vec2;

public class InputPinDisplay extends ComponentDisplay {
	private InputPinInstance inputPin;
	private ModelInstance dataRect, backgroundRect;

	public InputPinDisplay(InputPinInstance component, IVec2 offset, CircuitSimulatorWindow window) {
		super(component, offset, window);
		this.inputPin = component;
	}

	@Override
	public void update() {
		if (this.isVisible) {
			TruthValue data = this.inputPin.getOutput(0);
			switch (data) {
			case TRUE:
				this.dataRect.setMaterial(TRUE_MATERIAL);
				break;

			case FALSE:
				this.dataRect.setMaterial(FALSE_MATERIAL);
				break;

			case ERROR:
				this.dataRect.setMaterial(ERROR_MATERIAL);
				break;
			}
		}
	}

	@Override
	public void kill() {
		this.setVisible(false);
	}

	@Override
	public void setVisible(boolean b) {
		if (this.isVisible && !b) {
			this.dataRect.kill();
			this.dataRect = null;

			this.backgroundRect.kill();
			this.backgroundRect = null;
		}
		else if (!this.isVisible && b) {
			Vec2 offset = new Vec2(this.offset);
			float width = this.inputPin.getWidth();
			float height = this.inputPin.getHeight();
			Vec2 bl = new Vec2(offset);
			Vec2 tr = bl.add(width, height);
			this.backgroundRect = FilledRectangle.addDefaultRectangle(bl, tr, 0, COMPONENT_SCENE);

			float dr_size = 0.5f;
			Vec2 center = offset.add(width / 2, height / 2);
			this.dataRect = FilledRectangle.addDefaultRectangle(center.sub(dr_size), center.add(dr_size), 1, COMPONENT_SCENE);
		}
		this.isVisible = b;
	}
}
