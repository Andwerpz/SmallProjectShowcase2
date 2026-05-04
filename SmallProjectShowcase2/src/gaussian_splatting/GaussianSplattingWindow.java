package gaussian_splatting;

import static org.lwjgl.glfw.GLFW.GLFW_KEY_L;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_R;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_S;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_T;
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
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

import org.lwjgl.glfw.GLFW;

import lwjglengine.impulse3d.ImpulseScene;
import lwjglengine.impulse3d.Body;
import lwjglengine.graphics.Cubemap;
import lwjglengine.graphics.Framebuffer;
import lwjglengine.graphics.Material;
import lwjglengine.model.Cube;
import lwjglengine.model.CubeSphere;
import lwjglengine.model.Cylinder;
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
import lwjglengine.window.AdjustableWindow;
import lwjglengine.window.FileCreatorWindow;
import lwjglengine.window.FileSelectorWindow;
import lwjglengine.window.FileSelectorWindow.FileSelectorCallback;
import lwjglengine.window.ObjectEditorWindow;
import lwjglengine.window.Window;
import myutils.file.FileUtils;
import myutils.math.Mat3;
import myutils.math.Mat4;
import myutils.math.MathUtils;
import myutils.math.Quaternion;
import myutils.math.Vec2;
import myutils.math.Vec3;
import myutils.file.ply.PLYReader;

public class GaussianSplattingWindow extends Window implements FileSelectorCallback {

	//holy moly, gaussian splatting

	private final int WORLD_SCENE = Scene.generateScene();
	
	private GaussianSplattingScreen screen;
	private PlayerInputController pic;

	private Options options;
	
	public GaussianSplattingWindow(int xOffset, int yOffset, int width, int height, Window parentWindow) {
		super(xOffset, yOffset, width, height, parentWindow);
		this.init();
	}

	private void init() {
		this.setLockCursorOnSelect(true);
		this.setDeselectOnEscPressed(true);
		this.setUnlockCursorOnEscPressed(true);

		//options menu
		{
			this.options = new Options();
			AdjustableWindow adj = new AdjustableWindow("Gaussian Splatting Options", new ObjectEditorWindow(this.options), this);
		}
		
		this.pic = new PlayerInputController(new Vec3(0, 30, 100));
		this.pic.setAcceptPlayerInputs(false);

		this.screen = new GaussianSplattingScreen();		

		DirLight sun = new DirLight(new Vec3(-2, -1.5, -1), new Vec3(1), 0.4f);
		Light.addLight(WORLD_SCENE, sun);

		BufferedImage[] skyboxSides = new BufferedImage[6];
		String skyboxDir = "/res/skybox/lake/";
		for (int i = 0; i < 6; i++) {
			skyboxSides[i] = FileUtils.loadImageRelative(skyboxDir + Cubemap.CUBEMAP_SIDE_NAMES[i] + ".jpg");
		}
		Cubemap skybox = new Cubemap(skyboxSides);
		Scene.skyboxes.put(WORLD_SCENE, skybox);
		
//		Gaussian[] gaussians = this.readSplatFile("/res/gaussian_splats/copyright.ply");
//		Gaussian[] gaussians = this.readSplatFile("/res/gaussian_splats/chair.ply");
//		Gaussian[] gaussians = this.readSplatFile("/res/gaussian_splats/blowtorch.ply");
//		Gaussian[] gaussians = this.readSplatFile("/res/gaussian_splats/heart_cookie.ply");
//		Gaussian[] gaussians = this.readSplatFile("/res/gaussian_splats/star_cookie.ply");
		Gaussian[] gaussians = this.readSplatFile("/res/gaussian_splats/beetle.ply");
//		Gaussian[] gaussians = this.readSplatFile("/res/gaussian_splats/paper_towel.ply");
		
//		int N = 10;
//		Gaussian[] gaussians = new Gaussian[N * N];
//		{
//		    int idx = 0;
//		    float radius = 20.0f;
//
//		    for (int gy = 0; gy < N; gy++) {
//		        for (int gx = 0; gx < N; gx++) {
//		            Gaussian g = new Gaussian();
//
//		            // spherical grid
//		            float theta = (float)(Math.PI * (gy + 1) / N);      // polar angle: (0, pi)
//		            float phi   = (float)(2.0 * Math.PI * gx / N);      // azimuth: [0, 2pi)
//
//		            float x = radius * (float)(Math.sin(theta) * Math.cos(phi));
//		            float y = radius * (float)(Math.sin(theta) * Math.sin(phi));
//		            float z = radius * (float)(Math.cos(theta));
//
//		            g.center = new Vec3(x, y, z);
//
//		            // Make local +Z point toward the origin
//		            Vec3 forward = new Vec3(-x, -y, -z).normalize();  // desired +Z direction
//		            Vec3 zAxis = new Vec3(0, 0, 1);
//
//		            g.orient = MathUtils.quaternionRotationUToV(zAxis, forward);
//
//		            g.scale = new Vec3(0.4f, 0.4f, 2.0f);
//		            g.alpha = 1.0f;
//
//		            g.r_coeff[0] = 1.0f;
//		            g.g_coeff[0] = 1.0f;
//		            g.b_coeff[0] = 1.0f;
//
//		            gaussians[idx++] = g;
//		        }
//		    }
//		}
		

		this.setGaussians(gaussians);
		

		this._resize();
	}
	
	private void setGaussians(Gaussian[] gaussians) {
		this.screen.setGaussians(gaussians);
	}
	
	private Gaussian[] readSplatFile(String dir) {
		File splat_file = FileUtils.loadFileRelative(dir);
		return this.readSplatFile(splat_file);
	}
	
	private Gaussian[] readSplatFile(File file) {
		Gaussian[] gaussians = null;
		try {
			PLYReader ply = new PLYReader(file);
			ply.printHeader();
			
			PLYReader.PLYPayload payload = ply.getPayload();
			gaussians = new Gaussian[payload.elements.get(0).count];
			for(int i = 0; i < gaussians.length; i++) {
				float x = payload.elements.get(0).properties.get("x").getFloat(i);
				float y = payload.elements.get(0).properties.get("y").getFloat(i);
				float z = payload.elements.get(0).properties.get("z").getFloat(i);
				
				float alpha = payload.elements.get(0).properties.get("opacity").getFloat(i);
				alpha = (float) (1.0 / (1.0 + Math.exp(-alpha)));
				
				float scale_x = payload.elements.get(0).properties.get("scale_0").getFloat(i);
				float scale_y = payload.elements.get(0).properties.get("scale_1").getFloat(i);
				float scale_z = payload.elements.get(0).properties.get("scale_2").getFloat(i);
				scale_x = (float) Math.exp(scale_x);
				scale_y = (float) Math.exp(scale_y);
				scale_z = (float) Math.exp(scale_z);
				
				float rot_x = payload.elements.get(0).properties.get("rot_0").getFloat(i);
				float rot_y = payload.elements.get(0).properties.get("rot_1").getFloat(i);
				float rot_z = payload.elements.get(0).properties.get("rot_2").getFloat(i);
				float rot_w = payload.elements.get(0).properties.get("rot_3").getFloat(i);
				
				float[] coeffs = new float[48];
				coeffs[0] = payload.elements.get(0).properties.get("f_dc_0").getFloat(i);
				coeffs[1] = payload.elements.get(0).properties.get("f_dc_1").getFloat(i);
				coeffs[2] = payload.elements.get(0).properties.get("f_dc_2").getFloat(i);
				
				if(payload.elements.get(0).properties.containsKey("f_rest_0")) {
					for(int j = 0; j < 45; j++) {
						coeffs[j + 3] = payload.elements.get(0).properties.get("f_rest_" + j).getFloat(i);
					}
				}
				
				Gaussian g = new Gaussian();
				g.center = new Vec3(x, y, z);
				g.alpha = alpha;
				g.scale = new Vec3(scale_x, scale_y, scale_z);
				g.orient = new Quaternion(rot_x, rot_y, rot_z, rot_w);
				g.r_coeff[0] = coeffs[0];
				g.g_coeff[0] = coeffs[1];
				g.b_coeff[0] = coeffs[2];
				for (int j = 0; j < 15; j++) {
				    g.r_coeff[j + 1] = coeffs[3 + j];
				    g.g_coeff[j + 1] = coeffs[18 + j];
				    g.b_coeff[j + 1] = coeffs[33 + j];
				}
				
				gaussians[i] = g;
			}
			System.out.println("Gaussian count : " + gaussians.length);
		} 
		catch (IOException e) {
			System.out.println("PLY reader failed : " + e.getMessage());
			e.printStackTrace();
		}
		return gaussians;
	}

	@Override
	protected void _kill() {
		Scene.removeScene(WORLD_SCENE);
		this.screen.kill();
	}

	@Override
	protected void _resize() {
		this.screen.setScreenDimensions(this.getWidth(), this.getHeight());
	}

	@Override
	public String getDefaultTitle() {
		return "Gaussian Splatting";
	}

	@Override
	protected void _update() {
		this.pic.update();
		
		this.screen.setCameraPos(this.pic.getPos());
		this.screen.setCameraFacing(this.pic.getFacing());
	}

	@Override
	protected void renderContent(Framebuffer outputBuffer) {
		Camera camera = new Camera((float) Math.toRadians(90), this.getWidth(), this.getHeight(), 0.1f, 400);
		camera.setPos(this.pic.getPos());
		camera.setFacing(this.pic.getFacing());
		
		this.screen.render(outputBuffer);
	}

	@Override
	protected void renderOverlay(Framebuffer outputBuffer) {
		
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
		case GLFW_KEY_L: {
			FileSelectorWindow fsw = new FileSelectorWindow(this);
			fsw.setSingleEntrySelection(true);
			AdjustableWindow adj = new AdjustableWindow("Select Gaussian Splat File", fsw, this);
			break;
		}
		}
	}

	@Override
	protected void _keyReleased(int key) {
		// TODO Auto-generated method stub

	}

	@Override
	public void handleCallback(File[] files) {
		if(files.length != 1) {
			return;
		}
		
		Gaussian[] gaussians = this.readSplatFile(files[0]);
		if(gaussians != null) {
			this.setGaussians(gaussians);
		}
	}
	
	public class Options {
		public float movementSpeedMultiplier = 1.0f;
		public boolean reflectY = true;
		public float renderScale = 20.0f;
		public boolean timingEnabled = true;
		
		public boolean getTimingEnabled() {
			return this.timingEnabled;
		}
		
		public void setTimingEnabled(boolean timingEnabled) {
			this.timingEnabled = timingEnabled;
			screen.setTimingEnabled(this.timingEnabled);
		}
		
		public boolean getReflectY() {
			return reflectY;
		}

		public void setReflectY(boolean reflectY) {
			this.reflectY = reflectY;
			screen.setReflectY(this.reflectY);
		}

		public float getRenderScale() {
			return renderScale;
		}

		public void setRenderScale(float renderScale) {
			this.renderScale = renderScale;
			screen.setRenderScale(this.renderScale);
		}

		public float getMovementSpeedMultiplier() {
			return movementSpeedMultiplier;
		}

		public void setMovementSpeedMultiplier(float movementSpeedMultiplier) {
			this.movementSpeedMultiplier = movementSpeedMultiplier;
			pic.setMovementSpeedMultiplier(this.movementSpeedMultiplier);
		}
		
	}

}
