package logic_simulator.logic_component;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;

import logic_simulator.logic_component.logic_gate.LogicGate;
import myutils.math.IVec2;
import myutils.misc.Pair;

public class LogicComponentInstance {
	//a logic component instance should store exactly 1 gate, or a collection of logic component instances, with the 
	//runtime values saved. 
	
	private boolean isGate = false;
	private LogicGate gate = null;
	
	private boolean isWire = false;
	
	private TruthValue[] inputs, outputs;
	
	private LogicComponentInstance[] components;
	
	//supposed to be created from a LogicComponent
	protected LogicComponentInstance(LogicComponent component) {
		this.inputs = new TruthValue[component.getNrInputs()];
		this.outputs = new TruthValue[component.getNrOutputs()];
		
		//initialize to error
		Arrays.fill(this.inputs, TruthValue.ERROR);
		Arrays.fill(this.outputs, TruthValue.ERROR);
		
		if(component instanceof LogicGate) {
			//this is just a single logic gate
			this.isGate = true;
			this.gate = (LogicGate) component;
			return;
		}
		else if(component instanceof Wire) {
			this.isWire = true;
			return;
		}
		
		//TODO otherwise, figure out all the connections 
		LogicCircuit circuit = (LogicCircuit) component;
		HashMap<LogicComponent, HashSet<IVec2>> logicComponents = circuit.getComponents();
		int nr_components = 0;
		for(LogicComponent c : logicComponents.keySet()) {
			HashSet<IVec2> locs = logicComponents.get(c);
			nr_components += locs.size();
		}
		
		this.components = new LogicComponentInstance[nr_components];
		for(LogicComponent c : logicComponents.keySet()) {
			HashSet<IVec2> locs = logicComponents.get(c);
			for(IVec2 loc : locs) {
				
			}
		}
	}
	
	public void setInput(int ind, TruthValue val) {
		assert ind >= 0 && ind < this.inputs.length;
		if(this.inputs[ind] == val) {
			//nothing changed
			return;
		}
		this.inputs[ind] = val;
		
		if(this.isGate) {
			//we can directly compute output value
			this.outputs[0] = this.gate.calcTruthValue(this.inputs);
			return;
		}
		
		//TODO update everything inside
	}

	public TruthValue getOutput(int ind) {
		assert ind >= 0 && ind < this.outputs.length;
		return this.outputs[ind];
	}
	
	private class LogicInput {
		public LogicInput(LogicComponentInstance component, int ind) {
			
		}
	}
	
}
