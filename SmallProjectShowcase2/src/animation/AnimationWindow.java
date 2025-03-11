package animation;

import static org.lwjgl.assimp.Assimp.aiImportFile;
import static org.lwjgl.assimp.Assimp.aiProcess_JoinIdenticalVertices;
import static org.lwjgl.assimp.Assimp.aiProcess_Triangulate;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Queue;

import org.lwjgl.PointerBuffer;
import org.lwjgl.assimp.AIAnimation;
import org.lwjgl.assimp.AIBone;
import org.lwjgl.assimp.AIMesh;
import org.lwjgl.assimp.AINode;
import org.lwjgl.assimp.AINodeAnim;
import org.lwjgl.assimp.AIQuatKey;
import org.lwjgl.assimp.AIQuaternion;
import org.lwjgl.assimp.AIScene;
import org.lwjgl.assimp.AIVector3D;
import org.lwjgl.assimp.AIVector3D.Buffer;
import org.lwjgl.assimp.AIVectorKey;
import org.lwjgl.assimp.AIVertexWeight;

import lwjglengine.graphics.Cubemap;
import lwjglengine.graphics.Framebuffer;
import lwjglengine.model.Model;
import lwjglengine.model.ModelInstance;
import lwjglengine.player.Camera;
import lwjglengine.player.PlayerInputController;
import lwjglengine.scene.DirLight;
import lwjglengine.scene.Light;
import lwjglengine.scene.Scene;
import lwjglengine.screen.PerspectiveScreen;
import lwjglengine.window.Window;
import myutils.file.FileUtils;
import myutils.math.Quaternion;
import myutils.math.Vec3;
import myutils.misc.Pair;

public class AnimationWindow extends Window {
	//Bones represent the influence of nodes on a mesh. Bones are per-mesh, while nodes are per-model.
	//First, compute the node positions, then vertices in the mesh will lookup these node positions.
	
	//first compute node positions. This is done on CPU?
	//then, in vertex shader, each vertex is going to look up the positions of the bones (nodes) it's influenced by
	//each vertex knows what bones influence it (we'll take the most influential 4)
	//fragment shader works like normal
	
	//each bone should have a matching AINode, and AINodes have some sort of hierarchy. 
	
	private final int WORLD_SCENE = Scene.generateScene();
	private PerspectiveScreen perspective;
	private PlayerInputController pic;
	
	private Model vampire;
	private AnimationHandler animationHandler;

	public AnimationWindow(int xOffset, int yOffset, int width, int height, Window parentWindow) {
		super(xOffset, yOffset, width, height, parentWindow);
		this.init();
	}
	
	private void init() {
		this.setLockCursorOnSelect(true);
		this.setUnlockCursorOnEscPressed(true);
		this.setDeselectOnEscPressed(true);
		
		this.perspective = new PerspectiveScreen();
		this.perspective.setWorldScene(WORLD_SCENE);
		this.perspective.renderDecals(false);
		this.perspective.renderParticles(false);
		this.perspective.renderPlayermodel(false);
		this.perspective.renderSkybox(true);
		
		BufferedImage[] skyboxSides = new BufferedImage[6];
		String skyboxDir = "/res/skybox/lake/";
		for (int i = 0; i < 6; i++) {
			skyboxSides[i] = FileUtils.loadImageRelative(skyboxDir + Cubemap.CUBEMAP_SIDE_NAMES[i] + ".jpg");
		}
		Cubemap skybox = new Cubemap(skyboxSides);
		Scene.skyboxes.put(WORLD_SCENE, skybox);

		Light sun = new DirLight(new Vec3(1), new Vec3(1), 0.3f);
		Light.addLight(WORLD_SCENE, sun);

		this.pic = new PlayerInputController(new Vec3(0));
		this.pic.setAcceptPlayerInputs(false);
		
//		try {
//			this.vampire = Model.loadModelFileRelative("/res/dancing_vampire/dancing_vampire.dae");
//			ModelInstance v = new ModelInstance(this.vampire, WORLD_SCENE);
//			
//		} catch (IOException e) {
//			// TODO Auto-generated catch block
//			e.printStackTrace();
//		}
		
		File file = FileUtils.loadFileRelative("/res/dancing_vampire/dancing_vampire.dae");
		String filepath = file.getAbsolutePath();
		String parentFilepath = file.getParent() + "\\";
		
		AIScene scene = aiImportFile(filepath, aiProcess_Triangulate | aiProcess_JoinIdenticalVertices);
		this.animationHandler = new AnimationHandler(scene, WORLD_SCENE);
		this.animationHandler.setDoLooping(true);
		this.animationHandler.setRenderSkeleton(true);
		this.animationHandler.playAnimation(0);
		
//		PointerBuffer meshes = scene.mMeshes();
//		AIMesh mesh = AIMesh.create(meshes.get(0));
//		PointerBuffer aibones = mesh.mBones();
//		this.bones = new Bone[aibones.limit()];
//		for(int i = 0; i < aibones.limit(); i++) {
//			AIBone aibone = AIBone.create(aibones.get(i));
//			this.bones[i] = new Bone(aibone);
//		}
		
		this._resize();
	}

	@Override
	protected void _kill() {
		Scene.removeScene(WORLD_SCENE);
		this.perspective.kill();
		this.vampire.kill();
	}

	@Override
	protected void _resize() {
		this.perspective.setScreenDimensions(this.getWidth(), this.getHeight());
	}

	@Override
	public String getDefaultTitle() {
		return "Animation";
	}

	@Override
	protected void _update() {
		this.pic.update();
		this.animationHandler.update();
	}

	@Override
	protected void renderContent(Framebuffer outputBuffer) {
		Camera camera = new Camera((float) Math.toRadians(90), this.getWidth(), this.getHeight(), 0.1f, 400);
		camera.setPos(this.pic.getTop());
		camera.setFacing(this.pic.getFacing());
		this.perspective.setCamera(camera);
		this.perspective.render(outputBuffer);
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
}
