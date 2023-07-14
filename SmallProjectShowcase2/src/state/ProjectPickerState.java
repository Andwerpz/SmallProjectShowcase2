package state;

import lwjglengine.graphics.Framebuffer;
import lwjglengine.state.State;
import lwjglengine.window.Window;
import window.BackgroundWindow;
import window.ProjectPickerWindow;

public class ProjectPickerState extends State {

	@Override
	public void load() {
		//new instance of background window
		Window backgroundWindow = new BackgroundWindow(0, 0, this.sm.getWidth(), this.sm.getHeight(), this.sm);
	}

	@Override
	public void buildBuffers() {
		// TODO Auto-generated method stub

	}

	@Override
	public void kill() {
		// TODO Auto-generated method stub

	}

	@Override
	public void update() {
		// TODO Auto-generated method stub

	}

	@Override
	public void render(Framebuffer outputBuffer) {
		// TODO Auto-generated method stub

	}

	@Override
	public void mousePressed(int button) {
		// TODO Auto-generated method stub

	}

	@Override
	public void mouseReleased(int button) {
		// TODO Auto-generated method stub

	}

	@Override
	public void mouseScrolled(float wheelOffset, float smoothOffset) {
		// TODO Auto-generated method stub

	}

	@Override
	public void keyPressed(int key) {
		// TODO Auto-generated method stub

	}

	@Override
	public void keyReleased(int key) {
		// TODO Auto-generated method stub

	}

}
