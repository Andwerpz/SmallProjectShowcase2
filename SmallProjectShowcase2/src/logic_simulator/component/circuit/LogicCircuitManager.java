package logic_simulator.component.circuit;

import java.util.HashMap;

public class LogicCircuitManager {
	//static class for managing logic circuits since we ideally only want to save 1 copy of each blueprint. 

	//each logic circuit will have a user assigned name. 
	private static HashMap<String, LogicCircuitBlueprint> blueprints;

	/**
	 * Returns true if successful
	 * @param name
	 * @return
	 */
	public static boolean createNewBlueprint(String name) {
		if (blueprints.containsKey(name)) {
			return false;
		}
		blueprints.put(name, new LogicCircuitBlueprint());
		return true;
	}

	public static LogicCircuitBlueprint getBlueprint(String name) {
		return blueprints.get(name);
	}

	public static LogicCircuit getCircuit(String name) {
		return new LogicCircuit(getBlueprint(name));
	}

}
