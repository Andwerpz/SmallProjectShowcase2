package logic_simulator.component.instance;

import logic_simulator.component.TruthValue;
import logic_simulator.component.Wire;

public class WireInstance extends LogicComponentInstance {

	protected WireInstance(Wire wire) {
		super(wire);
	}

	@Override
	public boolean setInputs(TruthValue[] vals) {
		TruthValue o_val = Wire.calcOutput(vals);
		return this.setOutput(0, o_val) && this.setOutput(1, o_val);
	}

}
