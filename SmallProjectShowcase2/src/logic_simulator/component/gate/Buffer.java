package logic_simulator.component.gate;

import logic_simulator.component.TruthValue;

public class Buffer extends LogicGate {

	public Buffer() {
		super(GateType.BUFFER, 1, 1);
	}

	@Override
	public TruthValue calcTruthValue(TruthValue[] inputs) {
		assert inputs.length == 1;
		return inputs[0];
	}

}
