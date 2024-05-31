package vector_art;

import static org.lwjgl.glfw.GLFW.*;

import java.awt.Color;
import java.io.File;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Queue;
import java.util.TreeMap;

import lwjglengine.graphics.Framebuffer;
import lwjglengine.graphics.Material;
import lwjglengine.input.Button;
import lwjglengine.input.Input;
import lwjglengine.input.MouseInput;
import lwjglengine.model.Line;
import lwjglengine.model.ModelInstance;
import lwjglengine.scene.Scene;
import lwjglengine.screen.UIScreen;
import lwjglengine.ui.UIElement;
import lwjglengine.ui.UIFilledRectangle;
import lwjglengine.ui.UISection;
import lwjglengine.window.AdjustableWindow;
import lwjglengine.window.FileExplorerWindow;
import lwjglengine.window.FileSelectorWindow;
import lwjglengine.window.TextEditorWindow;
import lwjglengine.window.Window;
import myutils.file.FileUtils;
import myutils.file.xml.XMLNode;
import myutils.file.xml.XMLReader;
import myutils.file.xml.svg.SVGElement;
import myutils.file.xml.svg.SVGPath;
import myutils.file.xml.svg.SVGReader;
import myutils.math.MathUtils;
import myutils.math.Vec2;
import myutils.math.Vec3;
import myutils.math.Vec4;
import myutils.misc.Pair;

public class VectorArtWindow extends Window {

	// - trying to create a svg renderer is probably too hard; they have too many features.
	//   - let's just focus on rendering filled quadratic and cubic bezier curves
	//   - we can use cubics to approximate quadratics, so let's just render cubics. 
	// - https://developer.nvidia.com/gpugems/gpugems3/part-iv-image-effects/chapter-25-rendering-vector-art-gpu
	// - www.polygontriangulation.com/2018/07/triangulation-algorithm.html
	// - LaTeX math renderer

	//TODO:
	// - somehow make gridlines fade in / out 
	// - force all paths to be wound CCW
	// - take the subdivided curves and compute the triangulation
	//   - first we need to store the curves in multiple arraylists instead of one big hash. This is because we need 
	//     information as to which curve is part of which path
	//   - we know that v0 and v3 are automatically included in the polygon. It's just that we don't know if v1 or v2 are
	//     included, and if they are, which order they're in
	//   - to see if they are included, we can just determine if they're on the correct side of the line from v0 to v3. 
	//   - if only one of them is on the correct side, then we're done. Otherwise, we need to test if one of them 'contains'
	//     the other. 
	//   - if both of them are valid, then we need to determine the correct ordering. This shouldn't be too hard, as the vector
	//     in the correct order should be facing in the direction from v0 to v3. 
	// - fix path polygon generation
	//   - my assumption that v0 and v3 are automatically in the polygon is false; this is only the case
	//     when the convex hull for the curve includes v0 and v3. 

	private final int CONTROL_SCENE = Scene.generateScene();
	private final int CONVEX_HULL_SCENE = Scene.generateScene();
	private final int POLY_SCENE = Scene.generateScene();
	private final int TRIANGLE_SCENE = Scene.generateScene();
	private final int GRIDLINE_SCENE = Scene.generateScene();

	private boolean renderControlScene = true;
	private boolean renderConvexHullScene = false;
	private boolean renderPolyScene = false;
	private boolean renderTriangleScene = false;

	private UIScreen uiScreen;
	private UISection uiSection;

	private static float maxViewportScale = 1000;
	private static float minViewportScale = 0.001f;
	private float viewportScale = 1;
	private Vec2 viewportCenter = new Vec2(0);
	private int gridlineScale = 0;
	private TreeMap<Integer, ModelInstance> verticalGridlines, horizontalGridlines;

	private HashSet<Path> paths;

	private boolean viewportGrabbed = false;

	public VectorArtWindow(int xOffset, int yOffset, int width, int height, Window parentWindow) {
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

		this.setContextMenuRightClick(true);
		this.setContextMenuActions(new String[] { "Load svg" });

		this.paths = new HashSet<>();

		this.displayAsSVG(FileUtils.loadFileRelative("/res/test_svg/x_symbol.svg"));

		this.verticalGridlines = new TreeMap<>();
		this.horizontalGridlines = new TreeMap<>();

		this._resize();
	}

	@Override
	public void handleContextMenuAction(String action) {
		switch (action) {
		case "Load svg": {
			FileSelectorWindow fileExplorer = new FileSelectorWindow(this);
			fileExplorer.setSingleEntrySelection(true);
			AdjustableWindow adjWindow = new AdjustableWindow(fileExplorer, this);
			break;
		}
		}
	}

	private void addPath(Path p) {
		this.paths.add(p);
		p.setVisible(true);
	}

	private void addPath(List<Vec2[]> cubics) {
		this.addPath(new Path(cubics));
	}

	private void removePath(Path p) {
		this.paths.remove(p);
		p.kill();
	}

	private void removeAllPaths() {
		for (Path p : this.paths) {
			p.kill();
		}
		this.paths.clear();
	}

	private void displayAsSVG(File svgFile) {
		if (!FileUtils.getFileExtension(svgFile).equals("svg")) {
			return;
		}

		this.removeAllPaths();
		ArrayList<SVGElement> elements = SVGReader.parseStringAsSVG(FileUtils.readFileToString(svgFile));
		for (SVGElement e : elements) {
			if (!(e instanceof SVGPath)) {
				continue;
			}
			SVGPath p = (SVGPath) e;
			List<List<Vec2[]>> cubicCurves = p.getCubicCurves();
			for (List<Vec2[]> path : cubicCurves) {
				this.addPath(path);
			}
		}
	}

	@Override
	public void handleFiles(File[] files) {
		if (files.length != 1) {
			return;
		}

		this.displayAsSVG(files[0]);
	}

	@Override
	protected void _kill() {
		this.uiScreen.kill();
		this.uiSection.kill();

		Scene.removeScene(CONTROL_SCENE);
		Scene.removeScene(GRIDLINE_SCENE);
		Scene.removeScene(CONVEX_HULL_SCENE);
		Scene.removeScene(POLY_SCENE);
		Scene.removeScene(TRIANGLE_SCENE);
	}

	@Override
	protected void _resize() {
		this.uiScreen.setScreenDimensions(this.getWidth(), this.getHeight());
		this.uiSection.setScreenDimensions(this.getWidth(), this.getHeight());
	}

	@Override
	public String getDefaultTitle() {
		return "Vector Art";
	}

	private void updateGridlines() {
		//compute gridline scale
		int majorGridlineScale, minorGridlineScale;
		{
			int scaleMult = 4;
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

		if (this.renderTriangleScene) {
			this.uiScreen.setUIScene(TRIANGLE_SCENE);
			this.uiScreen.render(outputBuffer);
		}

		if (this.renderPolyScene) {
			this.uiScreen.setUIScene(POLY_SCENE);
			this.uiScreen.render(outputBuffer);
		}

		if (this.renderConvexHullScene) {
			this.uiScreen.setUIScene(CONVEX_HULL_SCENE);
			this.uiScreen.render(outputBuffer);
		}

		if (this.renderControlScene) {
			this.uiScreen.setUIScene(CONTROL_SCENE);
			this.uiScreen.render(outputBuffer);
		}

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
		case GLFW_KEY_S:
			for (Path p : this.paths) {
				p.subdivide();
			}
			break;

		case GLFW_KEY_C:
			this.renderControlScene = !this.renderControlScene;
			break;

		case GLFW_KEY_H:
			this.renderConvexHullScene = !this.renderConvexHullScene;
			break;

		case GLFW_KEY_P:
			this.renderPolyScene = !this.renderPolyScene;
			break;

		case GLFW_KEY_T:
			this.renderTriangleScene = !this.renderTriangleScene;
			break;
		}
	}

	@Override
	protected void _keyReleased(int key) {
		this.uiSection.keyReleased(key);
	}

	//winding order for path is always CCW
	class Path {
		boolean isVisible = false;
		ArrayList<Curve> curves;

		ArrayList<ModelInstance> poly_lines;
		ArrayList<ModelInstance> tri_lines;

		Path(Path other) {
			this.curves = new ArrayList<>();
			for (Curve c : other.curves) {
				this.curves.add(new Curve(c));
			}
			this.setVisible(other.isVisible);

			this.poly_lines = new ArrayList<>();
			this.tri_lines = new ArrayList<>();
		}

		Path(List<Vec2[]> cubics) {
			this.curves = new ArrayList<>();

			//invert y since svg is supposed to be relative from top left corner. 
			for (Vec2[] v : cubics) {
				for (Vec2 i : v) {
					i.y *= -1;
				}
				this.curves.add(new Curve(v));
			}

			//make sure winding order is CCW
			{
				ArrayList<Vec2> poly = new ArrayList<>();
				for (Curve c : this.curves) {
					poly.add(c.v0);
				}
				if (!MathUtils.isCounterClockwiseWinding(poly)) {
					//reverse everything 
					for (Curve c : this.curves) {
						c.reverse();
					}
					Collections.reverse(this.curves);
				}
			}

			this.poly_lines = new ArrayList<>();
			this.tri_lines = new ArrayList<>();
		}

		void setVisible(boolean b) {
			if (this.isVisible) {
				for (ModelInstance m : this.poly_lines) {
					m.kill();
				}
				this.poly_lines.clear();

				for (ModelInstance m : this.tri_lines) {
					m.kill();
				}
				this.tri_lines.clear();
			}

			this.isVisible = b;
			for (Curve c : this.curves) {
				c.setVisible(b);
			}

			if (this.isVisible) {
				//polygon lines
				ArrayList<Vec2> poly = this.generatePolygon();
				for (int i = 0; i < poly.size(); i++) {
					Vec2 v0 = poly.get(i);
					Vec2 v1 = poly.get((i + 1) % poly.size());
					ModelInstance m = Line.addDefaultLine(v0, v1, POLY_SCENE);
					m.setMaterial(new Material(Color.BLUE));
					this.poly_lines.add(m);
				}

				//triangle lines
				ArrayList<int[]> tris = MathUtils.calculateTrianglePartition(poly);
				for (int i = 0; i < tris.size(); i++) {
					int[] inds = tris.get(i);
					Vec2 t0 = poly.get(inds[0]);
					Vec2 t1 = poly.get(inds[1]);
					Vec2 t2 = poly.get(inds[2]);
					ModelInstance l0 = Line.addDefaultLine(t0, t1, TRIANGLE_SCENE);
					ModelInstance l1 = Line.addDefaultLine(t1, t2, TRIANGLE_SCENE);
					ModelInstance l2 = Line.addDefaultLine(t2, t0, TRIANGLE_SCENE);
					l0.setMaterial(new Material(Color.DARK_GRAY));
					l1.setMaterial(new Material(Color.DARK_GRAY));
					l2.setMaterial(new Material(Color.DARK_GRAY));
					this.tri_lines.add(l0);
					this.tri_lines.add(l1);
					this.tri_lines.add(l2);
				}

				for (Curve c : this.curves) {
					ArrayList<Vec2> hull = c.generateHull();
					ArrayList<int[]> hull_tris = MathUtils.calculateTrianglePartition(hull);
					for (int i = 0; i < hull_tris.size(); i++) {
						int[] inds = hull_tris.get(i);
						Vec2 t0 = hull.get(inds[0]);
						Vec2 t1 = hull.get(inds[1]);
						Vec2 t2 = hull.get(inds[2]);
						ModelInstance l0 = Line.addDefaultLine(t0, t1, TRIANGLE_SCENE);
						ModelInstance l1 = Line.addDefaultLine(t1, t2, TRIANGLE_SCENE);
						ModelInstance l2 = Line.addDefaultLine(t2, t0, TRIANGLE_SCENE);
						l0.setMaterial(new Material(Color.LIGHT_GRAY));
						l1.setMaterial(new Material(Color.LIGHT_GRAY));
						l2.setMaterial(new Material(Color.LIGHT_GRAY));
						this.tri_lines.add(l0);
						this.tri_lines.add(l1);
						this.tri_lines.add(l2);
					}
				}
			}
		}

		void kill() {
			this.setVisible(false);
			for (Curve c : this.curves) {
				c.kill();
			}
		}

		//subdivides all curves in this path
		void subdivide() {
			ArrayList<Curve> n_curves = new ArrayList<>();
			for (Curve c : this.curves) {
				Pair<Curve, Curve> ret = c.subdivide(0.5f);
				n_curves.add(ret.first);
				n_curves.add(ret.second);
				c.kill();
				ret.first.setVisible(this.isVisible);
				ret.second.setVisible(this.isVisible);
			}
			this.curves = n_curves;

			this.setVisible(this.isVisible);
		}

		//assuming that this path is closed, returns a polygon that corresponds to the purely filled in portion of the path. 
		//the non-purely filled in portions are the curves, and we need to render those seperately. 
		//in the convex hull for each curve, this is all points from v3 -> v0 assuming the hull is wound CCW
		ArrayList<Vec2> generatePolygon() {
			ArrayList<Vec2> v_list = new ArrayList<>();
			for (Curve c : this.curves) {
				ArrayList<Vec2> hull = c.generateHull();
				Collections.reverse(hull);
				int ptr = -1;
				for (int i = 0; i < hull.size(); i++) {
					if (hull.get(i).equals(c.v0)) {
						ptr = i;
						break;
					}
				}
				if (ptr == -1) {
					System.err.println("VectorArtWindow : Could not find v0");
					continue;
				}
				while (true) {
					Vec2 v = hull.get(ptr % hull.size());
					v_list.add(v);
					if (v.equals(c.v3)) {
						break;
					}
					ptr++;
				}
			}

			ArrayList<Vec2> poly = new ArrayList<>();
			for (int i = 0; i < v_list.size(); i++) {
				Vec2 v0 = v_list.get(i);
				Vec2 v1 = v_list.get((i + 1) % v_list.size());
				if (!v0.equals(v1)) {
					poly.add(v0);
				}
			}
			return poly;
		}
	}

	class Curve {
		boolean isVisible = false;
		Vec2 v0, v1, v2, v3;
		ModelInstance l0, l1, l2, l3;

		ArrayList<ModelInstance> hull_lines;

		Curve(Curve other) {
			this(other.v0, other.v1, other.v2, other.v3);
		}

		Curve(Vec2 v0, Vec2 v1, Vec2 v2, Vec2 v3) {
			this(new Vec2[] { v0, v1, v2, v3 });
		}

		Curve(Vec2[] v) {
			assert v.length == 4;
			this.v0 = new Vec2(v[0]);
			this.v1 = new Vec2(v[1]);
			this.v2 = new Vec2(v[2]);
			this.v3 = new Vec2(v[3]);
			this.hull_lines = new ArrayList<>();
		}

		void kill() {
			this.setVisible(false);
		}

		void reverse() {
			{
				Vec2 tmp = new Vec2(v0);
				v0.set(v3);
				v3.set(tmp);
			}
			{
				Vec2 tmp = new Vec2(v1);
				v1.set(v2);
				v2.set(tmp);
			}
		}

		void setVisible(boolean b) {
			if (this.isVisible) {
				this.l0.kill();
				this.l1.kill();
				this.l2.kill();
				this.l3.kill();

				for (ModelInstance m : this.hull_lines) {
					m.kill();
				}
				this.hull_lines.clear();
			}

			this.isVisible = b;

			if (this.isVisible) {
				this.l0 = Line.addDefaultLine(v0, v1, CONTROL_SCENE);
				this.l1 = Line.addDefaultLine(v1, v2, CONTROL_SCENE);
				this.l2 = Line.addDefaultLine(v2, v3, CONTROL_SCENE);
				this.l3 = Line.addDefaultLine(v3, v0, CONTROL_SCENE);

				this.l0.setMaterial(new Material(Color.WHITE));
				this.l1.setMaterial(new Material(Color.WHITE));
				this.l2.setMaterial(new Material(Color.WHITE));
				this.l3.setMaterial(new Material(Color.RED));

				ArrayList<Vec2> convex_hull = this.generateHull();
				for (int i = 0; i < convex_hull.size(); i++) {
					ModelInstance m = Line.addDefaultLine(convex_hull.get(i), convex_hull.get((i + 1) % convex_hull.size()), CONVEX_HULL_SCENE);
					m.setMaterial(new Material(Color.GREEN));
					this.hull_lines.add(m);
				}
			}
		}

		boolean isControlPointInHull(Vec2[] hull) {
			boolean ans = false;
			ans |= MathUtils.pointInsidePolygon(hull, v0);
			ans |= MathUtils.pointInsidePolygon(hull, v1);
			ans |= MathUtils.pointInsidePolygon(hull, v2);
			ans |= MathUtils.pointInsidePolygon(hull, v3);
			return ans;
		}

		ArrayList<Vec2> generateHull() {
			return MathUtils.calculateConvexHull(new Vec2[] { v0, v1, v2, v3 });
		}

		Pair<Curve, Curve> subdivide(float t) {
			Vec2 v10 = MathUtils.lerp(v0, 0, v1, 1, t);
			Vec2 v11 = MathUtils.lerp(v1, 0, v2, 1, t);
			Vec2 v12 = MathUtils.lerp(v2, 0, v3, 1, t);

			Vec2 v20 = MathUtils.lerp(v10, 0, v11, 1, t);
			Vec2 v21 = MathUtils.lerp(v11, 0, v12, 1, t);

			Vec2 v30 = MathUtils.lerp(v20, 0, v21, 1, t);

			Curve c0 = new Curve(new Vec2[] { v0, v10, v20, v30 });
			Curve c1 = new Curve(new Vec2[] { v30, v21, v12, v3 });
			return new Pair<>(c0, c1);
		}
	}

}
