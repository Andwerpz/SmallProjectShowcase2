package logic_simulator.component.gate;

import logic_simulator.component.LogicComponent;
import logic_simulator.component.TruthValue;
import logic_simulator.component.instance.LogicComponentInstance;

public abstract class LogicGate extends LogicComponent {
	//most basic logic components. 
	
	private GateType type;
	
	public LogicGate(GateType type, int nr_inputs, int nr_outputs) {
		super(nr_inputs, nr_outputs);
		this.type = type;
	}
	
	public GateType getType() {
		return this.type;
	}
	
	public abstract TruthValue calcTruthValue(TruthValue[] inputs);
	
}
