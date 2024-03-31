package logic_simulator.component.gate;

import static logic_simulator.component.TruthValue.*;

import logic_simulator.component.TruthValue;
import myutils.math.IVec2;

public class XNORGate extends LogicGate {

	public XNORGate(IVec2 offset) {
		super(GateType.XNOR, 2, 1, offset);
		this.setWidth(4);
		this.setHeight(2);
		this.setInputOffset(0, 0, 0);
		this.setInputOffset(1, 0, 2);
		this.setOutputOffset(0, 4, 1);
	}

	@Override
	public TruthValue calcTruthValue(TruthValue[] inputs) {
		assert inputs.length == 2;
		if (inputs[0] == ERROR || inputs[1] == ERROR) {
			return ERROR;
		}
		if (inputs[0] != inputs[1]) {
			return FALSE;
		}
		return TRUE;
	}

}
