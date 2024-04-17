package logic_simulator.component;

import myutils.file.xml.XMLNode;
import myutils.math.IVec2;

public class InputPin extends LogicComponent {
	private int inputInd; //which input does this correspond to?

	public InputPin(IVec2 offset) {
		super(ComponentType.INPUT_PIN, 0, 1, offset);
		this.setWidth(3);
		this.setHeight(2);
		this.setOutputOffset(0, new IVec2(3, 1));
	}

	public InputPin(InputPin c) {
		super(c);
	}

	public void setInputInd(int ind) {
		this.inputInd = ind;
	}

	public int getInputInd() {
		return this.inputInd;
	}

	@Override
	protected void _toXML(XMLNode node) {

	}
}
