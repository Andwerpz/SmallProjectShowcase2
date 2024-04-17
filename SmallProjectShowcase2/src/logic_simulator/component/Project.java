package logic_simulator.component;

import java.io.File;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Queue;
import java.util.Set;

import logic_simulator.component.LogicComponent.ComponentType;
import logic_simulator.component.circuit.LogicCircuit;
import logic_simulator.component.circuit.LogicCircuitBlueprint;
import logic_simulator.component.gate.GateType;
import logic_simulator.component.gate.LogicGate;
import myutils.file.xml.XMLNode;
import myutils.file.xml.XMLReader;
import myutils.math.IVec2;

public class Project {
	//stores all the relevant blueprints for this project. 
	//also keeps track of the dependency graph between blueprints. 

	//TODO 
	// - check for circular dependencies.
	// - when we're adding a logic circuit to a blueprint, we should double check with the project to make sure that adding it won't 
	//   create a dependency cycle. 

	//each logic circuit will have a user assigned name. 
	private HashMap<String, LogicCircuitBlueprint> blueprints;

	//by default is empty, and cannot be a dependent of anyone. 
	private LogicCircuitBlueprint main;

	public Project() {
		this.blueprints = new HashMap<>();
		this.main = this.createNewBlueprint("main");
	}

	public Project(File file) {
		this.blueprints = new HashMap<>();

		//read in project from file
		XMLNode root = XMLReader.parseFileAsXML(file);
		XMLNode project_root = root.getChildren().get(0);
		assert project_root.getName() == "project";

		//read in blueprints sequentially. They should be stored in some sort of topological order. 
		for (XMLNode blueprint_root : project_root.getChildren()) {
			LogicCircuitBlueprint blueprint = this.parseXMLAsBlueprint(blueprint_root);
			this.blueprints.put(blueprint.getName(), blueprint);
			if (blueprint.getName().equals("main")) {
				this.main = blueprint;
			}
		}
	}

	public LogicCircuitBlueprint parseXMLAsBlueprint(XMLNode root) {
		assert root.getName() == "blueprint";
		LogicCircuitBlueprint blueprint = new LogicCircuitBlueprint(root.getAttributeContent("name"));
		for (XMLNode component_node : root.getChildren()) {
			blueprint.addComponent(this.parseXMLAsComponent(component_node));
		}
		return blueprint;
	}

	public LogicComponent parseXMLAsComponent(XMLNode node) {
		assert node.getName() == "logic_component";
		ComponentType component_type = ComponentType.valueOf(node.getAttributeContent("component_type"));
		int width = Integer.parseInt(node.getAttributeContent("component_width"));
		int height = Integer.parseInt(node.getAttributeContent("component_height"));
		String[] offset_str = node.getAttributeContent("component_offset").split(" ");
		IVec2 offset = new IVec2(Integer.parseInt(offset_str[0]), Integer.parseInt(offset_str[1]));
		switch (component_type) {
		case CIRCUIT:
			String blueprint_name = node.getAttributeContent("blueprint_name");
			return new LogicCircuit(this.getBlueprint(blueprint_name), offset);
		case GATE:
			GateType gate_type = GateType.valueOf(node.getAttributeContent("gate_type"));
			return LogicGate.createGate(gate_type, offset);
		case INPUT_PIN:
			return new InputPin(offset);
		case OUTPUT_PIN:
			return new OutputPin(offset);
		case WIRE:
			return new Wire(offset, offset.add(width, height));
		}
		assert false;
		return null;
	}

	/**
	 * Returns the blueprint if successful, null otherwise
	 * @param name
	 * @return
	 */
	public LogicCircuitBlueprint createNewBlueprint(String name) {
		if (blueprints.containsKey(name)) {
			return null;
		}
		blueprints.put(name, new LogicCircuitBlueprint(name));
		return blueprints.get(name);
	}

	public Set<String> getBlueprintNames() {
		return blueprints.keySet();
	}

	public LogicCircuitBlueprint getBlueprint(String name) {
		return blueprints.get(name);
	}

	public LogicCircuit getCircuit(String name) {
		return new LogicCircuit(getBlueprint(name), new IVec2(0));
	}

	public LogicCircuitBlueprint getMainBlueprint() {
		return main;
	}

	/**
	 * Returns the set of all blueprints that this one depends on
	 * The set does not include itself
	 * @param blueprint
	 * @return
	 */
	private HashSet<LogicCircuitBlueprint> getDependencies(LogicCircuitBlueprint blueprint) {
		HashSet<LogicCircuitBlueprint> ret = new HashSet<>();
		Queue<LogicCircuitBlueprint> q = new ArrayDeque<>();
		q.add(blueprint);
		while (q.size() != 0) {
			LogicCircuitBlueprint cur = q.poll();
			for (LogicComponent component : cur.getComponents()) {
				if (!(component instanceof LogicCircuit)) {
					continue;
				}
				LogicCircuit circuit = (LogicCircuit) component;
				LogicCircuitBlueprint next = circuit.getBlueprint();
				if (!ret.contains(blueprint)) {
					ret.add(next);
					q.add(next);
				}
			}
		}
		return ret;
	}

	/**
	 * Returns an ordering such that any blueprint in the list has all of the blueprints it needs to function to its left. 
	 * @return
	 */
	public ArrayList<LogicCircuitBlueprint> getTopologicalOrdering() {
		//create graph
		HashMap<LogicCircuitBlueprint, HashSet<LogicCircuitBlueprint>> c = new HashMap<>();
		HashMap<LogicCircuitBlueprint, Integer> indeg = new HashMap<>();
		for (LogicCircuitBlueprint b : this.blueprints.values()) {
			c.put(b, new HashSet<>());
			indeg.put(b, 0);
		}

		for (LogicCircuitBlueprint b : this.blueprints.values()) {
			HashSet<LogicCircuitBlueprint> dependencies = this.getDependencies(b);
			for (LogicCircuitBlueprint d : dependencies) {
				//b depends on d; draw edge d -> b
				c.get(d).add(b);
				indeg.put(b, indeg.get(b) + 1);
			}
		}

		//toposort
		ArrayList<LogicCircuitBlueprint> ans = new ArrayList<>();
		Queue<LogicCircuitBlueprint> q = new ArrayDeque<>();
		for (LogicCircuitBlueprint b : this.blueprints.values()) {
			if (indeg.get(b) == 0) {
				q.add(b);
			}
		}
		while (q.size() != 0) {
			LogicCircuitBlueprint cur = q.poll();
			ans.add(cur);
			for (LogicCircuitBlueprint next : c.get(cur)) {
				indeg.put(next, indeg.get(next) - 1);
				if (indeg.get(next) == 0) {
					q.add(next);
				}
			}
		}
		return ans;
	}

	public XMLNode toXML() {
		XMLNode root = new XMLNode("project");
		ArrayList<LogicCircuitBlueprint> top_ordering = this.getTopologicalOrdering();
		for (LogicCircuitBlueprint b : top_ordering) {
			root.addChild(b.toXML());
		}
		return root;
	}

	public void saveToFile(File f) throws IOException {
		XMLNode root = new XMLNode("root");
		root.addChild(this.toXML());
		root.saveToFile(f);
	}

}
