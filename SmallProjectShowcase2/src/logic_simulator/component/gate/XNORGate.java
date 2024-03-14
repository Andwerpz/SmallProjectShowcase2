package logic_simulator.component.gate;

import static logic_simulator.component.TruthValue.*;

import logic_simulator.component.TruthValue;

public class XNORGate extends LogicGate {

	public XNORGate() {
		super(GateType.XNOR, 2, 1);
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
