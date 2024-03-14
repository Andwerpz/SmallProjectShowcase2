package logic_simulator.component;

import myutils.math.IVec2;

public class InputPin extends LogicComponent {
	private int inputInd; //which input does this correspond to?

	public InputPin() {
		super(0, 1);
		this.setWidth(3);
		this.setHeight(2);
		this.setOutputOffset(0, new IVec2(3, 1));
	}

	public void setInputInd(int ind) {
		this.inputInd = ind;
	}

	public int getInputInd() {
		return this.inputInd;
	}
}
