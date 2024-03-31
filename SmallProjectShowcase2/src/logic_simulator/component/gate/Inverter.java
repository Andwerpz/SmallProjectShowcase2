package logic_simulator.component.gate;

import static logic_simulator.component.TruthValue.*;

import logic_simulator.component.TruthValue;
import myutils.math.IVec2;

public class Inverter extends LogicGate {

	public Inverter(IVec2 offset) {
		super(GateType.INVERTER, 1, 1, offset);
		this.setWidth(3);
		this.setHeight(2);
		this.setInputOffset(0, 0, 1);
		this.setOutputOffset(0, 3, 1);
	}

	@Override
	public TruthValue calcTruthValue(TruthValue[] inputs) {
		assert inputs.length == 1;
		if (inputs[0] == ERROR) {
			return ERROR;
		}
		return inputs[0] == TRUE ? FALSE : TRUE;
	}

}
