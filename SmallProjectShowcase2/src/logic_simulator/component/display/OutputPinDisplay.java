package logic_simulator.component.display;

import logic_simulator.CircuitSimulatorWindow;
import logic_simulator.component.TruthValue;
import logic_simulator.component.instance.OutputPinInstance;
import lwjglengine.model.FilledRectangle;
import lwjglengine.model.ModelInstance;
import myutils.math.IVec2;
import myutils.math.Vec2;

public class OutputPinDisplay extends ComponentDisplay {
	private OutputPinInstance outputPin;
	private ModelInstance dataRect, backgroundRect;

	public OutputPinDisplay(OutputPinInstance component, IVec2 offset, CircuitSimulatorWindow window) {
		super(component, offset, window);
		this.outputPin = component;
	}

	@Override
	public void update() {
		if (this.isVisible) {
			TruthValue data = this.outputPin.getInput(0);
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
			float width = this.outputPin.getWidth();
			float height = this.outputPin.getHeight();
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
