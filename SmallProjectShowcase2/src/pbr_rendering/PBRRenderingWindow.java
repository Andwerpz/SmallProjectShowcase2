package pbr_rendering;

import org.lwjgl.stb.STBImage;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL12.*;
import static org.lwjgl.opengl.GL13.*;
import static org.lwjgl.opengl.GL30.*;
import static org.lwjgl.opengl.GL31.*;
import static org.lwjgl.opengl.GL32.*;
import static org.lwjgl.opengl.GL33.*;
import static org.lwjgl.opengl.GL42.glTexStorage2D;
import static org.lwjgl.opengl.GL46.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL12.*;
import static org.lwjgl.opengl.GL14.*;
import static org.lwjgl.opengl.GL13.*;
import static org.lwjgl.opengl.GL30.*;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.HashMap;

import lwjglengine.graphics.Cubemap;
import lwjglengine.graphics.Framebuffer;
import lwjglengine.graphics.Material;
import lwjglengine.graphics.Shader;
import lwjglengine.graphics.Texture;
import lwjglengine.model.Model;
import lwjglengine.model.ModelInstance;
import lwjglengine.player.Camera;
import lwjglengine.player.PlayerInputController;
import lwjglengine.scene.DirLight;
import lwjglengine.scene.Light;
import lwjglengine.scene.Scene;
import lwjglengine.screen.ScreenQuad;
import lwjglengine.screen.SkyboxCube;
import lwjglengine.util.BufferUtils;
import lwjglengine.window.AdjustableWindow;
import lwjglengine.window.FileExplorerWindow;
import lwjglengine.window.ObjectEditorWindow;
import lwjglengine.window.TextureViewerWindow;
import lwjglengine.window.Window;
import myutils.file.FileUtils;
import myutils.file.SystemUtils;
import myutils.math.Mat4;
import myutils.math.Vec3;

public class PBRRenderingWindow extends Window {

	private final int WORLD_SCENE = Scene.generateScene();

	private static String[] displayTextureList = new String[] { "geometryPositionMap", "geometryColorMap", "geometryNormalMap", "geometryAttrMap", "lightingColorMap", "postprocessColorMap" };
	private HashMap<String, Field> displayTextureFields;
	private HashMap<String, TextureViewerWindow> displayWindows;

	private PBRRenderingScreen pbrScreen;

	private Model model = null;

	private PlayerInputController pic;
	private float cameraDistFromCenter = 1f;

	public static Texture loadHDRTexture(String path) throws IOException {
		STBImage.stbi_set_flip_vertically_on_load(true);

		byte[] data = FileUtils.convertFileToByteArray(FileUtils.loadFile(path));
		ByteBuffer dataBuffer = BufferUtils.createByteBuffer(data);

		int[] w = new int[1];
		int[] h = new int[1];
		int[] nrComponents = new int[1];

		if (!STBImage.stbi_info_from_memory(dataBuffer, w, h, nrComponents)) {
			throw new IOException("Failed to read image information: " + STBImage.stbi_failure_reason());
		}

		ByteBuffer image = STBImage.stbi_load_from_memory(dataBuffer, w, h, nrComponents, 3);

		if (image == null) {
			throw new IOException("Failed to load image: " + STBImage.stbi_failure_reason());
		}

		int texID = glGenTextures();
		glBindTexture(GL_TEXTURE_2D, texID);
		glTexImage2D(GL_TEXTURE_2D, 0, GL_RGB16F, w[0], h[0], 0, GL_RGB, GL_UNSIGNED_BYTE, image);
		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);

		STBImage.stbi_image_free(image);

		return new Texture(texID);
	}

	public PBRRenderingWindow(int xOffset, int yOffset, int width, int height, Window parentWindow) {
		super(xOffset, yOffset, width, height, parentWindow);
		this.init();
	}

	private void init() {
		this.pbrScreen = new PBRRenderingScreen();
		this.pbrScreen.setWorldScene(WORLD_SCENE);

		//control panel for render settings
		{
			AdjustableWindow adjWindow = new AdjustableWindow("Render Settings", new ObjectEditorWindow(this.pbrScreen.getRenderSettings()), this);
		}

		//skybox
		this.setHDRSkybox(FileUtils.loadFile(SystemUtils.getWorkingDirectory() + "/res/hdr_radiance/thatch_chapel_4k.hdr"));

		DirLight sun = new DirLight(new Vec3(0.3, -0.6f, 1), new Vec3(23.47, 21.31, 20.79).mul(0.1f), 0);
		Light.addLight(WORLD_SCENE, sun);

		this.pic = new PlayerInputController(new Vec3(0, 0, -1));
		this.pic.setCamXRot((float) Math.PI / 4.0f);
		this.pic.setCamYRot((float) Math.PI / 4.0f);
		this.pic.setAcceptPlayerInputs(false);

		//get fields from pbr screen and create texture viewer windows
		this.displayTextureFields = new HashMap<String, Field>();
		this.displayWindows = new HashMap<String, TextureViewerWindow>();
		{
			HashMap<String, Field> fieldMap = new HashMap<>();
			Field[] fields = this.pbrScreen.getClass().getDeclaredFields();
			for (Field f : fields) {
				fieldMap.put(f.getName(), f);
			}
			for (String s : displayTextureList) {
				if (fieldMap.containsKey(s)) {
					Field f = fieldMap.get(s);
					f.setAccessible(true);
					this.displayTextureFields.put(s, f);
					TextureViewerWindow textureViewer = new TextureViewerWindow(null);
					this.displayWindows.put(s, textureViewer);
					AdjustableWindow adjWindow = new AdjustableWindow(s, textureViewer, this);
				}
			}
		}

		//enable context menu
		this.setContextMenuRightClick(true);
		String[] contextMenuActions = new String[] { "Load File" };
		this.setContextMenuActions(contextMenuActions);

		//initial sphere scene
		this.model = Model.loadModelFile(FileUtils.loadFileRelative("/res/sphere/sphere.obj"));
		{
			int sideAmt = 7;
			float sphereRadius = 1;
			float sphereScale = 0.33f;
			float attrInc = 1.0f / (sideAmt - 1);
			float posBase = -(sphereRadius * 3) * (sideAmt - 1) / 2.0f;
			float posInc = (sphereRadius * 3);
			for (int i = 0; i < sideAmt; i++) {
				for (int j = 0; j < sideAmt; j++) {
					Material material = Material.defaultMaterial();
					material.setRoughness(i * attrInc);
					material.setMetalness(j * attrInc);

					//material.setDiffuse(new Vec3(Math.random(), Math.random(), Math.random()));
					material.setDiffuse(new Vec3(1, 1, 1));

					Mat4 mat4 = Mat4.translate(posBase + i * posInc, posBase + j * posInc, 0);
					mat4.muli(Mat4.scale(sphereScale));

					ModelInstance instance = new ModelInstance(this.model, mat4, WORLD_SCENE);
					instance.setMaterial(material);
				}
			}
		}

		this._resize();

	}

	@Override
	public void handleContextMenuAction(String action) {
		switch (action) {
		case "Load File": {
			FileExplorerWindow fileExplorer = new FileExplorerWindow(this);
			AdjustableWindow fileExplorerAdj = new AdjustableWindow("Select File", fileExplorer, this);
			fileExplorer.setSingleEntrySelection(true);
			break;
		}
		}
	}

	@Override
	public void handleFiles(File[] f) {
		if (f.length != 1) {
			System.err.println("ModelViewerWindow : File amount should only be 1");
			return;
		}

		//see what type of file it is
		String fileExt = FileUtils.getFileExtension(f[0]);
		switch (fileExt) {
		case "obj": {
			this.setModel(f[0]);
			break;
		}

		case "hdr": {
			this.setHDRSkybox(f[0]);
			break;
		}
		}
	}

	public void setHDRSkybox(File file) {
		//import hdr radiance map into texture, and then use texture to create cubemap
		try {
			Texture hdrTexture = loadHDRTexture(file.getAbsolutePath());
			this.pbrScreen.setSkybox(hdrTexture);
			hdrTexture.kill();
		}
		catch (IOException e) {
			e.printStackTrace();
		}
	}

	public void setModel(File file) {
		//try to load model from file
		Model model = Model.loadModelFile(file);

		if (this.model != null) {
			this.model.kill();
		}
		this.model = null;

		if (model == null) {
			return;
		}

		this.model = model;
		ModelInstance instance = new ModelInstance(this.model, WORLD_SCENE);
	}

	@Override
	protected void _kill() {
		this.pbrScreen.kill();

		if (this.model != null) {
			this.model.kill();
		}

		Scene.removeScene(WORLD_SCENE);
	}

	@Override
	protected void _resize() {
		this.pbrScreen.setScreenDimensions(this.getWidth(), this.getHeight());

		//when resizing, all the display textures get deallocated. Rebind the new textures to the display windows here. 
		for (String s : displayTextureList) {
			if (!this.displayWindows.containsKey(s)) {
				continue;
			}

			Field f = this.displayTextureFields.get(s);
			TextureViewerWindow tv = this.displayWindows.get(s);
			try {
				tv.setTexture((Texture) f.get(this.pbrScreen));
			}
			catch (IllegalArgumentException e) {
				e.printStackTrace();
			}
			catch (IllegalAccessException e) {
				e.printStackTrace();
			}
		}
	}

	@Override
	public String getDefaultTitle() {
		return "PBR Rendering";
	}

	@Override
	protected void _update() {
		this.pic.update();

		this.pbrScreen.getCamera().setFacing(this.pic.getFacing());
		this.pbrScreen.getCamera().setPos(this.pic.getFacing().mul(-this.cameraDistFromCenter));
	}

	@Override
	protected void renderContent(Framebuffer outputBuffer) {
		this.pbrScreen.render(outputBuffer);
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
		this.pic.setAcceptPlayerInputs(true);
	}

	@Override
	protected void _mouseReleased(int button) {
		this.pic.setAcceptPlayerInputs(false);
	}

	@Override
	protected void _mouseScrolled(float wheelOffset, float smoothOffset) {
		if (smoothOffset < 0) {
			this.cameraDistFromCenter *= 1.1;
		}
		else {
			this.cameraDistFromCenter /= 1.1;
		}
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
