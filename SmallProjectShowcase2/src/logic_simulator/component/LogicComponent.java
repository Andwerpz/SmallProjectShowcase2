package logic_simulator.component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

import logic_simulator.component.gate.LogicGate;
import logic_simulator.component.instance.LogicComponentInstance;
import myutils.math.IVec2;

public abstract class LogicComponent {
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

	public LogicComponent(int nr_inputs, int nr_outputs, IVec2 offset) {
		this.nrInputs = nr_inputs;
		this.nrOutputs = nr_outputs;
		this.offset = new IVec2(offset);

		this.width = 10;
		this.height = 2;

		this.inputOffsets = new IVec2[nr_inputs];
		this.outputOffsets = new IVec2[nr_outputs];

		for (int i = 0; i < nr_inputs; i++) {
			this.inputOffsets[i] = new IVec2(0, 0);
		}
		for (int i = 0; i < nr_outputs; i++) {
			this.outputOffsets[i] = new IVec2(0, 0);
		}
	}

	public LogicComponent(LogicComponent c) {
		this.nrInputs = c.nrInputs;
		this.nrOutputs = c.nrOutputs;
		this.offset = new IVec2(c.offset);

		this.width = c.width;
		this.height = c.height;

		this.inputOffsets = new IVec2[this.nrInputs];
		this.outputOffsets = new IVec2[this.nrOutputs];

		for (int i = 0; i < this.nrInputs; i++) {
			this.inputOffsets[i] = new IVec2(c.inputOffsets[i]);
		}
		for (int i = 0; i < this.nrOutputs; i++) {
			this.outputOffsets[i] = new IVec2(c.outputOffsets[i]);
		}
	}

	public static LogicComponent copyComponent(LogicComponent c) {
		if (c instanceof LogicGate) {
			return LogicGate.createGate(((LogicGate) c).getType(), c.offset);
		}
		else if (c instanceof Wire) {
			return new Wire((Wire) c);
		}
		else if (c instanceof InputPin) {
			return new InputPin((InputPin) c);
		}
		else if (c instanceof OutputPin) {
			return new OutputPin((OutputPin) c);
		}
		assert false;
		return null;
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

	public void setInputOffset(int ind, int x, int y) {
		this.setInputOffset(ind, new IVec2(x, y));
	}

	public void setOutputOffset(int ind, int x, int y) {
		this.setOutputOffset(ind, new IVec2(x, y));
	}
}
