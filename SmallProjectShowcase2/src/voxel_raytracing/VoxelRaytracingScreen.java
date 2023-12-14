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
import myutils.math.Vec3;

public class VoxelRaytracingScreen extends Screen {

	private Shader voxelRaytracingShader;

	private Cubemap skybox;

	private ShaderStorageBuffer svoSSBO;

	private float sunRotRads = 0;

	public VoxelRaytracingScreen() {
		this.voxelRaytracingShader = ShaderUtils.createShader("/voxel_raytracing/raytracing.vert", "/voxel_raytracing/raytracing.frag");

		this.voxelRaytracingShader.setUniform1i("skybox_tex", 0);

		BufferedImage[] skyboxSides = new BufferedImage[6];
		String skyboxDir = "/res/skybox/lake/";
		for (int i = 0; i < 6; i++) {
			skyboxSides[i] = FileUtils.loadImageRelative(skyboxDir + Cubemap.CUBEMAP_SIDE_NAMES[i] + ".jpg");
		}
		this.skybox = new Cubemap(skyboxSides);

		System.out.print("BUILDING VOXEL OCTREE : ");
		long startMillis = System.currentTimeMillis();
		int size = 256;
		float prob = 1f;
		int nrRemove = 0;
		float radius = 100f;
		VoxelOctreeNode root = new VoxelOctreeNode(size);
		Vec3 center = new Vec3(size / 2, size / 2, size / 2);
		for (int i = 0; i < size; i++) {
			for (int j = 0; j < size; j++) {
				for (int k = 0; k < size; k++) {
					Vec3 v = new Vec3(center, new Vec3(i, j, k));
					if ((Math.random() < prob && v.length() < radius)) {
						int col = (int) (Math.random() * 15);
						int r = 79 + col;
						int g = 58 + col;
						int b = 43 + col;
						v.normalize();
						Vec3 color = new Vec3(r, g, b);
						color.muli(1.0 / 255.0);
						root.addVoxel(i, j, k, color, v);
					}
				}
			}
		}
		for (int i = 0; i < nrRemove; i++) {
			int x = (int) (Math.random() * size);
			int y = (int) (Math.random() * size);
			int z = (int) (Math.random() * size);
			root.removeVoxel(x, y, z);
		}
		System.out.println(System.currentTimeMillis() - startMillis);
		System.out.print("SERIALIZING OCTREE : ");
		startMillis = System.currentTimeMillis();
		boolean[] bits = VoxelOctreeNode.serialize(root);
		System.out.println(System.currentTimeMillis() - startMillis);
		System.out.println("BITS LENGTH : " + bits.length);

		System.out.print("DESERIALIZING OCTREE : ");
		startMillis = System.currentTimeMillis();
		VoxelOctreeNode root_cpy = VoxelOctreeNode.deserialize(bits);
		System.out.println(System.currentTimeMillis() - startMillis);

		System.out.println("ARE EQUAL : " + (root.equals(root_cpy)));
		System.out.println("ESTIMATED REQUIRED BITS : " + VoxelOctreeNode.countRequiredBits(root));

		int[] data = new int[bits.length / 32];
		for (int i = 0; i < bits.length; i++) {
			int bit_ind = i % 32;
			data[i / 32] = data[i / 32] | ((bits[i] ? 1 : 0) << (31 - bit_ind));
		}

		this.svoSSBO = new ShaderStorageBuffer();
		this.svoSSBO.setData(data);
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
		Vec3 sun_dir = new Vec3(0, 1, 1);
		sun_dir.normalize();

		this.sunRotRads += (Main.getDeltaMillis() / 1000.0f) / 5.0f;
		sun_dir.rotateY(this.sunRotRads);

		outputBuffer.bind();
		this.voxelRaytracingShader.enable();
		this.voxelRaytracingShader.setUniformMat4("pr_matrix", this.camera.getProjectionMatrix());
		this.voxelRaytracingShader.setUniformMat4("vw_matrix", this.camera.getViewMatrix());
		this.voxelRaytracingShader.setUniform3f("camera_pos", this.camera.getPos());
		this.voxelRaytracingShader.setUniform3f("sun_dir", sun_dir);
		this.voxelRaytracingShader.setUniform3f("svo_offset", new Vec3(0, 0, 0));

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
