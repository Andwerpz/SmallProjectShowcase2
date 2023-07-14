package main;

import lwjglengine.state.State;
import lwjglengine.state.StateFactory;
import state.ProjectPickerState;

public class MyStateFactory extends StateFactory {

	@Override
	public State createState() {
		return new ProjectPickerState();
	}

}
