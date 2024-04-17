package logic_simulator.component.gate;

import logic_simulator.component.LogicComponent;
import logic_simulator.component.TruthValue;
import logic_simulator.component.instance.LogicComponentInstance;
import myutils.file.xml.XMLNode;
import myutils.math.IVec2;

public abstract class LogicGate extends LogicComponent {
	//most basic logic components. 

	private GateType type;

	public LogicGate(GateType type, int nr_inputs, int nr_outputs, IVec2 offset) {
		super(ComponentType.GATE, nr_inputs, nr_outputs, offset);
		this.type = type;
	}

	public LogicGate(LogicGate g) {
		super(g);
		this.type = g.getType();
	}

	public GateType getType() {
		return this.type;
	}

	public abstract TruthValue calcTruthValue(TruthValue[] inputs);

	public static LogicGate createGate(GateType type, IVec2 offset) {
		switch (type) {
		case AND:
			return new ANDGate(offset);
		case NAND:
			return new NANDGate(offset);
		case OR:
			return new ORGate(offset);
		case NOR:
			return new NORGate(offset);
		case XOR:
			return new XORGate(offset);
		case XNOR:
			return new XNORGate(offset);
		case INVERTER:
			return new Inverter(offset);
		}
		return null;
	}

	public static IVec2 getGateTypeDimensions(GateType type) {
		LogicGate gate = createGate(type, new IVec2(0));
		IVec2 ans = new IVec2();
		ans.x = gate.getWidth();
		ans.y = gate.getHeight();
		return ans;
	}

	@Override
	protected void _toXML(XMLNode node) {
		node.addAttribute("gate_type", this.type.toString());
	}

}
