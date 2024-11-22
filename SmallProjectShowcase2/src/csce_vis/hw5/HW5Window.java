package csce_vis.hw5;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL12.*;
import static org.lwjgl.opengl.GL13.*;
import static org.lwjgl.opengl.GL14.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL21.*;
import static org.lwjgl.opengl.GL30.*;
import static org.lwjgl.opengl.GL31.*;
import static org.lwjgl.opengl.GL32.*;
import static org.lwjgl.opengl.GL33.*;
import static org.lwjgl.opengl.GL40.*;
import static org.lwjgl.opengl.GL41.*;
import static org.lwjgl.opengl.GL42.*;
import static org.lwjgl.opengl.GL43.*;
import static org.lwjgl.opengl.GL44.*;
import static org.lwjgl.opengl.GL45.*;
import static org.lwjgl.opengl.GL46.*;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;

import org.lwjgl.glfw.GLFW;

import csce_vis.hw5.shape.AABB;
import csce_vis.hw5.shape.Capsule;
import csce_vis.hw5.shape.KDOP;
import csce_vis.hw5.shape.Shape;
import lwjglengine.graphics.Cubemap;
import lwjglengine.graphics.Framebuffer;
import lwjglengine.graphics.Material;
import lwjglengine.model.CubeSphere;
import lwjglengine.model.Line;
import lwjglengine.model.Model;
import lwjglengine.model.ModelInstance;
import lwjglengine.model.ModelTransform;
import lwjglengine.model.Triangle;
import lwjglengine.model.VertexArray;
import lwjglengine.player.Camera;
import lwjglengine.player.PlayerInputController;
import lwjglengine.scene.DirLight;
import lwjglengine.scene.Light;
import lwjglengine.scene.Scene;
import lwjglengine.screen.PerspectiveScreen;
import lwjglengine.window.ObjectEditorWindow;
import lwjglengine.window.Window;
import myutils.file.FileUtils;
import myutils.math.Mat3;
import myutils.math.Mat4;
import myutils.math.MathUtils;
import myutils.math.Quaternion;
import myutils.math.Vec2;
import myutils.math.Vec3;

public class HW5Window extends Window {

	//finally, rigidbodies!!
	//look here: https://box2d.org/files/ErinCatto_IterativeDynamics_GDC2005.pdf

	//TODO
	// - figure out tetrahedron moment of inertia
	// - properly solve for friction in Manifold. 
	// - still some weird stuff going on with moment of inertia of thin objects
	// - optimize kdop-kdop collision narrow phase

	private ImpulseScene impulse;
	private ArrayList<DisplayBody> displayBodies;
	private Model cubeModel = null;
	private Model suzanne = null, suzanne_wireframe = null;
	private Model burrito = null, burrito_wireframe = null;
	private Model sphere, cylinder;

	private final int WORLD_SCENE = Scene.generateScene();
	private final int WIREFRAME_SCENE = Scene.generateScene();

	private PerspectiveScreen perspectiveScreen;
	private PlayerInputController pic;

	private SimulationOptions options = new SimulationOptions();

	public class SimulationOptions {
		private boolean pausePhysics = false;
		private boolean pauseOnCollide = false;
		private boolean generateWireframes = false;
		private int demosceneNumber = 7;

		public int getDemosceneNumber() {
			return demosceneNumber;
		}

		public void setDemosceneNumber(int demosceneNumber) {
			this.demosceneNumber = demosceneNumber;
		}

		public boolean getPausePhysics() {
			return pausePhysics;
		}

		public void setPausePhysics(boolean pausePhysics) {
			this.pausePhysics = pausePhysics;
		}

		public boolean getPauseOnCollide() {
			return pauseOnCollide;
		}

		public void setPauseOnCollide(boolean pauseOnCollide) {
			this.pauseOnCollide = pauseOnCollide;
		}

		public boolean getGenerateWireframes() {
			return generateWireframes;
		}

		public void setGenerateWireframes(boolean generateWireframes) {
			this.generateWireframes = generateWireframes;
		}
	}

	public HW5Window(int xOffset, int yOffset, int width, int height, Window parentWindow) {
		super(xOffset, yOffset, width, height, parentWindow);
		this.init();
	}

	private void init() {
		this.setLockCursorOnSelect(true);
		this.setDeselectOnEscPressed(true);
		this.setUnlockCursorOnEscPressed(true);

		this.impulse = new ImpulseScene();
		this.impulse.setGravity(new Vec3(0, -50, 0));
		this.displayBodies = new ArrayList<>();

		try {
			this.cubeModel = Model.loadModelFileRelative("/res/cube/cube.obj");
			this.suzanne = Model.loadModelFileRelative("/res/suzanne/suzanne.obj");
			this.burrito = Model.loadModelFileRelative("/res/burrito/burrito.obj");
		}
		catch (IOException e) {
			e.printStackTrace();
		}

		this.suzanne_wireframe = this.generateKDOPWireframe(this.suzanne);
		this.burrito_wireframe = this.generateKDOPWireframe(this.burrito);

		this.pic = new PlayerInputController(new Vec3(0, 30, 100));
		this.pic.setAcceptPlayerInputs(false);

		this.perspectiveScreen = new PerspectiveScreen();
		this.perspectiveScreen.setWorldCameraFOV(90f);
		this.perspectiveScreen.setWorldScene(WORLD_SCENE);
		this.perspectiveScreen.renderSkybox(true);
		this.perspectiveScreen.renderDecals(false);
		this.perspectiveScreen.renderPlayermodel(false);
		this.perspectiveScreen.renderParticles(false);

		DirLight sun = new DirLight(new Vec3(-2, -1.5, -1), new Vec3(1), 0.4f);
		Light.addLight(WORLD_SCENE, sun);

		BufferedImage[] skyboxSides = new BufferedImage[6];
		String skyboxDir = "/res/skybox/lake/";
		for (int i = 0; i < 6; i++) {
			skyboxSides[i] = FileUtils.loadImageRelative(skyboxDir + Cubemap.CUBEMAP_SIDE_NAMES[i] + ".jpg");
		}
		Cubemap skybox = new Cubemap(skyboxSides);
		Scene.skyboxes.put(WORLD_SCENE, skybox);

		//ground
		{
			float tile_size = 10;

			Vec3 g0 = new Vec3(-tile_size, 0, tile_size);
			Vec3 g1 = new Vec3(tile_size, 0, tile_size);
			Vec3 g2 = new Vec3(tile_size, 0, -tile_size);
			Vec3 g3 = new Vec3(-tile_size, 0, -tile_size);

			Material light_mat = new Material(Color.WHITE);
			Material dark_mat = new Material(new Vec3(0.6f));

			light_mat.setSpecular(new Vec3(0));
			dark_mat.setSpecular(new Vec3(0));

			int tile_amt = 10;
			for (int i = -tile_amt; i <= tile_amt; i++) {
				for (int j = -tile_amt; j <= tile_amt; j++) {
					Vec3 offset = new Vec3(i * tile_amt * 2, 0, j * tile_amt * 2);
					Vec3 v0 = g0.add(offset);
					Vec3 v1 = g1.add(offset);
					Vec3 v2 = g2.add(offset);
					Vec3 v3 = g3.add(offset);

					ModelInstance t0 = Triangle.addTriangle(v0, v1, v2, WORLD_SCENE);
					ModelInstance t1 = Triangle.addTriangle(v2, v3, v0, WORLD_SCENE);

					if (Math.abs(i + j) % 2 == 0) {
						t0.setMaterial(light_mat);
						t1.setMaterial(light_mat);
					}
					else {
						t0.setMaterial(dark_mat);
						t1.setMaterial(dark_mat);
					}
				}
			}
		}

		CubeSphere.addDefaultSphere(new Vec3(20, 20, 0), 10, WORLD_SCENE);

		//generate cylinder model
		{

		}

		this.addChildAdjWindow(new ObjectEditorWindow(this.options));

		this.resetImpulseScene();

		this._resize();
	}

	private void resetImpulseScene() {
		for (DisplayBody d : this.displayBodies) {
			d.kill();
		}
		this.displayBodies.clear();
		this.impulse.clearScene();
		options.pausePhysics = false;

		//ground
		{
			Body b = this.addAABB(new Vec3(0, -210 - 0.1, 0), new Vec3(420));
			b.setStatic();
		}

		switch (options.demosceneNumber) {
		//short wide box
		case 0: {
			Body b = this.addAABB(new Vec3(0, 5, 0), new Vec3(50, 10, 50));
			b.setStatic();
			break;
		}

		//short wide box resting on short wide box
		case 1: {
			{
				Body b = this.addAABB(new Vec3(0, 5, 0), new Vec3(50, 10, 50));
				b.setStatic();
			}
			{
				Body b = this.addAABB(new Vec3(3, 17.5, 0), new Vec3(20, 5, 20));
			}
			break;
		}

		//suzanne
		case 2: {
			Body b = this.addKDOP(new Vec3(0, 20, 0), this.suzanne, Mat4.scale(5));
			b.angvel = new Vec3(3, 0, 0);
			break;
		}

		//burrito
		case 3: {
			Body b = this.addKDOP(new Vec3(0, 20, 0), this.burrito, Mat4.scale(10));
			break;
		}

		//table
		case 4: {
			Body bl = this.addAABB(new Vec3(-10, 10, -10), new Vec3(2, 20, 2));
			Body br = this.addAABB(new Vec3(10, 10, -10), new Vec3(2, 20, 2));
			Body tl = this.addAABB(new Vec3(-10, 10, 10), new Vec3(2, 20, 2));
			Body tr = this.addAABB(new Vec3(10, 10, 10), new Vec3(2, 20, 2));

			Body top = this.addAABB(new Vec3(0, 22, 0), new Vec3(25, 2, 25));
			Body burrito = this.addKDOP(new Vec3(0, 30, 0), this.burrito, Mat4.scale(10));
			break;
		}

		//cube triangle
		case 5: {
			int layer_amt = 12;
			float cube_sz = 3;
			float yptr = cube_sz / 2;
			for (int i = layer_amt; i >= 1; i--) {
				float xptr = -(cube_sz * i) / 2;
				xptr += cube_sz / 2;
				for (int j = 0; j < i; j++) {
					Body b = this.addAABB(new Vec3(xptr, yptr, 0), new Vec3(cube_sz));
					xptr += cube_sz;
				}
				yptr += cube_sz;
			}
			break;
		}

		//cube tower
		case 6: {
			int cube_amt = 3;
			float cube_sz = 3;
			float yptr = cube_sz / 2;
			for (int i = cube_amt; i >= 1; i--) {
				Body b = this.addAABB(new Vec3(0, yptr, 0), new Vec3(cube_sz));
				yptr += cube_sz;
			}
			break;
		}

		//static capsule
		case 7: {
			//			Body c1 = this.addCapsule(new Vec3(0, 10, 0), 3, 10);
			//			c1.setStatic();

			Body c2 = this.addCapsule(new Vec3(0, 30, 0), 3, 10);
			c2.angvel.set(new Vec3(0, 2, 0));
			break;
		}
		}

	}

	private Model generateKDOPWireframe(Model m) {
		KDOP kdop = this.generateKDOP(m, Mat4.identity());
		return this.generateKDOPWireframe(kdop);
	}

	private Model generateKDOPWireframe(KDOP kdop) {
		ArrayList<Vec3> vertex_list = new ArrayList<>();
		ArrayList<Integer> index_list = new ArrayList<>();
		Vec3[][] faces = kdop.getFaces();
		for (Vec3[] f : faces) {
			int face_start = vertex_list.size();
			for (Vec3 v : f) {
				vertex_list.add(v);
			}
			for (int i = 0; i < f.length; i++) {
				index_list.add(i + face_start);
				index_list.add((i + 1) % f.length + face_start);
			}
		}

		float[] vertices = new float[vertex_list.size() * 3];
		int[] indices = new int[index_list.size()];
		for (int i = 0; i < vertex_list.size(); i++) {
			vertices[i * 3 + 0] = vertex_list.get(i).x;
			vertices[i * 3 + 1] = vertex_list.get(i).y;
			vertices[i * 3 + 2] = vertex_list.get(i).z;
		}
		for (int i = 0; i < index_list.size(); i++) {
			indices[i] = index_list.get(i);
		}

		VertexArray wire_va = new VertexArray(vertices, indices, GL_LINES);
		return new Model(wire_va);
	}

	private Model generateCapsuleWireframe(Capsule capsule) {
		int endcap_resolution = 20;
		Vec3[] cap2d = new Vec3[(endcap_resolution + 1) * 2];
		for (int i = 0; i <= endcap_resolution; i++) {
			float ang = (float) (Math.PI / 2.0 - Math.PI * ((float) i / (float) endcap_resolution));
			Vec2 dir = new Vec2(1, 0);
			dir.rotate(ang);
			cap2d[i] = new Vec3(0, dir.y * capsule.radius, dir.x * capsule.radius + capsule.length / 2.0f);
		}
		for (int i = 0; i <= endcap_resolution; i++) {
			float ang = (float) (-Math.PI / 2.0 - Math.PI * ((float) i / (float) endcap_resolution));
			Vec2 dir = new Vec2(1, 0);
			dir.rotate(ang);
			cap2d[i + (endcap_resolution + 1)] = new Vec3(0, dir.y * capsule.radius, dir.x * capsule.radius - capsule.length / 2.0f);
		}

		int nr_cap2d = 6;
		ArrayList<Vec3> vertex_list = new ArrayList<>();
		ArrayList<Integer> index_list = new ArrayList<>();
		for (int i = 0; i < nr_cap2d; i++) {
			float ang = (float) Math.PI * ((float) i / (float) nr_cap2d);
			for (int j = 0; j < cap2d.length; j++) {
				Vec3 v = new Vec3(cap2d[j]);
				v.rotateZ(ang);
				vertex_list.add(v);

				index_list.add(j + cap2d.length * i);
				index_list.add((j + 1) % cap2d.length + cap2d.length * i);
			}
		}

		float[] vertices = new float[vertex_list.size() * 3];
		int[] indices = new int[index_list.size()];
		for (int i = 0; i < vertex_list.size(); i++) {
			vertices[i * 3 + 0] = vertex_list.get(i).x;
			vertices[i * 3 + 1] = vertex_list.get(i).y;
			vertices[i * 3 + 2] = vertex_list.get(i).z;
		}
		for (int i = 0; i < index_list.size(); i++) {
			indices[i] = index_list.get(i);
		}

		VertexArray wire_va = new VertexArray(vertices, indices, GL_LINES);
		return new Model(wire_va);
	}

	private KDOP generateKDOP(Model m, Mat4 transform) {
		ArrayList<Vec3> pts_list = new ArrayList<>();
		for (VertexArray va : m.getMeshes()) {
			for (int i = 0; i < va.getVertices().length / 3; i++) {
				pts_list.add(new Vec3(va.getVertices()[i * 3 + 0], va.getVertices()[i * 3 + 1], va.getVertices()[i * 3 + 2]));
			}
		}
		Vec3[] pts = new Vec3[pts_list.size()];
		for (int i = 0; i < pts.length; i++) {
			pts[i] = transform.mul(pts_list.get(i), 1);
		}
		KDOP kdop = new KDOP(pts);
		return kdop;
	}

	private void addBody(Body b, DisplayBody d) {
		this.impulse.addBody(b);
		this.displayBodies.add(d);
	}

	private Body addAABB(Vec3 pos, Vec3 dim) {
		ModelInstance mi = new ModelInstance(this.cubeModel, WORLD_SCENE);
		mi.setModelTransform(new ModelTransform(Mat4.scale(dim.mul(0.5f))));

		Shape s = new AABB(dim);
		Body b = new Body(s, pos);
		DisplayBody d = new DisplayBody(b, mi);
		this.addBody(b, d);
		return b;
	}

	private Body addKDOP(Vec3 pos, Model m, Mat4 base_transform) {
		Shape s = this.generateKDOP(m, base_transform);
		Body b = new Body(s, pos);

		Mat4 transform = new Mat4(base_transform);
		transform.muli(Mat4.translate(((KDOP) s).getCOMCorrection().mul(-1)));
		ModelInstance mi = new ModelInstance(m, new ModelTransform(transform), WORLD_SCENE);

		DisplayBody d = new DisplayBody(b, mi);
		this.addBody(b, d);
		return b;
	}

	private Body addKDOP(Vec3 pos, Model m) {
		return this.addKDOP(pos, m, Mat4.identity());
	}

	private Body addCapsule(Vec3 pos, float radius, float length) {
		Capsule s = new Capsule(radius, length);
		Body b = new Body(s, pos);

		Model wireframe = this.generateCapsuleWireframe(s);
		ModelInstance winst = new ModelInstance(wireframe, WORLD_SCENE);
		DisplayBody d = new DisplayBody(b, winst);
		this.addBody(b, d);
		return b;
	}

	@Override
	protected void _kill() {
		for (DisplayBody d : this.displayBodies) {
			d.kill();
		}

		Scene.removeScene(WORLD_SCENE);
		Scene.removeScene(WIREFRAME_SCENE);

		this.perspectiveScreen.kill();
	}

	@Override
	protected void _resize() {
		this.perspectiveScreen.setScreenDimensions(this.getWidth(), this.getHeight());
	}

	@Override
	public String getDefaultTitle() {
		return "Homework 5";
	}

	@Override
	protected void _update() {
		if (!options.pausePhysics) {
			int itercnt = 4;
			for (int i = 0; i < itercnt; i++) {
				this.impulse.update(1.0f / (60.0f * itercnt));
				if (this.impulse.getCollisionOccurred() && options.pauseOnCollide) {
					options.pausePhysics = true;
				}
			}
		}

		//update physics model transforms
		for (DisplayBody d : this.displayBodies) {
			d.updateModelInstance();
		}

		this.pic.update();
	}

	@Override
	protected void renderContent(Framebuffer outputBuffer) {
		Camera camera = new Camera((float) Math.toRadians(90), this.getWidth(), this.getHeight(), 0.1f, 400);
		camera.setPos(this.pic.getTop());
		camera.setFacing(this.pic.getFacing());
		this.perspectiveScreen.setCamera(camera);
		this.perspectiveScreen.render(outputBuffer);
	}

	@Override
	protected void renderOverlay(Framebuffer outputBuffer) {
		// TODO Auto-generated method stub

	}

	@Override
	protected void selected() {
		this.pic.setAcceptPlayerInputs(true);
	}

	@Override
	protected void deselected() {
		this.pic.setAcceptPlayerInputs(false);
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
		// TODO Auto-generated method stub

	}

	@Override
	protected void _mouseReleased(int button) {
		// TODO Auto-generated method stub

	}

	@Override
	protected void _mouseScrolled(float wheelOffset, float smoothOffset) {
		// TODO Auto-generated method stub

	}

	@Override
	protected void _keyPressed(int key) {
		switch (key) {
		case GLFW.GLFW_KEY_R:
			this.resetImpulseScene();
			break;

		case GLFW.GLFW_KEY_Q: {
			Body b = this.addAABB(new Vec3(0, 20, 0), MathUtils.random(new Vec3(3), new Vec3(10)));
			b.angvel = MathUtils.randomUnitDir3D();
			break;
		}

		case GLFW.GLFW_KEY_E: {
			Body b = this.addAABB(this.pic.getPos().add(this.pic.getFacing().mul(5)), new Vec3(3));
			b.angvel = MathUtils.randomUnitDir3D().mul(10);
			b.vel = this.pic.getFacing().mul(50);
			break;
		}

		case GLFW.GLFW_KEY_B: {
			Body b = this.addKDOP(this.pic.getPos().add(this.pic.getFacing().mul(5)), this.burrito, Mat4.scale(10));
			b.angvel = MathUtils.randomUnitDir3D().mul(10);
			b.vel = this.pic.getFacing().mul(50);
			break;
		}

		case GLFW.GLFW_KEY_C: {
			Body b = this.addCapsule(this.pic.getPos().add(this.pic.getFacing().mul(5)), 3, 6);
			b.angvel = MathUtils.randomUnitDir3D().mul(10);
			b.vel = this.pic.getFacing().mul(50);
			break;
		}
		}
	}

	@Override
	protected void _keyReleased(int key) {
		// TODO Auto-generated method stub

	}

	class DisplayBody {
		Model kdop_wireframe = null;
		ModelInstance wmi = null; //wireframe model instance

		Body body;
		ModelInstance mi;
		Mat4 baseTransform;

		public DisplayBody(Body _body, ModelInstance _mi) {
			this.body = _body;
			this.mi = _mi;
			this.baseTransform = new Mat4(this.mi.getModelTransform().getModelMatrix());

			if (options.generateWireframes) {
				switch (this.body.shape.type) {
				case KDOP:
					this.kdop_wireframe = generateKDOPWireframe((KDOP) this.body.shape);
					this.wmi = new ModelInstance(this.kdop_wireframe, WORLD_SCENE);
					break;

				case CAPSULE:
					break;
				}
			}
		}

		public void updateModelInstance() {
			Mat4 transform = new Mat4(this.baseTransform);

			//apply general orientation and translation transforms
			Mat4 rot_transform = MathUtils.quaternionToRotationMat4(this.body.orient);
			transform.muli(rot_transform);
			transform.muli(Mat4.translate(this.body.pos));

			this.mi.setModelTransform(new ModelTransform(transform));

			if (this.kdop_wireframe != null) {
				this.wmi.setModelTransform(new ModelTransform(rot_transform.mul(Mat4.translate(this.body.pos))));
			}
		}

		public void kill() {
			this.mi.kill();

			if (this.kdop_wireframe != null) {
				this.kdop_wireframe.kill();
			}
		}
	}

}
