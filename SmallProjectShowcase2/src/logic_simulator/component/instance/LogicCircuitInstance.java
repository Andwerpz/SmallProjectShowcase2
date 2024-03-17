package logic_simulator.component.instance;

import static logic_simulator.component.TruthValue.ERROR;
import static logic_simulator.component.TruthValue.FALSE;
import static logic_simulator.component.TruthValue.TRUE;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Queue;

import logic_simulator.component.InputPin;
import logic_simulator.component.LogicComponent;
import logic_simulator.component.OutputPin;
import logic_simulator.component.TruthValue;
import logic_simulator.component.Wire;
import logic_simulator.component.circuit.LogicCircuit;
import logic_simulator.component.circuit.LogicCircuitBlueprint;
import logic_simulator.component.gate.LogicGate;
import myutils.math.IVec2;

public class LogicCircuitInstance extends LogicComponentInstance {
	//logic circuit instance should be 'immutable' in the sense that you can't edit the circuit once it's been initialized. 

	private LogicComponentInstance[] components;

	//for each location, give a list of logic inputs
	private HashMap<IVec2, ArrayList<LogicInput>> inputMap;

	private int[] inputPinInds, outputPinInds;

	//for each component, what are it's logic inputs?
	private LogicInput[][] componentLogicInputs;

	protected LogicCircuitInstance(LogicCircuit circuit) {
		super(circuit);

		LogicCircuitBlueprint blueprint = circuit.getBlueprint();
		HashSet<LogicComponent> components = blueprint.getComponents();

		this.components = new LogicComponentInstance[components.size()];
		this.inputPinInds = new int[blueprint.getNrInputs()];
		this.outputPinInds = new int[blueprint.getNrOutputs()];
		{
			int input_pin_ptr = 0;
			int output_pin_ptr = 0;
			int ptr = 0;
			for (LogicComponent c : components) {
				this.components[ptr] = LogicComponentInstance.createLogicComponentInstance(c);
				if (c instanceof InputPin) {
					this.inputPinInds[input_pin_ptr++] = ptr;
				}
				else if (c instanceof OutputPin) {
					this.outputPinInds[output_pin_ptr++] = ptr;
				}
				ptr++;
			}
		}

		//for each location, keep track of what inputs there are. 
		this.inputMap = new HashMap<>();
		this.componentLogicInputs = new LogicInput[components.size()][];
		for (int i = 0; i < this.components.length; i++) {
			IVec2 component_offset = this.components[i].getOffset();
			IVec2[] input_offsets = this.components[i].getInputOffsets();
			this.componentLogicInputs[i] = new LogicInput[input_offsets.length];
			for (int j = 0; j < input_offsets.length; j++) {
				IVec2 input_loc = component_offset.add(input_offsets[i]);
				LogicInput l_input = new LogicInput(i, j);
				if (!this.inputMap.containsKey(input_loc)) {
					this.inputMap.put(input_loc, new ArrayList<>());
				}
				this.inputMap.get(input_loc).add(l_input);
				this.componentLogicInputs[i][j] = l_input;
			}
		}
	}

	@Override
	public boolean setInputs(TruthValue[] vals) {
		assert vals.length == this.getNrInputs();
		int[] q_cnt = new int[this.components.length];
		Queue<Integer> q = new ArrayDeque<>();
		boolean changed = false;
		for (int i = 0; i < vals.length; i++) {
			if (this.inputs[i] != vals[i]) {
				this.inputs[i] = vals[i];
				changed = true;
				InputPinInstance i_pin = (InputPinInstance) this.components[this.inputPinInds[i]];
				i_pin.setOutput(0, this.inputs[i]);
				q.add(this.inputPinInds[i]);
				q_cnt[this.inputPinInds[i]]++;
			}
		}
		if (!changed) {
			return false;
		}

		//update everything inside
		while (q.size() != 0) {
			int cur = q.poll();
			LogicComponentInstance cur_component = this.components[cur];

			q_cnt[cur]--;
			if (q_cnt[cur] != 0) {
				continue;
			}

			//update component logic
			TruthValue[] input = new TruthValue[cur_component.getNrInputs()];
			for (int i = 0; i < input.length; i++) {
				input[i] = this.componentLogicInputs[cur][i].getData();
			}
			if (!cur_component.setInputs(input)) {
				continue;
			}

			//if outputs changed, put new stuff in queue
			IVec2 component_offset = cur_component.getOffset();
			IVec2[] output_offsets = cur_component.getOutputOffsets();
			for (int i = 0; i < output_offsets.length; i++) {
				IVec2 output_loc = component_offset.add(output_offsets[i]);
				ArrayList<LogicInput> logic_inputs = this.inputMap.get(output_loc);
				if (logic_inputs == null) {
					continue;
				}
				for (int j = 0; j < logic_inputs.size(); j++) {
					LogicInput l_input = logic_inputs.get(j);
					l_input.setOutput(cur, cur_component.getOutput(i));
					q.add(l_input.component_ind);
					q_cnt[l_input.component_ind]++;
				}
			}
		}

		//check if output pins have any different values
		TruthValue[] out_vals = new TruthValue[this.getNrOutputs()];
		for (int i = 0; i < this.outputPinInds.length; i++) {
			OutputPinInstance out_pin = (OutputPinInstance) this.components[this.outputPinInds[i]];
			out_vals[i] = out_pin.getInput(0);
		}
		return this.setOutputs(out_vals);
	}

	private class LogicInput {
		//helper class responsible for handling the case where there are multiple outputs feeding into an input. 
		//should behave the same as a wire. 

		public int component_ind;
		public int input_ind;

		private int nr_true, nr_false;
		private HashMap<Integer, TruthValue> outputMap; //{logic component index, truth value}
		private TruthValue data = ERROR;

		public LogicInput(int component_ind, int ind) {
			this.component_ind = component_ind;
			this.input_ind = ind;

			this.outputMap = new HashMap<>();

			this.nr_true = 0;
			this.nr_false = 0;
		}

		/**
		 * Returns true if data changes. 
		 * @param component
		 * @param val
		 * @return
		 */
		public boolean setOutput(int component_ind, TruthValue val) {
			if (component_ind == this.component_ind) {
				//in the case that the input and output locations overlap. 
				return false;
			}
			if (this.outputMap.containsKey(component_ind)) {
				TruthValue old_val = this.outputMap.get(component_ind);
				this.nr_true -= old_val == TRUE ? 1 : 0;
				this.nr_false -= old_val == FALSE ? 1 : 0;
			}
			this.nr_true += val == TRUE ? 1 : 0;
			this.nr_false += val == FALSE ? 1 : 0;
			this.outputMap.put(component_ind, val);
			TruthValue old_data = this.data;
			this.data = this.nr_true != 0 ? TRUE : (this.nr_false != 0 ? FALSE : ERROR);
			return old_data != this.data;
		}

		public TruthValue getData() {
			return this.data;
		}
	}

}
