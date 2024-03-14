package logic_simulator.component.circuit;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

import logic_simulator.component.InputPin;
import logic_simulator.component.LogicComponent;
import myutils.math.IVec2;

public class LogicCircuit extends LogicComponent {
	//LogicCircuits should consist of multiple other logic components.

	//each logic circuit is going to link up one or many other logic components inside it. 
	//LogicCircuit is only responsible for keeping track of the location of the components
	//Simulation on the other hand, is LogicComponentInstance's job. 
	//LogicComponentInstance will also figure out what components are connected together. 

	//the user should be able to edit a LogicCircuit any way they want, except in the case they create a circular dependency. 

	private LogicCircuitBlueprint blueprint;

	public LogicCircuit(LogicCircuit other) {
		this(other.getBlueprint());
		this.setOffset(other.getOffset());
	}

	public LogicCircuit(LogicCircuitBlueprint blueprint) {
		super(blueprint.getNrInputs(), blueprint.getNrOutputs());
		this.blueprint = blueprint;

		//set up input/output locations. 
		int nr_inputs = blueprint.getNrInputs();
		int nr_outputs = blueprint.getNrOutputs();
		this.setWidth(10);
		this.setHeight(Math.max(nr_inputs, nr_outputs) + 3);
		for (int i = 0; i < nr_inputs; i++) {
			this.setInputOffset(i, new IVec2(0, this.getHeight() - 1 - i));
		}
		for (int i = 0; i < nr_outputs; i++) {
			this.setInputOffset(i, new IVec2(this.getWidth(), this.getHeight() - 1 - i));
		}
	}

	public LogicCircuitBlueprint getBlueprint() {
		return this.blueprint;
	}
}
