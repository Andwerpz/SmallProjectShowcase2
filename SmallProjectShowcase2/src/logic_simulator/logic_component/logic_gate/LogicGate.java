package logic_simulator.logic_component.logic_gate;

import logic_simulator.logic_component.LogicComponent;
import logic_simulator.logic_component.LogicComponentInstance;
import logic_simulator.logic_component.TruthValue;

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
