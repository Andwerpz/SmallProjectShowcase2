package logic_simulator;

import java.awt.Color;
import java.io.File;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Queue;
import java.util.TreeMap;

import static logic_simulator.component.TruthValue.ERROR;
import static logic_simulator.component.TruthValue.FALSE;
import static logic_simulator.component.TruthValue.TRUE;
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

import org.lwjgl.glfw.GLFW;
import static org.lwjgl.glfw.GLFW.*;

import logic_simulator.component.InputPin;
import logic_simulator.component.LogicComponent;
import logic_simulator.component.OutputPin;
import logic_simulator.component.Project;
import logic_simulator.component.TruthValue;
import logic_simulator.component.Wire;
import logic_simulator.component.circuit.LogicCircuit;
import logic_simulator.component.circuit.LogicCircuitBlueprint;
import logic_simulator.component.display.ComponentDisplay;
import logic_simulator.component.gate.ANDGate;
import logic_simulator.component.gate.GateType;
import logic_simulator.component.gate.Inverter;
import logic_simulator.component.gate.LogicGate;
import logic_simulator.component.gate.NANDGate;
import logic_simulator.component.gate.NORGate;
import logic_simulator.component.gate.ORGate;
import logic_simulator.component.gate.XNORGate;
import logic_simulator.component.gate.XORGate;
import logic_simulator.component.instance.InputPinInstance;
import logic_simulator.component.instance.LogicCircuitInstance;
import logic_simulator.component.instance.LogicComponentInstance;
import logic_simulator.component.instance.LogicGateInstance;
import logic_simulator.component.instance.OutputPinInstance;
import logic_simulator.component.instance.WireInstance;
import lwjglengine.graphics.Framebuffer;
import lwjglengine.graphics.Material;
import lwjglengine.graphics.Texture;
import lwjglengine.graphics.TextureMaterial;
import lwjglengine.input.Input;
import lwjglengine.input.KeyboardInput;
import lwjglengine.input.MouseInput;
import lwjglengine.model.FilledRectangle;
import lwjglengine.model.Line;
import lwjglengine.model.ModelInstance;
import lwjglengine.model.ModelTransform;
import lwjglengine.scene.Scene;
import lwjglengine.screen.UIScreen;
import lwjglengine.ui.Text;
import lwjglengine.ui.UIElement;
import lwjglengine.ui.UIFilledRectangle;
import lwjglengine.ui.UISection;
import lwjglengine.window.AdjustableWindow;
import lwjglengine.window.NestedListViewerWindow;
import lwjglengine.window.Window;
import myutils.file.FileUtils;
import myutils.file.SystemUtils;
import myutils.file.xml.XMLNode;
import myutils.file.xml.XMLReader;
import myutils.graphics.GraphicsTools;
import myutils.math.IVec2;
import myutils.math.Mat4;
import myutils.math.MathUtils;
import myutils.math.Vec2;
import myutils.math.Vec3;
import myutils.misc.Pair;

public class CircuitSimulatorWindow extends Window {
	//this should just be responsible for simulating a circuit, with capability to add and remove 
	//logic components on the fly. 

	//TODO
	//FEATURES
	// - add logic circuit display
	// - allow user to add logic circuit instances. 
	// - for now, just notify the user which mode they switched to through some text
	// - when edit mode, if eligible to drag wires, have a little thing that pops up on the wire that is to be dragged. 

	//BUGFIXES
	// - if we have wires that form a loop, they will maintain a TRUE signal without outside input. 
	//   - for now, just don't build wire loops

	//perhaps move all the helper classes out of this file... 

	private enum InteractionMode {
		INTERACT_MODE, //should allow for toggling on and off input pins
		EDIT_MODE, //moving around logic components by dragging. Also should be able to select anything and delete it. 
	}

	private InteractionMode interactionMode = InteractionMode.INTERACT_MODE;

	private boolean mousePressed = false;

	//edit mode
	// - if you press the mouse, and it happens to be above a component, do single component selection logic. 
	// - otherwise, you'll start to do a rectangle selection, and you'll select the components once you release the mouse. 
	private HashSet<LogicComponent> selectedComponents;

	private boolean isRectangleSelecting = false;
	private Vec2 rectangleSelectAnchor;
	private ModelInstance[] rectangleSelectDisplay; //0-3 are lines, 4 is internal rectangle

	private boolean isDraggingSelection = false;
	private IVec2 selectionDragAnchor, selectionDragOffset;

	private boolean isCopying = false;
	private LogicComponent[] copiedComponents;
	private IVec2 copyRectangleDimensions;
	private ModelInstance[] copyRectangleDisplay; //tells you where the copied selection will go to when you press ctrl+v

	private boolean isPlacingComponent = false;
	private LogicComponent placeComponent;
	private ComponentDisplay placeComponentGhost;

	private boolean isDraggingWires = false;
	private boolean wireDragHorizontalFirst = true;
	private IVec2 wireDragAnchor; //where did the mouse get pressed?
	private ModelInstance[] wireDragDisplay;

	private final int GRIDLINE_SCENE = Scene.generateScene();
	private final int LOGIC_INPUT_SCENE = Scene.generateScene();
	private final int WIRE_SCENE = Scene.generateScene();
	private final int COMPONENT_SCENE = Scene.generateScene();
	private final int COMPONENT_TEXT_SCENE = Scene.generateScene();
	private final int COMPONENT_SELECT_SCENE = Scene.generateScene();

	public static final Material ERROR_MATERIAL = new Material(Color.RED);
	public static final Material TRUE_MATERIAL = new Material(Color.GREEN);
	public static final Material FALSE_MATERIAL = new Material(new Vec3(0, 100, 0).mul((float) (1.0 / 255.0)));

	public static final Material SELECT_MATERIAL = new Material(new Vec3(128, 128, 255).mul((float) (1.0 / 255.0)));
	private static final Material COPY_MATERIAL = new Material(Color.WHITE);

	public static final float WIRE_WIDTH = 0.15f;

	private UIScreen uiScreen;
	private UISection uiSection;

	private static float maxViewportScale = 0.5f;
	private static float minViewportScale = 0.01f;
	private float viewportScale = 0.1f;
	private Vec2 viewportCenter = new Vec2(0);
	private boolean viewportGrabbed = false;
	private int gridlineScale = 0;
	private TreeMap<Integer, ModelInstance> verticalGridlines, horizontalGridlines;

	//the active blueprint that we are editing. 
	private LogicCircuitBlueprint blueprint;

	//we should run our own simulation so that we can add and remove logic components during the simulation. 
	private HashMap<LogicComponent, ComponentInstance> componentInstances;
	private HashMap<IVec2, ArrayList<LogicInput>> inputMap;
	private HashMap<IVec2, ArrayList<LogicOutput>> outputMap;

	private HashMap<LogicComponent, Integer> updateQueueCnt;
	private Queue<LogicComponent> updateQueue;

	private HashMap<GateType, FilledRectangle> gateRects;

	private boolean shouldPruneWires = false;

	public CircuitSimulatorWindow(int xOffset, int yOffset, int width, int height, Window parentWindow) {
		super(xOffset, yOffset, width, height, parentWindow);
		this.init();
	}

	private void init() {
		this.setAllowInputWhenParentSubtreeSelected(true);

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
		this.inputMap = new HashMap<>();
		this.outputMap = new HashMap<>();

		this.updateQueue = new ArrayDeque<>();
		this.updateQueueCnt = new HashMap<>();

		//initialize gate textures
		this.gateRects = new HashMap<>();
		for (GateType t : GateType.values()) {
			FilledRectangle rect = new FilledRectangle();
			Texture tex = new Texture(FileUtils.loadImageRelative("/res/logic_simulator/gates/" + t.toString() + ".png"), 0, GL_NEAREST, GL_LINEAR, 5);
			rect.setTextureMaterial(new TextureMaterial(tex));
			this.gateRects.put(t, rect);
		}

		this.wireDragDisplay = new ModelInstance[2];

		this.selectedComponents = new HashSet<>();

		this._resize();
	}

	@Override
	protected void _kill() {
		this.uiScreen.kill();

		Scene.removeScene(GRIDLINE_SCENE);
		Scene.removeScene(LOGIC_INPUT_SCENE);
		Scene.removeScene(WIRE_SCENE);
		Scene.removeScene(COMPONENT_SCENE);
		Scene.removeScene(COMPONENT_TEXT_SCENE);
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

	public void setPlaceComponent(LogicComponent c) {
		if (this.interactionMode != InteractionMode.EDIT_MODE) {
			return;
		}

		if (c == null) {
			if (this.isPlacingComponent) {
				this.placeComponent = null;
				this.placeComponentGhost.kill();
				this.placeComponentGhost = null;
			}
			return;
		}
		this.isPlacingComponent = true;
		this.placeComponent = c;
		this.placeComponentGhost = ComponentDisplay.createComponentDisplay(c, this);
		this.placeComponentGhost.setVisible(true);
	}

	/**
	 * for now, also saves the blueprint. 
	 * @param blueprint
	 */
	public void setBlueprint(LogicCircuitBlueprint blueprint) {
		if (this.blueprint != null) {
			this.saveBlueprint();
		}

		//reset all active operations and stuff
		this.stopCopying();
		this.stopDraggingWires();
		this.stopDraggingSelection();
		this.stopRectangleSelecting();
		this.stopPlacingComponent();
		this.deselectAllComponents();

		for (LogicComponent c : this.componentInstances.keySet()) {
			this.componentInstances.get(c).kill();
		}
		this.componentInstances.clear();

		this.blueprint = null;
		for (LogicComponent c : blueprint.getComponents()) {
			this.addComponent(c);
		}
		this.blueprint = blueprint;
	}

	//saves whatever circuit we have into the current active blueprint. 
	public void saveBlueprint() {
		this.blueprint.removeAllComponents();
		for (LogicComponent c : this.componentInstances.keySet()) {
			this.blueprint.addComponent(c);
		}
	}

	private void _addComponent(LogicComponent c) {
		if (this.componentInstances.containsKey(c)) {
			return;
		}

		ComponentInstance inst = new ComponentInstance(c);
		this.componentInstances.put(c, inst);

		assert false;
		if (inst.component instanceof InputPin) {
			((InputPinInstance) inst.instance).setData(FALSE);
		}
		this.addToUpdateQueue(c);
	}

	private void addComponent(LogicComponent c) {
		this._addComponent(c);
		this.triggerPruneWires();
	}

	private void _removeComponent(LogicComponent c) {
		if (!this.componentInstances.containsKey(c)) {
			return;
		}

		if (this.componentInstances.get(c).isSelected) {
			this.deselectComponent(c);
		}
		this.componentInstances.get(c).kill();
		this.componentInstances.remove(c);
	}

	private void removeComponent(LogicComponent c) {
		this._removeComponent(c);
		this.triggerPruneWires();
	}

	private void triggerPruneWires() {
		this.shouldPruneWires = true;
	}

	//cut and merge wires
	// - if a wire has an input or output on its body, split it. 
	// - any two wires that are joined, parallel, and no other input/output at the join, should be merged into one
	// - redundant wires should be removed
	private void pruneWires() {
		//split wires
		{
			ArrayList<Wire> to_remove = new ArrayList<>();
			ArrayList<Wire> to_add = new ArrayList<>();
			for (LogicComponent c : this.componentInstances.keySet()) {
				if (!(c instanceof Wire)) {
					continue;
				}
				Wire w = (Wire) c;
				IVec2 e0 = w.getOffset().add(w.getInputOffsets()[0]);
				IVec2 e1 = w.getOffset().add(w.getInputOffsets()[1]);
				IVec2 b_min = MathUtils.min(e0, e1);
				IVec2 b_max = MathUtils.max(e0, e1);
				ArrayList<IVec2> pts = new ArrayList<>();
				pts.add(b_min);
				for (int x = b_min.x; x <= b_max.x; x++) {
					for (int y = b_min.y; y <= b_max.y; y++) {
						if (e0.equals(new IVec2(x, y)) || e1.equals(new IVec2(x, y))) {
							continue;
						}
						ArrayList<LogicInput> l_inputs = this.inputMap.get(new IVec2(x, y));
						ArrayList<LogicOutput> l_outputs = this.outputMap.get(new IVec2(x, y));
						if (l_inputs == null && l_outputs == null) {
							continue;
						}
						pts.add(new IVec2(x, y));
					}
				}
				pts.add(b_max);

				if (pts.size() == 2) {
					//no other inputs on body, do nothing. 
					continue;
				}
				//split this wire
				to_remove.add(w);
				for (int i = 0; i < pts.size() - 1; i++) {
					to_add.add(new Wire(pts.get(i), pts.get(i + 1)));
				}
			}

			for (Wire w : to_remove) {
				this._removeComponent(w);
			}
			for (Wire w : to_add) {
				this._addComponent(w);
			}
		}

		//get rid of redundant wires
		{
			//if there are duplicate wires, we want to avoid removing all instances of them, 
			//so we're going to do this in a really dumb way
			//loop through all the components, and as soon as we find a wire to remove, remove it, and start again. 
			while (true) {
				Wire to_remove = null;
				outer:
				for (LogicComponent c : this.componentInstances.keySet()) {
					if (!(c instanceof Wire)) {
						continue;
					}
					Wire w = (Wire) c;
					IVec2 e0 = w.getOffset().add(w.getInputOffsets()[0]);
					IVec2 e1 = w.getOffset().add(w.getInputOffsets()[1]);
					IVec2 b_min = MathUtils.min(e0, e1);
					IVec2 b_max = MathUtils.max(e0, e1);
					if (b_min.equals(b_max)) {
						//size 0 wire
						to_remove = w;
						break outer;
					}
					for (int x = b_min.x; x <= b_max.x; x++) {
						for (int y = b_min.y; y <= b_max.y; y++) {
							ArrayList<LogicInput> l_inputs = this.inputMap.get(new IVec2(x, y));
							if (l_inputs == null) {
								continue;
							}
							for (LogicInput l_input : l_inputs) {
								if (!(l_input.component instanceof Wire)) {
									continue;
								}
								Wire w_other = (Wire) l_input.component;
								if (w_other == w) {
									continue;
								}
								IVec2 e0_other = w_other.getOffset().add(w_other.getInputOffsets()[0]);
								IVec2 e1_other = w_other.getOffset().add(w_other.getInputOffsets()[1]);
								IVec2 b_min_other = MathUtils.min(e0_other, e1_other);
								IVec2 b_max_other = MathUtils.max(e0_other, e1_other);
								if (b_min.x <= b_min_other.x && b_min.y <= b_min_other.y && b_max.x >= b_max_other.x && b_max.y >= b_max_other.y) {
									to_remove = w_other;
									break outer;
								}
							}
						}
					}
				}
				if (to_remove == null) {
					break;
				}
				this._removeComponent(to_remove);
			}
		}

		//merge wires
		{
			//just check both endpoints, and if there is another wire that is parallel to me, 
			//and that's the only other input there, merge with it. 
			//since merges add and remove wires, we'll have to do it the dumb way. 
			while (true) {
				Wire[] to_remove = null;
				Wire to_add = null;
				outer:
				for (LogicComponent c : this.componentInstances.keySet()) {
					if (!(c instanceof Wire)) {
						continue;
					}
					Wire w = (Wire) c;
					IVec2 e0 = w.getOffset().add(w.getInputOffsets()[0]);
					IVec2 e1 = w.getOffset().add(w.getInputOffsets()[1]);
					IVec2 b_min = MathUtils.min(e0, e1);
					IVec2 b_max = MathUtils.max(e0, e1);
					IVec2[] bounds = new IVec2[] { b_min, b_max };
					for (int i = 0; i < 2; i++) {
						ArrayList<LogicInput> l_inputs = this.inputMap.get(bounds[i]);
						ArrayList<LogicOutput> l_outputs = this.outputMap.get(bounds[i]);
						if (l_inputs == null || l_inputs.size() != 2 || l_outputs == null || l_outputs.size() != 2) {
							continue;
						}
						LogicComponent other = null;
						for (LogicInput l_input : l_inputs) {
							if (l_input.component != w) {
								other = l_input.component;
							}
						}
						assert other != null;
						if (!(other instanceof Wire)) {
							continue;
						}
						Wire w_other = (Wire) other;
						IVec2 ne0 = w_other.getOffset().add(w_other.getInputOffsets()[0]);
						IVec2 ne1 = w_other.getOffset().add(w_other.getInputOffsets()[1]);
						IVec2 nb_min = MathUtils.min(new IVec2[] { b_min, ne0, ne1 });
						IVec2 nb_max = MathUtils.max(new IVec2[] { b_max, ne0, ne1 });
						if (nb_min.x != nb_max.x && nb_min.y != nb_max.y) {
							//other is not parallel with w. 
							continue;
						}
						to_add = new Wire(nb_min, nb_max);
						to_remove = new Wire[] { w, w_other };
						break outer;
					}
				}
				if (to_remove == null) {
					break;
				}
				this._removeComponent(to_remove[0]);
				this._removeComponent(to_remove[1]);
				this.addComponent(to_add);
			}
		}

		//update wire displays
		for (LogicComponent c : this.componentInstances.keySet()) {
			if (!(c instanceof Wire)) {
				continue;
			}
			this.componentInstances.get(c).display.refresh();
		}
	}

	private void selectComponent(LogicComponent c) {
		assert this.componentInstances.containsKey(c);
		if (this.selectedComponents.contains(c)) {
			return;
		}
		this.selectedComponents.add(c);
		this.componentInstances.get(c).setSelected(true);
	}

	private void deselectComponent(LogicComponent c) {
		assert this.componentInstances.containsKey(c);
		if (!this.selectedComponents.contains(c)) {
			return;
		}
		this.selectedComponents.remove(c);
		this.componentInstances.get(c).setSelected(false);
	}

	private void deselectAllComponents() {
		ArrayList<LogicComponent> to_remove = new ArrayList<>();
		to_remove.addAll(this.selectedComponents);
		for (LogicComponent c : to_remove) {
			this.deselectComponent(c);
		}
	}

	private void addToUpdateQueue(LogicComponent c) {
		this.updateQueue.add(c);
		this.updateQueueCnt.put(c, this.updateQueueCnt.getOrDefault(c, 0) + 1);
	}

	private void updateGridlines() {
		//compute gridline scale
		int majorGridlineScale, minorGridlineScale;
		{
			int scaleMult = 8;
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
			float axisColor = 0.5f;
			float majorColor = 0.4f;
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

		if (this.shouldPruneWires) {
			this.shouldPruneWires = false;
			this.pruneWires();
		}

		//go through update queue
		while (this.updateQueue.size() != 0) {
			LogicComponent component = this.updateQueue.poll();
			this.updateQueueCnt.put(component, this.updateQueueCnt.get(component) - 1);
			if (this.updateQueueCnt.get(component) != 0) {
				continue;
			}

			//we've removed this component before we're able to update it
			if (!this.componentInstances.containsKey(component)) {
				continue;
			}

			ComponentInstance inst = this.componentInstances.get(component);
			inst.update();
		}

		if (this.isDraggingWires) {
			IVec2 start = new IVec2(this.wireDragAnchor);
			IVec2 end = this.getMouseGridSnapPos();

			int dx = end.x - start.x;
			int dy = end.y - start.y;

			IVec2 e0 = start;
			IVec2 e1 = start.add(dx, 0);
			IVec2 e2 = start.add(dx, dy);
			if (!this.wireDragHorizontalFirst) {
				e1 = start.add(0, dy);
				e2 = start.add(dx, dy);
			}

			if (this.wireDragDisplay[0] != null) {
				this.wireDragDisplay[0].kill();
				this.wireDragDisplay[0] = null;
			}
			if (this.wireDragDisplay[1] != null) {
				this.wireDragDisplay[1].kill();
				this.wireDragDisplay[1] = null;
			}

			this.wireDragDisplay[0] = Line.addDefaultLine(e0, e1, WIRE_SCENE);
			this.wireDragDisplay[1] = Line.addDefaultLine(e1, e2, WIRE_SCENE);

			this.wireDragDisplay[0].setMaterial(new Material(Color.RED));
			this.wireDragDisplay[1].setMaterial(new Material(Color.RED));
		}

		if (this.isDraggingSelection) {
			IVec2 next_drag_offset = this.getMouseGridSnapPos().sub(this.selectionDragAnchor);
			if (!this.selectionDragOffset.equals(next_drag_offset)) {
				this.selectionDragOffset.set(next_drag_offset);
				for (LogicComponent c : this.selectedComponents) {
					ComponentInstance inst = this.componentInstances.get(c);
					inst.display.setOffset(inst.getOffset().add(this.selectionDragOffset));
				}
			}
		}

		if (this.isRectangleSelecting) {
			Vec2 bl = MathUtils.min(this.rectangleSelectAnchor, this.getMouseGridPos());
			Vec2 tr = MathUtils.max(this.rectangleSelectAnchor, this.getMouseGridPos());

			Vec2[] corners = { new Vec2(bl.x, bl.y), new Vec2(tr.x, bl.y), new Vec2(tr.x, tr.y), new Vec2(bl.x, tr.y) };
			for (int i = 0; i < 4; i++) {
				this.rectangleSelectDisplay[i].setModelTransform(Line.generateLineModelTransform(corners[i], corners[(i + 1) % 4], 1));
			}
			this.rectangleSelectDisplay[4].setModelTransform(FilledRectangle.generateRectangleModelTransform(bl, tr, 0.5f));
		}

		if (this.isCopying) {
			Vec2 bl = new Vec2(this.getMouseGridSnapPos());
			Vec2 tr = bl.add(new Vec2(this.copyRectangleDimensions));
			Vec2[] corners = { new Vec2(bl.x, bl.y), new Vec2(tr.x, bl.y), new Vec2(tr.x, tr.y), new Vec2(bl.x, tr.y) };
			for (int i = 0; i < 4; i++) {
				this.copyRectangleDisplay[i].setModelTransform(Line.generateLineModelTransform(corners[i], corners[(i + 1) % 4], 2));
			}
			this.copyRectangleDisplay[4].setModelTransform(FilledRectangle.generateRectangleModelTransform(bl, tr, 1.5f));
		}

		if (this.isPlacingComponent) {
			IVec2 mouse_loc = this.getMouseGridSnapPos();
			this.placeComponentGhost.setOffset(mouse_loc);
			this.placeComponentGhost.refresh();
		}
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

		this.uiScreen.setUIScene(LOGIC_INPUT_SCENE);
		this.uiScreen.render(outputBuffer);

		this.uiScreen.setUIScene(WIRE_SCENE);
		this.uiScreen.render(outputBuffer);

		this.uiScreen.setUIScene(COMPONENT_SCENE);
		this.uiScreen.render(outputBuffer);

		this.uiScreen.setUIScene(COMPONENT_TEXT_SCENE);
		this.uiScreen.render(outputBuffer);

		this.uiScreen.setUIScene(COMPONENT_SELECT_SCENE);
		this.uiScreen.render(outputBuffer);

		this.uiSection.render(outputBuffer, this.getWindowMousePos());
	}

	@Override
	protected void renderOverlay(Framebuffer outputBuffer) {

	}

	@Override
	protected void selected() {

	}

	@Override
	protected void deselected() {

	}

	@Override
	protected void subtreeSelected() {

	}

	@Override
	protected void subtreeDeselected() {

	}

	//just returns the first one it finds that overlaps pos. 
	private ComponentInstance getComponentInstanceAtPos(Vec2 pos) {
		float epsilon = 0.0001f;
		for (ComponentInstance c : this.componentInstances.values()) {
			IVec2 offset = c.getOffset();
			float dx = pos.x - offset.x;
			float dy = pos.y - offset.y;
			if (dx > -epsilon && dy > -epsilon && dx < c.getWidth() + epsilon && dy < c.getHeight() + epsilon) {
				return c;
			}
		}
		return null;
	}

	private ComponentInstance getComponentInstanceAtPos(IVec2 pos) {
		return this.getComponentInstanceAtPos(new Vec2(pos));
	}

	//snaps the mouse pos to the nearest integer
	private IVec2 getMouseGridSnapPos() {
		Vec2 grid_pos = this.getMouseGridPos();
		return new IVec2(Math.round(grid_pos.x), Math.round(grid_pos.y));
	}

	private Vec2 getMouseGridPos() {
		Vec2 window_mouse_pos = this.getWindowMousePos(); //relative to bottom left
		window_mouse_pos.subi(this.getWidth() / 2, this.getHeight() / 2); //now relative to center

		float viewport_width = this.getWidth() * this.viewportScale;
		float viewport_height = this.getHeight() * this.viewportScale;

		Vec2 grid_mouse_pos = this.viewportCenter.add(window_mouse_pos.mul(this.viewportScale));
		return grid_mouse_pos;
	}

	private void startCopying() {
		if (this.isCopying) {
			return;
		}

		this.isCopying = true;
		this.copyRectangleDisplay = new ModelInstance[5];
		this.copyRectangleDisplay[0] = Line.addDefaultLine(COMPONENT_SELECT_SCENE);
		this.copyRectangleDisplay[1] = Line.addDefaultLine(COMPONENT_SELECT_SCENE);
		this.copyRectangleDisplay[2] = Line.addDefaultLine(COMPONENT_SELECT_SCENE);
		this.copyRectangleDisplay[3] = Line.addDefaultLine(COMPONENT_SELECT_SCENE);
		this.copyRectangleDisplay[4] = FilledRectangle.addDefaultRectangle(COMPONENT_SELECT_SCENE);

		Material inner_material = new Material(COPY_MATERIAL);
		inner_material.setAlpha(0.5f);
		this.copyRectangleDisplay[0].setMaterial(COPY_MATERIAL);
		this.copyRectangleDisplay[1].setMaterial(COPY_MATERIAL);
		this.copyRectangleDisplay[2].setMaterial(COPY_MATERIAL);
		this.copyRectangleDisplay[3].setMaterial(COPY_MATERIAL);
		this.copyRectangleDisplay[4].setMaterial(inner_material);

		//copy all selected components, and figure out bounding box width and height
		this.copiedComponents = new LogicComponent[this.selectedComponents.size()];
		IVec2 bl = null, tr = null;
		{
			int ptr = 0;
			for (LogicComponent c : this.selectedComponents) {
				IVec2 cbl = new IVec2(c.getOffset());
				IVec2 ctr = cbl.add(c.getWidth(), c.getHeight());
				if (ptr == 0) {
					bl = new IVec2(cbl);
					tr = new IVec2(ctr);
				}
				bl = MathUtils.min(bl, cbl);
				tr = MathUtils.max(tr, ctr);
				this.copiedComponents[ptr] = LogicComponent.copyComponent(c);
				ptr++;
			}
		}

		//make offset of all copied components relative to the bl of bounding box
		for (LogicComponent c : this.copiedComponents) {
			c.setOffset(c.getOffset().sub(bl));
		}

		this.copyRectangleDimensions = tr.sub(bl);
	}

	private void stopCopying() {
		if (!this.isCopying) {
			return;
		}

		this.isCopying = false;
		for (int i = 0; i < 5; i++) {
			this.copyRectangleDisplay[i].kill();
		}
		this.copyRectangleDisplay = null;
		this.copiedComponents = null;
		this.copyRectangleDimensions = null;
	}

	//if we press the mouse over a wire, a logic input, or a logic output, we should start dragging wires. 
	private boolean shouldStartDraggingWires() {
		IVec2 mouse_pos = this.getMouseGridSnapPos();
		if (this.inputMap.containsKey(mouse_pos) || this.outputMap.containsKey(mouse_pos)) {
			return true;
		}
		for (LogicComponent c : this.componentInstances.keySet()) {
			if (!(c instanceof Wire)) {
				continue;
			}
			IVec2 bl = c.getOffset();
			IVec2 tr = c.getOffset().add(c.getWidth(), c.getHeight());
			if (bl.x <= mouse_pos.x && bl.y <= mouse_pos.y && tr.x >= mouse_pos.x && tr.y >= mouse_pos.y) {
				return true;
			}
		}
		return false;
	}

	private void startDraggingWires() {
		this.isDraggingWires = true;
		this.wireDragAnchor = this.getMouseGridSnapPos();
	}

	private void stopDraggingWires() {
		if (!this.isDraggingWires) {
			return;
		}
		this.isDraggingWires = false;

		this.wireDragDisplay[0].kill();
		this.wireDragDisplay[1].kill();
		this.wireDragDisplay[0] = null;
		this.wireDragDisplay[1] = null;
	}

	private void stopPlacingComponent() {
		if (!this.isPlacingComponent) {
			return;
		}
		this.isPlacingComponent = false;
		this.setPlaceComponent(null);
	}

	private void startRectangleSelecting() {
		this.isRectangleSelecting = true;
		this.rectangleSelectAnchor = this.getMouseGridPos();
		this.rectangleSelectDisplay = new ModelInstance[5];
		this.rectangleSelectDisplay[0] = Line.addDefaultLine(COMPONENT_SELECT_SCENE);
		this.rectangleSelectDisplay[1] = Line.addDefaultLine(COMPONENT_SELECT_SCENE);
		this.rectangleSelectDisplay[2] = Line.addDefaultLine(COMPONENT_SELECT_SCENE);
		this.rectangleSelectDisplay[3] = Line.addDefaultLine(COMPONENT_SELECT_SCENE);
		this.rectangleSelectDisplay[4] = FilledRectangle.addDefaultRectangle(COMPONENT_SELECT_SCENE);

		Material inner_material = new Material(SELECT_MATERIAL);
		inner_material.setAlpha(0.5f);
		this.rectangleSelectDisplay[0].setMaterial(SELECT_MATERIAL);
		this.rectangleSelectDisplay[1].setMaterial(SELECT_MATERIAL);
		this.rectangleSelectDisplay[2].setMaterial(SELECT_MATERIAL);
		this.rectangleSelectDisplay[3].setMaterial(SELECT_MATERIAL);
		this.rectangleSelectDisplay[4].setMaterial(inner_material);
	}

	private void stopRectangleSelecting() {
		if (!this.isRectangleSelecting) {
			return;
		}
		this.isRectangleSelecting = false;
		for (ModelInstance m : this.rectangleSelectDisplay) {
			m.kill();
		}
		this.rectangleSelectDisplay = null;
	}

	private void startDraggingSelection() {
		this.isDraggingSelection = true;
		this.selectionDragAnchor = this.getMouseGridSnapPos();
		this.selectionDragOffset = new IVec2(0);
	}

	private void stopDraggingSelection() {
		if (!this.isDraggingSelection) {
			return;
		}
		this.isDraggingSelection = false;
	}

	@Override
	protected void _mousePressed(int button) {
		this.uiSection.mousePressed(button);
		if (button == GLFW.GLFW_MOUSE_BUTTON_2) {
			this.viewportGrabbed = true;
		}
		else if (button == GLFW.GLFW_MOUSE_BUTTON_1) {
			switch (this.interactionMode) {
			case EDIT_MODE: {

				if (this.isPlacingComponent) {
					this.placeComponent.setOffset(this.getMouseGridSnapPos());
					this.addComponent(this.placeComponent);
					this.stopPlacingComponent();
					break;
				}

				if (this.shouldStartDraggingWires()) {
					this.startDraggingWires();
					break;
				}

				ComponentInstance clicked_component = this.getComponentInstanceAtPos(this.getMouseGridSnapPos());
				if (clicked_component == null) {
					this.deselectAllComponents();
					this.startRectangleSelecting();
					break;
				}

				if (!clicked_component.isSelected) {
					this.deselectAllComponents();
				}
				this.selectComponent(clicked_component.component);
				//start dragging selected components
				this.startDraggingSelection();
				break;
			}
			case INTERACT_MODE: {
				ComponentInstance inst = this.getComponentInstanceAtPos(this.getMouseGridPos());
				if (inst != null && inst.component instanceof InputPin) {
					InputPinInstance i_pin = (InputPinInstance) inst.instance;
					TruthValue n_data = i_pin.getData() == ERROR ? FALSE : (i_pin.getData() == TRUE ? FALSE : TRUE);
					((InputPinInstance) inst.instance).setData(n_data);
					this.addToUpdateQueue(inst.component);
				}
				break;
			}
			}
		}
	}

	@Override
	protected void _mouseReleased(int button) {
		this.uiSection.mouseReleased(button);
		if (button == GLFW.GLFW_MOUSE_BUTTON_2) {
			this.viewportGrabbed = false;
		}
		else if (button == GLFW.GLFW_MOUSE_BUTTON_1) {
			switch (this.interactionMode) {
			case EDIT_MODE: {
				if (this.isDraggingWires) {
					IVec2 start = new IVec2(this.wireDragAnchor);
					IVec2 end = this.getMouseGridSnapPos();

					int dx = end.x - start.x;
					int dy = end.y - start.y;

					IVec2 e0 = start;
					IVec2 e1 = start.add(dx, 0);
					IVec2 e2 = start.add(dx, dy);
					if (!this.wireDragHorizontalFirst) {
						e1 = start.add(0, dy);
						e2 = start.add(dx, dy);
					}

					if (!e0.equals(e1)) {
						this.addComponent(new Wire(e0, e1));
					}
					if (!e1.equals(e2)) {
						this.addComponent(new Wire(e1, e2));
					}

					this.stopDraggingWires();
				}
				if (this.isDraggingSelection) {
					this.stopDraggingSelection();
					for (LogicComponent c : this.selectedComponents) {
						ComponentInstance inst = this.componentInstances.get(c);
						inst.setOffset(inst.getOffset().add(this.selectionDragOffset));
					}
					this.triggerPruneWires();
				}
				if (this.isRectangleSelecting) {
					this.stopRectangleSelecting();

					Vec2 sel_bl = MathUtils.min(this.rectangleSelectAnchor, this.getMouseGridPos());
					Vec2 sel_tr = MathUtils.max(this.rectangleSelectAnchor, this.getMouseGridPos());

					for (LogicComponent c : this.componentInstances.keySet()) {
						Vec2 bl = new Vec2(c.getOffset());
						Vec2 tr = bl.add(c.getWidth(), c.getHeight());
						if (sel_bl.x < bl.x && sel_bl.y < bl.y && sel_tr.x > tr.x && sel_tr.y > tr.y) {
							this.selectComponent(c);
						}
					}
				}
				break;
			}

			case INTERACT_MODE: {
				break;
			}
			}
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

		//switching modes
		switch_mode_block:
		if (!this.mousePressed) {
			InteractionMode next_mode = this.interactionMode;
			switch (key) {
			case GLFW_KEY_1:
				next_mode = InteractionMode.INTERACT_MODE;
				break;

			case GLFW_KEY_2:
				next_mode = InteractionMode.EDIT_MODE;
				break;
			}

			if (next_mode == this.interactionMode) {
				//nothing happened
				break switch_mode_block;
			}

			//clean up previous mode
			switch (this.interactionMode) {
			case EDIT_MODE:
				this.setPlaceComponent(null);
				this.deselectAllComponents();
				this.stopCopying();
				break;
			case INTERACT_MODE:
				break;
			}

			//switch to next mode
			switch (next_mode) {
			case EDIT_MODE:
				break;
			case INTERACT_MODE:
				break;
			}
			this.interactionMode = next_mode;
		}

		//handle keypresses for current modes
		switch (this.interactionMode) {
		case EDIT_MODE: {
			switch (key) {
			case GLFW_KEY_BACKSPACE: {
				ArrayList<LogicComponent> to_remove = new ArrayList<>();
				to_remove.addAll(this.selectedComponents);
				for (LogicComponent c : to_remove) {
					this.removeComponent(c);
				}
				break;
			}
			case GLFW_KEY_C: {
				if (!KeyboardInput.isControlPressed()) {
					break;
				}
				if (this.selectedComponents.size() == 0) {
					break;
				}
				if (this.isCopying) {
					this.stopCopying();
				}
				this.startCopying();
				break;
			}
			case GLFW_KEY_V: {
				if (!KeyboardInput.isControlPressed()) {
					break;
				}
				if (!this.isCopying) {
					break;
				}
				//paste
				IVec2 mouse_pos = this.getMouseGridSnapPos();
				for (LogicComponent c : this.copiedComponents) {
					LogicComponent new_component = LogicComponent.copyComponent(c);
					new_component.setOffset(c.getOffset().add(mouse_pos));
					this.addComponent(new_component);
				}
				break;
			}
			case GLFW_KEY_ESCAPE: {
				if (this.isCopying) {
					this.stopCopying();
				}
				else if (this.isPlacingComponent) {
					this.setPlaceComponent(null);
				}
				else {
					this.deselectAllComponents();
				}
				break;
			}
			case GLFW_KEY_R: {
				if (this.isDraggingWires) {
					this.wireDragHorizontalFirst = !this.wireDragHorizontalFirst;
					break;
				}
				break;
			}
			}
			break;
		}
		case INTERACT_MODE: {
			break;
		}
		}
	}

	@Override
	protected void _keyReleased(int key) {
		this.uiSection.keyReleased(key);
	}

	public class LogicInput {
		//helper class responsible for handling the case where there are multiple outputs feeding into an input. 
		//should behave the same as a wire. 

		public LogicComponent component;
		public int input_ind;
		public IVec2 loc;

		private int nr_true, nr_false;
		private HashMap<LogicComponent, TruthValue> outputValueMap; //{logic component instance, truth value}
		private TruthValue data = ERROR;

		private ModelInstance displayRect;

		public LogicInput(LogicComponent component, int ind, IVec2 loc) {
			this.component = component;
			this.input_ind = ind;
			this.loc = new IVec2(loc);

			this.outputValueMap = new HashMap<>();

			this.nr_true = 0;
			this.nr_false = 0;

			if (!inputMap.containsKey(this.loc)) {
				inputMap.put(this.loc, new ArrayList<>());
			}
			inputMap.get(this.loc).add(this);

			Vec2 r_pos = new Vec2(loc);
			this.displayRect = FilledRectangle.addDefaultRectangle(r_pos.add(-WIRE_WIDTH / 2), r_pos.add(WIRE_WIDTH / 2), 0, LOGIC_INPUT_SCENE);
			this.displayRect.setMaterial(ERROR_MATERIAL);

			//look for already existing logic outputs at this location
			ArrayList<LogicOutput> l_outputs = outputMap.get(this.loc);
			if (l_outputs != null) {
				for (LogicOutput out : l_outputs) {
					this.setOutput(out.component, out.getOutput());
				}
			}
		}

		public void kill() {
			inputMap.get(this.loc).remove(this);
			if (inputMap.get(this.loc).size() == 0) {
				inputMap.remove(this.loc);
			}

			this.displayRect.kill();
		}

		/**
		 * Returns true if data changes. 
		 * @param component
		 * @param val
		 * @return
		 */
		public boolean setOutput(LogicComponent component, TruthValue val) {
			if (component == this.component) {
				//in the case that the input and output locations overlap. 
				return false;
			}
			if (this.outputValueMap.containsKey(component)) {
				TruthValue old_val = this.outputValueMap.get(component);
				this.nr_true -= old_val == TRUE ? 1 : 0;
				this.nr_false -= old_val == FALSE ? 1 : 0;
			}
			this.nr_true += val == TRUE ? 1 : 0;
			this.nr_false += val == FALSE ? 1 : 0;
			this.outputValueMap.put(component, val);
			TruthValue old_data = this.data;
			this.data = this.nr_true != 0 ? TRUE : (this.nr_false != 0 ? FALSE : ERROR);

			switch (this.data) {
			case TRUE:
				this.displayRect.setMaterial(TRUE_MATERIAL);
				break;

			case FALSE:
				this.displayRect.setMaterial(FALSE_MATERIAL);
				break;

			case ERROR:
				this.displayRect.setMaterial(ERROR_MATERIAL);
				break;
			}

			return old_data != this.data;
		}

		public TruthValue getData() {
			return this.data;
		}
	}

	public class LogicOutput {
		//this exists for display purposes, and for knowing where all the outputs are
		public LogicComponent component;
		public int output_ind;
		public IVec2 loc;
		private ModelInstance displayRect;
		private TruthValue data = ERROR;

		public LogicOutput(LogicComponent component, int ind, IVec2 loc) {
			this.component = component;
			this.output_ind = ind;
			this.loc = new IVec2(loc);

			Vec2 r_pos = new Vec2(loc);
			this.displayRect = FilledRectangle.addDefaultRectangle(r_pos.add(-WIRE_WIDTH / 2), r_pos.add(WIRE_WIDTH / 2), 0, LOGIC_INPUT_SCENE);
			this.displayRect.setMaterial(ERROR_MATERIAL);

			if (!outputMap.containsKey(this.loc)) {
				outputMap.put(this.loc, new ArrayList<>());
			}
			outputMap.get(this.loc).add(this);
		}

		public void kill() {
			this.displayRect.kill();

			//notify associated logic inputs
			this.setOutput(ERROR);

			outputMap.get(this.loc).remove(this);
			if (outputMap.get(this.loc).size() == 0) {
				outputMap.remove(this.loc);
			}
		}

		public void update() {
			switch (this.data) {
			case TRUE:
				this.displayRect.setMaterial(TRUE_MATERIAL);
				break;

			case FALSE:
				this.displayRect.setMaterial(FALSE_MATERIAL);
				break;

			case ERROR:
				this.displayRect.setMaterial(ERROR_MATERIAL);
				break;
			}
		}

		public void setOutput(TruthValue data) {
			if (data == this.data) {
				return;
			}
			this.data = data;
			ArrayList<LogicInput> logic_inputs = inputMap.get(this.loc);
			if (logic_inputs == null) {
				return;
			}
			for (int j = 0; j < logic_inputs.size(); j++) {
				LogicInput l_input = logic_inputs.get(j);
				if (l_input.component == this.component) {
					continue;
				}
				l_input.setOutput(this.component, this.data);
				addToUpdateQueue(l_input.component);
			}
			this.update();
		}

		public TruthValue getOutput() {
			return componentInstances.get(this.component).instance.getOutput(this.output_ind);
		}
	}

	private CircuitSimulatorWindow thiswindow = this;

	class ComponentInstance {
		LogicComponent component;
		ComponentDisplay display;
		LogicComponentInstance instance;

		LogicInput[] logicInputs;
		LogicOutput[] logicOutputs;

		public boolean isSelected = false;

		public ComponentInstance(LogicComponent component) {
			this.component = component;
			this.instance = LogicComponentInstance.createLogicComponentInstance(component);

			//create logic inputs
			IVec2 component_offset = this.component.getOffset();
			IVec2[] input_offsets = this.component.getInputOffsets();
			this.logicInputs = new LogicInput[this.component.getNrInputs()];
			for (int i = 0; i < this.logicInputs.length; i++) {
				IVec2 input_loc = component_offset.add(input_offsets[i]);
				LogicInput l_input = new LogicInput(this.component, i, input_loc);
				this.logicInputs[i] = l_input;
			}

			//create logic outputs
			IVec2[] output_offsets = this.component.getOutputOffsets();
			this.logicOutputs = new LogicOutput[this.component.getNrOutputs()];
			for (int i = 0; i < this.logicOutputs.length; i++) {
				IVec2 output_loc = component_offset.add(output_offsets[i]);
				LogicOutput l_output = new LogicOutput(this.component, i, output_loc);
				this.logicOutputs[i] = l_output;
			}

			this.display = ComponentDisplay.createComponentDisplay(component, this.instance, thiswindow);
			this.display.setVisible(true);
			this.display.update();

			//add to update queue
			addToUpdateQueue(this.component);
		}

		/**
		 * Updates the underlying LogicComponentInstance according to the associated LogicInputs. 
		 */
		public void update() {
			//update component logic
			TruthValue[] input = new TruthValue[this.component.getNrInputs()];
			for (int i = 0; i < input.length; i++) {
				input[i] = this.logicInputs[i].getData();
			}
			if (!this.instance.setInputs(input)) {
				return;
			}

			//update display
			this.display.update();

			//update logic outputs
			for (int i = 0; i < this.logicOutputs.length; i++) {
				this.logicOutputs[i].setOutput(this.instance.getOutput(i));
			}
		}

		public void kill() {
			this.display.kill();

			for (LogicInput l : this.logicInputs) {
				l.kill();
			}
			for (LogicOutput l : this.logicOutputs) {
				l.kill();
			}
		}

		public TruthValue getOutput(int ind) {
			return this.instance.getOutput(ind);
		}

		public int getWidth() {
			return this.component.getWidth();
		}

		public int getHeight() {
			return this.component.getHeight();
		}

		public IVec2 getOffset() {
			return this.component.getOffset();
		}

		public void setOffset(IVec2 offset) {
			if (this.component.getOffset().equals(offset)) {
				return;
			}
			this.component.setOffset(offset);

			//update input and output locations, and update internal state. 
			//reset logic inputs
			IVec2 component_offset = this.component.getOffset();
			IVec2[] input_offsets = this.component.getInputOffsets();
			for (int i = 0; i < this.logicInputs.length; i++) {
				this.logicInputs[i].kill();
				IVec2 input_loc = component_offset.add(input_offsets[i]);
				LogicInput l_input = new LogicInput(this.component, i, input_loc);
				this.logicInputs[i] = l_input;
			}

			//reset logic outputs
			IVec2[] output_offsets = this.component.getOutputOffsets();
			for (int i = 0; i < this.logicOutputs.length; i++) {
				this.logicOutputs[i].kill();
				IVec2 output_loc = component_offset.add(output_offsets[i]);
				LogicOutput l_output = new LogicOutput(this.component, i, output_loc);
				this.logicOutputs[i] = l_output;
				this.logicOutputs[i].setOutput(this.instance.getOutput(i));
			}
			addToUpdateQueue(this.component);

			//update display
			this.display.setOffset(offset);
		}

		public void setSelected(boolean b) {
			this.isSelected = b;
			this.display.setSelected(b);
		}
	}

	public int getComponentSelectScene() {
		return this.COMPONENT_SELECT_SCENE;
	}

	public int getComponentScene() {
		return this.COMPONENT_SCENE;
	}

	public int getWireScene() {
		return this.WIRE_SCENE;
	}

	public HashMap<IVec2, ArrayList<LogicInput>> getInputMap() {
		return this.inputMap;
	}

	public HashMap<IVec2, ArrayList<LogicOutput>> getOutputMap() {
		return this.outputMap;
	}

	public HashMap<GateType, FilledRectangle> getGateRects() {
		return this.gateRects;
	}
}
