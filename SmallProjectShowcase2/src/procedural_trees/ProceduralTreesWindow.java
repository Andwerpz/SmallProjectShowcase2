package procedural_trees;

import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;
import static org.lwjgl.opengl.GL31.*;
import static org.lwjgl.opengl.GL33.*;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashSet;

import org.lwjgl.glfw.GLFW;

import lwjglengine.graphics.Cubemap;
import lwjglengine.graphics.Framebuffer;
import lwjglengine.graphics.Material;
import lwjglengine.model.Line;
import lwjglengine.model.Model;
import lwjglengine.model.ModelInstance;
import lwjglengine.model.ModelTransform;
import lwjglengine.model.VertexArray;
import lwjglengine.player.Camera;
import lwjglengine.scene.DirLight;
import lwjglengine.scene.Light;
import lwjglengine.scene.Scene;
import lwjglengine.screen.PerspectiveScreen;
import lwjglengine.window.Window;
import myutils.file.FileUtils;
import myutils.math.Mat4;
import myutils.math.MathUtils;
import myutils.math.Quaternion;
import myutils.math.Vec2;
import myutils.math.Vec3;

public class ProceduralTreesWindow extends Window {

	private final int WORLD_SCENE = Scene.generateScene();
	private PerspectiveScreen perspectiveScreen;

	private float cameraDist = 10;
	private float cameraXRot = (float) (Math.PI / 8.0);
	private float cameraYRot = 0;
	private Vec3 cameraFacing = new Vec3(0, 0, -1).rotateX(this.cameraXRot).rotateY(this.cameraYRot);
	private Vec3 cameraCenter = new Vec3(0, 5, 0);

	private Vec2 mousePos;

	private boolean mousePressed = false;

	private Tree tree;

	private Model groundModel;

	public ProceduralTreesWindow(int xOffset, int yOffset, int width, int height, Window parentWindow) {
		super(xOffset, yOffset, width, height, parentWindow);
		this.init();
	}

	private void init() {
		this.setLockCursorOnSelect(true);
		this.setDeselectOnEscPressed(true);
		this.setUnlockCursorOnEscPressed(true);

		this.perspectiveScreen = new PerspectiveScreen();
		this.perspectiveScreen.renderSkybox(true);

		DirLight sun = new DirLight(new Vec3(-1), new Vec3(1), 0.6f);
		Light.addLight(WORLD_SCENE, sun);

		BufferedImage[] skyboxSides = new BufferedImage[6];
		String skyboxDir = "/res/skybox/lake/";
		for (int i = 0; i < 6; i++) {
			skyboxSides[i] = FileUtils.loadImageRelative(skyboxDir + Cubemap.CUBEMAP_SIDE_NAMES[i] + ".jpg");
		}
		Cubemap skybox = new Cubemap(skyboxSides);
		Scene.skyboxes.put(WORLD_SCENE, skybox);

		this.tree = new Tree(new Vec3(0, 0, 0), new Vec3(0, 1, 0));

		this.mousePos = this.getWindowMousePos();

		this.groundModel = createGroundModel();
		Material groundMaterial = new Material(Color.WHITE);
		groundMaterial.setSpecular(new Vec3(0));
		ModelInstance groundModelInstance = new ModelInstance(this.groundModel, WORLD_SCENE);
		groundModelInstance.setMaterial(groundMaterial);

		this._resize();
	}

	private static Model createGroundModel() {
		ArrayList<Float> vertices = new ArrayList<>();
		ArrayList<Float> uvs = new ArrayList<>();
		ArrayList<Integer> indices = new ArrayList<>();

		vertices.add((float) -100);
		vertices.add((float) 0);
		vertices.add((float) -100);

		vertices.add((float) 100);
		vertices.add((float) 0);
		vertices.add((float) -100);

		vertices.add((float) 100);
		vertices.add((float) 0);
		vertices.add((float) 100);

		vertices.add((float) -100);
		vertices.add((float) 0);
		vertices.add((float) 100);

		uvs.add((float) 0);
		uvs.add((float) 0);

		uvs.add((float) 1);
		uvs.add((float) 0);

		uvs.add((float) 1);
		uvs.add((float) 1);

		uvs.add((float) 0);
		uvs.add((float) 1);

		indices.add(0);
		indices.add(2);
		indices.add(1);

		indices.add(0);
		indices.add(3);
		indices.add(2);

		VertexArray va = new VertexArray(vertices, uvs, indices, GL_TRIANGLES);
		return new Model(va);
	}

	@Override
	protected void _kill() {
		this.tree.kill();

		this.groundModel.kill();

		this.perspectiveScreen.kill();
		Scene.removeScene(WORLD_SCENE);
	}

	@Override
	protected void _resize() {
		this.perspectiveScreen.setScreenDimensions(this.getWidth(), this.getHeight());
	}

	@Override
	public String getDefaultTitle() {
		return "Procedural Trees";
	}

	@Override
	protected void _update() {
		{
			Vec2 nextMouse = this.getWindowMousePos();
			float dx = nextMouse.x - this.mousePos.x;
			float dy = nextMouse.y - this.mousePos.y;

			if (this.mousePressed) {
				this.cameraYRot += dx * 0.01f;
				this.cameraXRot -= dy * 0.01f;

				this.cameraXRot = (float) MathUtils.clamp(-Math.PI / 2.0 + 0.01f, Math.PI / 2.0 - 0.01f, this.cameraXRot);

				this.cameraFacing = new Vec3(0, 0, -1).rotateX(this.cameraXRot).rotateY(this.cameraYRot);
			}

			this.mousePos.set(nextMouse);
		}

		this.tree.update();

		//update camera pos
		Camera camera = this.perspectiveScreen.getCamera();
		camera.setFacing(this.cameraFacing);
		camera.setPos(this.cameraCenter.add(this.cameraFacing.mul(-this.cameraDist)));
	}

	@Override
	protected void renderContent(Framebuffer outputBuffer) {
		this.perspectiveScreen.setWorldScene(WORLD_SCENE);
		this.perspectiveScreen.render(outputBuffer);
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
		this.mousePressed = true;
	}

	@Override
	protected void _mouseReleased(int button) {
		this.mousePressed = false;
	}

	@Override
	protected void _mouseScrolled(float wheelOffset, float smoothOffset) {
		this.cameraDist += smoothOffset * 2.5f;
	}

	@Override
	protected void _keyPressed(int key) {
		switch (key) {
		case GLFW.GLFW_KEY_R: {
			this.tree.kill();
			this.tree = new Tree(new Vec3(0, 0, 0), new Vec3(0, 1, 0));
			break;
		}
		}
	}

	@Override
	protected void _keyReleased(int key) {
		// TODO Auto-generated method stub

	}

	class Tree {
		public Branch root;
		public ArrayList<Branch> branches;

		public float baseSplitLength = 2;
		public float splitRatio = 0.6f;
		public float spread = 1f;
		public float directedness = 1f;

		public Model cylinderModel;
		public float taper = Math.max(splitRatio, 1.0f - splitRatio); //how much narrower the top of the cylinder is compared to the bottom

		public int nrGrowthIterationsLeft = 100;
		public float feedAmt = 0.05f;

		public Model leafModel;

		public Tree(Vec3 rootPos, Vec3 dir) {
			this.cylinderModel = this.createCylinderModel(10);

			this.branches = new ArrayList<Branch>();
			this.root = new Branch(rootPos, dir);
		}

		public Model createLeafModel() {
			//just a cube for now

			return null;
		}

		public Model createCylinderModel(int nrPoints) {
			ArrayList<Vec2> circle = new ArrayList<>();
			for (int i = 0; i < nrPoints; i++) {
				float ang = (float) (Math.PI * 2.0f / nrPoints) * i;
				Vec2 pt = new Vec2(Math.cos(ang), Math.sin(ang));
				circle.add(pt);
			}

			int capNrTriangles = nrPoints - 2;

			ArrayList<Float> vertices = new ArrayList<>();
			ArrayList<Float> uvs = new ArrayList<>();
			ArrayList<Integer> indices = new ArrayList<>();
			//bottom cap
			for (int i = 0; i < nrPoints; i++) {
				vertices.add(circle.get(i).x);
				vertices.add(0f);
				vertices.add(circle.get(i).y);

				uvs.add((float) Math.random());
				uvs.add((float) Math.random());
			}
			for (int i = 0; i < capNrTriangles; i++) {
				indices.add(0);
				indices.add(i + 1);
				indices.add(i + 2);
			}
			//top cap
			for (int i = 0; i < nrPoints; i++) {
				vertices.add(circle.get(i).x * this.taper);
				vertices.add(1f);
				vertices.add(circle.get(i).y * this.taper);

				uvs.add((float) Math.random());
				uvs.add((float) Math.random());
			}
			for (int i = 0; i < nrPoints - 2; i++) {
				indices.add(nrPoints + 0);
				indices.add(nrPoints + i + 2);
				indices.add(nrPoints + i + 1);
			}

			//sides
			for (int i = 0; i < nrPoints; i++) {
				vertices.add(circle.get(i).x);
				vertices.add(0f);
				vertices.add(circle.get(i).y);

				uvs.add((float) Math.random());
				uvs.add((float) Math.random());
			}
			for (int i = 0; i < nrPoints; i++) {
				vertices.add(circle.get(i).x * this.taper);
				vertices.add(1f);
				vertices.add(circle.get(i).y * this.taper);

				uvs.add((float) Math.random());
				uvs.add((float) Math.random());
			}
			for (int i = 0; i < nrPoints; i++) {
				indices.add(nrPoints * 2 + i);
				indices.add(nrPoints * 2 + nrPoints + i);
				indices.add(nrPoints * 2 + (i + 1) % nrPoints);

				indices.add(nrPoints * 2 + (i + 1) % nrPoints);
				indices.add(nrPoints * 2 + nrPoints + i);
				indices.add(nrPoints * 2 + nrPoints + (i + 1) % nrPoints);
			}

			VertexArray va = new VertexArray(vertices, uvs, indices, GL_TRIANGLES);
			return new Model(va);
		}

		public void update() {
			if (this.nrGrowthIterationsLeft != 0) {
				this.nrGrowthIterationsLeft--;
				this.root.grow(this.feedAmt);
			}
		}

		public void kill() {
			this.root.kill();

			this.cylinderModel.kill();
		}

		//returns the average direction from this position to leaf nodes
		private Vec3 avgLeafDir(Branch branch) {
			if (branch == this.root) {
				Vec3 randomVec = new Vec3((float) Math.random(), (float) Math.random(), (float) Math.random()).sub(new Vec3(0.5f)).normalize();
				return randomVec;
			}

			Vec3 avgLeafDir = new Vec3(0);
			Vec3 endPos = branch.getEndPos();

			for (Branch b : this.branches) {
				if (b.isLeaf) {
					Vec3 toLeaf = new Vec3(endPos, b.getEndPos()).normalize();
					Vec3 randomVec = new Vec3((float) Math.random(), (float) Math.random(), (float) Math.random()).sub(new Vec3(0.5f)).normalize();
					toLeaf.muli(directedness);
					randomVec.muli(1.0f - directedness);
					avgLeafDir.addi(toLeaf);
					avgLeafDir.addi(randomVec);
				}
			}

			avgLeafDir.normalize();
			return avgLeafDir;
		}

		class Branch {
			public Branch parent;
			public Branch childA, childB;
			public boolean isLeaf;
			public int depth; //length of shortest path to root

			public Vec3 pos, dir;
			public float length, area;

			private ModelInstance branchModelInstance;
			private ModelInstance lineModelInstance;

			public Branch(Branch parent, Vec3 pos, Vec3 dir) {
				this.init();

				this.parent = parent;
				this.depth = this.parent.depth + 1;

				this.pos.set(pos);
				this.dir.set(dir);

				this.updateModel();
			}

			public Branch(Vec3 pos, Vec3 dir) {
				this.init();

				this.pos.set(pos);
				this.dir.set(dir);

				this.updateModel();
			}

			private void init() {
				this.pos = new Vec3(0);
				this.dir = new Vec3(0, 1, 0);

				this.length = 0f;
				this.area = 0.1f;

				this.parent = null;
				this.childA = null;
				this.childB = null;
				this.isLeaf = true;
				this.depth = 0;

				this.branchModelInstance = null;
				this.lineModelInstance = null;

				branches.add(this);
			}

			private void updateModel() {
				if (this.branchModelInstance == null) {
					this.branchModelInstance = new ModelInstance(cylinderModel, WORLD_SCENE);
					this.lineModelInstance = Line.addDefaultLine(this.pos, this.getEndPos(), WORLD_SCENE);
				}

				float radius = (float) Math.sqrt(this.area / Math.PI);

				if (length < 1e-5) {
					radius = 0;
				}

				Mat4 transformMat4 = Mat4.identity();
				transformMat4.muli(Mat4.scale(radius, this.length, radius));
				transformMat4.muli(Mat4.rotateAToB(new Vec3(0, 1, 0), this.dir));
				transformMat4.muli(Mat4.translate(this.pos));
				ModelTransform transform = new ModelTransform(transformMat4);
				this.branchModelInstance.setModelTransform(transform);

				this.lineModelInstance.setModelTransform(Line.generateLineModelTransform(this.pos, this.getEndPos()));
			}

			public void grow(float feed) {
				if (this.isLeaf) {
					//grow in length
					float lengthInc = (float) Math.cbrt(feed);
					this.length += lengthInc;
					feed -= lengthInc * this.area;

					//grow in area
					this.area += feed / this.length;

					//check if is long enough to split
					float splitLength = (float) (baseSplitLength / Math.cbrt(this.depth + 1));
					if (this.length > splitLength) {
						this.split();
					}
				}
				else {
					//figure out how much feed to pass to children
					float childAreaSum = this.childA.area + this.childB.area;
					float passRatio = childAreaSum / (childAreaSum + this.area); //percentage of feed to grow yourself

					//grow yourself
					this.area += feed * passRatio / this.length;
					feed -= feed * passRatio;

					//grow children
					this.childA.grow(feed * splitRatio);
					this.childB.grow(feed * (1.0f - splitRatio));
				}

				this.updateModel();
			}

			public void split() {
				this.isLeaf = false;

				Vec3 densityDir = avgLeafDir(this);

				Vec3 norm = this.dir.cross(densityDir);
				Vec3 rnorm = norm.mul(-1);

				if (Math.random() < 0.5) {
					norm.muli(-1);
					rnorm.muli(-1);
				}

				norm.muli(spread);
				rnorm.muli(spread);

				Vec3 adir = MathUtils.lerp(norm, 0, this.dir, 1, splitRatio).normalize();
				Vec3 bdir = MathUtils.lerp(rnorm, 0, this.dir, 1, 1.0f - splitRatio).normalize();

				Vec3 childStartPos = this.getEndPos().sub(this.dir.mul(this.length * 0f));

				this.childA = new Branch(this, childStartPos, adir);
				this.childB = new Branch(this, childStartPos, bdir);
			}

			public Vec3 getEndPos() {
				return this.pos.add(this.dir.mul(this.length));
			}

			public void kill() {
				this.branchModelInstance.kill();
				this.lineModelInstance.kill();

				if (!this.isLeaf) {
					this.childA.kill();
					this.childB.kill();
				}
			}
		}
	}

}
