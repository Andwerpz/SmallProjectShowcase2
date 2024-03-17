package logic_simulator.component.instance;

import logic_simulator.component.TruthValue;
import logic_simulator.component.Wire;
import myutils.math.IVec2;

public class WireInstance extends LogicComponentInstance {
	
	public IVec2 e0, e1;

	protected WireInstance(Wire wire) {
		super(wire);
		this.e0 = new IVec2(wire.e0);
		this.e1 = new IVec2(wire.e1);
	}

	@Override
	public boolean setInputs(TruthValue[] vals) {
		TruthValue o_val = Wire.calcOutput(vals);
		return this.setOutput(0, o_val) && this.setOutput(1, o_val);
	}

}
