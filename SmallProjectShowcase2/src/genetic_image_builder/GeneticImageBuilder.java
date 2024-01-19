package genetic_image_builder;

import java.util.ArrayList;
import java.util.Arrays;

import static org.lwjgl.glfw.GLFW.*;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL13.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL21.*;
import static org.lwjgl.opengl.GL30.*;
import static org.lwjgl.opengl.GL31.*;
import static org.lwjgl.opengl.GL32.*;
import static org.lwjgl.opengl.GL33.*;
import static org.lwjgl.opengl.GL42.GL_SHADER_IMAGE_ACCESS_BARRIER_BIT;
import static org.lwjgl.opengl.GL42.glBindImageTexture;
import static org.lwjgl.opengl.GL42.glMemoryBarrier;
import static org.lwjgl.opengl.GL43.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;
import static org.lwjgl.opengl.GL31.*;
import static org.lwjgl.opengl.GL33.*;

import lwjglengine.graphics.Framebuffer;
import lwjglengine.graphics.Shader;
import lwjglengine.graphics.ShaderStorageBuffer;
import lwjglengine.graphics.Texture;
import lwjglengine.util.ShaderUtils;
import lwjglengine.window.AdjustableWindow;
import lwjglengine.window.TextureViewerWindow;
import lwjglengine.window.Window;
import myutils.file.FileUtils;
import myutils.file.xml.XMLNode;
import myutils.file.xml.XMLReader;
import myutils.math.IVec2;
import myutils.math.Mat4;
import myutils.math.MathUtils;
import myutils.math.Vec2;
import myutils.math.Vec3;
import myutils.math.Vec4;

public class GeneticImageBuilder extends Window {

	//TODO 
	// - get atomic floats to work
	//   - GL_NV_shader_atomic_float
	//   - only works on NVIDIA hardware
	// - move this to the vertex / fragment shader pipeline so that we don't have to loop through all canvas pixels. 
	//   - probably will have to implement custom vertex array
	//   - per instance model transforms, hues, uvs.
	//   - don't need projection matrix, we'll save that for the rendering step
	//   - target and canvas provided through uniforms
	//   - score kept through ssbo of atomic floats. 

	private Texture spriteTexture;
	private Sprite[] sprites;

	private static final int NR_GENERATIONS = 16;
	private static final int GENERATION_POPULATION = 1024;
	private static final float SURVIVE_SCORE_THRESHOLD = -50;
	private static final int SURVIVE_MAX = 32;

	private Texture target, canvas;
	private int canvasWidth, canvasHeight;

	private ShaderStorageBuffer invMat4Buffer, materialBuffer, uvBuffer, scoreBuffer;

	private Shader deltaShader, drawShader;

	private boolean isBuilding = false;

	public GeneticImageBuilder(int xOffset, int yOffset, int width, int height, Window parentWindow) {
		super(xOffset, yOffset, width, height, parentWindow);

		//load spritesheet
		this.spriteTexture = new Texture("/res/GD_decor/GJ_GameSheet-hd.png");

		//load spritesheet xml
		XMLNode root = null;
		{
			String xmlString = FileUtils.loadAsStringRelative("/res/GD_decor/GJ_GameSheet-hd.plist");
			root = XMLReader.parseStringAsXML(xmlString);
		}

		XMLNode spriteDict = root;
		spriteDict = spriteDict.getChildren().get(0);
		spriteDict = spriteDict.getChildren().get(1);

		//even indices are sprite names, odd ones hold sprite information
		//in .plist, the 'textureRect' attribute holds {top left corner, {width, height}}
		this.sprites = new Sprite[spriteDict.getChildren().size() / 2];
		for (int i = 0; i < spriteDict.getChildren().size() / 2; i++) {
			XMLNode key = spriteDict.getChildren().get(i * 2 + 0);
			XMLNode dict = spriteDict.getChildren().get(i * 2 + 1);

			String spriteName = key.getContent().get(0);
			String spriteLoc = dict.getChildren().get(9).getContent().get(0);

			ArrayList<Integer> loc = new ArrayList<>();
			for (int j = 0; j < spriteLoc.length();) {
				char c = spriteLoc.charAt(j);
				if (c == '{' || c == '}' || c == ',') {
					j++;
					continue;
				}
				int r = j;
				while (true) {
					c = spriteLoc.charAt(r);
					if (c == '{' || c == '}' || c == ',') {
						break;
					}
					r++;
				}
				loc.add(Integer.parseInt(spriteLoc.substring(j, r)));
				j = r;
			}

			//size
			int sx = loc.get(2);
			int sy = loc.get(3);

			//offset from top left
			int ox = loc.get(0);
			int oy = loc.get(1);

			//convert to offset from bottom left
			oy = this.spriteTexture.getHeight() - 1 - oy;

			//now it refers to bottom left corner of sprite
			oy -= sy;

			IVec2 size = new IVec2(sx, sy);
			Vec2 uv_00 = new Vec2(ox, oy);
			Vec2 uv_11 = new Vec2(ox + sx, oy + sy);
			uv_00.x /= this.spriteTexture.getWidth();
			uv_00.y /= this.spriteTexture.getHeight();
			uv_11.x /= this.spriteTexture.getWidth();
			uv_11.y /= this.spriteTexture.getHeight();

			Sprite s = new Sprite(size, uv_00, uv_11);
			this.sprites[i] = s;
		}

		//choose target
		this.target = new Texture("/res/laugh-point.png", 0, GL_RGBA32F, GL_NEAREST, GL_NEAREST, 1);
		this.canvasWidth = this.target.getWidth();
		this.canvasHeight = this.target.getHeight();
		this.canvas = new Texture(GL_RGBA32F, this.canvasWidth, this.canvasHeight, GL_RGBA, GL_FLOAT);

		//display target and canvas to user
		{
			AdjustableWindow targetV = new AdjustableWindow("Target", new TextureViewerWindow(this.target), this);
			AdjustableWindow canvasV = new AdjustableWindow("Canvas", new TextureViewerWindow(this.canvas), this);
		}

		//set up shader buffers
		this.invMat4Buffer = new ShaderStorageBuffer();
		this.materialBuffer = new ShaderStorageBuffer();
		this.uvBuffer = new ShaderStorageBuffer();
		this.scoreBuffer = new ShaderStorageBuffer();

		this.invMat4Buffer.setUsage(GL_DYNAMIC_READ);
		this.materialBuffer.setUsage(GL_DYNAMIC_READ);
		this.uvBuffer.setUsage(GL_DYNAMIC_READ);
		this.scoreBuffer.setUsage(GL_DYNAMIC_READ);

		this.invMat4Buffer.setSize(GENERATION_POPULATION * 16 * 4); //mat4 for each sprite
		this.materialBuffer.setSize(GENERATION_POPULATION * 4 * 4); //vec4 for each sprite
		this.uvBuffer.setSize(GENERATION_POPULATION * 4 * 4);
		this.scoreBuffer.setSize(GENERATION_POPULATION * 4);//float for each sprite

		//init shaders
		this.deltaShader = ShaderUtils.createShader("/genetic_image_builder/calc_delta.compute", GL_COMPUTE_SHADER);
		this.drawShader = ShaderUtils.createShader("/genetic_image_builder/draw_sprite.compute", GL_COMPUTE_SHADER);
	}

	@Override
	protected void _kill() {
		this.target.kill();
		this.canvas.kill();
		this.spriteTexture.kill();

		this.invMat4Buffer.kill();
		this.materialBuffer.kill();
		this.uvBuffer.kill();
		this.scoreBuffer.kill();

		this.deltaShader.kill();
		this.drawShader.kill();
	}

	@Override
	protected void _resize() {

	}

	@Override
	public String getDefaultTitle() {
		return "Genetic Image Builder";
	}

	@Override
	protected void _update() {
		if (this.isBuilding) {
			//initialize population
			SpriteInstance[] pop = new SpriteInstance[GENERATION_POPULATION];
			for (int i = 0; i < pop.length; i++) {
				pop[i] = new SpriteInstance();
			}

			int gen_cnt = 0;
			for (int i = 0; i < NR_GENERATIONS || pop[0].score > SURVIVE_SCORE_THRESHOLD; i++) {
				gen_cnt++;

				//populate buffers
				float[] invMat4Data = new float[GENERATION_POPULATION * 16];
				float[] materialData = new float[GENERATION_POPULATION * 4];
				float[] uvData = new float[GENERATION_POPULATION * 4];
				for (int j = 0; j < pop.length; j++) {
					float[] matFloats = pop[j].generateInvTransform().toFloatArray();
					for (int k = 0; k < 16; k++) {
						invMat4Data[j * 16 + k] = matFloats[k];
					}
					materialData[j * 4 + 0] = pop[j].hue.x;
					materialData[j * 4 + 1] = pop[j].hue.y;
					materialData[j * 4 + 2] = pop[j].hue.z;
					materialData[j * 4 + 3] = pop[j].hue.w;
					uvData[j * 4 + 0] = this.sprites[pop[j].ID].uv_00.x;
					uvData[j * 4 + 1] = this.sprites[pop[j].ID].uv_00.y;
					uvData[j * 4 + 2] = this.sprites[pop[j].ID].uv_11.x;
					uvData[j * 4 + 3] = this.sprites[pop[j].ID].uv_11.y;
				}
				this.invMat4Buffer.setSubData(invMat4Data, 0);
				this.materialBuffer.setSubData(materialData, 0);
				this.uvBuffer.setSubData(uvData, 0);

				//compute scores
				this.deltaShader.enable();
				this.deltaShader.setUniform1i("canvas_width", this.canvasWidth);
				this.deltaShader.setUniform1i("canvas_height", this.canvasHeight);

				this.invMat4Buffer.bindToBase(0);
				this.materialBuffer.bindToBase(1);
				this.uvBuffer.bindToBase(2);
				this.scoreBuffer.bindToBase(3);
				glBindImageTexture(4, this.canvas.getID(), 0, true, 0, GL_READ_WRITE, GL_RGBA32F);
				glBindImageTexture(5, this.target.getID(), 0, true, 0, GL_READ_ONLY, GL_RGBA32F);
				this.spriteTexture.bind(GL_TEXTURE6);

				glDispatchCompute(GENERATION_POPULATION, 1, 1);
				glMemoryBarrier(GL_SHADER_IMAGE_ACCESS_BARRIER_BIT);

				//reproduce
				float[] scoreData = new float[GENERATION_POPULATION];
				this.scoreBuffer.getSubData(scoreData, 0);
				for (int j = 0; j < pop.length; j++) {
					pop[j].score = scoreData[j];
				}
				Arrays.sort(pop, (a, b) -> Float.compare(a.score, b.score));
				int nr_survived = pop.length;
				for (int j = 0; j < pop.length; j++) {
					if (pop[j].score > SURVIVE_SCORE_THRESHOLD) {
						nr_survived = j;
						break;
					}
				}
				nr_survived = Math.min(nr_survived, SURVIVE_MAX);
				if (nr_survived == 0) {
					//regenerate everyone
					for (int j = 0; j < pop.length; j++) {
						pop[j] = new SpriteInstance();
					}
				}
				else {
					//mutate or regenerate
					for (int j = nr_survived; j < pop.length; j++) {
						float p = (float) Math.random();
						if (p < 0.2) {
							//create a brand new instance
							pop[j] = new SpriteInstance();
						}
						else {
							//pick a survivor and mutate them
							int ind = (int) (Math.random() * nr_survived);
							pop[j] = new SpriteInstance(pop[ind]);
							pop[j].mutate();
						}
					}
				}

				if (nr_survived == 0) {
					i--;
				}
				System.out.println("Generation " + gen_cnt + " best score : " + pop[0].score);
			}

			System.out.println("Found score : " + pop[0].score + " after " + gen_cnt + " generations");
			System.out.println(pop[0].scale + " " + pop[0].rot + " " + pop[0].offset + " " + pop[0].hue);

			//choose sprite with best score and add to canvas
			this.drawShader.enable();
			this.drawShader.setUniform1i("canvas_width", this.canvas.getWidth());
			this.drawShader.setUniform1i("canvas_height", this.canvas.getHeight());

			Mat4 inv = pop[0].generateInvTransform();
			inv.transpose();
			this.drawShader.setUniformMat4("inv_model_mat", inv);
			this.drawShader.setUniform4f("sprite_material", pop[0].hue);
			this.drawShader.setUniform2f("uv_00", this.sprites[pop[0].ID].uv_00);
			this.drawShader.setUniform2f("uv_11", this.sprites[pop[0].ID].uv_11);

			glBindImageTexture(0, this.canvas.getID(), 0, true, 0, GL_READ_WRITE, GL_RGBA32F);
			this.spriteTexture.bind(GL_TEXTURE1);

			glDispatchCompute(1, 1, 1);
			glMemoryBarrier(GL_SHADER_IMAGE_ACCESS_BARRIER_BIT);
		}
	}

	@Override
	protected void renderContent(Framebuffer outputBuffer) {
		// TODO Auto-generated method stub

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
		switch (key) {
		case GLFW_KEY_T:
			this.isBuilding = !this.isBuilding;
			break;
		}
	}

	@Override
	protected void _keyReleased(int key) {
		// TODO Auto-generated method stub

	}

	class SpriteInstance {
		public int ID;
		public float rot, scale;
		public Vec2 offset;
		public Vec4 hue;
		public float score; //assigned externally

		//generate with some random attributes
		public SpriteInstance() {
			//			this.ID = 0;
			//			this.rot = 0;
			//			this.scale = 50;
			//			this.offset = new Vec2(MathUtils.random(0, canvasWidth), MathUtils.random(0, canvasHeight));
			//			this.hue = new Vec4(1);

			this.ID = (int) (Math.random() * sprites.length);
			this.rot = (float) (Math.random() * Math.PI * 2.0);
			this.scale = MathUtils.random(25, 500);
			this.offset = new Vec2(MathUtils.random(0, canvasWidth), MathUtils.random(0, canvasHeight));
			this.hue = new Vec4(Math.random(), Math.random(), Math.random(), 1);
		}

		public SpriteInstance(SpriteInstance other) {
			this.ID = other.ID;
			this.rot = other.rot;
			this.scale = other.scale;
			this.offset = new Vec2(other.offset);
			this.hue = new Vec4(other.hue);
		}

		public Mat4 generateTransform() {
			float sx = sprites[this.ID].size.x;
			float sy = sprites[this.ID].size.y;
			float scaleX = sx / Math.max(sx, sy);
			float scaleY = sy / Math.max(sx, sy);

			Mat4 mat = Mat4.identity();
			mat.muli(Mat4.translate(new Vec2(-0.5f)));
			mat.muli(Mat4.scale(scaleX, scaleY, 1));
			mat.muli(Mat4.scale(this.scale));
			mat.muli(Mat4.rotateZ(this.rot));
			mat.muli(Mat4.translate(this.offset));
			return mat;
		}

		public Mat4 generateInvTransform() {
			Mat4 mat = this.generateTransform();
			mat.invertAffine();
			return mat;
		}

		public void mutate() {
			float p = (float) Math.random();

			if (p < 0.25) {
				//modify rot
				this.rot += MathUtils.random(-0.2f, 0.2f);
			}
			else if (p < 0.50) {
				//modify scale
				this.scale *= MathUtils.random(0.75f, 1.33f);
			}
			else if (p < 0.75) {
				//modify offset
				Vec2 dir = new Vec2(1, 0).muli(MathUtils.random(5, 100)).rotate(MathUtils.random(0, (float) Math.PI * 2f));
				this.offset.addi(dir);
				this.offset.x = MathUtils.clamp(0, canvasWidth, this.offset.x);
				this.offset.y = MathUtils.clamp(0, canvasHeight, this.offset.y);
			}
			else {
				//modify hue
				p = (float) Math.random();
				if (p < 0.33) {
					this.hue.x = (float) Math.random();
				}
				else if (p < 0.66) {
					this.hue.y = (float) Math.random();
				}
				else {
					this.hue.z = (float) Math.random();
				}
			}
		}
	}

	class Sprite {
		public IVec2 size;
		public Vec2 uv_00, uv_11;

		public Sprite(IVec2 size, Vec2 uv_00, Vec2 uv_11) {
			this.size = new IVec2(size);
			this.uv_00 = new Vec2(uv_00);
			this.uv_11 = new Vec2(uv_11);
		}
	}

}
