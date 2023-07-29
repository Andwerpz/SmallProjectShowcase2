package hydraulic_terrain;

import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;
import static org.lwjgl.opengl.GL31.*;
import static org.lwjgl.opengl.GL33.*;

import java.awt.Color;
import java.awt.image.BufferedImage;

import lwjglengine.graphics.Cubemap;
import lwjglengine.graphics.Framebuffer;
import lwjglengine.graphics.Material;
import lwjglengine.model.Line;
import lwjglengine.model.Model;
import lwjglengine.model.ModelInstance;
import lwjglengine.model.VertexArray;
import lwjglengine.player.Camera;
import lwjglengine.scene.DirLight;
import lwjglengine.scene.Light;
import lwjglengine.scene.Scene;
import lwjglengine.screen.PerspectiveScreen;
import lwjglengine.window.Window;
import myutils.v10.math.MathUtils;
import myutils.v10.math.PerlinNoiseGenerator;
import myutils.v10.math.Vec2;
import myutils.v10.math.Vec3;
import myutils.v11.file.FileUtils;

public class HydraulicTerrainWindow extends Window {
	//for now, just generate a new vertex array 

	//later, i want to be able to render using the vertex shader. Feed the heightmap as a texture into the vertex
	//shader, and use a central limit to estimate the normal direction. 

	//even later tho, perhaps we can use a geometry shader to get perfect normals. 

	private int WORLD_SCENE = Scene.generateScene();

	private PerspectiveScreen perspectiveScreen;

	private static final int TERRAIN_RESOLUTION = 32;
	private float terrainScale = 1;

	private float[][] heightmap;

	private Model terrainModel;

	private float cameraDist = 50;
	private Vec3 cameraFacing = new Vec3(0, -1, -1).normalize();

	private Vec2 mousePos;

	private boolean mousePressed = false;

	public HydraulicTerrainWindow(Window parentWindow) {
		super(parentWindow);
		this.init();
	}

	private void init() {
		this.perspectiveScreen = new PerspectiveScreen();

		this.heightmap = HydraulicTerrainWindow.generatePerlinNoise();

		this.terrainModel = HydraulicTerrainWindow.generateTerrainModel(this.heightmap);

		//normal lines
		for (int i = 0; i < TERRAIN_RESOLUTION - 1; i++) {
			for (int j = 0; j < TERRAIN_RESOLUTION - 1; j++) {
				float x = i;
				float z = j;

				float height = HydraulicTerrainWindow.sampleHeight(this.heightmap, x, z);
				Vec3 normal = HydraulicTerrainWindow.sampleNormal(this.heightmap, x, z);
				//Vec3 normal = new Vec3(0, 1, 0);
				Vec3 ground = new Vec3(x - (TERRAIN_RESOLUTION - 1) / 2.0f, height, z - (TERRAIN_RESOLUTION - 1) / 2.0f);

				ModelInstance lineInstance = Line.addLine(ground, ground.add(normal.mul(2)), WORLD_SCENE);
				lineInstance.setMaterial(new Material(Color.YELLOW));
			}
		}

		DirLight sun = new DirLight(new Vec3(-1), new Vec3(1), 0.3f);
		Light.addLight(WORLD_SCENE, sun);

		BufferedImage[] skyboxSides = new BufferedImage[6];
		String skyboxDir = "/res/skybox/lake/";
		for (int i = 0; i < 6; i++) {
			skyboxSides[i] = FileUtils.loadImageRelative(skyboxDir + Cubemap.CUBEMAP_SIDE_NAMES[i] + ".jpg");
		}
		Cubemap skybox = new Cubemap(skyboxSides);
		Scene.skyboxes.put(WORLD_SCENE, skybox);

		ModelInstance terrainInstance = new ModelInstance(this.terrainModel, WORLD_SCENE);

		this.mousePos = this.getWindowMousePos();

		this._resize();
	}

	//generates a perlin noise heightmap
	private static float[][] generatePerlinNoise() {
		float[][] noise = new float[TERRAIN_RESOLUTION][TERRAIN_RESOLUTION];

		float frequency = 1.0f / 128.0f;
		float amplitude = 64;
		float persistence = 0.5f;
		float lacunarity = 2.0f;

		int octaves = 5;

		for (int i = 0; i < TERRAIN_RESOLUTION; i++) {
			for (int j = 0; j < TERRAIN_RESOLUTION; j++) {
				noise[i][j] = (float) PerlinNoiseGenerator.noise(i, j, frequency, amplitude, persistence, lacunarity, octaves);
			}
		}

		return noise;
	}

	//round to nearest int and then sample
	private static float sampleHeight(float[][] heightmap, float x, float z) {
		int r = MathUtils.clamp(0, TERRAIN_RESOLUTION - 1, Math.round(x));
		int c = MathUtils.clamp(0, TERRAIN_RESOLUTION - 1, Math.round(z));
		return heightmap[r][c];
	}

	//use central limit to approximate the normal
	private static Vec3 sampleNormal(float[][] heightmap, float x, float z) {
		float epsilon = 0.01f;

		float dx = (sampleHeight(heightmap, x + epsilon, z) - sampleHeight(heightmap, x - epsilon, z)) / (2 * epsilon);
		float dz = (sampleHeight(heightmap, x, z + epsilon) - sampleHeight(heightmap, x, z - epsilon)) / (2 * epsilon);

		//create triangle from 3 known points, and find the normal. 
		Vec3 t0 = new Vec3(1, dx, 0);
		Vec3 t1 = new Vec3(0, dz, 1);
		return t1.cross(t0).normalize();
	}

	private static void performSnowballErosion(float[][] heightmap) {
		float x = (float) (Math.random() * TERRAIN_RESOLUTION);
		float y = (float) (Math.random() * TERRAIN_RESOLUTION);
	}

	private static Model generateTerrainModel(float[][] heightmap) {
		float[] vertices = new float[TERRAIN_RESOLUTION * TERRAIN_RESOLUTION * 3];
		int[][] vertexIndices = new int[TERRAIN_RESOLUTION][TERRAIN_RESOLUTION];
		{
			int ptr = 0;
			for (int i = 0; i < TERRAIN_RESOLUTION; i++) {
				for (int j = 0; j < TERRAIN_RESOLUTION; j++) {
					vertexIndices[i][j] = ptr / 3;

					vertices[ptr++] = i - ((TERRAIN_RESOLUTION - 1) / 2.0f);
					vertices[ptr++] = heightmap[i][j];
					vertices[ptr++] = j - ((TERRAIN_RESOLUTION - 1) / 2.0f);
				}
			}
		}

		float[] uvs = new float[TERRAIN_RESOLUTION * TERRAIN_RESOLUTION * 2];
		{
			int ptr = 0;
			for (int i = 0; i < TERRAIN_RESOLUTION; i++) {
				for (int j = 0; j < TERRAIN_RESOLUTION; j++) {
					uvs[ptr++] = i;
					uvs[ptr++] = j;
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
	}

	@Override
	protected void _resize() {
		this.perspectiveScreen.setScreenDimensions(this.getWidth(), this.getHeight());
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
				this.cameraFacing.rotateY(dx * 0.01f);
			}

			this.mousePos.set(nextMouse);
		}

		//update camera pos
		Camera camera = this.perspectiveScreen.getCamera();
		camera.setFacing(this.cameraFacing);
		camera.setPos(this.cameraFacing.mul(-this.cameraDist));
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
		this.cameraDist += smoothOffset * 5;
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
