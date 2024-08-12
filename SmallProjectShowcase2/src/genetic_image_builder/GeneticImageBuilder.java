package genetic_image_builder;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;

import javax.imageio.ImageIO;

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
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;
import static org.lwjgl.opengl.GL31.*;
import static org.lwjgl.opengl.GL33.*;

import lwjglengine.graphics.Framebuffer;
import lwjglengine.graphics.Shader;
import lwjglengine.graphics.ShaderStorageBuffer;
import lwjglengine.graphics.Texture;
import lwjglengine.screen.ScreenQuad;
import lwjglengine.util.BufferUtils;
import lwjglengine.util.ShaderUtils;
import lwjglengine.window.AdjustableWindow;
import lwjglengine.window.FileCreatorWindow;
import lwjglengine.window.FileExplorerWindow;
import lwjglengine.window.FileSelectorWindow;
import lwjglengine.window.FileSelectorWindow.FileSelectorCallback;
import lwjglengine.window.ObjectEditorWindow;
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

public class GeneticImageBuilder extends Window implements FileSelectorCallback {

	private Texture spriteTexture;
	private Sprite[] sprites;

	private static final int GENERATION_POPULATION = 1024;

	private RenderOptions renderOptions;

	private Texture target, canvas;
	private AdjustableWindow targetW, canvasW;
	private int canvasWidth, canvasHeight;

	private ShaderStorageBuffer invMat4Buffer, materialBuffer, uvBuffer, scoreBuffer;

	private Shader scoreShader, drawShader;

	private boolean isBuilding = false;
	private int nrBuiltSprites = 0;

	//vertex array buffer handles
	private int vao, vbo, ibo;
	private int mat4bo, huebo, uvbo;

	private Framebuffer canvasFramebuffer;

	private static final int VERTEX_LOC = 0;
	private static final int INSTANCED_MAT4_LOC = 1; //takes 4 slots, 16 floats. 
	private static final int INSTANCED_HUE_LOC = 5;
	private static final int INSTANCED_UV_LOC = 6;

	public GeneticImageBuilder(int xOffset, int yOffset, int width, int height, Window parentWindow) {
		super(xOffset, yOffset, width, height, parentWindow);

		this.renderOptions = new RenderOptions();

		//options menu
		{
			AdjustableWindow adj = new AdjustableWindow("Rendering Options", new ObjectEditorWindow(this.renderOptions), this);
		}

		//load spritesheet
		this.spriteTexture = new Texture("/res/GD_decor/GJ_GameSheet-hd.png");

		//load spritesheet xml
		XMLNode root = null;
		{
			String xmlString = FileUtils.loadStringRelative("/res/GD_decor/GJ_GameSheet-hd.plist");
			root = XMLReader.parseStringAsXML(xmlString);

			File test_file = FileUtils.loadFileRelative("/res/test_xml.txt");
			try {
				root.saveToFile(test_file);
			}
			catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}

		XMLNode spriteDict = root;
		spriteDict = spriteDict.getChildren().get(2);
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
			int sx = loc.get(3);
			int sy = loc.get(2);

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
		this.target = new Texture("/res/astolfo 11.jpg", 0, GL_RGBA32F, GL_NEAREST, GL_NEAREST, 1);
		this.canvasWidth = this.target.getWidth();
		this.canvasHeight = this.target.getHeight();
		this.canvas = new Texture(this.canvasWidth, this.canvasHeight, GL_RGBA32F, 0, 0, 0, 255);

		this.canvasFramebuffer = new Framebuffer(this.canvasWidth, this.canvasHeight);
		this.canvasFramebuffer.bindTextureToBuffer(GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, this.canvas.getID());
		this.canvasFramebuffer.setDrawBuffers(new int[] { GL_COLOR_ATTACHMENT0 });
		this.canvasFramebuffer.isComplete();

		//display target and canvas to user
		{
			this.targetW = new AdjustableWindow("Target", new TextureViewerWindow(this.target), this);
			this.canvasW = new AdjustableWindow("Canvas", new TextureViewerWindow(this.canvas), this);
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
		this.scoreShader = ShaderUtils.createShader("/genetic_image_builder/calc_score.vert", "/genetic_image_builder/calc_score.frag");
		this.scoreShader.setUniform1i("tex_spritesheet", 0);
		this.drawShader = ShaderUtils.createShader("/genetic_image_builder/draw_sprite.vert", "/genetic_image_builder/draw_sprite.frag");
		this.drawShader.setUniform1i("tex_spritesheet", 0);

		//set up vertex array buffers
		float[] vertices = new float[] { 0, 0, 0, 1, 0, 0, 1, 1, 0, 0, 1, 0, };
		int[] indices = new int[] { 0, 1, 2, 0, 2, 3, };

		this.vao = glGenVertexArrays();
		glBindVertexArray(this.vao);

		this.vbo = glGenBuffers(); //vertices
		glBindBuffer(GL_ARRAY_BUFFER, this.vbo);
		glBufferData(GL_ARRAY_BUFFER, BufferUtils.createFloatBuffer(vertices), GL_STATIC_DRAW);
		glVertexAttribPointer(VERTEX_LOC, 3, GL_FLOAT, false, 0, 0);
		glEnableVertexAttribArray(VERTEX_LOC);

		this.ibo = glGenBuffers(); //indices
		glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, ibo);
		glBufferData(GL_ELEMENT_ARRAY_BUFFER, BufferUtils.createIntBuffer(indices), GL_STATIC_DRAW);

		this.mat4bo = glGenBuffers(); //model mat4s
		glBindBuffer(GL_ARRAY_BUFFER, this.mat4bo);
		glBufferData(GL_ARRAY_BUFFER, GENERATION_POPULATION * 16 * 4, GL_DYNAMIC_DRAW);

		this.huebo = glGenBuffers(); //hue
		glBindBuffer(GL_ARRAY_BUFFER, this.huebo);
		glBufferData(GL_ARRAY_BUFFER, GENERATION_POPULATION * 4 * 4, GL_DYNAMIC_DRAW);

		this.uvbo = glGenBuffers(); //uvs
		glBindBuffer(GL_ARRAY_BUFFER, this.uvbo);
		glBufferData(GL_ARRAY_BUFFER, GENERATION_POPULATION * 4 * 4, GL_DYNAMIC_DRAW);

		glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, 0);
		glBindBuffer(GL_ARRAY_BUFFER, 0);
		glBindVertexArray(0);
	}

	private void resetCanvas() {
		this.canvasFramebuffer.kill();
		this.canvas = new Texture(this.canvasWidth, this.canvasHeight, GL_RGBA32F, 0, 0, 0, 255);

		this.canvasFramebuffer = new Framebuffer(this.canvasWidth, this.canvasHeight);
		this.canvasFramebuffer.bindTextureToBuffer(GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, this.canvas.getID());
		this.canvasFramebuffer.setDrawBuffers(new int[] { GL_COLOR_ATTACHMENT0 });
		this.canvasFramebuffer.isComplete();

		((TextureViewerWindow) this.canvasW.getContentWindow()).setTexture(this.canvas);

		this.nrBuiltSprites = 0;
	}

	private void setTarget(BufferedImage img) {
		this.isBuilding = false;

		this.target.kill();

		this.target = new Texture(img, 0, GL_RGBA32F, GL_NEAREST, GL_NEAREST, 1);
		this.canvasWidth = this.target.getWidth();
		this.canvasHeight = this.target.getHeight();

		((TextureViewerWindow) this.targetW.getContentWindow()).setTexture(this.target);

		this.resetCanvas();

		this.nrBuiltSprites = 0;
	}

	@Override
	protected void _kill() {
		this.target.kill();
		this.canvas.kill();
		this.canvasFramebuffer.kill();
		this.spriteTexture.kill();

		this.invMat4Buffer.kill();
		this.materialBuffer.kill();
		this.uvBuffer.kill();
		this.scoreBuffer.kill();

		this.scoreShader.kill();
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
			//set some opengl stuff
			glViewport(0, 0, this.canvasWidth, this.canvasHeight);
			glDisable(GL_DEPTH_TEST);
			glEnable(GL_BLEND);
			glDisable(GL_CULL_FACE);
			glDisable(GL_BLEND);
			glPolygonMode(GL_FRONT, GL_FILL);
			Mat4 pr_matrix = Mat4.orthographic(0, this.canvasWidth, 0, this.canvasHeight, -100, 100);

			//initialize population
			SpriteInstance[] pop = new SpriteInstance[GENERATION_POPULATION];
			for (int i = 0; i < pop.length; i++) {
				pop[i] = new SpriteInstance();
			}

			int gen_cnt = 0;
			for (int i = 0; (i < this.renderOptions.nrGenerations || pop[0].score > this.renderOptions.passScoreThreshold) && (gen_cnt < this.renderOptions.generationCutoff); i++) {
				gen_cnt++;

				//populate buffers
				Mat4[] mat4s = new Mat4[GENERATION_POPULATION];
				Vec4[] hues = new Vec4[GENERATION_POPULATION];
				Vec4[] uvs = new Vec4[GENERATION_POPULATION];
				int[] sprite_ids = new int[GENERATION_POPULATION];

				for (int j = 0; j < pop.length; j++) {
					mat4s[j] = pop[j].generateTransform();
					hues[j] = pop[j].hue;
					uvs[j] = new Vec4(this.sprites[pop[j].ID].uv_00, this.sprites[pop[j].ID].uv_11);
					sprite_ids[j] = j;
				}

				glBindVertexArray(this.vao);
				glBindBuffer(GL_ARRAY_BUFFER, this.mat4bo);
				glBufferSubData(GL_ARRAY_BUFFER, 0, BufferUtils.createFloatBuffer(mat4s));
				for (int j = 0; j < 4; j++) {
					glVertexAttribPointer(INSTANCED_MAT4_LOC + j, 4, GL_FLOAT, false, 16 * 4, 16 * j);
					glVertexAttribDivisor(INSTANCED_MAT4_LOC + j, 1);
					glEnableVertexAttribArray(INSTANCED_MAT4_LOC + j);
				}

				glBindBuffer(GL_ARRAY_BUFFER, this.huebo);
				glBufferSubData(GL_ARRAY_BUFFER, 0, BufferUtils.createFloatBuffer(hues));
				glVertexAttribPointer(INSTANCED_HUE_LOC, 4, GL_FLOAT, false, 4 * 4, 0);
				glVertexAttribDivisor(INSTANCED_HUE_LOC, 1);
				glEnableVertexAttribArray(INSTANCED_HUE_LOC);

				glBindBuffer(GL_ARRAY_BUFFER, this.uvbo);
				glBufferSubData(GL_ARRAY_BUFFER, 0, BufferUtils.createFloatBuffer(uvs));
				glVertexAttribPointer(INSTANCED_UV_LOC, 4, GL_FLOAT, false, 4 * 4, 0);
				glVertexAttribDivisor(INSTANCED_UV_LOC, 1);
				glEnableVertexAttribArray(INSTANCED_UV_LOC);

				this.scoreBuffer.setSubData(new float[GENERATION_POPULATION], 0);

				//compute scores
				this.canvasFramebuffer.bind();
				this.scoreShader.enable();
				this.scoreShader.setUniformMat4("pr_matrix", pr_matrix);
				this.scoreShader.setUniform1i("canvas_width", this.canvasWidth);
				this.scoreShader.setUniform1i("canvas_height", this.canvasHeight);
				this.spriteTexture.bind(GL_TEXTURE0);
				glBindImageTexture(1, this.canvas.getID(), 0, true, 0, GL_READ_WRITE, GL_RGBA32F);
				glBindImageTexture(2, this.target.getID(), 0, true, 0, GL_READ_WRITE, GL_RGBA32F);
				this.scoreBuffer.bindToBase(3);

				glBindVertexArray(this.vao);
				glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, this.ibo);
				glDrawElementsInstanced(GL_TRIANGLES, 6, GL_UNSIGNED_INT, 0, GENERATION_POPULATION);

				//reproduce
				float[] scoreData = new float[GENERATION_POPULATION];
				this.scoreBuffer.getSubData(scoreData, 0);
				for (int j = 0; j < pop.length; j++) {
					pop[j].score = scoreData[j];
				}
				Arrays.sort(pop, (a, b) -> Float.compare(a.score, b.score));
				System.out.println("Generation " + gen_cnt + " best score : " + pop[0].score);
				int nr_survived = pop.length;
				for (int j = 0; j < pop.length; j++) {
					if (pop[j].score > this.renderOptions.surviveScoreThreshold) {
						nr_survived = j;
						break;
					}
				}
				nr_survived = Math.min(nr_survived, this.renderOptions.surviveMax);
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
			}

			if (gen_cnt == this.renderOptions.generationCutoff) {
				System.out.println("Spent " + this.renderOptions.generationCutoff + " generations, but nothing passed. Toggling off building.");
				this.isBuilding = false;
			}
			else {
				this.nrBuiltSprites++;
				System.out.println("Found sprite no. " + this.nrBuiltSprites + " score " + pop[0].score + " after " + gen_cnt + " generations");

				//choose sprite with best score and add to canvas
				this.canvasFramebuffer.bind();
				this.drawShader.enable();
				this.drawShader.setUniformMat4("md_matrix", pop[0].generateTransform());
				this.drawShader.setUniform4f("hue", pop[0].hue);
				this.drawShader.setUniform4f("uv", new Vec4(this.sprites[pop[0].ID].uv_00, this.sprites[pop[0].ID].uv_11));
				this.drawShader.setUniformMat4("pr_matrix", pr_matrix);
				this.spriteTexture.bind(GL_TEXTURE0);
				glBindImageTexture(1, this.canvas.getID(), 0, true, 0, GL_READ_WRITE, GL_RGBA32F);
				glBindImageTexture(2, this.target.getID(), 0, true, 0, GL_READ_WRITE, GL_RGBA32F);

				glBindVertexArray(this.vao);
				glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, this.ibo);
				glDrawElements(GL_TRIANGLES, 6, GL_UNSIGNED_INT, 0);
			}

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

		case GLFW_KEY_C: {
			AdjustableWindow adj = new AdjustableWindow("Select Target Image", new FileSelectorWindow(this), this);
			break;
		}

		case GLFW_KEY_S: {
			BufferedImage img = this.canvas.toBufferedImage();
			AdjustableWindow adj = new AdjustableWindow("Save Image As", new FileCreatorWindow(img), this);
			break;
		}

		case GLFW_KEY_R: {
			this.resetCanvas();
			break;
		}
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
			this.ID = (int) (Math.random() * sprites.length);
			this.rot = (float) (Math.random() * Math.PI * 2.0);
			this.scale = MathUtils.random(5, 100);
			this.offset = new Vec2(MathUtils.random(0, canvasWidth), MathUtils.random(0, canvasHeight));
			this.hue = new Vec4(Math.random(), Math.random(), Math.random(), 1);
			this.hue.w = 1;
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
				this.scale = Math.min(this.scale, 500);
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

	public class RenderOptions {
		public int nrGenerations = 16;
		public float surviveScoreThreshold = 5;
		public float passScoreThreshold = -30;
		public int surviveMax = 64;
		public int generationCutoff = 1000;

		public RenderOptions() {

		}

		public int getNrGenerations() {
			return nrGenerations;
		}

		public void setNrGenerations(int nrGenerations) {
			this.nrGenerations = nrGenerations;
		}

		public float getSurviveScoreThreshold() {
			return surviveScoreThreshold;
		}

		public void setSurviveScoreThreshold(float surviveScoreThreshold) {
			this.surviveScoreThreshold = surviveScoreThreshold;
		}

		public float getPassScoreThreshold() {
			return passScoreThreshold;
		}

		public void setPassScoreThreshold(float passScoreThreshold) {
			this.passScoreThreshold = passScoreThreshold;
		}

		public int getSurviveMax() {
			return surviveMax;
		}

		public void setSurviveMax(int surviveMax) {
			this.surviveMax = surviveMax;
		}

		public int getGenerationCutoff() {
			return generationCutoff;
		}

		public void setGenerationCutoff(int generationCutoff) {
			this.generationCutoff = generationCutoff;
		}
	}

	@Override
	public void handleCallback(File[] files) {
		if (files.length != 1) {
			return;
		}

		//parse as buffered image
		BufferedImage img = FileUtils.loadImage(files[0]);
		this.setTarget(img);
	}
}
