package logic_simulator.component.gate;

import static logic_simulator.component.TruthValue.*;

import logic_simulator.component.TruthValue;

public class Inverter extends LogicGate {

	public Inverter() {
		super(GateType.INVERTER, 1, 1);
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
