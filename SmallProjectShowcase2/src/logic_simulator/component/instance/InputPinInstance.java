package logic_simulator.component.instance;

import logic_simulator.component.InputPin;
import logic_simulator.component.LogicComponent;
import logic_simulator.component.TruthValue;

public class InputPinInstance extends LogicComponentInstance {

	private int inputInd;
	private TruthValue data;

	protected InputPinInstance(InputPin component) {
		super(component);
		this.inputInd = component.getInputInd();
		this.data = TruthValue.ERROR;
	}

	public void setData(TruthValue new_data) {
		this.data = new_data;
		this.setOutput(0, this.data);
	}

	public TruthValue getData() {
		return this.data;
	}

	@Override
	public boolean setInputs(TruthValue[] vals) {
		//an input pin should not have an input, so this method doesn't really make sense. 
		//buut, it's useful to return true, sooo
		return true;
	}

	public int getInputInd() {
		return this.inputInd;
	}
}
