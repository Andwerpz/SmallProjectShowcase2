package logic_simulator.component.gate;

import static logic_simulator.component.TruthValue.*;

import logic_simulator.component.TruthValue;
import myutils.math.IVec2;

public class XORGate extends LogicGate {

	public XORGate(IVec2 offset) {
		super(GateType.XOR, 2, 1, offset);
		this.setWidth(3);
		this.setHeight(2);
		this.setInputOffset(0, 0, 0);
		this.setInputOffset(1, 0, 2);
		this.setOutputOffset(0, 3, 1);
	}

	@Override
	public TruthValue calcTruthValue(TruthValue[] inputs) {
		assert inputs.length == 2;
		if (inputs[0] == ERROR || inputs[1] == ERROR) {
			return ERROR;
		}
		if (inputs[0] != inputs[1]) {
			return TRUE;
		}
		return FALSE;
	}

}
