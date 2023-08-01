package hydraulic_terrain;

import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;
import static org.lwjgl.opengl.GL31.*;
import static org.lwjgl.opengl.GL33.*;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.Stack;

import org.lwjgl.glfw.GLFW;

import lwjglengine.graphics.Cubemap;
import lwjglengine.graphics.Framebuffer;
import lwjglengine.graphics.Material;
import lwjglengine.graphics.Texture;
import lwjglengine.graphics.TextureMaterial;
import lwjglengine.input.Button;
import lwjglengine.input.Input;
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
import lwjglengine.screen.UIScreen;
import lwjglengine.ui.UIElement;
import lwjglengine.ui.UIFilledRectangle;
import lwjglengine.ui.UISection;
import lwjglengine.window.Window;
import myutils.v10.math.MathUtils;
import myutils.v10.math.PerlinNoiseGenerator;
import myutils.v10.math.Vec2;
import myutils.v10.math.Vec3;
import myutils.v10.misc.Pair;
import myutils.v11.file.FileUtils;

public class HydraulicTerrainWindow extends Window {
	//for now, just generate a new vertex array 

	//later, i want to be able to render using the vertex shader. Feed the heightmap as a texture into the vertex
	//shader, and use a central limit to estimate the normal direction. 

	//even later tho, perhaps we can use a geometry shader to get perfect normals. 
	
	//TODO 
	// - simulate sand dunes using wind?

	private int WORLD_SCENE = Scene.generateScene();

	private PerspectiveScreen perspectiveScreen;
	
	private UIScreen uiScreen;
	private UISection uiSection;

	private static final int TERRAIN_RESOLUTION = 512;
	private float terrainScale = 0.5f;
	
	//for each (x, y) coord, store base height of solid layer, and height of sediment layer on top
	//[x][y][0] = base solid height
	//[x][y][1] = covering sediment height
	private float[][][] heightmap;

	private Model terrainModel;
	private TextureMaterial terrainTextureMaterial;

	private float cameraDist = 50;
	private float cameraXRot = (float) (Math.PI / 8.0);
	private float cameraYRot = 0;
	private Vec3 cameraFacing = new Vec3(0, 0, -1).rotateX(this.cameraXRot).rotateY(this.cameraYRot);

	private Vec2 mousePos;

	private boolean mousePressed = false;

	public HydraulicTerrainWindow(Window parentWindow) {
		super(parentWindow);
		this.init();
	}

	private void init() {
		this.perspectiveScreen = new PerspectiveScreen();

		this.heightmap = HydraulicTerrainWindow.generatePerlinNoise();
		this.regenerateTerrainModel();

		DirLight sun = new DirLight(new Vec3(-1), new Vec3(1), 0.6f);
		Light.addLight(WORLD_SCENE, sun);

		BufferedImage[] skyboxSides = new BufferedImage[6];
		String skyboxDir = "/res/skybox/lake/";
		for (int i = 0; i < 6; i++) {
			skyboxSides[i] = FileUtils.loadImageRelative(skyboxDir + Cubemap.CUBEMAP_SIDE_NAMES[i] + ".jpg");
		}
		Cubemap skybox = new Cubemap(skyboxSides);
		Scene.skyboxes.put(WORLD_SCENE, skybox);

		this.mousePos = this.getWindowMousePos();
		
		this.uiScreen = new UIScreen();
		this.uiSection = new UISection(0, 0, this.getWidth(), this.getHeight(), this.uiScreen);
		
		Material backgroundMaterial = new Material(this.contentDefaultMaterial);
		backgroundMaterial.setAlpha(0.5f);
		
		UIFilledRectangle backgroundRect = this.uiSection.getBackgroundRect();
		backgroundRect.setWidth(200);
		backgroundRect.setHeight(105);
		backgroundRect.setFrameAlignmentStyle(UIElement.FROM_LEFT, UIElement.FROM_TOP);
		backgroundRect.setContentAlignmentStyle(UIElement.ALIGN_LEFT, UIElement.ALIGN_TOP);
		backgroundRect.setFrameAlignmentOffset(10, 10);
		backgroundRect.setMaterial(backgroundMaterial);
		backgroundRect.bind(this.rootUIElement);
		
		Button btnRegenerateNoise = new Button(0, 10, 100, 25, "btn_regenerate_noise", "Regenerate Terrain", 12, this.uiSection.getSelectionScene(), this.uiSection.getTextScene());
		btnRegenerateNoise.setFrameAlignmentStyle(UIElement.FROM_CENTER_LEFT, UIElement.FROM_TOP);
		btnRegenerateNoise.setContentAlignmentStyle(UIElement.ALIGN_CENTER, UIElement.ALIGN_TOP);
		btnRegenerateNoise.setFillWidth(true);
		btnRegenerateNoise.setFillWidthMargin(10);
		btnRegenerateNoise.getButtonText().setDoAntialiasing(false);
		btnRegenerateNoise.bind(backgroundRect);
		
		Button btnErodeTerrain = new Button(0, 40, 100, 25, "btn_erode_terrain", "Erode Terrain", 12, this.uiSection.getSelectionScene(), this.uiSection.getTextScene());
		btnErodeTerrain.setFrameAlignmentStyle(UIElement.FROM_CENTER_LEFT, UIElement.FROM_TOP);
		btnErodeTerrain.setContentAlignmentStyle(UIElement.ALIGN_CENTER, UIElement.ALIGN_TOP);
		btnErodeTerrain.setFillWidth(true);
		btnErodeTerrain.setFillWidthMargin(10);
		btnErodeTerrain.getButtonText().setDoAntialiasing(false);
		btnErodeTerrain.bind(backgroundRect);
		
		Button btnBlurTerrain = new Button(0, 70, 100, 25, "btn_blur_terrain", "Blur Terrain", 12, this.uiSection.getSelectionScene(), this.uiSection.getTextScene());
		btnBlurTerrain.setFrameAlignmentStyle(UIElement.FROM_CENTER_LEFT, UIElement.FROM_TOP);
		btnBlurTerrain.setContentAlignmentStyle(UIElement.ALIGN_CENTER, UIElement.ALIGN_TOP);
		btnBlurTerrain.setFillWidth(true);
		btnBlurTerrain.setFillWidthMargin(10);
		btnBlurTerrain.getButtonText().setDoAntialiasing(false);
		btnBlurTerrain.bind(backgroundRect);
		
		this._resize();
	}
	
	private void regenerateTerrainModel() {
		if(this.terrainModel != null) {
			this.terrainModel.kill();
		}
		
		if(this.terrainTextureMaterial != null) {
			this.terrainTextureMaterial.kill();
		}
		
		this.terrainModel = HydraulicTerrainWindow.generateTerrainModel(this.heightmap);
		this.terrainTextureMaterial = HydraulicTerrainWindow.generateTextureMaterial(this.heightmap);
		this.terrainModel.setTextureMaterial(this.terrainTextureMaterial);
		
		ModelInstance terrainInstance = new ModelInstance(this.terrainModel, WORLD_SCENE);
		ModelTransform terrainTransform = new ModelTransform();
		terrainTransform.setScale(this.terrainScale);
		terrainInstance.setModelTransform(terrainTransform);
	}
	
	private static TextureMaterial generateTextureMaterial(float[][][] heightmap) {
		TextureMaterial tm = new TextureMaterial();
		tm.setTexture(generateDiffuseTexture(heightmap), TextureMaterial.DIFFUSE);
		tm.setTexture(generateSpecularTexture(heightmap), TextureMaterial.SPECULAR);
		return tm;
	}
	
	private static Texture generateDiffuseTexture(float[][][] heightmap) {
		Vec3 snowColor = new Vec3(255, 255, 255);
		Vec3 sandColor = new Vec3(244, 164, 96);
		Vec3 dirtColor = new Vec3(114, 93, 76);
		Vec3 stoneColor = new Vec3(58, 50, 50);
		
		Vec3 sedimentColor = new Vec3(snowColor);
		Vec3 solidColor = new Vec3(stoneColor);
		
		int[] data = new int[TERRAIN_RESOLUTION * TERRAIN_RESOLUTION];
		for(int i = 0; i < TERRAIN_RESOLUTION; i++) {
			for(int j = 0; j < TERRAIN_RESOLUTION; j++) {
				float sedimentAmt = Math.max(0, sampleSediment(heightmap, i, j));
				Vec3 curColor = MathUtils.lerp(solidColor, 0, sedimentColor, 1, Math.min(1, sedimentAmt));
				data[j * TERRAIN_RESOLUTION + i] = (((int) curColor.x) << 0) + (((int) curColor.y) << 8) + (((int) curColor.z) << 16) + (255 << 24);
			}
		}
		
		Texture texture = new Texture(data, TERRAIN_RESOLUTION, TERRAIN_RESOLUTION, GL_LINEAR_MIPMAP_LINEAR, GL_LINEAR);
		texture.setWrapping(GL_CLAMP_TO_EDGE);
		
		return texture;
	}
	
	private static Texture generateSpecularTexture(float[][][] heightmap) {
		//for now, just set specular to 0
		return new Texture(0, 0, 0, 0);
	}

	//generates a perlin noise heightmap
	private static float[][][] generatePerlinNoise() {
		PerlinNoiseGenerator.randomizeNoise();
		
		float[][][] noise = new float[TERRAIN_RESOLUTION][TERRAIN_RESOLUTION][2];

		float frequency = 1.0f / 256.0f;
		float amplitude = 128;
		float persistence = 0.3f;
		float lacunarity = 3.0f;

		int octaves = 4;
		
		float sedimentLayerThickness = 2;

		for (int i = 0; i < TERRAIN_RESOLUTION; i++) {
			for (int j = 0; j < TERRAIN_RESOLUTION; j++) {
				noise[i][j][0] = (float) PerlinNoiseGenerator.noise(i, j, frequency, amplitude, persistence, lacunarity, octaves);
				noise[i][j][1] = sedimentLayerThickness;
			}
		}

		return noise;
	}
	
	//applies a two pass gaussian filter of size 3
	private static void applyGaussianBlur3(float[][][] heightmap) {
		float[][][] tmp = new float[TERRAIN_RESOLUTION][TERRAIN_RESOLUTION][2];
		
		float[] kernel = {0.1f, 0.8f, 0.1f};
		
		//horizontal blur
		for(int i = 0; i < TERRAIN_RESOLUTION; i++) {
			for(int j = 0; j < TERRAIN_RESOLUTION; j++) {
				for(int k = 0; k < 3; k++) {
					int r = i;
					int c = j + k - 1;
					c = MathUtils.clamp(0, TERRAIN_RESOLUTION - 1, c);
					tmp[i][j][0] += heightmap[r][c][0] * kernel[k];
					tmp[i][j][1] += heightmap[r][c][1] * kernel[k];
				}
			}
		}
		
		//vertical blur
		for(int i = 0; i < TERRAIN_RESOLUTION; i++) {
			for(int j = 0; j < TERRAIN_RESOLUTION; j++) {
				heightmap[i][j][0] = 0;
				heightmap[i][j][1] = 0;
				for(int k = 0; k < 3; k++) {
					int r = i + k - 1;
					r = MathUtils.clamp(0, TERRAIN_RESOLUTION - 1, r);
					int c = j;
					heightmap[i][j][0] += tmp[r][c][0] * kernel[k];
					heightmap[i][j][1] += tmp[r][c][1] * kernel[k];
				}
			}
		}
	}

	//round to nearest int and then sample
	private static float sampleHeight(float[][][] heightmap, float x, float z) {
		int r = MathUtils.clamp(0, TERRAIN_RESOLUTION - 1, Math.round(x));
		int c = MathUtils.clamp(0, TERRAIN_RESOLUTION - 1, Math.round(z));
		return heightmap[r][c][0] + heightmap[r][c][1];
	}
	
	private static float sampleSolid(float[][][] heightmap, float x, float z) {
		int r = MathUtils.clamp(0, TERRAIN_RESOLUTION - 1, Math.round(x));
		int c = MathUtils.clamp(0, TERRAIN_RESOLUTION - 1, Math.round(z));
		return heightmap[r][c][0];
	}
	
	private static float sampleSediment(float[][][] heightmap, float x, float z) {
		int r = MathUtils.clamp(0, TERRAIN_RESOLUTION - 1, Math.round(x));
		int c = MathUtils.clamp(0, TERRAIN_RESOLUTION - 1, Math.round(z));
		return heightmap[r][c][1];
	}

	//create 4 triangles and average to sample normal
	private static Vec3 sampleNormal(float[][][] heightmap, float x, float z) {
		int r = MathUtils.clamp(1, TERRAIN_RESOLUTION - 2, Math.round(x));
		int c = MathUtils.clamp(1, TERRAIN_RESOLUTION - 2, Math.round(z));
		
		Vec3 center = new Vec3(r, sampleHeight(heightmap, r, c), c);
		Vec3 up = center.sub(new Vec3(r - 1, sampleHeight(heightmap, r - 1, c), c));
		Vec3 down = center.sub(new Vec3(r + 1, sampleHeight(heightmap, r + 1, c), c));
		Vec3 left = center.sub(new Vec3(r, sampleHeight(heightmap, r, c - 1), c - 1));
		Vec3 right = center.sub(new Vec3(r, sampleHeight(heightmap, r, c + 1), c + 1));
		
		Vec3 normal = new Vec3(0);
		normal.addi(left.cross(up));
		normal.addi(down.cross(left));
		normal.addi(right.cross(down));
		normal.addi(up.cross(right));
		
		normal.normalize();
		
		return normal;
	}
	
	private static void incrementSediment(float[][][] heightmap, float x, float z, float increment) {
		int r = MathUtils.clamp(0, TERRAIN_RESOLUTION - 1, Math.round(x));
		int c = MathUtils.clamp(0, TERRAIN_RESOLUTION - 1, Math.round(z));
		heightmap[r][c][1] += increment;
	}
	
	private static void incrementSolid(float[][][] heightmap, float x, float z, float increment) {
		int r = MathUtils.clamp(0, TERRAIN_RESOLUTION - 1, Math.round(x));
		int c = MathUtils.clamp(0, TERRAIN_RESOLUTION - 1, Math.round(z));
		heightmap[r][c][0] += increment;
	}
	
	private static void performSnowballErosion(float[][][] heightmap) {
		float epsilon = 0.01f;
		
		float solidErosionRate = 0.15f;
		
		float sedimentErosionRate = 3f;
		float sedimentDepositionRate = 0.5f;
		
		float friction = 0.7f;
		float speedMult = 1;
		
		float x = (float) (Math.random() * TERRAIN_RESOLUTION);
		float y = (float) (Math.random() * TERRAIN_RESOLUTION);
		x = MathUtils.clamp(1, TERRAIN_RESOLUTION - 1, x);
		y = MathUtils.clamp(1, TERRAIN_RESOLUTION - 1, y);
		
		//velocity
		float vx = 0;
		float vy = 0;
		
		//previous x, y
		float px = x;
		float py = y;
		
		//the amount of carried sediment
		float carriedSediment = 0;	
		
		int maxIterations = TERRAIN_RESOLUTION;
		
		for(int i = 0; i < maxIterations; i++) {
			//get surface normal of terrain 
			Vec3 surfaceNormal = sampleNormal(heightmap, x, y);
			
			//if surface normal is flat, then we can stop
			if(Math.abs(1.0 - surfaceNormal.y) <= epsilon) {
				break;
			}
			
			//first, calculate erosion amt
			float sedimentErosion = (1 - surfaceNormal.y) * sedimentErosionRate; 
			float solidErosion = 0;
			float pSedimentAmt = sampleSediment(heightmap, px, py);
			
			//factor in eroding solid layer
			if(sedimentErosion > pSedimentAmt) {
				sedimentErosion = pSedimentAmt;
				solidErosion = (1 - pSedimentAmt / sedimentErosionRate) * solidErosionRate;
			}
			
			//can't erode previous location to a lower height than the current one
			float heightDiff = sampleHeight(heightmap, px, py) - sampleHeight(heightmap, x, y);
			sedimentErosion = Math.min(heightDiff, sedimentErosion);
			heightDiff -= sedimentErosion;
			solidErosion = Math.min(heightDiff, solidErosion);
			
			//next, calculate what we deposit
			float depositCoeff = surfaceNormal.y;
			
			//make sure that we can't deposit sediment on steep slopes
			if(depositCoeff < 0.3) {
				depositCoeff = 0;
			}
			
			float deposit = carriedSediment * sedimentDepositionRate * depositCoeff;
			
			//update the heightmap
			incrementSolid(heightmap, px, py, -solidErosion);
			incrementSediment(heightmap, px, py, deposit - sedimentErosion);
			
			carriedSediment += solidErosion + sedimentErosion - deposit;
			
			//finally, update position of the droplet
			vx = friction * vx + (surfaceNormal.x) * speedMult;
			vy = friction * vy + (surfaceNormal.z) * speedMult;
			px = x;
			py = y;
			x += vx;
			y += vy;
			
			//we fell off the edge
			if(x < 0 || x > TERRAIN_RESOLUTION - 1 || y < 0 || y > TERRAIN_RESOLUTION - 1) {
				break;
			}
		}
	}

	private static Model generateTerrainModel(float[][][] heightmap) {
		float[] vertices = new float[TERRAIN_RESOLUTION * TERRAIN_RESOLUTION * 3];
		int[][] vertexIndices = new int[TERRAIN_RESOLUTION][TERRAIN_RESOLUTION];
		{
			int ptr = 0;
			for (int i = 0; i < TERRAIN_RESOLUTION; i++) {
				for (int j = 0; j < TERRAIN_RESOLUTION; j++) {
					vertexIndices[i][j] = ptr / 3;

					vertices[ptr++] = i - ((TERRAIN_RESOLUTION - 1) / 2.0f);
					vertices[ptr++] = heightmap[i][j][0] + heightmap[i][j][1];
					vertices[ptr++] = j - ((TERRAIN_RESOLUTION - 1) / 2.0f);
				}
			}
		}

		float[] uvs = new float[TERRAIN_RESOLUTION * TERRAIN_RESOLUTION * 2];
		{
			int ptr = 0;
			for (int i = 0; i < TERRAIN_RESOLUTION; i++) {
				for (int j = 0; j < TERRAIN_RESOLUTION; j++) {
					uvs[ptr++] = i * (1.0f / (float) TERRAIN_RESOLUTION);
					uvs[ptr++] = j * (1.0f / (float) TERRAIN_RESOLUTION);
				}
			}
		}

		int[] indices = new int[(TERRAIN_RESOLUTION - 1) * (TERRAIN_RESOLUTION - 1) * 2 * 3];
		{
			int ptr = 0;
			for (int i = 0; i < TERRAIN_RESOLUTION - 1; i++) {
				for (int j = 0; j < TERRAIN_RESOLUTION - 1; j++) {
					indices[ptr++] = vertexIndices[i][j];
					indices[ptr++] = vertexIndices[i][j + 1];
					indices[ptr++] = vertexIndices[i + 1][j + 1];

					indices[ptr++] = vertexIndices[i][j];
					indices[ptr++] = vertexIndices[i + 1][j + 1];
					indices[ptr++] = vertexIndices[i + 1][j];
				}
			}
		}

		VertexArray va = new VertexArray(vertices, uvs, indices, GL_TRIANGLES);
		Model model = new Model(va);

		return model;
	}

	@Override
	protected void _kill() {
		this.perspectiveScreen.kill();
		Scene.removeScene(WORLD_SCENE);
		
		this.uiScreen.kill();
		
		if(this.terrainModel != null) {
			this.terrainModel.kill();
		}
		
		if(this.terrainTextureMaterial != null) {
			this.terrainTextureMaterial.kill();
		}
	}

	@Override
	protected void _resize() {
		this.perspectiveScreen.setScreenDimensions(this.getWidth(), this.getHeight());
		this.uiScreen.setScreenDimensions(this.getWidth(), this.getHeight());
	}

	@Override
	public String getDefaultTitle() {
		// TODO Auto-generated method stub
		return "Hydraulic Terrain Generation";
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
		
		//ui updates
		this.uiSection.update();

		//update camera pos
		Camera camera = this.perspectiveScreen.getCamera();
		camera.setFacing(this.cameraFacing);
		camera.setPos(this.cameraFacing.mul(-this.cameraDist));
	}

	@Override
	protected void renderContent(Framebuffer outputBuffer) {
		this.perspectiveScreen.setWorldScene(WORLD_SCENE);
		this.perspectiveScreen.render(outputBuffer);
		
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
		if(this.uiSection.sectionHovered()) {
			
		}
		else {
			this.mousePressed = true;
		}
	}

	@Override
	protected void _mouseReleased(int button) {
		this.mousePressed = false;
		this.uiSection.mouseReleased(button);
		switch(Input.getClicked(this.uiSection.getSelectionScene())) {
		case "btn_regenerate_noise": {
			this.heightmap = HydraulicTerrainWindow.generatePerlinNoise();
			this.regenerateTerrainModel();
			break;
		}
		
		case "btn_erode_terrain": {
			int nrSteps = TERRAIN_RESOLUTION * TERRAIN_RESOLUTION;
			
			System.out.print("Erosion in progress... ");
			for(int i = 0; i < nrSteps; i++) {
				HydraulicTerrainWindow.performSnowballErosion(this.heightmap);
			}
			System.out.println(" DONE");

			this.regenerateTerrainModel();
			break;
		}
		
		case "btn_blur_terrain": {
			HydraulicTerrainWindow.applyGaussianBlur3(this.heightmap);
			this.regenerateTerrainModel();
			break;
		}
		}
	}

	@Override
	protected void _mouseScrolled(float wheelOffset, float smoothOffset) {
		this.cameraDist += smoothOffset * 2.5f;
		
	}

	@Override
	protected void _keyPressed(int key) {
		switch(key) {
		}
	}

	@Override
	protected void _keyReleased(int key) {
		// TODO Auto-generated method stub

	}

}
