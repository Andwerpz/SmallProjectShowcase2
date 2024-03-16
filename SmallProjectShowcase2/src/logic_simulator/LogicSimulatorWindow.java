package logic_simulator;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.TreeMap;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL13.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL21.*;
import static org.lwjgl.opengl.GL30.*;
import static org.lwjgl.opengl.GL31.*;
import static org.lwjgl.opengl.GL32.*;
import static org.lwjgl.opengl.GL33.*;
import static org.lwjgl.opengl.GL43.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;
import static org.lwjgl.opengl.GL31.*;
import static org.lwjgl.opengl.GL33.*;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL13.*;
import static org.lwjgl.opengl.GL12.*;
import static org.lwjgl.opengl.GL31.*;
import static org.lwjgl.opengl.GL32.*;
import static org.lwjgl.opengl.GL42.GL_SHADER_IMAGE_ACCESS_BARRIER_BIT;
import static org.lwjgl.opengl.GL42.glMemoryBarrier;
import static org.lwjgl.opengl.GL30.*;

import logic_simulator.component.LogicComponent;
import logic_simulator.component.Wire;
import logic_simulator.component.circuit.LogicCircuit;
import logic_simulator.component.circuit.LogicCircuitBlueprint;
import logic_simulator.component.circuit.LogicCircuitManager;
import logic_simulator.component.instance.LogicCircuitInstance;
import logic_simulator.component.instance.LogicComponentInstance;
import lwjglengine.graphics.Framebuffer;
import lwjglengine.graphics.Material;
import lwjglengine.input.Input;
import lwjglengine.input.MouseInput;
import lwjglengine.model.FilledRectangle;
import lwjglengine.model.Line;
import lwjglengine.model.ModelInstance;
import lwjglengine.scene.Scene;
import lwjglengine.screen.UIScreen;
import lwjglengine.ui.UIElement;
import lwjglengine.ui.UIFilledRectangle;
import lwjglengine.ui.UISection;
import lwjglengine.window.Window;
import myutils.math.IVec2;
import myutils.math.MathUtils;
import myutils.math.Vec2;
import myutils.math.Vec3;

public class LogicSimulatorWindow extends Window {
	//this should be fully capable of simulating and editing a single logic circuit blueprint

	//TODO
	// - wire drawing
	//   - if a wire ends in the middle of another wire, then the other wire should split
	//   - if 3 wires end at the same point, create a junction. 
	//   - also should be able to select to delete wires. 
	//   - if two wires end at the same point and go in the same direction with no other wires ending at that point, they should merge. 
	// - component drawing
	//   - just draw a rectangle box for now. 

	private final int GRIDLINE_SCENE = Scene.generateScene();
	private final int WIRE_SCENE = Scene.generateScene();
	private final int COMPONENT_SELECT_SCENE = Scene.generateScene();

	private UIScreen uiScreen;
	private UISection uiSection;

	private static float maxViewportScale = 100;
	private static float minViewportScale = 0.025f;
	private float viewportScale = 0.1f;
	private Vec2 viewportCenter = new Vec2(0);
	private boolean viewportGrabbed = false;
	private int gridlineScale = 0;
	private TreeMap<Integer, ModelInstance> verticalGridlines, horizontalGridlines;

	//the active blueprint that we are editing. 
	private LogicCircuitBlueprint blueprint;

	//we should run our own simulation so that we can add and remove logic components during the simulation. 
	private HashMap<LogicComponent, LogicComponentInstance> componentInstances;
	private HashMap<LogicComponent, ComponentDisplay> componentDisplays;

	//TODO
	private boolean isDraggingWire = false;
	private ArrayList<LogicComponent> dragWires;

	public LogicSimulatorWindow(int xOffset, int yOffset, int width, int height, Window parentWindow) {
		super(xOffset, yOffset, width, height, parentWindow);
		this.init();
	}

	private void init() {
		this.uiScreen = new UIScreen();

		this.uiSection = new UISection();
		UIFilledRectangle backgroundRect = this.uiSection.getBackgroundRect();
		backgroundRect.bind(this.rootUIElement);
		backgroundRect.setFrameAlignmentStyle(UIElement.FROM_LEFT, UIElement.FROM_TOP);
		backgroundRect.setContentAlignmentStyle(UIElement.ALIGN_LEFT, UIElement.ALIGN_TOP);
		backgroundRect.setMaterial(Material.transparent());
		backgroundRect.setFillWidth(true);
		backgroundRect.setFillHeight(true);

		this.verticalGridlines = new TreeMap<>();
		this.horizontalGridlines = new TreeMap<>();

		this.componentInstances = new HashMap<>();
		this.componentDisplays = new HashMap<>();

		this.setBlueprint(LogicCircuitManager.getMainBlueprint());

		this.addComponent(new Wire(new IVec2(10, 10), new IVec2(20, 10)));

		this._resize();
	}

	@Override
	protected void _kill() {
		this.uiScreen.kill();

		Scene.removeScene(GRIDLINE_SCENE);
		Scene.removeScene(WIRE_SCENE);
		Scene.removeScene(COMPONENT_SELECT_SCENE);
	}

	@Override
	protected void _resize() {
		this.uiScreen.setScreenDimensions(this.getWidth(), this.getHeight());
	}

	@Override
	public String getDefaultTitle() {
		return "Logic Simulator";
	}

	private ComponentDisplay createComponentDisplay(LogicComponent component) {
		if (component instanceof Wire) {
			return new WireDisplay(component);
		}
		assert false;
		return null;
	}

	private void addComponent(LogicComponent c) {
		if(this.)
	}

	private void removeComponent(LogicComponent c) {
		if (!this.components.containsKey(c)) {
			return;
		}
		this.components.get(c).kill();
		this.components.remove(c);
		this.blueprint.removeComponent(c);
	}

	//saves whatever circuit we have into the current active blueprint. 
	private void saveBlueprint() {

	}

	private void setBlueprint(LogicCircuitBlueprint blueprint) {
		for (LogicComponent c : this.componentInstances.keySet()) {
			this.componentDisplays.get(c).kill();
		}
		this.componentInstances.clear();
		this.componentDisplays.clear();

		this.blueprint = blueprint;
		for (LogicComponent c : this.blueprint.getComponents()) {

			this.components.put(c, this.createComponentDisplay(c));
		}
	}

	private void updateGridlines() {
		//compute gridline scale
		int majorGridlineScale, minorGridlineScale;
		{
			int scaleMult = 10;
			float gridlineScaleRef = this.viewportScale * 750f;
			majorGridlineScale = scaleMult;
			while (majorGridlineScale * scaleMult <= gridlineScaleRef) {
				majorGridlineScale *= scaleMult;
			}
			minorGridlineScale = majorGridlineScale / scaleMult;
		}

		//if gridline scale changed, then remove all old gridlines
		if (this.gridlineScale != majorGridlineScale) {
			this.gridlineScale = majorGridlineScale;
			Scene.clearScene(GRIDLINE_SCENE);
			this.horizontalGridlines.clear();
			this.verticalGridlines.clear();
		}

		float viewportLeft = this.viewportCenter.x - (this.getWidth() * this.viewportScale / 2.0f);
		float viewportRight = this.viewportCenter.x + (this.getWidth() * this.viewportScale / 2.0f);
		float viewportBottom = this.viewportCenter.y - (this.getHeight() * this.viewportScale / 2.0f);
		float viewportTop = this.viewportCenter.y + (this.getHeight() * this.viewportScale / 2.0f);

		int startX = Math.round(viewportLeft / minorGridlineScale) * minorGridlineScale;
		int startY = Math.round(viewportBottom / minorGridlineScale) * minorGridlineScale;
		int endX = Math.round(viewportRight / minorGridlineScale) * minorGridlineScale;
		int endY = Math.round(viewportTop / minorGridlineScale) * minorGridlineScale;

		//remove gridlines outside of viewport
		{
			while (this.verticalGridlines.size() != 0 && this.verticalGridlines.firstKey() < startX) {
				int x = this.verticalGridlines.firstKey();
				this.verticalGridlines.get(x).kill();
				this.verticalGridlines.remove(x);
			}
			while (this.verticalGridlines.size() != 0 && this.verticalGridlines.lastKey() > endX) {
				int x = this.verticalGridlines.lastKey();
				this.verticalGridlines.get(x).kill();
				this.verticalGridlines.remove(x);
			}
			while (this.horizontalGridlines.size() != 0 && this.horizontalGridlines.firstKey() < startY) {
				int y = this.horizontalGridlines.firstKey();
				this.horizontalGridlines.get(y).kill();
				this.horizontalGridlines.remove(y);
			}
			while (this.horizontalGridlines.size() != 0 && this.horizontalGridlines.lastKey() > endY) {
				int y = this.horizontalGridlines.lastKey();
				this.horizontalGridlines.get(y).kill();
				this.horizontalGridlines.remove(y);
			}
		}

		//put gridlines into scene
		{
			float axisColor = 0.8f;
			float majorColor = 0.5f;
			float minorColor = 0.3f;

			for (int x = startX; x < viewportRight; x += minorGridlineScale) {
				if (this.verticalGridlines.containsKey(x)) {
					continue;
				}
				float color, z;
				if (x == 0) {
					color = axisColor;
					z = 2;
				}
				else if (x % majorGridlineScale == 0) {
					color = majorColor;
					z = 1;
				}
				else {
					color = minorColor;
					z = 0;
				}
				ModelInstance line = Line.addDefaultLine(x, -1e9f, z, x, 1e9f, z, GRIDLINE_SCENE);
				line.setMaterial(new Material(new Vec3(color)));
				this.verticalGridlines.put(x, line);
			}
			for (int y = startY; y < viewportTop; y += minorGridlineScale) {
				if (this.horizontalGridlines.containsKey(y)) {
					continue;
				}
				float color, z;
				if (y == 0) {
					color = axisColor;
					z = 2;
				}
				else if (y % majorGridlineScale == 0) {
					color = majorColor;
					z = 1;
				}
				else {
					color = minorColor;
					z = 0;
				}
				ModelInstance line = Line.addDefaultLine(-1e9f, y, z, 1e9f, y, z, GRIDLINE_SCENE);
				line.setMaterial(new Material(new Vec3(color)));
				this.horizontalGridlines.put(y, line);
			}
		}
	}

	@Override
	protected void _update() {
		this.uiSection.update();

		if (this.viewportGrabbed) {
			this.viewportCenter.subi(MouseInput.mouseDiff.mul(this.viewportScale));
		}

		this.updateGridlines();
	}

	@Override
	protected void renderContent(Framebuffer outputBuffer) {
		float viewportWidth = this.getWidth() * this.viewportScale;
		float viewportHeight = this.getHeight() * this.viewportScale;
		Vec2 viewportOffset = this.viewportCenter.sub(new Vec2(viewportWidth / 2.0f, viewportHeight / 2.0f));
		this.uiScreen.setViewportDimensions(viewportWidth, viewportHeight);
		this.uiScreen.setViewportOffset(viewportOffset);

		this.uiScreen.setUIScene(GRIDLINE_SCENE);
		this.uiScreen.render(outputBuffer);

		this.uiScreen.setUIScene(WIRE_SCENE);
		this.uiScreen.render(outputBuffer);

		this.uiScreen.setUIScene(COMPONENT_SELECT_SCENE);
		this.uiScreen.render(outputBuffer);

		this.uiSection.render(outputBuffer, this.getWindowMousePos());
	}

	@Override
	protected void renderOverlay(Framebuffer outputBuffer) {
		// TODO Auto-generated method stub

	}

	@Override
	protected void selected() {
		// TODO Auto-generated method stub

	}

	@Override
	protected void deselected() {
		// TODO Auto-generated method stub

	}

	@Override
	protected void subtreeSelected() {
		// TODO Auto-generated method stub

	}

	@Override
	protected void subtreeDeselected() {
		// TODO Auto-generated method stub

	}

	@Override
	protected void _mousePressed(int button) {
		this.uiSection.mousePressed(button);
		this.viewportGrabbed = true;
	}

	@Override
	protected void _mouseReleased(int button) {
		this.uiSection.mouseReleased(button);
		this.viewportGrabbed = false;

		String which = Input.getClicked(this.uiSection.getSelectionScene());
		switch (which) {

		}
	}

	@Override
	protected void _mouseScrolled(float wheelOffset, float smoothOffset) {
		this.uiSection.mouseScrolled(wheelOffset, smoothOffset);
		if (smoothOffset < 0) {
			this.viewportScale *= 1.1f;
		}
		else {
			this.viewportScale /= 1.1f;
		}
		this.viewportScale = MathUtils.clamp(minViewportScale, maxViewportScale, this.viewportScale);
	}

	@Override
	protected void _keyPressed(int key) {
		this.uiSection.keyPressed(key);

		switch (key) {

		}
	}

	@Override
	protected void _keyReleased(int key) {
		this.uiSection.keyReleased(key);
	}

	class ComponentInstance {
		ComponentDisplay display;
	}

	private static final Material ERROR_MATERIAL = new Material(Color.RED);
	private static final Material TRUE_MATERIAL = new Material(Color.GREEN);
	private static final Material FALSE_MATERIAL = new Material(new Vec3(73, 149, 206).mul((float) (1.0 / 255.0)));

	abstract class ComponentDisplay {

		private LogicComponent component;
		protected boolean isVisible = false;

		public ComponentDisplay(LogicComponent component) {
			this.component = component;
		}

		public abstract void update();

		public abstract void kill();

		public abstract void setVisible(boolean b);
	}

	class WireDisplay extends ComponentDisplay {
		private static final float WIRE_THICKNESS = 0.2f;

		private Wire wire;

		private ModelInstance wireInstance;

		public WireDisplay(LogicComponent component) {
			super(component);
			this.wire = (Wire) component;
			this.setVisible(true);
		}

		@Override
		public void update() {
			this.setVisible(false);
			this.setVisible(true);

			if (this.isVisible) {
				//huh
			}
		}

		@Override
		public void kill() {
			this.setVisible(false);
		}

		@Override
		public void setVisible(boolean b) {
			if (this.isVisible && !b) {
				this.wireInstance.kill();
				this.wireInstance = null;
			}
			else if (!this.isVisible && b) {
				Vec2 e0 = new Vec2(this.wire.e0);
				Vec2 e1 = new Vec2(this.wire.e1);
				Vec2 bl = new Vec2(MathUtils.min(e0, e1));
				Vec2 tr = new Vec2(MathUtils.max(e0, e1));
				bl.subi(new Vec2(WIRE_THICKNESS / 2.0));
				tr.addi(new Vec2(WIRE_THICKNESS / 2.0));
				this.wireInstance = FilledRectangle.addDefaultRectangle(bl, tr, 0, WIRE_SCENE);
			}
			this.isVisible = b;
		}
	}

	class CircuitDisplay {

	}

	class GateDisplay {

	}

}
