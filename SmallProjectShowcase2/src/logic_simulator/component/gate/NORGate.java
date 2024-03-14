package logic_simulator.component.gate;

import static logic_simulator.component.TruthValue.*;

import logic_simulator.component.TruthValue;

public class NORGate extends LogicGate {

	public NORGate() {
		super(GateType.NOR, 2, 1);
	}

	@Override
	public TruthValue calcTruthValue(TruthValue[] inputs) {
		assert inputs.length == 2;
		if (inputs[0] == TRUE || inputs[1] == TRUE) {
			return FALSE;
		}
		if (inputs[0] == FALSE && inputs[1] == FALSE) {
			return TRUE;
		}
		return ERROR;
	}

}
