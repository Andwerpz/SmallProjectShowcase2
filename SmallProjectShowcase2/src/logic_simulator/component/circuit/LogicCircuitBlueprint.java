package logic_simulator.component.circuit;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

import logic_simulator.component.InputPin;
import logic_simulator.component.LogicComponent;
import logic_simulator.component.OutputPin;
import myutils.file.xml.XMLNode;
import myutils.math.IVec2;

public class LogicCircuitBlueprint {
	//responsible for storing the inner workings of a LogicCircuit

	private String name;

	//for each logic component, have a map of locations. 
	//each location is the bottom left corner of that component. 
	private HashSet<LogicComponent> components;

	private ArrayList<InputPin> inputPins;
	private ArrayList<OutputPin> outputPins;

	public LogicCircuitBlueprint(String name) {
		this.name = name;
		this.components = new HashSet<>();
		this.inputPins = new ArrayList<>();
		this.outputPins = new ArrayList<>();
	}

	public String getName() {
		return this.name;
	}

	public int getNrInputs() {
		return this.inputPins.size();
	}

	public int getNrOutputs() {
		return this.outputPins.size();
	}

	public ArrayList<InputPin> getInputPins() {
		return this.inputPins;
	}

	public ArrayList<OutputPin> getOutputPins() {
		return this.outputPins;
	}

	public HashSet<LogicComponent> getComponents() {
		return this.components;
	}

	public void addComponent(LogicComponent component) {
		if (this.components.contains(component)) {
			return;
		}
		this.components.add(component);
		if (component instanceof InputPin) {
			this.inputPins.add((InputPin) component);
			((InputPin) component).setInputInd(this.inputPins.size() - 1);
		}
		else if (component instanceof OutputPin) {
			this.outputPins.add((OutputPin) component);
			((OutputPin) component).setOutputInd(this.outputPins.size() - 1);
		}
	}

	public void removeComponent(LogicComponent component) {
		if (!this.components.contains(component)) {
			return;
		}
		this.components.remove(component);
		if (component instanceof InputPin) {
			this.inputPins.remove(component);
			for (int i = 0; i < this.inputPins.size(); i++) {
				this.inputPins.get(i).setInputInd(i);
			}
		}
		else if (component instanceof OutputPin) {
			this.outputPins.remove(component);
			for (int i = 0; i < this.outputPins.size(); i++) {
				this.outputPins.get(i).setOutputInd(i);
			}
		}
	}

	public void removeAllComponents() {
		ArrayList<LogicComponent> to_remove = new ArrayList<>();
		to_remove.addAll(this.components);
		for (LogicComponent c : to_remove) {
			this.removeComponent(c);
		}
	}

	public XMLNode toXML() {
		XMLNode root = new XMLNode("blueprint");
		root.addAttribute("name", this.name);

		//have to write all components into xml
		for (LogicComponent c : this.components) {
			root.addChild(c.toXML());
		}

		return root;
	}

}
