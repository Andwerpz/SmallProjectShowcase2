package logic_simulator.component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

import logic_simulator.component.instance.LogicComponentInstance;
import myutils.math.IVec2;

public class LogicComponent {
	//the external size of a logic component is determined by how many inputs and outputs it has. 
	//this is why i'm thinking to make the number of inputs and outputs immutable. 
	//inputs enumerated along the left side, and outputs along the right side by default, just like logisim. 
	//locations of inputs and outputs should be allowed to change though. 

	private int nrInputs, nrOutputs;

	//external io locations relative to bottom left corner. 
	private IVec2[] inputOffsets, outputOffsets;

	//external dimensions and offset of the component
	private int width, height;
	private IVec2 offset;

	public LogicComponent(int nr_inputs, int nr_outputs) {
		this.nrInputs = nr_inputs;
		this.nrOutputs = nr_outputs;

		this.width = 10;
		this.height = 2;
		this.offset = new IVec2(0);

		this.inputOffsets = new IVec2[nr_inputs];
		this.outputOffsets = new IVec2[nr_outputs];
	}

	public int getNrInputs() {
		return this.nrInputs;
	}

	public int getNrOutputs() {
		return this.nrOutputs;
	}

	public void setWidth(int width) {
		this.width = width;
	}

	public void setHeight(int height) {
		this.height = height;
	}

	public void setOffset(IVec2 offset) {
		this.offset.set(offset);
	}

	public int getWidth() {
		return this.width;
	}

	public int getHeight() {
		return this.height;
	}

	public IVec2 getOffset() {
		return this.offset;
	}

	public IVec2[] getInputOffsets() {
		return this.inputOffsets;
	}

	public IVec2[] getOutputOffsets() {
		return this.outputOffsets;
	}

	public void setInputOffset(int ind, IVec2 offset) {
		this.inputOffsets[ind].set(offset);
	}

	public void setOutputOffset(int ind, IVec2 offset) {
		this.outputOffsets[ind].set(offset);
	}
}
