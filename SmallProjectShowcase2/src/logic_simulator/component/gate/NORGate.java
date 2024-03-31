package logic_simulator.component.gate;

import static logic_simulator.component.TruthValue.*;

import logic_simulator.component.TruthValue;
import myutils.math.IVec2;

public class NORGate extends LogicGate {

	public NORGate(IVec2 offset) {
		super(GateType.NOR, 2, 1, offset);
		this.setWidth(4);
		this.setHeight(2);
		this.setInputOffset(0, 0, 0);
		this.setInputOffset(1, 0, 2);
		this.setOutputOffset(0, 4, 1);
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
