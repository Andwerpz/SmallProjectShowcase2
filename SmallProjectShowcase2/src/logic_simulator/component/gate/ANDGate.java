package logic_simulator.component.gate;

import static logic_simulator.component.TruthValue.*;

import logic_simulator.component.TruthValue;
import myutils.math.IVec2;

public class ANDGate extends LogicGate {

	public ANDGate(IVec2 offset) {
		super(GateType.AND, 2, 1, offset);
		this.setWidth(3);
		this.setHeight(2);
		this.setInputOffset(0, 0, 0);
		this.setInputOffset(1, 0, 2);
		this.setOutputOffset(0, 3, 1);
	}

	@Override
	public TruthValue calcTruthValue(TruthValue[] inputs) {
		assert inputs.length == 2;
		if (inputs[0] == TRUE && inputs[1] == TRUE) {
			return TRUE;
		}
		if (inputs[0] == FALSE || inputs[1] == FALSE) {
			return FALSE;
		}
		return ERROR;
	}

}
