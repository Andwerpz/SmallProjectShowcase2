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
import lwjglengine.window.TextureViewerWindow;
import lwjglengine.window.Window;
import myutils.v10.math.Vec2;
import myutils.v10.math.Vec3;
import myutils.v11.file.FileUtils;
import myutils.v11.file.JarUtils;

public class VolumetricCloudsWindow extends Window {

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

		this.cloudsShader = new Shader("/volumetric_clouds/clouds.vert", "/volumetric_clouds/clouds.frag");
		this.cloudsShader.setUniform1i("tex_worley_noise", 0);

		this.cloudBox = new CloudBoundingBox(new Vec3(0, 0, 0), new Vec3(50, 10, 50));
		this.cloudBox.setDrawBoundingLines(true);
		
		this.worleyNoise = this.generateWorleyNoise(16, 8);

		this._resize();
	}
	
	private Texture3D generateWorleyNoise(int cellSize, int nrCells) {
		int texSize = cellSize * nrCells;
		int[] data = new int[texSize * texSize * texSize];
		
		float[][][] noise = new float[texSize][texSize][texSize];
		
		//generate points
		//place the points one per cell
		Vec3[][][] points = new Vec3[nrCells][nrCells][nrCells];
		for(int i = 0; i < nrCells; i++) {
			for(int j = 0; j < nrCells; j++) {
				for(int k = 0; k < nrCells; k++) {
					Vec3 point = new Vec3(i * cellSize, j * cellSize, k * cellSize);
					point.x += (float) (Math.random() * cellSize);
					point.y += (float) (Math.random() * cellSize);
					point.z += (float) (Math.random() * cellSize);
					points[i][j][k] = point;
				}
			}
		}
		
		int[] dr = {-1, -1, -1, -1, -1, -1, -1, -1, -1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1};
		int[] dc = {-1, -1, -1, 0, 0, 0, 1, 1, 1, -1, -1, -1, 0, 0, 0, 1, 1, 1, -1, -1, -1, 0, 0, 0, 1, 1, 1};
		int[] dl = {-1, 0, 1, -1, 0, 1, -1, 0, 1, -1, 0, 1, -1, 0, 1, -1, 0, 1, -1, 0, 1, -1, 0, 1, -1, 0, 1};
		
		//calculate distances
		float maxDist = 0;
		for(int i = 0; i < texSize; i++) {
			for(int j = 0; j < texSize; j++) {
				for(int k = 0; k < texSize; k++) {
					Vec3 cur = new Vec3(i, j, k);
					float minDist = (float) 1e9;
					for(int l = 0; l < dr.length; l++) {
						int nr = (i / cellSize) + dr[l];
						int nc = (j / cellSize) + dc[l];
						int nl = (k / cellSize) + dl[l];
						int horizontalAdj = 0;
						int verticalAdj = 0;
						int layerAdj = 0;
						if(nr < 0) {
							horizontalAdj = -1;
							nr += nrCells;
						}
						if(nr >= nrCells) {
							horizontalAdj = 1;
							nr -= nrCells;
						}
						if(nc < 0) {
							verticalAdj = -1;
							nc += nrCells;
						}
						if(nc >= nrCells) {
							verticalAdj = 1;
							nc -= nrCells;
						}
						if(nl < 0) {
							layerAdj = -1;
							nl += nrCells;
						}
						if(nl >= nrCells) {
							layerAdj = 1;
							nl -= nrCells;
						}
						Vec3 point = new Vec3(points[nr][nc][nl]);
						point.x += texSize * horizontalAdj;
						point.y += texSize * verticalAdj;
						point.z += texSize * layerAdj;
						
						minDist = Math.min(minDist, point.sub(cur).lengthSq());
					}
					minDist = (float) Math.sqrt(minDist);
					maxDist = Math.max(maxDist, minDist);
					noise[i][j][k] = minDist;
				}
			}
		}
		
		//normalize and invert
		for(int i = 0; i < texSize; i++) {
			for(int j = 0; j < texSize; j++) {
				for(int k = 0; k < texSize; k++) {
					noise[i][j][k] /= maxDist;
					noise[i][j][k] = 1.0f - noise[i][j][k];
				}
			}
		}
		
		//write to data
		int ind = 0;
		for(int depth = 0; depth < texSize; depth++) {
			for(int row = 0; row < texSize; row++) {
				for(int col = 0; col < texSize; col++) {
					int color = (int) (255.0f * noise[row][col][depth]);
					int rgb = (255 << 24) + (color << 16) + (color << 8) + (color << 0);
					data[ind] = rgb;
					
					ind ++;
				}
			}
		}
		
		Texture3D tex = new Texture3D(texSize, texSize, texSize, data);
		return tex;
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

	class CloudBoundingBox {

		public Vec3[] baseCorners = new Vec3[] { new Vec3(-0.5f, -0.5f, -0.5f), new Vec3(-0.5f, -0.5f, 0.5f), new Vec3(0.5f, -0.5f, 0.5f), new Vec3(0.5f, -0.5f, -0.5f), new Vec3(-0.5f, 0.5f, -0.5f), new Vec3(-0.5f, 0.5f, 0.5f), new Vec3(0.5f, 0.5f, 0.5f), new Vec3(0.5f, 0.5f, -0.5f), };

		public boolean drawBoundingLines = false;
		public ModelInstance[] boundingLines = null;

		public Vec3 pos; //refers to the center of the box. 
		public Vec3 scale; //individual scaling of x y z

		public CloudBoundingBox() {
			this.init();
		}

		public CloudBoundingBox(Vec3 pos, Vec3 scale) {
			this.init();
			this.setPos(pos);
			this.setScale(scale);
		}

		private void init() {
			this.pos = new Vec3(0);
			this.scale = new Vec3(1);
		}

		public void setPos(Vec3 pos) {
			this.pos.set(pos);
			this.updateBoundingLineInstances();
		}

		public void setScale(Vec3 scale) {
			this.scale.set(scale);
			this.updateBoundingLineInstances();
		}

		public void setDrawBoundingLines(boolean b) {
			this.drawBoundingLines = b;

			if (this.drawBoundingLines) {
				this.updateBoundingLineInstances();
			}
			else {
				//kill bounding lines
				if (this.boundingLines != null) {
					for (int i = 0; i < this.boundingLines.length; i++) {
						this.boundingLines[i].kill();
					}
				}
				this.boundingLines = null;
			}
		}

		private void updateBoundingLineInstances() {
			if (!this.drawBoundingLines) {
				return;
			}

			if (this.boundingLines == null) {
				this.boundingLines = new ModelInstance[12];
				for (int i = 0; i < this.boundingLines.length; i++) {
					this.boundingLines[i] = Line.addLine(new Vec3(0), new Vec3(0), WORLD_SCENE);
				}
			}

			//enumerate all the corners. 
			Vec3[] corners = new Vec3[8];
			for (int i = 0; i < 8; i++) {
				corners[i] = this.baseCorners[i].mul(this.scale.x, this.scale.y, this.scale.z).add(this.pos);
			}

			//bottom ring
			this.boundingLines[0].setModelTransform(Line.generateLineModelTransform(corners[0], corners[1]));
			this.boundingLines[1].setModelTransform(Line.generateLineModelTransform(corners[1], corners[2]));
			this.boundingLines[2].setModelTransform(Line.generateLineModelTransform(corners[2], corners[3]));
			this.boundingLines[3].setModelTransform(Line.generateLineModelTransform(corners[3], corners[0]));

			//top ring
			this.boundingLines[4].setModelTransform(Line.generateLineModelTransform(corners[4], corners[5]));
			this.boundingLines[5].setModelTransform(Line.generateLineModelTransform(corners[5], corners[6]));
			this.boundingLines[6].setModelTransform(Line.generateLineModelTransform(corners[6], corners[7]));
			this.boundingLines[7].setModelTransform(Line.generateLineModelTransform(corners[7], corners[4]));

			//connecting edges
			this.boundingLines[8].setModelTransform(Line.generateLineModelTransform(corners[0], corners[4]));
			this.boundingLines[9].setModelTransform(Line.generateLineModelTransform(corners[1], corners[5]));
			this.boundingLines[10].setModelTransform(Line.generateLineModelTransform(corners[2], corners[6]));
			this.boundingLines[11].setModelTransform(Line.generateLineModelTransform(corners[3], corners[7]));
		}

	}

}
