package logic_simulator.component;

import myutils.file.xml.XMLNode;
import myutils.math.IVec2;

public class OutputPin extends LogicComponent {
	private int outputInd; //which output does this correspond to?

	public OutputPin(IVec2 offset) {
		super(ComponentType.OUTPUT_PIN, 1, 0, offset);
		this.setWidth(3);
		this.setHeight(2);
		this.setInputOffset(0, new IVec2(0, 1));
	}

	public OutputPin(OutputPin c) {
		super(c);
	}

	public void setOutputInd(int ind) {
		this.outputInd = ind;
	}

	public int getOutputInd() {
		return this.outputInd;
	}

	@Override
	protected void _toXML(XMLNode node) {

	}
}
