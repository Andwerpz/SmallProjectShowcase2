package logic_simulator.component.instance;

import logic_simulator.component.TruthValue;
import logic_simulator.component.Wire;
import myutils.math.IVec2;

public class WireInstance extends LogicComponentInstance {

	protected WireInstance(Wire wire) {
		super(wire);
	}

	@Override
	public boolean setInputs(TruthValue[] vals) {
		return this.setOutputs(vals);
	}

}
