package volumetric_clouds;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL12.*;
import static org.lwjgl.opengl.GL14.*;
import static org.lwjgl.opengl.GL13.*;
import static org.lwjgl.opengl.GL30.*;

import java.awt.image.BufferedImage;
import java.text.DecimalFormat;

import lwjglengine.graphics.Cubemap;
import lwjglengine.graphics.Framebuffer;
import lwjglengine.graphics.Shader;
import lwjglengine.graphics.Texture;
import lwjglengine.graphics.Texture3D;
import lwjglengine.main.Main;
import lwjglengine.model.Line;
import lwjglengine.model.ModelInstance;
import lwjglengine.player.PlayerInputController;
import lwjglengine.scene.DirLight;
import lwjglengine.scene.Light;
import lwjglengine.scene.Scene;
import lwjglengine.screen.PerspectiveScreen;
import lwjglengine.screen.SkyboxCube;
import lwjglengine.util.ShaderUtils;
import lwjglengine.window.AdjustableWindow;
import lwjglengine.window.ObjectEditorWindow;
import lwjglengine.window.Texture3DViewerWindow;
import lwjglengine.window.TextureViewerWindow;
import lwjglengine.window.Window;
import myutils.file.FileUtils;
import myutils.file.JarUtils;
import myutils.math.Vec2;
import myutils.math.Vec3;

public class VolumetricCloudsWindow extends Window {

	private static final int NOISE_SIZE = 64;

	private final int WORLD_SCENE = Scene.generateScene();

	private PerspectiveScreen perspectiveScreen;

	private PlayerInputController pic;

	private Shader cloudsShader;

	private CloudBoundingBox cloudBox;

	private Texture3D worleyNoise;

	private DirLight sun;

	public VolumetricCloudsWindow(int xOffset, int yOffset, int width, int height, Window parentWindow) {
		super(xOffset, yOffset, width, height, parentWindow);
		this.init();
	}

	private void init() {
		this.setLockCursorOnSelect(true);
		this.setUnlockCursorOnEscPressed(true);
		this.setDeselectOnEscPressed(true);

		this.perspectiveScreen = new PerspectiveScreen();
		this.perspectiveScreen.renderDecals(false);
		this.perspectiveScreen.renderParticles(false);
		this.perspectiveScreen.renderPlayermodel(false);
		this.perspectiveScreen.renderSkybox(true);

		BufferedImage[] skyboxSides = new BufferedImage[6];
		String skyboxDir = "/res/skybox/lake/";
		for (int i = 0; i < 6; i++) {
			skyboxSides[i] = FileUtils.loadImageRelative(skyboxDir + Cubemap.CUBEMAP_SIDE_NAMES[i] + ".jpg");
		}
		Cubemap skybox = new Cubemap(skyboxSides);
		Scene.skyboxes.put(WORLD_SCENE, skybox);

		this.sun = new DirLight(new Vec3(1), new Vec3(1), 0.3f);
		Light.addLight(WORLD_SCENE, sun);

		this.pic = new PlayerInputController(new Vec3(0));

		this.cloudsShader = ShaderUtils.createShader("/volumetric_clouds/clouds.vert", "/volumetric_clouds/clouds.frag");
		this.cloudsShader.setUniform1i("tex_worley_noise", 0);

		this.cloudBox = new CloudBoundingBox(WORLD_SCENE, new Vec3(0, 0, 0), new Vec3(50, 10, 50));
		this.cloudBox.setDrawBoundingLines(true);

		float[][][] mainDetailNoiseFine = this.generateWorleyNoise(24);
		float[][][] mainDetailNoiseCoarse = this.generateWorleyNoise(12);
		float[][][] mainShapeNoise = this.generateWorleyNoise(4);
		this.addNoise(mainDetailNoiseCoarse, mainDetailNoiseFine, 0.5f);
		this.addNoise(mainShapeNoise, mainDetailNoiseCoarse, 0.5f);
		this.normalizeNoise(mainShapeNoise);

		float[][][] detailNoise = this.generateWorleyNoise(10);

		float[][][] subtractNoise = this.generateWorleyNoise(8);

		this.worleyNoise = this.generateTexture3D(mainShapeNoise, detailNoise, subtractNoise);

		Window testWindow = new AdjustableWindow(new Texture3DViewerWindow(this.worleyNoise, this), this);
		Window cloudEditorWindow = new AdjustableWindow(new ObjectEditorWindow(this.cloudBox, this), this);

		this._resize();
	}

	private Texture3D generateTexture3D(float[][][] red, float[][][] green, float[][][] blue) {
		int[] data = new int[NOISE_SIZE * NOISE_SIZE * NOISE_SIZE];

		//write to data
		int ind = 0;
		for (int depth = 0; depth < NOISE_SIZE; depth++) {
			for (int row = 0; row < NOISE_SIZE; row++) {
				for (int col = 0; col < NOISE_SIZE; col++) {
					int cred = (int) (255 * red[row][col][depth]);
					int cgreen = (int) (255 * green[row][col][depth]);
					int cblue = (int) (255 * blue[row][col][depth]);
					int rgb = (255 << 24) + (cred << 0) + (cgreen << 8) + (cblue << 16);
					data[ind] = rgb;

					ind++;
				}
			}
		}

		Texture3D tex = new Texture3D(NOISE_SIZE, NOISE_SIZE, NOISE_SIZE, data);
		return tex;
	}

	//compresses all values to range [0, 1]. 
	private void normalizeNoise(float[][][] a) {
		float max = 0;
		for (int i = 0; i < NOISE_SIZE; i++) {
			for (int j = 0; j < NOISE_SIZE; j++) {
				for (int k = 0; k < NOISE_SIZE; k++) {
					max = Math.max(a[i][j][k], max);
				}
			}
		}
		for (int i = 0; i < NOISE_SIZE; i++) {
			for (int j = 0; j < NOISE_SIZE; j++) {
				for (int k = 0; k < NOISE_SIZE; k++) {
					a[i][j][k] /= max;
				}
			}
		}
	}

	//a += b * bCoeff
	private void addNoise(float[][][] a, float[][][] b, float bCoeff) {
		for (int i = 0; i < NOISE_SIZE; i++) {
			for (int j = 0; j < NOISE_SIZE; j++) {
				for (int k = 0; k < NOISE_SIZE; k++) {
					a[i][j][k] += b[i][j][k] * bCoeff;
				}
			}
		}
	}

	private float[][][] generateWorleyNoise(int nrCells) {
		float[][][] noise = new float[NOISE_SIZE][NOISE_SIZE][NOISE_SIZE];

		//generate points
		Vec3[][][] points = new Vec3[nrCells][nrCells][nrCells];
		for (int i = 0; i < nrCells; i++) {
			for (int j = 0; j < nrCells; j++) {
				for (int k = 0; k < nrCells; k++) {
					Vec3 point = new Vec3(i, j, k);
					point.x += (float) (Math.random() * 1);
					point.y += (float) (Math.random() * 1);
					point.z += (float) (Math.random() * 1);
					points[i][j][k] = point;
				}
			}
		}

		int[] dr = { -1, -1, -1, -1, -1, -1, -1, -1, -1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1 };
		int[] dc = { -1, -1, -1, 0, 0, 0, 1, 1, 1, -1, -1, -1, 0, 0, 0, 1, 1, 1, -1, -1, -1, 0, 0, 0, 1, 1, 1 };
		int[] dl = { -1, 0, 1, -1, 0, 1, -1, 0, 1, -1, 0, 1, -1, 0, 1, -1, 0, 1, -1, 0, 1, -1, 0, 1, -1, 0, 1 };

		//calculate distances
		float maxDist = 0;
		for (int i = 0; i < NOISE_SIZE; i++) {
			for (int j = 0; j < NOISE_SIZE; j++) {
				for (int k = 0; k < NOISE_SIZE; k++) {
					Vec3 cur = new Vec3(i, j, k);
					int cellR = (int) (cur.x / NOISE_SIZE * nrCells);
					int cellC = (int) (cur.y / NOISE_SIZE * nrCells);
					int cellL = (int) (cur.z / NOISE_SIZE * nrCells);
					cur.divi(NOISE_SIZE);
					cur.muli(nrCells);
					float minDist = (float) 1e9;
					for (int l = 0; l < dr.length; l++) {
						int nr = cellR + dr[l];
						int nc = cellC + dc[l];
						int nl = cellL + dl[l];
						int horizontalAdj = 0;
						int verticalAdj = 0;
						int layerAdj = 0;
						if (nr < 0) {
							horizontalAdj = -1;
							nr += nrCells;
						}
						if (nr >= nrCells) {
							horizontalAdj = 1;
							nr -= nrCells;
						}
						if (nc < 0) {
							verticalAdj = -1;
							nc += nrCells;
						}
						if (nc >= nrCells) {
							verticalAdj = 1;
							nc -= nrCells;
						}
						if (nl < 0) {
							layerAdj = -1;
							nl += nrCells;
						}
						if (nl >= nrCells) {
							layerAdj = 1;
							nl -= nrCells;
						}
						Vec3 point = new Vec3(points[nr][nc][nl]);
						point.x += nrCells * horizontalAdj;
						point.y += nrCells * verticalAdj;
						point.z += nrCells * layerAdj;

						minDist = Math.min(minDist, point.sub(cur).lengthSq());
					}
					minDist = (float) Math.sqrt(minDist);
					maxDist = Math.max(maxDist, minDist);
					noise[i][j][k] = minDist;
				}
			}
		}

		//normalize and invert
		for (int i = 0; i < NOISE_SIZE; i++) {
			for (int j = 0; j < NOISE_SIZE; j++) {
				for (int k = 0; k < NOISE_SIZE; k++) {
					noise[i][j][k] /= maxDist;
					noise[i][j][k] = 1.0f - noise[i][j][k];
				}
			}
		}

		return noise;
	}

	@Override
	public String getDefaultTitle() {
		return "Volumetric Clouds";
	}

	@Override
	protected void _kill() {
		this.perspectiveScreen.kill();
		Scene.removeScene(WORLD_SCENE);

		this.cloudsShader.kill();

		this.worleyNoise.kill();
	}

	@Override
	protected void _resize() {
		this.perspectiveScreen.setScreenDimensions(this.getWidth(), this.getHeight());
	}

	@Override
	protected void _update() {
		if (this.isSelected()) {
			this.pic.update();

			//update camera position
			this.perspectiveScreen.getCamera().setFacing(this.pic.getFacing());
			this.perspectiveScreen.getCamera().setPos(this.pic.getPos());
		}
	}

	@Override
	protected void renderContent(Framebuffer outputBuffer) {
		this.perspectiveScreen.setWorldScene(WORLD_SCENE);
		this.perspectiveScreen.render(outputBuffer);

		glViewport(0, 0, this.getWidth(), this.getHeight());

		//render volumetric clouds

		//probably have to first extract the direction vector for each pixel into a buffer, 
		//so that we can compute the intersection between the view ray and the cloud bounding box. 
		glEnable(GL_BLEND);
		glBlendFuncSeparate(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA, GL_ONE, GL_ONE_MINUS_SRC_ALPHA);

		this.cloudsShader.enable();

		this.cloudsShader.setUniformMat4("pr_matrix", this.perspectiveScreen.getCamera().getProjectionMatrix());
		this.cloudsShader.setUniformMat4("vw_matrix", this.perspectiveScreen.getCamera().getViewMatrix());
		this.cloudsShader.setUniform3f("camera_pos", this.perspectiveScreen.getCamera().getPos());

		this.cloudsShader.setUniform3f("cloud_pos", this.cloudBox.pos);
		this.cloudsShader.setUniform3f("cloud_scale", this.cloudBox.scale);

		this.cloudsShader.setUniform3f("sun_dir", this.sun.dir);
		this.cloudsShader.setUniform3f("sun_color", this.sun.color);

		this.cloudsShader.setUniform1f("density_threshold", this.cloudBox.density_threshold);
		this.cloudsShader.setUniform1f("density_multiplier", this.cloudBox.density_multiplier);
		this.cloudsShader.setUniform1f("density_offset", this.cloudBox.density_offset);

		this.cloudsShader.setUniform1f("detail_multiplier", this.cloudBox.detail_multiplier);
		this.cloudsShader.setUniform1f("subtract_multiplier", this.cloudBox.subtract_multiplier);

		this.cloudsShader.setUniform1f("scale_main_1", this.cloudBox.scale_main_1);
		this.cloudsShader.setUniform1f("scale_main_2", this.cloudBox.scale_main_2);
		this.cloudsShader.setUniform1f("scale_detail", this.cloudBox.scale_detail);
		this.cloudsShader.setUniform1f("scale_subtract", this.cloudBox.scale_subtract);

		this.cloudsShader.setUniform1f("light_absorption_towards_sun", this.cloudBox.light_absorption_towards_sun);
		this.cloudsShader.setUniform1f("light_absorption_through_clouds", this.cloudBox.light_absorption_through_clouds);

		this.cloudsShader.setUniform1f("darkness_threshold", this.cloudBox.darkness_threshold);

		this.cloudsShader.setUniform1f("forward_scattering", this.cloudBox.forward_scattering);
		this.cloudsShader.setUniform1f("backward_scattering", this.cloudBox.backward_scattering);
		this.cloudsShader.setUniform1f("base_brightness", this.cloudBox.base_brightness);
		this.cloudsShader.setUniform1f("phase_factor", this.cloudBox.phase_factor);

		this.worleyNoise.bind(GL_TEXTURE0);
		outputBuffer.bind();
		SkyboxCube.skyboxCube.render();

		glViewport(0, 0, Main.windowWidth, Main.windowHeight);

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
