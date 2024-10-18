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
	
	private State state;

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
		
		Shape cube = null;
		{
			ArrayList<Node> nodes = new ArrayList<>();
			ArrayList<Spring> springs = new ArrayList<>();
			ArrayList<Face> faces = new ArrayList<>();
			
			Node n0 = new Node(new Vec3(-5, -5, -5), 1);
			Node n1 = new Node(new Vec3(-5, -5, -5), 1);
			Node n2 = new Node(new Vec3(-5, -5, -5), 1);
			Node n3 = new Node(new Vec3(-5, -5, -5), 1);
			Node n4 = new Node(new Vec3(-5, -5, -5), 1);
			Node n5 = new Node(new Vec3(-5, -5, -5), 1);
			Node n6 = new Node(new Vec3(-5, -5, -5), 1);
			Node n7 = new Node(new Vec3(-5, -5, -5), 1);
			
			//suspend cube by corner
		}
		
		//build state
		{
			
			
			
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
		
		this.state.step(1.0f / 60.0f);
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
		Vec3 pos, vel;
		float inv_mass;

		public Node(Vec3 _pos, float _mass) {
			this.pos = new Vec3(_pos);
			this.vel = new Vec3(0);
			this.inv_mass = 1.0f / _mass;
		}

		public Node(Node n) {
			this.pos = new Vec3(n.pos);
			this.vel = new Vec3(n.vel);
			this.inv_mass = n.inv_mass;
		}
	}

	class Spring {
		int a, b;
		float k, rest_len;
		
		public Spring(int _a, int _b, float _k, float _rest_len) {
			this.a = _a;
			this.b = _b;
			this.k = _k;
			this.rest_len = _rest_len;
		}
		
		public Spring(Spring s) {
			this.a = s.a;
			this.b = s.b;
			this.k = s.k;
			this.rest_len = s.rest_len;
		}
	}
	
	class Face {
		int a, b, c;
		
		public Face(int _a, int _b, int _c) {
			this.a = _a;
			this.b = _b;
			this.c = _c;
		}
		
		public Face(Face f) {
			this.a = f.a;
			this.b = f.b;
			this.c = f.c;
		}
	}
	
	class Shape {
		//a collection of nodes, springs, and faces
		//ideally, should be closed. 
		Node[] nodes;
		Spring[] springs;
		Face[] faces;
		
		ModelInstance[] model_instances;
		boolean is_rendering = false;
		
		public Shape(ArrayList<Node> _nodes, ArrayList<Spring> _springs, ArrayList<Face> _faces) {
			this.nodes = new Node[_nodes.size()];
			this.springs = new Spring[_springs.size()];
			this.faces = new Face[_faces.size()];
			
			for (int i = 0; i < _nodes.size(); i++) {
				this.nodes[i] = new Node(_nodes.get(i));
			}
			for (int i = 0; i < _springs.size(); i++) {
				this.springs[i] = new Spring(_springs.get(i));
			}
			for (int i = 0; i < _faces.size(); i++) {
				this.faces[i] = new Face(_faces.get(i));
			}
		}
		
		public Shape(Shape _s) {
			this.nodes = new Node[_s.nodes.length];
			this.springs = new Spring[_s.springs.length];
			this.faces = new Face[_s.faces.length];
			
			for (int i = 0; i < this.nodes.length; i++) {
				this.nodes[i] = new Node(_s.nodes[i]);
			}
			for (int i = 0; i < this.springs.length; i++) {
				this.springs[i] = new Spring(_s.springs[i]);
			}
			for (int i = 0; i < this.faces.length; i++) {
				this.faces[i] = new Face(_s.faces[i]);
			}
		}
		
		public void step(float dt) {
			//forces are pretty much accel * mass
			Vec3[] force = new Vec3[this.nodes.length];	
			for(int i = 0; i < this.nodes.length; i++) {
				force[i] = new Vec3(0);
			}
			
			//springs
			for(int i = 0; i < this.springs.length; i++) {
				Spring s = this.springs[i];
				Node na = this.nodes[s.a];
				Node nb = this.nodes[s.b];
				
				Vec3 ab = new Vec3(na.pos, nb.pos);
				float len = ab.length();
				float diff = s.rest_len - len;
				
				force[s.a].addi(ab.mul(diff * s.k * dt));
				force[s.b].addi(ab.mul(-diff * s.k * dt));
			}
			
			//integrate
			for(int i = 0; i < this.nodes.length; i++) {
				Node n = this.nodes[i];
				
				Vec3 n_pos = n.pos.add(n.vel.mul(n.inv_mass * dt));
				Vec3 n_vel = n.vel.add(force[i].mul(n.inv_mass));
				
				n.pos.set(n_pos);
				n.vel.set(n_vel);
			}
			
			this.updateModelInstances();
		}
		
		public void updateModelInstances() {
			//TODO
		}
		
		public void setIsRendering(boolean b) {
			if(this.is_rendering == b) {
				return;
			}
			this.is_rendering = b;
			
			if(this.is_rendering) {
				this.model_instances = new ModelInstance[this.faces.length];
				for(int i = 0; i < this.faces.length; i++) {
					Face f = this.faces[i];
					Node n0 = this.nodes[f.a];
					Node n1 = this.nodes[f.b];
					Node n2 = this.nodes[f.c];
					this.model_instances[i] = Triangle.addTriangle(n0.pos, n1.pos, n2.pos, WORLD_SCENE);
				}
			}
			else {
				for(ModelInstance m : this.model_instances) {
					m.kill();
				}
				this.model_instances = null;
			}
		}
	}

	class State {
		//a collection of shapes
		ArrayList<Shape> shapes;
		
		boolean is_rendering = false;

		public State() {
			this.shapes = new ArrayList<>();
		}
		
		public State(State _s) {
			this.shapes = new ArrayList<>();
			for(Shape s : _s.shapes) {
				this.shapes.add(new Shape(s));
			}
		}
		
		public void setIsRendering(boolean b) {
			this.is_rendering = b;
			for(Shape s : this.shapes) {
				s.setIsRendering(b);
			}
		}

		public void step(float dt) {
			for(Shape s : this.shapes) {
				s.step(dt);
			}
			
			//do shape collisions
		}
	}

}
