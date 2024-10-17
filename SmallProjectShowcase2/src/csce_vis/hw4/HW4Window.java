package csce_vis.hw4;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;

import lwjglengine.graphics.Cubemap;
import lwjglengine.graphics.Framebuffer;
import lwjglengine.graphics.Material;
import lwjglengine.model.ModelInstance;
import lwjglengine.model.Triangle;
import lwjglengine.player.Camera;
import lwjglengine.player.PlayerInputController;
import lwjglengine.scene.DirLight;
import lwjglengine.scene.Light;
import lwjglengine.scene.Scene;
import lwjglengine.screen.PerspectiveScreen;
import lwjglengine.window.Window;
import myutils.file.FileUtils;
import myutils.math.Mat4;
import myutils.math.Vec3;

public class HW4Window extends Window {

	private final int WORLD_SCENE = Scene.generateScene();
	private PerspectiveScreen perspectiveScreen;

	private PlayerInputController pic;

	public HW4Window(int xOffset, int yOffset, int width, int height, Window parentWindow) {
		super(xOffset, yOffset, width, height, parentWindow);
		this.init();
	}

	private void init() {
		this.setLockCursorOnSelect(true);
		this.setDeselectOnEscPressed(true);
		this.setUnlockCursorOnEscPressed(true);

		this.pic = new PlayerInputController(new Vec3(0));
		this.pic.setAcceptPlayerInputs(false);

		this.perspectiveScreen = new PerspectiveScreen();
		this.perspectiveScreen.setWorldCameraFOV(90f);
		this.perspectiveScreen.setWorldScene(WORLD_SCENE);
		this.perspectiveScreen.renderSkybox(true);
		this.perspectiveScreen.renderDecals(false);
		this.perspectiveScreen.renderPlayermodel(false);
		this.perspectiveScreen.renderParticles(false);

		DirLight sun = new DirLight(new Vec3(1, -1, 1), new Vec3(1), 0.4f);
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

			Vec3 g0 = new Vec3(-tile_size, -tile_size, -tile_size);
			Vec3 g1 = new Vec3(tile_size, -tile_size, -tile_size);
			Vec3 g2 = new Vec3(tile_size, -tile_size, tile_size);
			Vec3 g3 = new Vec3(-tile_size, -tile_size, tile_size);

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

		this._resize();
	}

	@Override
	protected void _kill() {
		this.perspectiveScreen.kill();

		Scene.removeScene(WORLD_SCENE);
	}

	@Override
	protected void _resize() {
		this.perspectiveScreen.setScreenDimensions(this.getWidth(), this.getHeight());
	}

	@Override
	public String getDefaultTitle() {
		return "Homework 4";
	}

	@Override
	protected void _update() {
		this.pic.update();
	}

	@Override
	protected void renderContent(Framebuffer outputBuffer) {
		Camera camera = new Camera((float) Math.toRadians(90), this.getWidth(), this.getHeight(), 0.1f, 400);
		camera.setPos(this.pic.getTop());
		camera.setFacing(this.pic.getFacing());
		Mat4 pr_matrix = camera.getProjectionMatrix();
		Mat4 vw_matrix = camera.getViewMatrix();

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
		// TODO Auto-generated method stub

	}

	@Override
	protected void _keyReleased(int key) {
		// TODO Auto-generated method stub

	}

	class Node {
		Vec3 pos;
		float mass = 1;

		public Node(Vec3 _pos, float _mass) {
			this.pos = new Vec3(_pos);
			this.mass = _mass;
		}

		public Node(Node n) {
			this.pos = new Vec3(n.pos);
			this.mass = n.mass;
		}
	}

	class Spring {
		Node a, b;
		float k, rest_len;

		public Spring(Node _a, Node _b, float _k, float _rest_len) {
			this.a = _a;
			this.b = _b;
			this.k = _k;
			this.rest_len = _rest_len;
		}
	}

	class State {
		Node[] nodes;
		Spring[] springs;
		Node[][] faces;

		public State(ArrayList<Node> _nodes, ArrayList<Spring> _springs, ArrayList<Node[]> _faces) {
			this.nodes = new Node[_nodes.size()];
			this.springs = new Spring[_springs.size()];
			this.faces = new Node[_faces.size()][3];

			HashMap<Node, Node> node_map = new HashMap<>(); //map old to new nodes
			for (int i = 0; i < _nodes.size(); i++) {
				this.nodes[i] = new Node(_nodes.get(i));
				node_map.put(_nodes.get(i), this.nodes[i]);
			}

			for (int i = 0; i < _springs.size(); i++) {
				Spring s = _springs.get(i);
				this.springs[i] = new Spring(node_map.get(s.a), node_map.get(s.b), s.k, s.rest_len);
			}

			for (int i = 0; i < _faces.size(); i++) {
				Node a = _faces.get(i)[0];
				Node b = _faces.get(i)[1];
				Node c = _faces.get(i)[2];
				this.faces[i] = new Node[] { node_map.get(a), node_map.get(b), node_map.get(c) };
			}
		}

		public State(State s) {

		}

		public void step(float dt) {

		}
	}

}
