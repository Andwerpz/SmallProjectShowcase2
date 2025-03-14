package animation;

import static org.lwjgl.assimp.Assimp.aiImportFile;
import static org.lwjgl.assimp.Assimp.aiProcess_JoinIdenticalVertices;
import static org.lwjgl.assimp.Assimp.aiProcess_Triangulate;

import java.awt.Color;
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
import org.lwjgl.glfw.GLFW;

import lwjglengine.animation.AnimatedModel;
import lwjglengine.animation.AnimatedModelInstance;
import lwjglengine.animation.AnimationHandler;
import lwjglengine.graphics.Cubemap;
import lwjglengine.graphics.Framebuffer;
import lwjglengine.graphics.Material;
import lwjglengine.model.Model;
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
	
	private AnimatedModel vampire;
	private AnimatedModelInstance vampireInst;

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

		Light sun = new DirLight(new Vec3(1, -1, -1), new Vec3(1), 0.3f);
		Light.addLight(WORLD_SCENE, sun);

		this.pic = new PlayerInputController(new Vec3(0, 125, 200));
		this.pic.setAcceptPlayerInputs(false);
		
		try {
//			this.vampire = AnimatedModel.loadAnimatedModelFileRelative("/res/dancing_vampire/dancing_vampire.dae");
			this.vampire = AnimatedModel.loadAnimatedModelFileRelative("/res/eremite/eremite.dae");
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		this.vampireInst = new AnimatedModelInstance(this.vampire, WORLD_SCENE);
		AnimationHandler ah = this.vampireInst.getAnimationHandler();
		ah.playAnimation(0);
		ah.setDoLooping(true);
		
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
		this.vampireInst.getAnimationHandler().update();
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
		AnimationHandler ah = this.vampireInst.getAnimationHandler();
		switch(key) {
		case GLFW.GLFW_KEY_Z: 
			if(ah.isPlayingAnimation()) {
				ah.stopAnimation();
			}
			else {
				ah.playAnimation(0);
			}
			break;
			
		case GLFW.GLFW_KEY_X:
			ah.setRenderSkeleton(!ah.getRenderSkeleton());
			break;
			
		case GLFW.GLFW_KEY_C:
			ah.setApplyAnimationToDefaultPose(!ah.getApplyAnimationToDefaultPose());
			break;
		}
	}

	@Override
	protected void _keyReleased(int key) {
		// TODO Auto-generated method stub
		
	}
}
