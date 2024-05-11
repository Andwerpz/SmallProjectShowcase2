package logic_simulator.component.display;

import static logic_simulator.component.TruthValue.ERROR;
import static logic_simulator.component.TruthValue.FALSE;
import static logic_simulator.component.TruthValue.TRUE;

import java.util.ArrayList;
import java.util.HashMap;

import logic_simulator.CircuitSimulatorWindow;
import logic_simulator.CircuitSimulatorWindow.LogicInput;
import logic_simulator.CircuitSimulatorWindow.LogicOutput;
import logic_simulator.component.TruthValue;
import logic_simulator.component.instance.WireInstance;
import lwjglengine.model.FilledRectangle;
import lwjglengine.model.ModelInstance;
import myutils.math.IVec2;
import myutils.math.MathUtils;
import myutils.math.Vec2;

public class WireDisplay extends ComponentDisplay {
	private static final float WIRE_WIDTH = CircuitSimulatorWindow.WIRE_WIDTH;

	private WireInstance wire;

	private ModelInstance wireInstance, e0Instance, e1Instance;

	public WireDisplay(WireInstance component, IVec2 offset, CircuitSimulatorWindow window) {
		super(component, offset, window);
		this.wire = component;
	}

	@Override
	public void update() {
		if (this.isVisible) {
			TruthValue d0 = this.wire.getOutput(0);
			TruthValue d1 = this.wire.getOutput(1);
			TruthValue data = ERROR;
			if (d0 == TRUE || d1 == TRUE) {
				data = TRUE;
			}
			else if (d0 == FALSE || d1 == FALSE) {
				data = FALSE;
			}
			switch (data) {
			case TRUE:
				this.wireInstance.setMaterial(TRUE_MATERIAL);
				this.e0Instance.setMaterial(TRUE_MATERIAL);
				this.e1Instance.setMaterial(TRUE_MATERIAL);
				break;

			case FALSE:
				this.wireInstance.setMaterial(FALSE_MATERIAL);
				this.e0Instance.setMaterial(FALSE_MATERIAL);
				this.e1Instance.setMaterial(FALSE_MATERIAL);
				break;

			case ERROR:
				this.wireInstance.setMaterial(ERROR_MATERIAL);
				this.e0Instance.setMaterial(ERROR_MATERIAL);
				this.e1Instance.setMaterial(ERROR_MATERIAL);
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
			this.wireInstance.kill();
			this.wireInstance = null;

			this.e0Instance.kill();
			this.e1Instance.kill();
			this.e0Instance = null;
			this.e1Instance = null;
		}
		else if (!this.isVisible && b) {
			IVec2 ie0 = this.offset.add(this.wire.getInputOffsets()[0]);
			IVec2 ie1 = this.offset.add(this.wire.getInputOffsets()[1]);

			Vec2 e0 = new Vec2(this.offset.add(this.wire.getInputOffsets()[0]));
			Vec2 e1 = new Vec2(this.offset.add(this.wire.getInputOffsets()[1]));
			Vec2 bl = MathUtils.min(e0, e1);
			Vec2 tr = MathUtils.max(e0, e1);
			this.wireInstance = FilledRectangle.addDefaultRectangle(bl.add(-WIRE_WIDTH / 2), tr.add(WIRE_WIDTH / 2), 0, WIRE_SCENE);

			HashMap<IVec2, ArrayList<LogicInput>> inputMap = this.window.getInputMap();
			HashMap<IVec2, ArrayList<LogicOutput>> outputMap = this.window.getOutputMap();

			int e0_ioamt = (inputMap.get(ie0) != null ? inputMap.get(ie0).size() : 0) + (outputMap.get(ie0) != null ? outputMap.get(ie0).size() : 0);
			int e1_ioamt = (inputMap.get(ie1) != null ? inputMap.get(ie1).size() : 0) + (outputMap.get(ie1) != null ? outputMap.get(ie1).size() : 0);

			float e0_size = e0_ioamt > 4 ? WIRE_WIDTH : WIRE_WIDTH / 2;
			float e1_size = e1_ioamt > 4 ? WIRE_WIDTH : WIRE_WIDTH / 2;

			this.e0Instance = FilledRectangle.addDefaultRectangle(e0.sub(e0_size), e0.add(e0_size), 0, WIRE_SCENE);
			this.e1Instance = FilledRectangle.addDefaultRectangle(e1.sub(e1_size), e1.add(e1_size), 0, WIRE_SCENE);
		}
		this.isVisible = b;
	}
}
