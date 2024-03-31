package logic_simulator.component.instance;

import logic_simulator.component.LogicComponent;
import logic_simulator.component.OutputPin;
import logic_simulator.component.TruthValue;

public class OutputPinInstance extends LogicComponentInstance {

	private int outputInd;

	protected OutputPinInstance(OutputPin component) {
		super(component);
		this.outputInd = component.getOutputInd();
	}

	@Override
	public boolean setInputs(TruthValue[] vals) {
		//an output pin has no outputs, so result is always false. 
		assert vals.length == 1;
		this.inputs[0] = vals[0];
		return false;
	}

	public int getOutputInd() {
		return this.outputInd;
	}

}
