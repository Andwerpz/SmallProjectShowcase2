package logic_simulator.component.display;

import java.awt.Color;

import logic_simulator.CircuitSimulatorWindow;
import logic_simulator.component.instance.LogicCircuitInstance;
import lwjglengine.graphics.Material;
import lwjglengine.graphics.TextureMaterial;
import lwjglengine.model.ModelInstance;
import myutils.math.IVec2;
import myutils.math.Vec2;

public class CircuitDisplay extends ComponentDisplay {

	//TODO add text

	private LogicCircuitInstance circuit;
	private ModelInstance circuitRect = null;

	private TextureMaterial textTexture;
	private float textWidth, textHeight;
	private ModelInstance textRect;

	public CircuitDisplay(LogicCircuitInstance component, IVec2 offset, CircuitSimulatorWindow window) {
		super(component, offset, window);
		this.circuit = component;
		this.circuitRect = null;

		this.textRect = null;

		//generate text texture. 
		{
			//				String circuit_name = 
			//				BufferedImage text_img = GraphicsTools.generateTextImage(getDefaultTitle(), null, null, GLFW_ACCUM_ALPHA_BITS)
		}
	}

	@Override
	public void update() {
		if (this.isVisible) {
			//nothing
		}
	}

	@Override
	public void kill() {
		this.setVisible(false);
	}

	@Override
	public void setVisible(boolean b) {
		if (this.isVisible && !b) {
			this.textRect.kill();
			this.textTexture.kill();

			this.circuitRect.kill();
		}
		else if (!this.isVisible && b) {
			//circuit rect
			Vec2 offset = new Vec2(this.offset);
			float width = this.circuit.getWidth();
			float height = this.circuit.getHeight();
			Vec2 bl = new Vec2(offset);
			Vec2 tr = new Vec2(bl.x + width, bl.y + height);

			float extra_height = 0.3f;
			bl.addi(0, -extra_height);
			tr.addi(0, extra_height);
			this.circuitRect.setMaterial(new Material(Color.WHITE));

			//text rect

		}
		this.isVisible = b;
	}

}
