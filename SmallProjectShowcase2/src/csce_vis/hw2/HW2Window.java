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

import lwjglengine.graphics.Framebuffer;
import lwjglengine.graphics.Shader;
import lwjglengine.graphics.ShaderStorageBuffer;
import lwjglengine.util.BufferUtils;
import lwjglengine.util.ShaderUtils;
import lwjglengine.window.Window;
import myutils.math.MathUtils;
import myutils.math.Vec3;

public class HW2Window extends Window {

	//TODO
	// - store all particles on GPU side, and update them there. 
	// - mark dead particles as dead, the CPU will occasionally check through particles to find dead ones. 
	//   - CPU also has to create new alive particles, so CPU knows which particles to check. 
	//   - making efficient dead checks may be challenging. 
	//   - if being dead is based off of lifespan though, then it isn't that bad, just throw a pqueue at it. 
	//   - when creating particles, create them randomly throughout the buffer. This way we don't even need
	//     dead checks. GPU just has to mark particle as dead, and when rendering, just don't render. 
	//   - however, initializing randomly has it's own issues, namely you will have to query data from GPU many times. 
	//     Instead, we can initialize blocks of particles. 
	// - lorentz attractor. Target = 1 million particles
	// - physics scene. Target = TBD
	//   - perhaps use BVH to speed up particle vs environment collisions?

	//particle buffers
	private static final int MAX_PARTICLE_CNT = 1; //maximum amount of particles possible within buffer
	private static final int PARTICLE_GEN_PER_SECOND = 100; //number of tries to generate particles. 
	private int vao, vbo, ibo;
	private ShaderStorageBuffer posbo, velbo, huebo;
	
	private Shader particleUpdateShader;

	private static final int VERTEX_LOC = 0;
	private static final int INSTANCED_POS_LOC = 1;
	private static final int INSTANCED_HUE_LOC = 2;

	public HW2Window(int xOffset, int yOffset, int width, int height, Window parentWindow) {
		super(xOffset, yOffset, width, height, parentWindow);
		
		this.particleUpdateShader = ShaderUtils.createShader("/csce_vis/hw2/particle_update.compute", GL_COMPUTE_SHADER);

		//set up particle array buffers
		//also need to keep track of:
		// - position
		// - velocity
		// - hue

		//basic geometry information
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

		this.posbo = new ShaderStorageBuffer(MAX_PARTICLE_CNT * 4 * 4);
		this.posbo.setUsage(GL_DYNAMIC_DRAW);

		this.velbo = new ShaderStorageBuffer(MAX_PARTICLE_CNT * 4 * 4);
		this.velbo.setUsage(GL_DYNAMIC_DRAW);

		this.huebo = new ShaderStorageBuffer(MAX_PARTICLE_CNT * 4 * 4);
		this.huebo.setUsage(GL_DYNAMIC_DRAW);

		glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, 0);
		glBindBuffer(GL_ARRAY_BUFFER, 0);
		glBindVertexArray(0);

		//initialize particles here for now
		float[] pos_data = new float[MAX_PARTICLE_CNT * 4];
		float[] vel_data = new float[MAX_PARTICLE_CNT * 4];
		float[] hue_data = new float[MAX_PARTICLE_CNT * 4];
		for (int i = 0; i < MAX_PARTICLE_CNT; i++) {
			Vec3 pos = MathUtils.random(new Vec3(-10), new Vec3(10));
			Vec3 vel = new Vec3(0);
			Vec3 hue = new Vec3(1);
			
			pos_data[i * 4 + 0] = pos.x;
			pos_data[i * 4 + 1] = pos.y;
			pos_data[i * 4 + 2] = pos.z;
			
			vel_data[i * 4 + 0] = vel.x;
			vel_data[i * 4 + 1] = vel.y;
			vel_data[i * 4 + 2] = vel.z;
			
			hue_data[i * 4 + 0] = hue.x;
			hue_data[i * 4 + 1] = hue.y;
			hue_data[i * 4 + 2] = hue.z;
		}
		
		this.posbo.setData(pos_data);
		this.velbo.setData(vel_data);
		this.huebo.setData(hue_data);
	}

	@Override
	protected void _kill() {
		glDeleteVertexArrays(new int[] { this.vao });
		glDeleteBuffers(new int[] { this.vbo, this.ibo });
		
		this.posbo.kill();
		this.velbo.kill();
		this.huebo.kill();
		
		this.particleUpdateShader.kill();
	}

	@Override
	protected void _resize() {
		// TODO Auto-generated method stub

	}

	@Override
	public String getDefaultTitle() {
		return "Homework 2";
	}

	@Override
	protected void _update() {
		
	}

	@Override
	protected void renderContent(Framebuffer outputBuffer) {

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
