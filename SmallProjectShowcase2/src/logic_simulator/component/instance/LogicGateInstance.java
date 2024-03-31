package logic_simulator.component.instance;

import logic_simulator.component.TruthValue;
import logic_simulator.component.gate.GateType;
import logic_simulator.component.gate.LogicGate;

public class LogicGateInstance extends LogicComponentInstance {

	private LogicGate gate;

	protected LogicGateInstance(LogicGate gate) {
		super(gate);
		this.gate = gate;
	}

	@Override
	public boolean setInputs(TruthValue[] vals) {
		return this.setOutput(0, this.gate.calcTruthValue(vals));
	}

	public GateType getType() {
		return this.gate.getType();
	}

}
