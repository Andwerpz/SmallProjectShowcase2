package csce_vis.hw2;

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

import java.util.ArrayDeque;
import java.util.Queue;

import org.lwjgl.glfw.GLFW;

import lwjglengine.graphics.Framebuffer;
import lwjglengine.graphics.Shader;
import lwjglengine.graphics.ShaderStorageBuffer;
import lwjglengine.graphics.Texture;
import lwjglengine.player.Camera;
import lwjglengine.player.PlayerInputController;
import lwjglengine.screen.ScreenQuad;
import lwjglengine.util.BufferUtils;
import lwjglengine.util.ShaderUtils;
import lwjglengine.window.Window;
import myutils.math.Mat4;
import myutils.math.MathUtils;
import myutils.math.Vec3;

public class HW2Window extends Window {

	//TODO
	// - ok, seems like we hit a bottleneck on rendering at around 8 million particles. 
	//   - ideas for speeding up rendering?
	// - mark dead particles as dead, the CPU will occasionally check through particles to find dead ones. 
	//   - CPU also has to create new alive particles, so CPU knows which particles to check. 
	//   - making efficient dead checks may be challenging. 
	//   - if being dead is based off of lifespan though, then it isn't that bad, just throw a pqueue at it. 
	//   - when creating particles, create them randomly throughout the buffer. This way we don't even need
	//     dead checks. GPU just has to mark particle as dead, and when rendering, just don't render. 
	//   - however, initializing randomly has it's own issues, namely you will have to query data from GPU many times. 
	//     Instead, we can initialize blocks of particles. 
	// - physics scene
	//   - perhaps use BVH to speed up particle vs environment collisions?
	// - correctly render particles
	//   - currently, im doing very jank thing of just calling glRenderArrays without having an array bound
	//   - perhaps rendering will be faster with a vao / vbo?

	//maximum amount of particles possible within buffer
	private static final int MAX_PARTICLE_CNT = (1 << 20);

	//how many particles we'll read from the buffer to try to generate new ones
	//should be a divisor of MAX_PARTICLE_CNT
	private static final int GEN_BATCH_SIZE = (1 << 12);

	//how many batches we'll pull out of the particle buffer before giving up on generating particles
	//per update
	private static final int GEN_MAX_TRIES = (1 << 4);

	private int genPtr = 0;
	private Queue<Particle> genList;

	private ShaderStorageBuffer posbo; //{x, y, z, lifespan}
	private ShaderStorageBuffer velbo; //{vx, vy, vz, -1}
	private ShaderStorageBuffer huebo; //{r, g, b, -1}

	private Shader particleUpdateShader, particleRenderShader;

	private PlayerInputController pic;

	private Framebuffer renderBuffer = null;
	private Texture renderColorMap;

	public HW2Window(int xOffset, int yOffset, int width, int height, Window parentWindow) {
		super(xOffset, yOffset, width, height, parentWindow);
		this.init();
	}

	private void init() {
		this.setLockCursorOnSelect(true);
		this.setDeselectOnEscPressed(true);
		this.setUnlockCursorOnEscPressed(true);

		this.genList = new ArrayDeque<>();

		this.pic = new PlayerInputController(new Vec3(0, 0, 30));

		this.particleUpdateShader = ShaderUtils.createShader("/csce_vis/hw2/particle_update.compute", GL_COMPUTE_SHADER);
		this.particleRenderShader = ShaderUtils.createShader("/csce_vis/hw2/particle.vert", "/csce_vis/hw2/particle.frag");

		//set up particle array buffers. need to keep track of:
		// - position
		// - velocity
		// - hue
		this.posbo = new ShaderStorageBuffer(MAX_PARTICLE_CNT * 4 * 4);
		this.posbo.setUsage(GL_DYNAMIC_DRAW);

		this.velbo = new ShaderStorageBuffer(MAX_PARTICLE_CNT * 4 * 4);
		this.velbo.setUsage(GL_DYNAMIC_DRAW);

		this.huebo = new ShaderStorageBuffer(MAX_PARTICLE_CNT * 4 * 4);
		this.huebo.setUsage(GL_DYNAMIC_DRAW);

		this.resetParticles();

		this._resize();
	}

	@Override
	protected void _kill() {
		this.posbo.kill();
		this.velbo.kill();
		this.huebo.kill();

		this.particleUpdateShader.kill();
		this.particleRenderShader.kill();

		this.renderBuffer.kill();
	}

	@Override
	protected void _resize() {
		if (this.renderBuffer != null) {
			this.renderBuffer.kill();
			this.renderBuffer = null;
		}

		if (this.getWidth() > 0 && this.getHeight() > 0) {
			this.renderBuffer = new Framebuffer(this.getWidth(), this.getHeight());
			this.renderColorMap = new Texture(this.getWidth(), this.getHeight(), GL_RGBA32F, GL_RGBA, GL_FLOAT);
			this.renderBuffer.bindTextureToBuffer(GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, this.renderColorMap.getID());
			this.renderBuffer.addDepthBuffer();
			this.renderBuffer.setDrawBuffers(new int[] { GL_COLOR_ATTACHMENT0 });
			this.renderBuffer.isComplete();
		}
	}

	@Override
	public String getDefaultTitle() {
		return "Homework 2";
	}

	private void resetParticles() {
		float[] pos_data = new float[MAX_PARTICLE_CNT * 4];
		float[] vel_data = new float[MAX_PARTICLE_CNT * 4];
		float[] hue_data = new float[MAX_PARTICLE_CNT * 4];
		for (int i = 0; i < MAX_PARTICLE_CNT; i++) {
			Vec3 pos = MathUtils.random(new Vec3(-10), new Vec3(10));
			Vec3 vel = new Vec3(0);
			Vec3 hue = new Vec3(pos);
			hue.normalize();
			hue = hue.mul(0.5f).add(new Vec3(0.5));

			//position
			pos_data[i * 4 + 0] = pos.x;
			pos_data[i * 4 + 1] = pos.y;
			pos_data[i * 4 + 2] = pos.z;

			//velocity
			vel_data[i * 4 + 0] = vel.x;
			vel_data[i * 4 + 1] = vel.y;
			vel_data[i * 4 + 2] = vel.z;

			//hue
			hue_data[i * 4 + 0] = hue.x;
			hue_data[i * 4 + 1] = hue.y;
			hue_data[i * 4 + 2] = hue.z;

			//lifespan
			pos_data[i * 4 + 3] = -1;
		}

		this.posbo.setData(pos_data);
		this.velbo.setData(vel_data);
		this.huebo.setData(hue_data);
	}

	//tries to generate whatever is inside genList
	//if it runs out of tries, then try to generate on next update
	private void generateParticles() {
		for (int i = 0; i < GEN_MAX_TRIES && this.genList.size() != 0; i++) {
			float[] pos_data = new float[GEN_BATCH_SIZE * 4];
			float[] vel_data = new float[GEN_BATCH_SIZE * 4];
			float[] hue_data = new float[GEN_BATCH_SIZE * 4];

		}
	}

	@Override
	protected void _update() {
		this.pic.update();

		//update particles
		this.particleUpdateShader.enable();

		this.posbo.bindToBase(1);
		this.velbo.bindToBase(2);
		this.huebo.bindToBase(3);

		glDispatchCompute(MAX_PARTICLE_CNT, 1, 1);
		glMemoryBarrier(GL_SHADER_STORAGE_BARRIER_BIT);

		//print some stuff
		//		{
		//			float[] pos_data = new float[4];
		//			this.posbo.getSubData(pos_data, 0);
		//
		//			for (int i = 0; i < 4; i++) {
		//				System.out.print(pos_data[i] + " ");
		//			}
		//			System.out.println();
		//		}
	}

	@Override
	protected void renderContent(Framebuffer outputBuffer) {
		//render scene
		{
			Camera camera = new Camera((float) Math.toRadians(90), this.getWidth(), this.getHeight(), 0.1f, 400);
			camera.setPos(this.pic.getPos());
			camera.setFacing(this.pic.getFacing());
			Mat4 pr_matrix = camera.getProjectionMatrix();
			Mat4 vw_matrix = camera.getViewMatrix();

			this.particleRenderShader.enable();
			this.particleRenderShader.setUniformMat4("pr_matrix", pr_matrix);
			this.particleRenderShader.setUniformMat4("vw_matrix", vw_matrix);

			this.posbo.bindToBase(1);
			this.velbo.bindToBase(2);
			this.huebo.bindToBase(3);

			this.renderBuffer.bind();
			glClearDepth(1); // maximum value
			glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

			glEnable(GL_DEPTH_TEST);
			glDepthFunc(GL_LESS);

			glPointSize(5f);
			glViewport(0, 0, this.getWidth(), this.getHeight());
			glDrawArrays(GL_POINTS, 0, MAX_PARTICLE_CNT);
		}

		//render back to output buffer
		{
			outputBuffer.bind();
			Shader.SPLASH.enable();
			Shader.SPLASH.setUniform1f("alpha", 1f);
			this.renderColorMap.bind(GL_TEXTURE0);
			ScreenQuad.screenQuad.render();
		}
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
		switch (key) {
		case GLFW.GLFW_KEY_Q:
			this.regenerateParticles();
			break;
		}
	}

	@Override
	protected void _keyReleased(int key) {
		// TODO Auto-generated method stub

	}

	class Particle {
		Vec3 pos, vel, hue;
		float lifespan;

		public Particle(Vec3 _pos, Vec3 _vel, Vec3 _hue, float _lifespan) {
			this.pos = new Vec3(_pos);
			this.vel = new Vec3(_vel);
			this.hue = new Vec3(_hue);
			this.lifespan = _lifespan;
		}
	}

}
