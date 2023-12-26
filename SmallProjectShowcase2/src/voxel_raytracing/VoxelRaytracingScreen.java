package voxel_raytracing;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL13.*;
import static org.lwjgl.opengl.GL14.glBlendFuncSeparate;
import static org.lwjgl.opengl.GL15.GL_WRITE_ONLY;
import static org.lwjgl.opengl.GL30.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL21.*;
import static org.lwjgl.opengl.GL40.*;
import static org.lwjgl.opengl.GL41.*;
import static org.lwjgl.opengl.GL42.*;
import static org.lwjgl.opengl.GL43.*;

import static org.lwjgl.opengl.GL44.*;
import static org.lwjgl.opengl.GL45.*;
import static org.lwjgl.opengl.GL46.*;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL12.*;
import static org.lwjgl.opengl.GL13.*;
import static org.lwjgl.opengl.GL30.*;
import static org.lwjgl.opengl.GL31.*;
import static org.lwjgl.opengl.GL32.*;
import static org.lwjgl.opengl.GL33.*;
import static org.lwjgl.opengl.GL46.*;

import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;

import lwjglengine.graphics.Cubemap;
import lwjglengine.graphics.Framebuffer;
import lwjglengine.graphics.Shader;
import lwjglengine.graphics.ShaderStorageBuffer;
import lwjglengine.graphics.Texture;
import lwjglengine.graphics.Texture1D;
import lwjglengine.main.Main;
import lwjglengine.player.Camera;
import lwjglengine.screen.Screen;
import lwjglengine.screen.ScreenQuad;
import lwjglengine.screen.SkyboxCube;
import lwjglengine.util.BufferUtils;
import lwjglengine.util.ShaderUtils;
import myutils.file.FileUtils;
import myutils.math.IVec3;
import myutils.math.Vec3;

public class VoxelRaytracingScreen extends Screen {

	private Shader voxelRaytracingShader;

	private Cubemap skybox;

	private VoxelOctreeManager voxelManager;
	private ShaderStorageBuffer svoSSBO;

	public VoxelRaytracingScreen() {
		this.voxelRaytracingShader = ShaderUtils.createShader("/voxel_raytracing/raytracing.vert", "/voxel_raytracing/raytracing.frag");

		this.voxelRaytracingShader.setUniform1i("skybox_tex", 0);

		BufferedImage[] skyboxSides = new BufferedImage[6];
		String skyboxDir = "/res/skybox/lake/";
		for (int i = 0; i < 6; i++) {
			skyboxSides[i] = FileUtils.loadImageRelative(skyboxDir + Cubemap.CUBEMAP_SIDE_NAMES[i] + ".jpg");
		}
		this.skybox = new Cubemap(skyboxSides);

		this.svoSSBO = new ShaderStorageBuffer();
		this.voxelManager = new VoxelOctreeManager(this.svoSSBO);
	}

	public void setCameraPos(Vec3 pos) {
		this.camera.setPos(pos);
	}

	public void setCameraFacing(Vec3 facing) {
		this.camera.setFacing(facing);
	}

	@Override
	public void buildBuffers() {
		Vec3 cameraPos = new Vec3(0, 0, 0);
		Vec3 cameraFacing = new Vec3(0, 0, 1);
		if (this.camera != null) {
			cameraPos = this.camera.getPos();
			cameraFacing = this.camera.getFacing();
		}
		this.camera = new Camera((float) Math.toRadians(90f), this.screenWidth, this.screenHeight, 0.1f, 200f);
		this.camera.setPos(cameraPos);
		this.camera.setFacing(cameraFacing);
	}

	@Override
	protected void _render(Framebuffer outputBuffer) {
		//TODO move this somewhere else
		this.voxelManager.updateSVOSSBO(this.camera.getPos());

		Vec3 sun_dir = new Vec3(0.2, 1, 0.7);
		sun_dir.normalize();

		outputBuffer.bind();
		this.voxelRaytracingShader.enable();
		this.voxelRaytracingShader.setUniformMat4("pr_matrix", this.camera.getProjectionMatrix());
		this.voxelRaytracingShader.setUniformMat4("vw_matrix", this.camera.getViewMatrix());
		this.voxelRaytracingShader.setUniform3f("camera_pos", this.camera.getPos());
		this.voxelRaytracingShader.setUniform3f("sun_dir", sun_dir);

		this.skybox.bind(GL_TEXTURE0);
		this.svoSSBO.bindToBase(1);
		glDisable(GL_DEPTH_TEST);
		glDisable(GL_CULL_FACE);
		glDisable(GL_BLEND);
		SkyboxCube.skyboxCube.render();
	}

	@Override
	protected void _kill() {
		this.voxelRaytracingShader.kill();

		this.skybox.kill();
	}

}
