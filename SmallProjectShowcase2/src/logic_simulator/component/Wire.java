package logic_simulator.component;

import static logic_simulator.component.TruthValue.*;

import myutils.file.xml.XMLNode;
import myutils.math.IVec2;
import myutils.math.MathUtils;

public class Wire extends LogicComponent {
	//a wire is only defined by it's starting and ending points. 
	//it should only be able to connect to other things through it's endpoints. 

	//for example, in order to create a T junction, 2 wires might seem intuitive, but we need
	//3 if we want there to be a 3 way connection. 

	//a wire should behave the same as two parallel buffers. 

	public Wire(IVec2 e0, IVec2 e1) {
		super(ComponentType.WIRE, 2, 2, MathUtils.min(e0, e1));

		this.setWidth(Math.abs(e0.x - e1.x));
		this.setHeight(Math.abs(e0.y - e1.y));

		this.setInputOffset(0, e0.sub(this.getOffset()));
		this.setInputOffset(1, e1.sub(this.getOffset()));
		this.setOutputOffset(0, e1.sub(this.getOffset()));
		this.setOutputOffset(1, e0.sub(this.getOffset()));
	}

	public Wire(Wire w) {
		super(w);
	}

	@Override
	protected void _toXML(XMLNode node) {

	}

}
