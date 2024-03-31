package logic_simulator.component.circuit;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Queue;
import java.util.Set;

import logic_simulator.component.LogicComponent;
import myutils.math.IVec2;

public class LogicCircuitManager {
	//static class for managing logic circuits since we ideally only want to save 1 copy of each blueprint. 

	//each logic circuit will have a user assigned name. 
	private static HashMap<String, LogicCircuitBlueprint> blueprints;

	//by default is empty, and cannot be a dependent of anyone. 
	private static LogicCircuitBlueprint main;
	static {
		blueprints = new HashMap<>();
		main = createNewBlueprint("main");
	}

	/**
	 * Returns the blueprint if successful, null otherwise
	 * @param name
	 * @return
	 */
	public static LogicCircuitBlueprint createNewBlueprint(String name) {
		if (blueprints.containsKey(name)) {
			return null;
		}
		blueprints.put(name, new LogicCircuitBlueprint(name));
		return blueprints.get(name);
	}

	public static Set<String> getBlueprintNames() {
		return blueprints.keySet();
	}

	public static LogicCircuitBlueprint getBlueprint(String name) {
		return blueprints.get(name);
	}

	public static LogicCircuit getCircuit(String name) {
		return new LogicCircuit(getBlueprint(name), new IVec2(0));
	}

	public static LogicCircuitBlueprint getMainBlueprint() {
		return main;
	}

	/**
	 * Returns the set of all blueprints that this one depends on
	 * The set includes itself. 
	 * @param blueprint
	 * @return
	 */
	public static HashSet<LogicCircuitBlueprint> getDependencies(LogicCircuitBlueprint blueprint) {
		HashSet<LogicCircuitBlueprint> ret = new HashSet<>();
		Queue<LogicCircuitBlueprint> q = new ArrayDeque<>();
		q.add(blueprint);
		ret.add(blueprint);
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

}
