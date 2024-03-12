package logic_simulator.logic_component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

import myutils.math.IVec2;

public class LogicComponent {
	
	
	//each logic component is going to link up one or many other logic components inside it. 
	//LogicComponent is only responsible for keeping track of the location of the components, and wiring that connects those components. 
	
	//Simulation on the other hand, is LogicComponentInstance's job. 
	//LogicComponentInstance will also figure out what components are connected together. 
	
	//the external size of a logic component is determined by how many inputs and outputs it has. 
	//this is why i'm thinking to make the number of inputs and outputs immutable. 
	//inputs enumerated along the left side, and outputs along the right side by default, just like logisim. 
	//locations of inputs and outputs should be allowed to change though. 
	
	//the user should be able to edit a LogicComponent any way they want, except in the case they create a circular dependency. 
	
	private int nr_inputs, nr_outputs;
	private IVec2[] input_offsets, output_offsets;	//relative to bottom left corner. 
	
	//external size of component
	private int width, height;
	
	public LogicComponent(int nr_inputs, int nr_outputs) {
		this.nr_inputs = nr_inputs;
		this.nr_outputs = nr_outputs;
		
		this.width = 10;
		this.height = Math.max(nr_inputs, nr_outputs) + 2;
		
		this.input_offsets = new IVec2[this.nr_inputs];
		this.output_offsets = new IVec2[this.nr_outputs];
		for(int i = 0; i < this.nr_inputs; i++) {
			this.input_offsets[i] = new IVec2(0, this.height - 1 - i);
		}
		for(int i = 0; i < this.nr_outputs; i++) {
			this.output_offsets[i] = new IVec2(this.width, this.height - 1 - i);
		}
	}
	
	public LogicComponentInstance toLogicComponentInstance() {
		return new LogicComponentInstance(this);
	}
	
	public int getNrInputs() {
		return this.nr_inputs;
	}
	
	public int getNrOutputs() {
		return this.nr_outputs;
	}
	
	public void setWidth(int width) {
		this.width = width;
	}
	
	public void setHeight(int height) {
		this.height = height;
	}
	
	public void setInputOffset(int ind, IVec2 offset) {
		assert ind >= 0 && ind < this.nr_inputs;
		this.input_offsets[ind].set(offset);
	}
	
	public void setOutputOffset(int ind, IVec2 offset) {
		assert ind >= 0 && ind < this.nr_outputs;
		this.output_offsets[ind].set(offset);
	}
}
