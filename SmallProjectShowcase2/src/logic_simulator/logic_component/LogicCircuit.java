package logic_simulator.logic_component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

import myutils.math.IVec2;

public class LogicCircuit extends LogicComponent {
	//LogicCircuits should consist of multiple other logic components.
	
	//for each logic component, have a map of locations. 
	//each location is the bottom left corner of that component. 
	private HashMap<LogicComponent, HashSet<IVec2>> components;
	
	public LogicCircuit(int nr_inputs, int nr_outputs) {
		super(nr_inputs, nr_outputs);
		this.components = new HashMap<>();
	}
	
	public HashMap<LogicComponent, HashSet<IVec2>> getComponents() {
		return this.components;
	}
}
