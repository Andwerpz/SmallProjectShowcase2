package logic_simulator.component.instance;

import static logic_simulator.component.TruthValue.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;

import logic_simulator.component.InputPin;
import logic_simulator.component.LogicComponent;
import logic_simulator.component.OutputPin;
import logic_simulator.component.TruthValue;
import logic_simulator.component.Wire;
import logic_simulator.component.circuit.LogicCircuit;
import logic_simulator.component.gate.LogicGate;
import myutils.math.IVec2;
import myutils.misc.Pair;

public abstract class LogicComponentInstance {
	//a logic component instance should be a self-contained circuit. 

	protected TruthValue[] inputs, outputs;

	private int width, height;

	private IVec2[] inputOffsets, outputOffsets;

	//supposed to be created from a LogicComponent
	public static LogicComponentInstance createLogicComponentInstance(LogicComponent component) {
		if (component instanceof LogicCircuit) {
			return new LogicCircuitInstance((LogicCircuit) component);
		}
		else if (component instanceof LogicGate) {
			return new LogicGateInstance((LogicGate) component);
		}
		else if (component instanceof Wire) {
			return new WireInstance((Wire) component);
		}
		else if (component instanceof InputPin) {
			return new InputPinInstance((InputPin) component);
		}
		else if (component instanceof OutputPin) {
			return new OutputPinInstance((OutputPin) component);
		}
		assert false; //D:
		return null;
	}

	protected LogicComponentInstance(LogicComponent component) {
		this.width = component.getWidth();
		this.height = component.getHeight();

		this.inputOffsets = new IVec2[component.getNrInputs()];
		this.outputOffsets = new IVec2[component.getNrOutputs()];
		for (int i = 0; i < this.inputOffsets.length; i++) {
			this.inputOffsets[i] = new IVec2(component.getInputOffsets()[i]);
		}
		for (int i = 0; i < this.outputOffsets.length; i++) {
			this.outputOffsets[i] = new IVec2(component.getOutputOffsets()[i]);
		}

		this.inputs = new TruthValue[component.getNrInputs()];
		this.outputs = new TruthValue[component.getNrOutputs()];

		//initialize to error
		Arrays.fill(this.inputs, TruthValue.ERROR);
		Arrays.fill(this.outputs, TruthValue.ERROR);
	}

	public int getNrInputs() {
		return this.inputs.length;
	}

	public int getNrOutputs() {
		return this.outputs.length;
	}

	public int getWidth() {
		return this.width;
	}

	public int getHeight() {
		return this.height;
	}

	public IVec2[] getInputOffsets() {
		return this.inputOffsets;
	}

	public IVec2[] getOutputOffsets() {
		return this.outputOffsets;
	}

	public boolean setInput(int ind, TruthValue val) {
		assert ind >= 0 && ind < this.getNrInputs();
		TruthValue[] vals = new TruthValue[this.getNrInputs()];
		for (int i = 0; i < vals.length; i++) {
			vals[i] = i == ind ? val : this.inputs[i];
		}
		return this.setInputs(vals);
	}

	public abstract boolean setInputs(TruthValue[] vals);

	public TruthValue getInput(int ind) {
		assert ind >= 0 && ind < this.getNrInputs();
		return this.inputs[ind];
	}

	/**
	 * Returns true if output changes
	 * @param ind
	 * @param val
	 * @return
	 */
	protected boolean setOutput(int ind, TruthValue val) {
		assert ind >= 0 && ind < this.outputs.length;
		boolean ans = this.outputs[ind] != val;
		this.outputs[ind] = val;
		return ans;
	}

	protected boolean setOutputs(TruthValue[] vals) {
		assert vals.length == this.outputs.length;
		boolean ans = false;
		for (int i = 0; i < vals.length; i++) {
			ans |= this.setOutput(i, vals[i]);
		}
		return ans;
	}

	public TruthValue getOutput(int ind) {
		assert ind >= 0 && ind < this.outputs.length;
		return this.outputs[ind];
	}

}
