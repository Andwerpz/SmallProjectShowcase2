package csce_vis.hw1;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL13.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL21.*;
import static org.lwjgl.opengl.GL30.*;
import static org.lwjgl.opengl.GL31.*;
import static org.lwjgl.opengl.GL32.*;
import static org.lwjgl.opengl.GL33.*;
import static org.lwjgl.opengl.GL43.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;
import static org.lwjgl.opengl.GL31.*;
import static org.lwjgl.opengl.GL33.*;

import java.util.ArrayList;
import java.util.List;

import csce_vis.hw1.HW1Window.Sphere;
import csce_vis.hw1.HW1Window.Triangle;
import lwjglengine.graphics.Framebuffer;
import lwjglengine.graphics.Shader;
import lwjglengine.graphics.ShaderStorageBuffer;
import lwjglengine.player.Camera;
import lwjglengine.screen.Screen;
import lwjglengine.screen.SkyboxCube;
import lwjglengine.util.ShaderUtils;
import myutils.math.Mat4;
import myutils.math.MathUtils;
import myutils.math.Vec3;

public class RenderScreen extends Screen {

	private Shader renderShader;

	private int sphereSSBOLen = 0;
	private ShaderStorageBuffer sphereSSBO;

	private int triSSBOLen = 0;
	private ShaderStorageBuffer triSSBO;

	private float boxSize;

	private boolean renderSphereTexture = false;

	public RenderScreen() {
		super();

		this.sphereSSBO = new ShaderStorageBuffer();
		this.sphereSSBO.setUsage(GL_DYNAMIC_READ);

		this.triSSBO = new ShaderStorageBuffer();
		this.triSSBO.setUsage(GL_STATIC_READ);

		this.renderShader = ShaderUtils.createShader("/csce_vis/hw1/render.vert", "/csce_vis/hw1/render.frag");
	}

	public void setCameraPos(Vec3 _pos) {
		this.camera.setPos(_pos);
	}

	public void setCameraFacing(Vec3 _facing) {
		this.camera.setFacing(_facing);
	}

	public void setBoxSize(float _box_size) {
		this.boxSize = _box_size;
	}

	public void setRenderSphereTexture(boolean b) {
		this.renderSphereTexture = b;
	}

	public void setSpheres(ArrayList<Sphere> spheres) {
		this.sphereSSBOLen = spheres.size() * (3 + 1 + 16); //pos, radius, orient mat
		float[] data = new float[this.sphereSSBOLen];
		int ptr = 0;
		for (Sphere s : spheres) {
			data[ptr++] = s.pos.x;
			data[ptr++] = s.pos.y;
			data[ptr++] = s.pos.z;
			data[ptr++] = s.radius;
			Mat4 omat = MathUtils.quaternionToRotationMat4(s.orient);
			float[] mat_data = omat.toFloatArray();
			for (int i = 0; i < 16; i++) {
				data[ptr++] = mat_data[i];
			}
		}
		this.sphereSSBO.setData(data);
	}

	public void setTriangles(List<Triangle> triangles) {
		this.triSSBOLen = triangles.size() * (3 + 3 + 3); //a, b, c
		float[] data = new float[this.triSSBOLen];
		int ptr = 0;
		for (Triangle t : triangles) {
			data[ptr++] = t.p0.x;
			data[ptr++] = t.p0.y;
			data[ptr++] = t.p0.z;
			data[ptr++] = t.p1.x;
			data[ptr++] = t.p1.y;
			data[ptr++] = t.p1.z;
			data[ptr++] = t.p2.x;
			data[ptr++] = t.p2.y;
			data[ptr++] = t.p2.z;
		}
		this.triSSBO.setData(data);
	}

	@Override
	public void buildBuffers() {
		this.camera = new Camera((float) Math.toRadians(90), this.getScreenWidth(), this.getScreenHeight(), 0.1f, 400);
	}

	@Override
	protected void _render(Framebuffer outputBuffer) {
		glDisable(GL_BLEND);
		glDisable(GL_DEPTH_TEST);
		glDisable(GL_CULL_FACE);

		outputBuffer.bind();
		this.renderShader.enable();
		this.renderShader.setUniformMat4("pr_matrix", this.camera.getProjectionMatrix());
		this.renderShader.setUniformMat4("vw_matrix", this.camera.getViewMatrix());
		this.renderShader.setUniform3f("camera_pos", this.camera.getPos());
		this.renderShader.setUniform1i("sphere_buf_sz", this.sphereSSBOLen);
		this.renderShader.setUniform1i("tri_buf_sz", this.triSSBOLen);
		this.renderShader.setUniform1f("box_size", this.boxSize);
		this.renderShader.setUniform1i("render_sphere_texture", this.renderSphereTexture ? 1 : 0);

		this.sphereSSBO.bindToBase(1);
		this.triSSBO.bindToBase(2);

		SkyboxCube.skyboxCube.render();
	}

	@Override
	protected void _kill() {
		this.renderShader.kill();
		this.sphereSSBO.kill();
	}

}
