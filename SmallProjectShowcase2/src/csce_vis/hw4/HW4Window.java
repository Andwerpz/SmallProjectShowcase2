package csce_vis.hw4;

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
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

import org.lwjgl.glfw.GLFW;

import lwjglengine.graphics.Cubemap;
import lwjglengine.graphics.Framebuffer;
import lwjglengine.graphics.Material;
import lwjglengine.graphics.Shader;
import lwjglengine.graphics.ShaderStorageBuffer;
import lwjglengine.main.Main;
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
import lwjglengine.util.ShaderUtils;
import lwjglengine.window.ObjectEditorWindow;
import lwjglengine.window.Window;
import myutils.file.FileUtils;
import myutils.file.xml.XMLNode;
import myutils.file.xml.XMLReader;
import myutils.math.IVec3;
import myutils.math.Mat4;
import myutils.math.MathUtils;
import myutils.math.Vec3;
import myutils.math.Vec4;
import myutils.math.tetrahedralizer.Tetrahedralizer;
import myutils.misc.Pair;
import myutils.misc.Triple;

public class HW4Window extends Window {

	//TODO
	// - vertex v. face collision
	// - edge v. edge collision
	//   - how do this one D: Maybe have to look at if the two edges will switch orientations with each other, and 
	//   - if they do, look at the closest point between the edges. 
	// - decrease the amount of springs for complicated models. 
	// - shape tetrahedralization. 
	//   - could look into delaunay tetrahedralization
	//   - or do it my own way: first do a convex partition using voronoi, then tetrahedralize every convex piece
	//   - could try to do grid partition? That would still leave some concave pieces, but its much better than naive. 

	// ok, i probably give up on edge v. edge collision, and pivot to nice rendering of large models. 
	// for a large model, create a decimated collision mesh, and use that to do collisions. 

	// will probably have to have custom shader pipeline to recompute vertex attributes every frame. 

	// mesh tetrahedralization
	// https://matthias-research.github.io/pages/tenMinutePhysics/13-Tetrahedralize.pdf

	// XPBD
	// - perhaps distribute mass based off of volume of adjacent tetrahedra?

	// last thing: speed up assocation of skin vertices to collision tetrahedra. 
	//  can do with hashing. 
	//  also, options menu.
	//  also, grabber?

	private final int WORLD_SCENE = Scene.generateScene();
	private PerspectiveScreen perspectiveScreen;

	private PlayerInputController pic;

	//each node, face, edge, spring, should appear in exactly 1 shape. 
	//each face, edge, spring, should only connect nodes within the same shape
	//each shape should consist of a closed shell of faces
	private Node[] nodes;
	private Spring[] springs;
	private Volume[] volumes;
	private Shape[] shapes;

	//get transformed by tetrahedral meshes. 
	private Skin[] skins;
	private ShaderStorageBuffer nodePosBuffer;
	private Shader updateSkinVertexShader, updateSkinNormalShader;

	private Vec3 gravity = new Vec3(0, -10, 0);
	private float timeDebt = 0;

	private SimulationOptions options = new SimulationOptions();

	public class SimulationOptions {
		public float coeffRestitution = 0.1f;
		public float staticFriction = 0.8f;
		public float dynamicFriction = 0.5f;

		public float springConstant = 5000f;
		public float dampingConstant = 0.5f;

		public float compliance = 0;

		public int iterationsPerSecond = 600;

		public boolean doEulerStep = true;
		public boolean doRK4Step = false;
		public boolean doXPBDStep = false;

		public boolean renderFaces = true;
		public boolean renderEdges = false;
		public boolean renderSkin = false;

		public int sceneID = 0;

		public float getCoeffRestitution() {
			return coeffRestitution;
		}

		public void setCoeffRestitution(float coeffRestitution) {
			this.coeffRestitution = coeffRestitution;
		}

		public float getStaticFriction() {
			return staticFriction;
		}

		public void setStaticFriction(float staticFriction) {
			this.staticFriction = staticFriction;
		}

		public float getDynamicFriction() {
			return dynamicFriction;
		}

		public void setDynamicFriction(float dynamicFriction) {
			this.dynamicFriction = dynamicFriction;
		}

		public float getSpringConstant() {
			return springConstant;
		}

		public void setSpringConstant(float springConstant) {
			this.springConstant = springConstant;
		}

		public float getDampingConstant() {
			return dampingConstant;
		}

		public void setDampingConstant(float dampingConstant) {
			this.dampingConstant = dampingConstant;
		}

		public float getCompliance() {
			return compliance;
		}

		public void setCompliance(float compliance) {
			this.compliance = compliance;
		}

		public int getIterationsPerSecond() {
			return iterationsPerSecond;
		}

		public void setIterationsPerSecond(int iterationsPerSecond) {
			this.iterationsPerSecond = iterationsPerSecond;
		}

		public boolean getDoEulerStep() {
			return doEulerStep;
		}

		public void setDoEulerStep(boolean doEulerStep) {
			this.doEulerStep = doEulerStep;
		}

		public boolean getDoRK4Step() {
			return doRK4Step;
		}

		public void setDoRK4Step(boolean doRK4Step) {
			this.doRK4Step = doRK4Step;
		}

		public boolean getDoXPBDStep() {
			return doXPBDStep;
		}

		public void setDoXPBDStep(boolean doXPBDStep) {
			this.doXPBDStep = doXPBDStep;
		}

		public boolean getRenderFaces() {
			return renderFaces;
		}

		public void setRenderFaces(boolean renderFaces) {
			this.renderFaces = renderFaces;
		}

		public boolean getRenderEdges() {
			return renderEdges;
		}

		public void setRenderEdges(boolean renderEdges) {
			this.renderEdges = renderEdges;
		}

		public boolean getRenderSkin() {
			return renderSkin;
		}

		public void setRenderSkin(boolean renderSkin) {
			this.renderSkin = renderSkin;
		}

		public int getSceneID() {
			return this.sceneID;
		}

		public void setSceneID(int i) {
			this.sceneID = i;
		}
	}

	public HW4Window(int xOffset, int yOffset, int width, int height, Window parentWindow) {
		super(xOffset, yOffset, width, height, parentWindow);
		this.init();
	}

	private void init() {
		this.setLockCursorOnSelect(true);
		this.setDeselectOnEscPressed(true);
		this.setUnlockCursorOnEscPressed(true);

		this.pic = new PlayerInputController(new Vec3(0, 30, 100));
		this.pic.setAcceptPlayerInputs(false);

		this.perspectiveScreen = new PerspectiveScreen();
		this.perspectiveScreen.setWorldCameraFOV(90f);
		this.perspectiveScreen.setWorldScene(WORLD_SCENE);
		this.perspectiveScreen.renderSkybox(true);
		this.perspectiveScreen.renderDecals(false);
		this.perspectiveScreen.renderPlayermodel(false);
		this.perspectiveScreen.renderParticles(false);

		DirLight sun = new DirLight(new Vec3(-1, -1, -1), new Vec3(1), 0.4f);
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

		this.updateSkinVertexShader = ShaderUtils.createShader("/csce_vis/hw4/update_skin_vertex.compute", GL_COMPUTE_SHADER);
		this.updateSkinNormalShader = ShaderUtils.createShader("/csce_vis/hw4/update_skin_normal.compute", GL_COMPUTE_SHADER);

		this.addChildAdjWindow(new ObjectEditorWindow(this.options));

		this.resetState();

		this._resize();
	}

	//aggregate everything required, then pack into arrays. 
	private void resetState() {
		if (this.nodes != null) {
			for (Shape s : this.shapes) {
				s.kill();
			}
			for (Skin s : this.skins) {
				s.kill();
			}

			this.nodes = null;
			this.springs = null;
			this.volumes = null;
			this.shapes = null;
			this.skins = null;

			this.nodePosBuffer.kill();
			this.nodePosBuffer = null;
		}

		ArrayList<Node> node_list = new ArrayList<>();
		ArrayList<Spring> spring_list = new ArrayList<>();
		ArrayList<Volume> volume_list = new ArrayList<>();
		ArrayList<Shape> shape_list = new ArrayList<>();
		ArrayList<Skin> skin_list = new ArrayList<>();

		switch (options.sceneID) {
		case 0: {
			Mat4 transform = Mat4.scale(5f);
			transform.muli(Mat4.translate(0, 5, 0));
			this.loadObj("/res/cube/cube.obj", transform, node_list, spring_list, shape_list);

			Mat4 transform2 = Mat4.scale(3f);
			transform2.muli(Mat4.rotate(MathUtils.randomUnitDir3D(), MathUtils.random(0, (float) Math.PI * 2)));
			transform2.muli(Mat4.translate(0, 16, 0));
			this.loadObj("/res/cube/cube.obj", transform2, node_list, spring_list, shape_list);
			break;
		}

		case 1: {
			Mat4 transform = Mat4.scale(5f);
			transform.muli(Mat4.translate(new Vec3(0, 20, 0)));
			this.loadTetXML("/res/tet_mesh/sphere_nice.xml", transform, node_list, spring_list, volume_list, shape_list);
			break;
		}

		case 2: {
			Mat4 transform = Mat4.scale(20f);
			transform.muli(Mat4.rotate(MathUtils.randomUnitDir3D(), MathUtils.random(0, (float) Math.PI * 2.0f)));
			transform.muli(Mat4.translate(new Vec3(0, 20, 0)));
			this.loadTetXML("/res/tet_mesh/bunny.xml", transform, node_list, spring_list, volume_list, shape_list);
			break;
		}

		case 3: {
			Mat4 transform = Mat4.scale(20f);
			transform.muli(Mat4.rotate(MathUtils.randomUnitDir3D(), MathUtils.random(0, (float) Math.PI * 2.0f)));
			transform.muli(Mat4.translate(new Vec3(0, 10, 0)));
			this.loadTetXMLWithSkin("/res/tet_mesh/dragon.xml", "/res/tet_mesh/dragon.obj", transform, node_list, spring_list, volume_list, shape_list, skin_list);
			break;
		}
		}
		if (false) {
			Mat4 transform = Mat4.scale(5f);
			transform.muli(Mat4.translate(new Vec3(0, 20, 0)));
			this.loadTetXML("/res/tet_mesh/sphere.xml", transform, node_list, spring_list, volume_list, shape_list);
		}
		if (false) {
			Mat4 transform = Mat4.scale(5f);
			transform.muli(Mat4.translate(new Vec3(0, 10, 0)));
			this.loadTetXML("/res/tet_mesh/suzanne.xml", transform, node_list, spring_list, volume_list, shape_list);
		}
		if (false) {
			Mat4 transform = Mat4.scale(20f);
			transform.muli(Mat4.rotate(MathUtils.randomUnitDir3D(), MathUtils.random(0, (float) Math.PI * 2.0f)));
			transform.muli(Mat4.translate(new Vec3(0, 20, 0)));
			this.loadTetXML("/res/tet_mesh/bunny.xml", transform, node_list, spring_list, volume_list, shape_list);
		}
		if (false) {
			Mat4 transform = Mat4.scale(20f);
			transform.muli(Mat4.rotate(MathUtils.randomUnitDir3D(), MathUtils.random(0, (float) Math.PI * 2.0f)));
			transform.muli(Mat4.translate(new Vec3(0, 20, 0)));
			this.loadTetXML("/res/tet_mesh/tetrahedron.xml", transform, node_list, spring_list, volume_list, shape_list);
		}
		if (false) {
			Mat4 transform = Mat4.scale(20f);
			//			transform.muli(Mat4.rotate(MathUtils.randomUnitDir3D(), MathUtils.random(0, (float) Math.PI * 2.0f)));
			transform.muli(Mat4.translate(new Vec3(0, 10, 0)));
			this.loadTetXML("/res/tet_mesh/dragon.xml", transform, node_list, spring_list, volume_list, shape_list);
		}
		if (false) {
			Mat4 transform = Mat4.scale(20f);
			transform.muli(Mat4.rotate(MathUtils.randomUnitDir3D(), MathUtils.random(0, (float) Math.PI * 2.0f)));
			transform.muli(Mat4.translate(new Vec3(0, 10, 0)));
			this.loadTetXMLWithSkin("/res/tet_mesh/dragon.xml", "/res/tet_mesh/dragon.obj", transform, node_list, spring_list, volume_list, shape_list, skin_list);
		}

		if (false) {
			Node n1 = new Node(new Vec3(0, 10, 0), 1);
			Node n2 = new Node(new Vec3(0, 20, 0), 1);
			Spring s1 = new Spring(0, 1, MathUtils.dist(n1.pos, n2.pos) / 2f);
			Edge e1 = new Edge(0, 1);
			ArrayList<Integer> node_ind_list = new ArrayList<>();
			node_ind_list.add(0);
			node_ind_list.add(1);
			ArrayList<Face> face_list = new ArrayList<>();
			ArrayList<Edge> edge_list = new ArrayList<>();
			edge_list.add(e1);
			Shape shape = new Shape(node_ind_list, face_list, edge_list);

			node_list.add(n1);
			node_list.add(n2);
			spring_list.add(s1);
			shape_list.add(shape);
		}

		if (false) {
			Node n1 = new Node(new Vec3(0, 10, 0), 1);
			Node n2 = new Node(new Vec3(0, 20, 0), 1);
			Node n3 = new Node(new Vec3(10, 10, 0), 1);
			float rest_len = MathUtils.dist(n1.pos, n2.pos) / 2f;
			Spring s1 = new Spring(0, 1, rest_len);
			Spring s2 = new Spring(1, 2, rest_len);
			Spring s3 = new Spring(2, 0, rest_len);
			Edge e1 = new Edge(0, 1);
			Edge e2 = new Edge(1, 2);
			Edge e3 = new Edge(2, 0);
			ArrayList<Integer> node_ind_list = new ArrayList<>();
			node_ind_list.add(0);
			node_ind_list.add(1);
			node_ind_list.add(2);
			ArrayList<Face> face_list = new ArrayList<>();
			ArrayList<Edge> edge_list = new ArrayList<>();
			edge_list.add(e1);
			edge_list.add(e2);
			edge_list.add(e3);
			Shape shape = new Shape(node_ind_list, face_list, edge_list);

			node_list.add(n1);
			node_list.add(n2);
			node_list.add(n3);
			spring_list.add(s1);
			spring_list.add(s2);
			spring_list.add(s3);
			shape_list.add(shape);
		}

		this.nodes = new Node[node_list.size()];
		this.springs = new Spring[spring_list.size()];
		this.volumes = new Volume[volume_list.size()];
		this.shapes = new Shape[shape_list.size()];
		this.skins = new Skin[skin_list.size()];

		for (int i = 0; i < node_list.size(); i++) {
			this.nodes[i] = node_list.get(i);
		}
		for (int i = 0; i < spring_list.size(); i++) {
			this.springs[i] = spring_list.get(i);
		}
		for (int i = 0; i < volume_list.size(); i++) {
			this.volumes[i] = volume_list.get(i);
		}
		for (int i = 0; i < shape_list.size(); i++) {
			this.shapes[i] = shape_list.get(i);
			this.shapes[i].init();
		}
		for (int i = 0; i < skin_list.size(); i++) {
			this.skins[i] = skin_list.get(i);
			this.skins[i].init();
		}

		this.nodePosBuffer = new ShaderStorageBuffer();
		this.nodePosBuffer.setSize(16 * this.nodes.length);
		this.nodePosBuffer.setUsage(GL_DYNAMIC_DRAW);
	}

	private void loadObj(String path, Mat4 transform, ArrayList<Node> out_nodes, ArrayList<Spring> out_springs, ArrayList<Shape> out_shapes) {
		Model model = null;
		try {
			model = Model.loadModelFileRelative(path);
		}
		catch (IOException e) {
			e.printStackTrace();
			return;
		}
		VertexArray va = model.getMeshes().get(0);

		ArrayList<Edge> edge_list = new ArrayList<>();
		ArrayList<Face> face_list = new ArrayList<>();
		ArrayList<Integer> node_ind_list = new ArrayList<>();

		HashMap<Vec3, Integer> pos_ind_mp = new HashMap<>();
		HashMap<Integer, Integer> ind_mp = new HashMap<>();
		for (int i = 0; i < va.getVertices().length; i += 3) {
			Vec3 pos = new Vec3(va.getVertices()[i + 0], va.getVertices()[i + 1], va.getVertices()[i + 2]);
			if (!pos_ind_mp.containsKey(pos)) {
				pos_ind_mp.put(pos, out_nodes.size());
				node_ind_list.add(out_nodes.size());
				Node n = new Node(transform.mul(pos, 1), 1);
				out_nodes.add(n);
			}
			ind_mp.put(i / 3, pos_ind_mp.get(pos));
		}

		HashSet<Pair<Integer, Integer>> added_edges = new HashSet<>();
		for (int i = 0; i < va.getIndices().length; i += 3) {
			int a = ind_mp.get(va.getIndices()[i + 0]);
			int b = ind_mp.get(va.getIndices()[i + 1]);
			int c = ind_mp.get(va.getIndices()[i + 2]);
			added_edges.add(new Pair<>(Math.min(a, b), Math.max(a, b)));
			added_edges.add(new Pair<>(Math.min(b, c), Math.max(b, c)));
			added_edges.add(new Pair<>(Math.min(c, a), Math.max(c, a)));

			Face f = new Face(a, b, c);
			face_list.add(f);
		}

		for (Pair<Integer, Integer> p : added_edges) {
			Edge e = new Edge(p.first, p.second);
			edge_list.add(e);

			Spring s = new Spring(p.first, p.second, MathUtils.dist(out_nodes.get(p.first).pos, out_nodes.get(p.second).pos));
			out_springs.add(s);
		}

		Shape shape = new Shape(node_ind_list, face_list, edge_list);
		out_shapes.add(shape);
	}

	private void loadTetXMLWithSkin(String tet_path, String skin_path, Mat4 transform, ArrayList<Node> out_nodes, ArrayList<Spring> out_springs, ArrayList<Volume> out_volumes, ArrayList<Shape> out_shapes, ArrayList<Skin> out_skins) {
		//load collision model
		int volume_start_ind = out_volumes.size();
		this.loadTetXML(tet_path, transform, out_nodes, out_springs, out_volumes, out_shapes);

		if (!options.renderSkin) {
			return;
		}

		//load skin model
		Vec3[] vertices = null;
		int[][] faces = null;
		Model skin_model = null;
		try {
			skin_model = Model.loadModelFileRelative(skin_path);
			VertexArray va = skin_model.getMeshes().get(0);
			float[] vertex_data = va.getVertices();
			int[] face_data = va.getIndices();

			vertices = new Vec3[vertex_data.length / 3];
			faces = new int[face_data.length / 3][];
			for (int i = 0; i < vertex_data.length; i += 3) {
				Vec3 v = new Vec3(vertex_data[i + 0], vertex_data[i + 1], vertex_data[i + 2]);
				vertices[i / 3] = transform.mul(v, 1);
			}
			for (int i = 0; i < face_data.length; i += 3) {
				faces[i / 3] = new int[] { face_data[i + 0], face_data[i + 1], face_data[i + 2] };
			}
		}
		catch (IOException e) {
			e.printStackTrace();
			return;
		}

		//for each vertex, find the tetrahedron that it's inside, or if it's not inside any, find the
		//one which maximizes the minimum barycentric coordinate
		int[] tet_inds = new int[vertices.length];
		Vec4[] bary_coords = new Vec4[vertices.length];
		{
			//hash all the vertices
			long hash_mod = 1000003;
			long px = 31;
			long py = 37;
			long pz = 41;
			ArrayList<Integer>[] buckets = new ArrayList[(int) hash_mod];
			for (int i = 0; i < buckets.length; i++) {
				buckets[i] = new ArrayList<>();
			}
			for (int i = 0; i < vertices.length; i++) {
				IVec3 iv = MathUtils.floor(vertices[i]);
				long hash = (iv.x * px + iv.y * py + iv.z * pz) % hash_mod;
				if (hash < 0) {
					hash = (hash_mod + hash) % hash_mod;
				}
				buckets[(int) hash].add(i);
			}

			//for each volume, look in hash buckets to see what nodes we can match.
			float[] best_min = new float[vertices.length];
			for (int i = 0; i < vertices.length; i++) {
				tet_inds[i] = -1;
				best_min[i] = -1e18f;
				bary_coords[i] = null;
			}
			for (int i = volume_start_ind; i < out_volumes.size(); i++) {
				//calc bounding box
				Volume v = out_volumes.get(i);
				Vec3 a = out_nodes.get(v.a).pos;
				Vec3 b = out_nodes.get(v.b).pos;
				Vec3 c = out_nodes.get(v.c).pos;
				Vec3 d = out_nodes.get(v.d).pos;
				Vec3 bb_min = MathUtils.min(new Vec3[] { a, b, c, d });
				Vec3 bb_max = MathUtils.max(new Vec3[] { a, b, c, d });
				IVec3 ibb_min = MathUtils.floor(bb_min);
				IVec3 ibb_max = MathUtils.ceil(bb_max);

				//look through buckets
				for (int x = ibb_min.x; x < ibb_max.x; x++) {
					for (int y = ibb_min.y; y < ibb_max.y; y++) {
						for (int z = ibb_min.z; z < ibb_max.z; z++) {
							long hash = (x * px + y * py + z * pz) % hash_mod;
							if (hash < 0) {
								hash = (hash_mod + hash) % hash_mod;
							}
							for (int vind : buckets[(int) hash]) {
								Vec4 coord = MathUtils.barycentricCoords(a, b, c, d, vertices[vind]);
								float min_coord = Math.min(Math.min(coord.x, coord.y), Math.min(coord.z, coord.w));
								if (min_coord > best_min[vind]) {
									best_min[vind] = min_coord;
									tet_inds[vind] = i;
									bary_coords[vind] = coord;
								}
							}
						}
					}
				}
			}

			//for all vertices that haven't been assigned a volume, look for best volume
			for (int i = 0; i < vertices.length; i++) {
				if (tet_inds[i] != -1) {
					continue;
				}

				float cbest_min = -1e18f;
				Vec4 cbest_coord = null;
				int cbest_tet = -1;
				for (int j = volume_start_ind; j < out_volumes.size(); j++) {
					Volume v = out_volumes.get(j);
					Vec3 a = out_nodes.get(v.a).pos;
					Vec3 b = out_nodes.get(v.b).pos;
					Vec3 c = out_nodes.get(v.c).pos;
					Vec3 d = out_nodes.get(v.d).pos;
					Vec4 coord = MathUtils.barycentricCoords(a, b, c, d, vertices[i]);
					float min_coord = Math.min(Math.min(coord.x, coord.y), Math.min(coord.z, coord.w));
					if (min_coord > cbest_min) {
						cbest_min = min_coord;
						cbest_coord = coord;
						cbest_tet = j;
					}
				}
				if (cbest_tet == -1) {
					System.err.println("Failed to find coord for vertex : " + vertices[i]);
				}
				tet_inds[i] = cbest_tet;
				bary_coords[i] = cbest_coord;
			}
		}

		Skin skin = new Skin(tet_inds, bary_coords, skin_model);
		out_skins.add(skin);
	}

	private void loadTetXML(String path, Mat4 transform, ArrayList<Node> out_nodes, ArrayList<Spring> out_springs, ArrayList<Volume> out_volumes, ArrayList<Shape> out_shapes) {
		String xmlString = FileUtils.loadStringRelative(path);
		XMLNode root = XMLReader.parseStringAsXML(xmlString);

		XMLNode vert_root = root.getChildren().get(0).getChildren().get(0).getChildren().get(0);
		XMLNode tet_root = root.getChildren().get(0).getChildren().get(0).getChildren().get(1);
		System.out.println("VCNT, TETCNT : " + vert_root.getChildren().size() + " " + tet_root.getChildren().size());

		Node[] nodes = new Node[vert_root.getChildren().size()];
		for (int i = 0; i < vert_root.getChildren().size(); i++) {
			XMLNode vert_node = vert_root.getChildren().get(i);
			int ind = Integer.parseInt(vert_node.getAttributeContent("index"));
			float x = Float.parseFloat(vert_node.getAttributeContent("x"));
			float y = Float.parseFloat(vert_node.getAttributeContent("y"));
			float z = Float.parseFloat(vert_node.getAttributeContent("z"));
			Vec3 pos = new Vec3(x, y, z);
			pos = transform.mul(pos, 1);
			nodes[ind] = new Node(pos, 1);
		}
		int node_start_ind = out_nodes.size();
		ArrayList<Integer> node_ind_list = new ArrayList<>();
		for (int i = 0; i < nodes.length; i++) {
			node_ind_list.add(out_nodes.size());
			out_nodes.add(nodes[i]);
		}

		ArrayList<Face> face_list = new ArrayList<>();
		ArrayList<Edge> edge_list = new ArrayList<>();
		HashSet<Pair<Integer, Integer>> added_edges = new HashSet<>();
		HashMap<Triple<Integer, Integer, Integer>, ArrayList<Triple<Integer, Integer, Integer>>> added_faces = new HashMap<>();

		for (int i = 0; i < tet_root.getChildren().size(); i++) {
			XMLNode tet_node = tet_root.getChildren().get(i);
			int[] t = new int[4];
			t[0] = Integer.parseInt(tet_node.getAttributeContent("v0")) + node_start_ind;
			t[1] = Integer.parseInt(tet_node.getAttributeContent("v1")) + node_start_ind;
			t[2] = Integer.parseInt(tet_node.getAttributeContent("v2")) + node_start_ind;
			t[3] = Integer.parseInt(tet_node.getAttributeContent("v3")) + node_start_ind;

			Vec3 a = out_nodes.get(t[0]).pos;
			Vec3 b = out_nodes.get(t[1]).pos;
			Vec3 c = out_nodes.get(t[2]).pos;
			Vec3 d = out_nodes.get(t[3]).pos;
			out_volumes.add(new Volume(t[0], t[1], t[2], t[3], MathUtils.signedTetrahedronVolume(a, b, c, d)));

			added_edges.add(new Pair<>(Math.min(t[0], t[1]), Math.max(t[0], t[1])));
			added_edges.add(new Pair<>(Math.min(t[0], t[2]), Math.max(t[0], t[2])));
			added_edges.add(new Pair<>(Math.min(t[0], t[3]), Math.max(t[0], t[3])));
			added_edges.add(new Pair<>(Math.min(t[1], t[2]), Math.max(t[1], t[2])));
			added_edges.add(new Pair<>(Math.min(t[1], t[3]), Math.max(t[1], t[3])));
			added_edges.add(new Pair<>(Math.min(t[2], t[3]), Math.max(t[2], t[3])));

			Triple<Integer, Integer, Integer> f0 = order(t[2], t[1], t[0]);
			Triple<Integer, Integer, Integer> f1 = order(t[0], t[1], t[3]);
			Triple<Integer, Integer, Integer> f2 = order(t[2], t[0], t[3]);
			Triple<Integer, Integer, Integer> f3 = order(t[1], t[2], t[3]);

			Triple<Integer, Integer, Integer> t0 = new Triple<>(t[2], t[1], t[0]);
			Triple<Integer, Integer, Integer> t1 = new Triple<>(t[0], t[1], t[3]);
			Triple<Integer, Integer, Integer> t2 = new Triple<>(t[2], t[0], t[3]);
			Triple<Integer, Integer, Integer> t3 = new Triple<>(t[1], t[2], t[3]);

			if (!added_faces.containsKey(f0))
				added_faces.put(f0, new ArrayList<>());
			if (!added_faces.containsKey(f1))
				added_faces.put(f1, new ArrayList<>());
			if (!added_faces.containsKey(f2))
				added_faces.put(f2, new ArrayList<>());
			if (!added_faces.containsKey(f3))
				added_faces.put(f3, new ArrayList<>());
			added_faces.get(f0).add(t0);
			added_faces.get(f1).add(t1);
			added_faces.get(f2).add(t2);
			added_faces.get(f3).add(t3);
		}

		for (Triple<Integer, Integer, Integer> f : added_faces.keySet()) {
			if (added_faces.get(f).size() > 1) {
				continue;
			}
			Triple<Integer, Integer, Integer> t = added_faces.get(f).get(0);

			int a = t.first;
			int b = t.second;
			int c = t.third;
			face_list.add(new Face(a, b, c));
		}

		for (Pair<Integer, Integer> p : added_edges) {
			edge_list.add(new Edge(p.first, p.second));

			Vec3 a = out_nodes.get(p.first).pos;
			Vec3 b = out_nodes.get(p.second).pos;
			out_springs.add(new Spring(p.first, p.second, (new Vec3(a, b)).length()));
		}

		out_shapes.add(new Shape(node_ind_list, face_list, edge_list));
	}

	//first < second < third
	private Triple<Integer, Integer, Integer> order(int a, int b, int c) {
		if (a > b) {
			int tmp = a;
			a = b;
			b = tmp;
		}
		if (a > c) {
			int tmp = a;
			a = c;
			c = tmp;
		}
		if (b > c) {
			int tmp = b;
			b = c;
			c = tmp;
		}
		return new Triple<>(a, b, c);
	}

	private void addTriangleWithDir(Vec3 dir, Vec3 a, Vec3 b, Vec3 c) {
		if (MathUtils.dot(dir, MathUtils.cross(new Vec3(a, b), new Vec3(a, c))) >= 0) {
			Triangle.addTriangle(a, b, c, WORLD_SCENE);
		}
		else {
			Triangle.addTriangle(a, c, b, WORLD_SCENE);
		}
	}

	private void addMarker(Vec3 a) {
		Line.addDefaultLine(new Vec3(a.x - 0.5, a.y, a.z), new Vec3(a.x + 0.5, a.y, a.z), WORLD_SCENE);
		Line.addDefaultLine(new Vec3(a.x, a.y - 0.5, a.z), new Vec3(a.x, a.y + 0.5, a.z), WORLD_SCENE);
		Line.addDefaultLine(new Vec3(a.x, a.y, a.z - 0.5), new Vec3(a.x, a.y, a.z + 0.5), WORLD_SCENE);
	}

	@Override
	protected void _kill() {
		if (this.shapes != null) {
			for (Shape s : this.shapes) {
				s.kill();
			}
		}
		if (this.skins != null) {
			for (Skin s : this.skins) {
				s.kill();
			}
		}

		this.updateSkinVertexShader.kill();
		this.updateSkinNormalShader.kill();

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

	private Vec3[] copy(Vec3[] a) {
		Vec3[] res = new Vec3[a.length];
		for (int i = 0; i < a.length; i++) {
			res[i] = new Vec3(a[i]);
		}
		return res;
	}

	private void addsi(Vec3[] a, Vec3[] b, float f) {
		for (int i = 0; i < a.length; i++) {
			a[i].addi(b[i].mul(f));
		}
	}

	private Vec3[] adds(Vec3[] a, Vec3[] b, float f) {
		Vec3[] res = copy(a);
		addsi(res, b, f);
		return res;
	}

	private void muli(Vec3[] a, float f) {
		for (int i = 0; i < a.length; i++) {
			a[i].muli(f);
		}
	}

	private Vec3[] mul(Vec3[] a, float f) {
		Vec3[] res = copy(a);
		muli(res, f);
		return res;
	}

	private void handleCollisions(float dt) {
		Vec3[] accel = new Vec3[this.nodes.length];
		Vec3[] offset = new Vec3[this.nodes.length];
		for (int i = 0; i < this.nodes.length; i++) {
			accel[i] = new Vec3(0);
			offset[i] = new Vec3(0);
		}

		for (int _cur = 0; _cur < this.shapes.length; _cur++) {
			Shape s = this.shapes[_cur];

			for (int i : s.node_inds) {
				Node n = this.nodes[i];

				//ground
				if (n.pos.y < 0 && n.inv_mass != 0) {
					Vec3 coll_norm = new Vec3(0, 1, 0);
					Vec3 norm = coll_norm.mul(coll_norm.dot(n.vel));
					Vec3 tang = n.vel.sub(norm);

					Vec3 tang_norm = new Vec3(tang);
					tang_norm.normalize();
					tang_norm.muli(-1);

					float norm_vel = norm.length();
					float tang_vel = tang.length();

					float inv_mass_sum = n.inv_mass;
					float norm_scalar = (1.0f + options.coeffRestitution) * norm_vel / inv_mass_sum;

					float tang_scalar = tang_vel / inv_mass_sum;
					if (tang_scalar > norm_scalar * options.staticFriction) {
						tang_scalar = norm_scalar * options.dynamicFriction;
					}

					accel[i].addi(coll_norm.mul(norm_scalar));
					accel[i].addi(tang_norm.mul(tang_scalar));

					offset[i].addi(new Vec3(0, -n.pos.y, 0));
				}

				//node v. shape
				for (Shape other : this.shapes) {
					if (other == s) {
						continue;
					}

					//check if this node is inside this shape
					if (!other.isPointInside(n.pos)) {
						continue;
					}

					//find nearest point clamped to face, and force this node outside
					Face f = other.findClosestFace(n.pos);
					Vec3 a = this.nodes[f.a].pos;
					Vec3 b = this.nodes[f.b].pos;
					Vec3 c = this.nodes[f.c].pos;

					Vec3 coll_pos = MathUtils.point_triangleProjectClamped(n.pos, a, b, c);
					offset[i].addi(new Vec3(n.pos, coll_pos));

					//handle collision 
					Vec3 coll_norm = (b.sub(a)).cross(c.sub(a));
					coll_norm.normalize();

					Vec3 bary = MathUtils.barycentricCoords(a, b, c, coll_pos);

					Vec3 rel_vel = new Vec3(0);
					rel_vel.addi(this.nodes[f.a].vel.mul(bary.x));
					rel_vel.addi(this.nodes[f.b].vel.mul(bary.y));
					rel_vel.addi(this.nodes[f.c].vel.mul(bary.z));
					rel_vel.subi(n.vel);

					//treat collision like it's with two particles, then distribute forces at the end
					Vec3 norm = coll_norm.mul(coll_norm.dot(rel_vel));
					Vec3 tang = rel_vel.sub(norm);

					Vec3 tang_norm = new Vec3(tang);
					tang_norm.normalize();
					tang_norm.muli(-1);

					float norm_vel = MathUtils.dot(coll_norm, norm);
					float tang_vel = tang.length();

					if (norm_vel < 0) {
						//moving away, therefore not colliding
						continue;
					}

					//TODO fix in case where one node has infinite mass (eg. a fixed node). 
					float inv_mass_sum = n.inv_mass + 1.0f / (this.nodes[f.a].mass * bary.x + this.nodes[f.b].mass * bary.y + this.nodes[f.c].mass * bary.z);
					float norm_scalar = (1.0f + options.coeffRestitution) * norm_vel / inv_mass_sum;

					float tang_scalar = tang_vel / inv_mass_sum;
					if (tang_scalar > norm_scalar * options.staticFriction) {
						tang_scalar = norm_scalar * options.dynamicFriction;
					}

					tang_scalar *= -1;

					//apply forces
					accel[i].addi(coll_norm.mul(norm_scalar));
					accel[i].addi(tang_norm.mul(tang_scalar));
					accel[f.a].subi(coll_norm.mul(norm_scalar * bary.x));
					accel[f.a].subi(tang_norm.mul(tang_scalar * bary.x));
					accel[f.b].subi(coll_norm.mul(norm_scalar * bary.y));
					accel[f.b].subi(tang_norm.mul(tang_scalar * bary.y));
					accel[f.c].subi(coll_norm.mul(norm_scalar * bary.z));
					accel[f.c].subi(tang_norm.mul(tang_scalar * bary.z));
				}

			}
		}

		for (int i = 0; i < this.nodes.length; i++) {
			Node n = this.nodes[i];
			n.pos.addi(offset[i]);
			n.vel.addi(accel[i]);
		}
	}

	private Vec3[] calcAccel(Vec3[] cpos, Vec3[] cvel) {
		Vec3[] force = new Vec3[this.nodes.length];
		for (int i = 0; i < this.nodes.length; i++) {
			force[i] = new Vec3(0);
		}

		//springs
		for (int i = 0; i < this.springs.length; i++) {
			Spring s = this.springs[i];

			Vec3 ab = new Vec3(cpos[s.a], cpos[s.b]);
			float len = ab.length();
			float diff = len - s.rest_len;
			ab.normalize();

			//hooke's law
			force[s.a].addi(ab.mul(diff * options.springConstant));
			force[s.b].addi(ab.mul(-diff * options.springConstant));

			//damping
			float rel_vel = ab.dot(cvel[s.a]) - ab.dot(cvel[s.b]);
			force[s.a].addi(ab.mul(-rel_vel * options.dampingConstant));
			force[s.b].addi(ab.mul(rel_vel * options.dampingConstant));
		}

		//gravity
		for (int i = 0; i < this.nodes.length; i++) {
			Node n = this.nodes[i];
			force[i].addi(gravity.mul(n.mass));
		}

		//turn force into acceleration
		for (int i = 0; i < this.nodes.length; i++) {
			Node n = this.nodes[i];
			force[i].muli(n.inv_mass);
		}

		return force;
	}

	private void RK4Step(float dt) {
		Vec3[] k1p = new Vec3[this.nodes.length];
		Vec3[] k1v = new Vec3[this.nodes.length]; //we already know k1v
		for (int i = 0; i < this.nodes.length; i++) {
			k1p[i] = new Vec3(this.nodes[i].pos);
			k1v[i] = new Vec3(this.nodes[i].vel);
		}
		Vec3[] k1a = calcAccel(k1p, k1v);

		Vec3[] k2p = adds(k1p, k1v, dt / 2.0f);
		Vec3[] k2v = adds(k1v, k1a, dt / 2.0f);
		Vec3[] k2a = calcAccel(k2p, k2v);

		Vec3[] k3p = adds(k1p, k2v, dt / 2.0f);
		Vec3[] k3v = adds(k1v, k2a, dt / 2.0f);
		Vec3[] k3a = calcAccel(k3p, k3v);

		Vec3[] k4p = adds(k1p, k3v, dt);
		Vec3[] k4v = adds(k1v, k3a, dt);
		Vec3[] k4a = calcAccel(k4p, k4v);

		//aggregate results
		addsi(k1v, k2v, 2);
		addsi(k1v, k3v, 2);
		addsi(k1v, k4v, 1);
		muli(k1v, dt / 6.0f);

		addsi(k1a, k2a, 2);
		addsi(k1a, k3a, 2);
		addsi(k1a, k4a, 1);
		muli(k1a, dt / 6.0f);

		for (int i = 0; i < this.nodes.length; i++) {
			this.nodes[i].prev_pos.set(this.nodes[i].pos);
			this.nodes[i].pos.addi(k1v[i]);
			this.nodes[i].vel.addi(k1a[i]);
		}

		this.handleCollisions(dt);
	}

	private void eulerStep(float dt) {
		Vec3[] pos = new Vec3[this.nodes.length];
		Vec3[] vel = new Vec3[this.nodes.length];
		for (int i = 0; i < this.nodes.length; i++) {
			pos[i] = new Vec3(this.nodes[i].pos);
			vel[i] = new Vec3(this.nodes[i].vel);
		}
		Vec3[] acc = calcAccel(pos, vel);

		//integrate
		for (int i = 0; i < this.nodes.length; i++) {
			Node n = this.nodes[i];

			Vec3 n_pos = n.pos.add(n.vel.mul(dt));
			Vec3 n_vel = n.vel.add(acc[i].mul(dt));

			n.prev_pos.set(n.pos);
			n.pos.set(n_pos);
			n.vel.set(n_vel);
		}

		this.handleCollisions(dt);
	}

	private void XPBDStep(float dt) {
		// -- apply external forces
		for (int i = 0; i < this.nodes.length; i++) {
			this.nodes[i].vel.addi(this.gravity.mul(dt));
		}
		this.handleCollisions(dt);

		// -- update previous position and compute next pos
		for (int i = 0; i < this.nodes.length; i++) {
			Node n = this.nodes[i];
			n.prev_pos.set(n.pos);
			n.pos.addi(n.vel.mul(dt));
		}

		// -- solve all constraints
		Vec3[] offsets = new Vec3[this.nodes.length];
		int[] offset_cnt = new int[this.nodes.length];
		for (int i = 0; i < this.nodes.length; i++) {
			offsets[i] = new Vec3(0);
			offset_cnt[i] = 0;
		}
		for (int i = 0; i < this.springs.length; i++) {
			Spring s = this.springs[i];

			Node n1 = this.nodes[s.a];
			Node n2 = this.nodes[s.b];

			Vec3 x1 = new Vec3(n1.pos);
			Vec3 x2 = new Vec3(n2.pos);
			if (x1.equals(x2)) {
				x1.addi(MathUtils.randomUnitDir3D().mul(0.001f));
			}

			float dist = MathUtils.dist(x1, x2);

			Vec3 c1 = new Vec3(x2, x1).divi(dist);
			Vec3 c2 = new Vec3(x1, x2).divi(dist);
			c1.normalize();
			c2.normalize();

			float C = dist - s.rest_len;
			float lambda = -C / (n1.inv_mass + n2.inv_mass + options.compliance / (dt * dt));

			offsets[s.a].addi(c1.mul(lambda * n1.inv_mass));
			offsets[s.b].addi(c2.mul(lambda * n2.inv_mass));

			offset_cnt[s.a]++;
			offset_cnt[s.b]++;
		}
		for (int i = 0; i < this.volumes.length; i++) {
			Volume v = this.volumes[i];

			Node n1 = this.nodes[v.a];
			Node n2 = this.nodes[v.b];
			Node n3 = this.nodes[v.c];
			Node n4 = this.nodes[v.d];

			Vec3 x1 = n1.pos;
			Vec3 x2 = n2.pos;
			Vec3 x3 = n3.pos;
			Vec3 x4 = n4.pos;

			Vec3 c1 = MathUtils.cross(new Vec3(x2, x4), new Vec3(x2, x3));
			Vec3 c2 = MathUtils.cross(new Vec3(x1, x3), new Vec3(x1, x4));
			Vec3 c3 = MathUtils.cross(new Vec3(x1, x4), new Vec3(x1, x2));
			Vec3 c4 = MathUtils.cross(new Vec3(x1, x2), new Vec3(x1, x3));

			float C = 6.0f * (MathUtils.signedTetrahedronVolume(x1, x2, x3, x4) - v.rest_vol);
			float lambda = -C / (n1.inv_mass * c1.lengthSq() + n2.inv_mass * c2.lengthSq() + n3.inv_mass * c3.lengthSq() + n4.inv_mass * c4.lengthSq() + options.compliance / (dt * dt));

			offsets[v.a].addi(c1.mul(lambda * n1.inv_mass));
			offsets[v.b].addi(c2.mul(lambda * n2.inv_mass));
			offsets[v.c].addi(c3.mul(lambda * n3.inv_mass));
			offsets[v.d].addi(c4.mul(lambda * n4.inv_mass));

			offset_cnt[v.a]++;
			offset_cnt[v.b]++;
			offset_cnt[v.c]++;
			offset_cnt[v.d]++;
		}
		for (int i = 0; i < this.nodes.length; i++) {
			if (offset_cnt[i] == 0) {
				continue;
			}
			Node n = this.nodes[i];
			n.pos.addi(offsets[i].div(offset_cnt[i]));
		}

		// -- compute new velocity
		for (int i = 0; i < this.nodes.length; i++) {
			Node n = this.nodes[i];
			n.vel.set(n.pos.sub(n.prev_pos).div(dt));
		}
	}

	@Override
	protected void _update() {
		this.pic.update();

		this.timeDebt += Main.getDeltaSeconds();
		this.timeDebt = Math.min(0.5f, timeDebt);
		while (this.timeDebt > 0) {
			float dt = 1.0f / options.iterationsPerSecond;
			this.timeDebt -= dt;

			if (options.doEulerStep) {
				this.eulerStep(dt);
			}
			else if (options.doRK4Step) {
				this.RK4Step(dt);
			}
			else if (options.doXPBDStep) {
				this.XPBDStep(dt);
			}
		}

		//update node pos buffer
		{
			int[] pos_data = new int[4 * this.nodes.length];
			for (int i = 0; i < this.nodes.length; i++) {
				Vec3 pos = this.nodes[i].pos;
				pos_data[i * 4 + 0] = Float.floatToIntBits(pos.x);
				pos_data[i * 4 + 1] = Float.floatToIntBits(pos.y);
				pos_data[i * 4 + 2] = Float.floatToIntBits(pos.z);
			}
			this.nodePosBuffer.setSubData(pos_data, 0);
		}

		//update model instances
		for (Shape s : this.shapes) {
			s.updateModelInstances();
		}
		for (Skin s : this.skins) {
			s.updateModelInstances();
		}

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
		switch (key) {
		case GLFW.GLFW_KEY_R:
			this.resetState();
			break;
		}
	}

	@Override
	protected void _keyReleased(int key) {
		// TODO Auto-generated method stub

	}

	class Node {
		Vec3 pos, vel, prev_pos;
		float inv_mass, mass;

		public Node(Vec3 _pos, float _mass) {
			this.pos = new Vec3(_pos);
			this.vel = new Vec3(0);
			this.prev_pos = new Vec3(_pos);
			this.mass = _mass;
			this.inv_mass = 1.0f / _mass;
		}

		public Node(Node n) {
			this.pos = new Vec3(n.pos);
			this.vel = new Vec3(n.vel);
			this.prev_pos = new Vec3(n.prev_pos);
			this.mass = n.mass;
			this.inv_mass = n.inv_mass;
		}

		public void fix() {
			this.prev_pos = new Vec3(this.pos);
			this.inv_mass = 0;
			this.mass = 1;
		}
	}

	class Spring {
		int a, b;
		float rest_len;

		public Spring(int _a, int _b, float _rest_len) {
			this.a = _a;
			this.b = _b;
			this.rest_len = _rest_len;
		}

		public Spring(Spring s) {
			this.a = s.a;
			this.b = s.b;
			this.rest_len = s.rest_len;
		}
	}

	class Volume {
		//(d - a) * ((b - a) x (c - a)) > 0
		int a, b, c, d;
		float rest_vol;

		public Volume(int _a, int _b, int _c, int _d, float _rest_vol) {
			this.a = _a;
			this.b = _b;
			this.c = _c;
			this.d = _d;
			this.rest_vol = _rest_vol;
		}

		public Volume(Volume v) {
			this.a = v.a;
			this.b = v.b;
			this.c = v.c;
			this.d = v.d;
			this.rest_vol = v.rest_vol;
		}
	}

	class Shape {
		int[] node_inds;
		Face[] faces;
		Edge[] edges;
		ModelInstance[] face_instances;
		ModelInstance[] edge_instances;

		public Shape(ArrayList<Integer> node_ind_list, ArrayList<Face> face_list, ArrayList<Edge> edge_list) {
			this.node_inds = new int[node_ind_list.size()];
			this.faces = new Face[face_list.size()];
			this.edges = new Edge[edge_list.size()];
			for (int i = 0; i < node_ind_list.size(); i++) {
				this.node_inds[i] = node_ind_list.get(i);
			}
			for (int i = 0; i < face_list.size(); i++) {
				this.faces[i] = new Face(face_list.get(i));
			}
			for (int i = 0; i < edge_list.size(); i++) {
				this.edges[i] = new Edge(edge_list.get(i));
			}

			this.face_instances = new ModelInstance[0];
			this.edge_instances = new ModelInstance[0];
		}

		public Shape(Shape s) {
			this.node_inds = new int[s.node_inds.length];
			this.faces = new Face[s.faces.length];
			this.edges = new Edge[s.edges.length];
			for (int i = 0; i < this.node_inds.length; i++) {
				this.node_inds[i] = s.node_inds[i];
			}
			for (int i = 0; i < this.faces.length; i++) {
				this.faces[i] = new Face(s.faces[i]);
			}
			for (int i = 0; i < this.edges.length; i++) {
				this.edges[i] = new Edge(s.edges[i]);
			}
		}

		//only call after all shapes are loaded. 
		public void init() {
			if (options.renderFaces) {
				this.face_instances = new ModelInstance[this.faces.length];
				for (int i = 0; i < this.faces.length; i++) {
					Face f = this.faces[i];
					Vec3 a = nodes[f.a].pos;
					Vec3 b = nodes[f.b].pos;
					Vec3 c = nodes[f.c].pos;

					this.face_instances[i] = Triangle.addTriangle(a, b, c, WORLD_SCENE);
					Material m = new Material(Color.RED);
					m.setSpecular(new Vec3(0.5));
					this.face_instances[i].setMaterial(m);
				}
			}

			if (options.renderEdges) {
				this.edge_instances = new ModelInstance[this.edges.length];
				for (int i = 0; i < this.edges.length; i++) {
					Edge e = this.edges[i];
					Vec3 a = nodes[e.a].pos;
					Vec3 b = nodes[e.b].pos;

					this.edge_instances[i] = Line.addDefaultLine(a, b, WORLD_SCENE);
					this.edge_instances[i].setMaterial(new Material(Color.BLACK));
				}
			}
		}

		//just cast ray and see if it collides an odd amount of times
		public boolean isPointInside(Vec3 pt) {
			Vec3 dir = MathUtils.randomUnitDir3D();
			int cnt = 0;
			for (Face f : this.faces) {
				cnt += MathUtils.ray_triangleIntersect(pt, dir, nodes[f.a].pos, nodes[f.b].pos, nodes[f.c].pos) != null ? 1 : 0;
			}
			return cnt % 2 == 1;
		}

		public Face findClosestFace(Vec3 pt) {
			Face ans = this.faces[0];
			float best_dist = (float) 1e18;
			for (Face f : this.faces) {
				Vec3 cans = MathUtils.point_triangleProjectClamped(pt, nodes[f.a].pos, nodes[f.b].pos, nodes[f.c].pos);
				float cdist = MathUtils.dist(cans, pt);
				if (cdist < best_dist) {
					best_dist = cdist;
					ans = f;
				}
			}
			return ans;
		}

		public Edge findClosestEdge(Vec3 pt) {
			Edge ans = this.edges[0];
			float best_dist = (float) 1e18;
			for (Edge e : this.edges) {
				float cdist = MathUtils.point_lineSegmentDistance(pt, nodes[e.a].pos, nodes[e.b].pos);
				if (cdist < best_dist) {
					best_dist = cdist;
					ans = e;
				}
			}
			return ans;
		}

		public void updateModelInstances() {
			for (int i = 0; i < this.face_instances.length; i++) {
				Face f = this.faces[i];
				Vec3 a = nodes[f.a].pos;
				Vec3 b = nodes[f.b].pos;
				Vec3 c = nodes[f.c].pos;
				ModelTransform transform = Triangle.generateTriangleModelTransform(a, b, c);
				this.face_instances[i].setModelTransform(transform);
			}

			for (int i = 0; i < this.edge_instances.length; i++) {
				Edge e = this.edges[i];
				Vec3 a = nodes[e.a].pos;
				Vec3 b = nodes[e.b].pos;
				ModelTransform transform = Line.generateLineModelTransform(a, b);
				this.edge_instances[i].setModelTransform(transform);
			}
		}

		public void kill() {
			for (int i = 0; i < this.face_instances.length; i++) {
				this.face_instances[i].kill();
			}

			for (int i = 0; i < this.edge_instances.length; i++) {
				this.edge_instances[i].kill();
			}
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

	class Edge {
		int a, b;

		public Edge(int _a, int _b) {
			this.a = _a;
			this.b = _b;
		}

		public Edge(Edge e) {
			this.a = e.a;
			this.b = e.b;
		}
	}

	class Skin {
		int[] tet_inds;
		Vec4[] bary_coords;

		ShaderStorageBuffer tetBuffer, baryBuffer;
		ShaderStorageBuffer faceListPtrBuffer, faceListBuffer, faceBuffer;
		VertexArray va;
		ModelInstance instance;

		public Skin(int[] _tet_inds, Vec4[] _bary_coords, Model model) {
			this.tet_inds = new int[_tet_inds.length];
			this.bary_coords = new Vec4[_tet_inds.length];
			for (int i = 0; i < _tet_inds.length; i++) {
				this.tet_inds[i] = _tet_inds[i];
				this.bary_coords[i] = new Vec4(_bary_coords[i]);
			}

			this.instance = new ModelInstance(model, WORLD_SCENE);
			Material m = new Material(Color.CYAN);
			m.setSpecular(new Vec3(0.5));
			this.instance.setMaterial(m);
			this.va = model.getMeshes().get(0);

		}

		public void init() {
			int[] tet_data = new int[this.tet_inds.length * 4];
			int[] bary_data = new int[this.tet_inds.length * 4];
			for (int i = 0; i < this.tet_inds.length; i++) {
				Volume v = volumes[this.tet_inds[i]];
				tet_data[i * 4 + 0] = v.a;
				tet_data[i * 4 + 1] = v.b;
				tet_data[i * 4 + 2] = v.c;
				tet_data[i * 4 + 3] = v.d;
				bary_data[i * 4 + 0] = Float.floatToIntBits(this.bary_coords[i].x);
				bary_data[i * 4 + 1] = Float.floatToIntBits(this.bary_coords[i].y);
				bary_data[i * 4 + 2] = Float.floatToIntBits(this.bary_coords[i].z);
				bary_data[i * 4 + 3] = Float.floatToIntBits(this.bary_coords[i].w);
			}

			this.tetBuffer = new ShaderStorageBuffer();
			this.tetBuffer.setSize(tet_data.length * 4);
			this.tetBuffer.setUsage(GL_STATIC_READ);
			this.tetBuffer.setSubData(tet_data, 0);

			this.baryBuffer = new ShaderStorageBuffer();
			this.baryBuffer.setSize(tet_data.length * 4);
			this.baryBuffer.setUsage(GL_STATIC_READ);
			this.baryBuffer.setSubData(bary_data, 0);

			//compute some lookup tables for normal updates
			int vcnt = this.va.getVertices().length / 3;
			ArrayList<Integer>[] vertex_faces = new ArrayList[vcnt];
			for (int i = 0; i < vcnt; i++) {
				vertex_faces[i] = new ArrayList<Integer>();
			}
			for (int i = 0; i < this.va.getIndices().length; i += 3) {
				vertex_faces[this.va.getIndices()[i + 0]].add(i);
				vertex_faces[this.va.getIndices()[i + 1]].add(i);
				vertex_faces[this.va.getIndices()[i + 2]].add(i);
			}
			int[] list_ptr_data = new int[vcnt * 2];
			int[] list_data = new int[this.va.getIndices().length];
			int offset_ptr = 0;
			for (int i = 0; i < vcnt; i++) {
				list_ptr_data[i * 2 + 0] = offset_ptr;
				list_ptr_data[i * 2 + 1] = vertex_faces[i].size();
				for (int j = 0; j < vertex_faces[i].size(); j++) {
					list_data[offset_ptr++] = vertex_faces[i].get(j);
				}
			}
			int[] face_data = new int[this.va.getIndices().length];
			for (int i = 0; i < this.va.getIndices().length; i++) {
				face_data[i] = this.va.getIndices()[i];
			}

			this.faceListPtrBuffer = new ShaderStorageBuffer();
			this.faceListPtrBuffer.setUsage(GL_STATIC_READ);
			this.faceListPtrBuffer.setData(list_ptr_data);

			this.faceListBuffer = new ShaderStorageBuffer();
			this.faceListBuffer.setUsage(GL_STATIC_READ);
			this.faceListBuffer.setData(list_data);

			this.faceBuffer = new ShaderStorageBuffer();
			this.faceBuffer.setUsage(GL_STATIC_READ);
			this.faceBuffer.setData(face_data);
		}

		public void updateModelInstances() {
			// -- update vertex positions
			{
				nodePosBuffer.bindToBase(0);
				this.tetBuffer.bindToBase(1);
				this.baryBuffer.bindToBase(2);
				int vbo = this.va.getVBO();
				glBindBuffer(GL_SHADER_STORAGE_BUFFER, vbo);
				glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 3, vbo);

				updateSkinVertexShader.enable();
				glDispatchCompute(this.tet_inds.length, 1, 1);
				glMemoryBarrier(GL_SHADER_STORAGE_BARRIER_BIT);
			}

			// -- update normals
			if (true) {
				int vbo = this.va.getVBO();
				glBindBuffer(GL_SHADER_STORAGE_BUFFER, vbo);
				glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 0, vbo);
				this.faceListPtrBuffer.bindToBase(1);
				this.faceListBuffer.bindToBase(2);
				this.faceBuffer.bindToBase(3);
				int nbo = this.va.getNBO();
				glBindBuffer(GL_SHADER_STORAGE_BUFFER, nbo);
				glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 4, nbo);
				int ntbo = this.va.getNTBO();
				glBindBuffer(GL_SHADER_STORAGE_BUFFER, ntbo);
				glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 5, ntbo);
				int nbtbo = this.va.getNBTBO();
				glBindBuffer(GL_SHADER_STORAGE_BUFFER, nbtbo);
				glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 6, nbtbo);

				updateSkinNormalShader.enable();
				glDispatchCompute(this.tet_inds.length, 1, 1);
				glMemoryBarrier(GL_SHADER_STORAGE_BARRIER_BIT);
			}
		}

		public void kill() {
			this.tetBuffer.kill();
			this.baryBuffer.kill();
			this.faceListPtrBuffer.kill();
			this.faceListBuffer.kill();
			this.faceBuffer.kill();
			this.va.kill();
		}
	}

}
