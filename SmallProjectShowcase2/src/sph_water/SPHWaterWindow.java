package sph_water;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import org.lwjgl.glfw.GLFW;

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
import lwjglengine.graphics.Material;
import lwjglengine.graphics.Shader;
import lwjglengine.graphics.Texture;
import lwjglengine.graphics.Texture1D;
import lwjglengine.graphics.TextureMaterial;
import lwjglengine.main.Main;
import lwjglengine.model.FilledRectangle;
import lwjglengine.model.Model;
import lwjglengine.model.ModelInstance;
import lwjglengine.model.ModelTransform;
import lwjglengine.scene.Scene;
import lwjglengine.screen.ScreenQuad;
import lwjglengine.screen.UIScreen;
import lwjglengine.ui.UIElement;
import lwjglengine.ui.UIFilledRectangle;
import lwjglengine.ui.UISection;
import lwjglengine.util.ShaderUtils;
import lwjglengine.window.AdjustableWindow;
import lwjglengine.window.ObjectEditorWindow;
import lwjglengine.window.TextureViewerWindow;
import lwjglengine.window.Window;
import myutils.math.Mat4;
import myutils.math.MathUtils;
import myutils.math.Vec2;
import myutils.math.Vec3;
import myutils.misc.Pair;

public class SPHWaterWindow extends Window {

	//TODO
	// - weird behaviour when viscosity gets too high
	// - implement bitonic merge sort

	private static final int LUT_P1 = 251527;
	private static final int LUT_P2 = 6037;
	private static final int LUT_P3 = 8443;
	private static final int LUT_P4 = 107;
	private static final int LUT_P5 = 403;

	private static float waterModelScale = 5f;
	private static float waterPositionScale = 10f;

	//used to sample properties from the point cloud
	private static float smoothingRadius = 3f;
	//(S - r)^3
	private static float densitySmoothingKernelVolume = (float) (Math.PI * Math.pow(smoothingRadius, 5) / 10.0);
	//(S - r)^6
	private static float nearDensitySmoothingKernelVolume = (float) (Math.PI * Math.pow(smoothingRadius, 8) / 28.0);
	//(S^2 - r^2)^3
	private static float viscositySmoothingKernelVolume = (float) (Math.PI * Math.pow(smoothingRadius, 8) / 4.0);

	private static Vec3 slowParticleColor = new Vec3(0, 0, 1);
	private static Vec3 fastParticleColor = new Vec3(1, 0, 0);
	private static float slowParticleSpeed = 0f;
	private static float fastParticleSpeed = 25f;

	private static float waterMass = 1f;

	private SPHWaterSettings settings;

	private UIScreen uiScreen;
	private final int WATER_PARTICLE_SCENE = Scene.generateScene();

	private List<ModelInstance> waterModels;

	private Vec2 boundsMin, boundsMax;

	private FilledRectangle circleRect;

	private boolean mousePressed = false;
	private boolean mouseAttract = false; //if false, will repel.

	private Shader waterComputeShader1, waterComputeShader3, waterComputeShader4;
	private static final int COMPUTE_NR_PARTICLES = 4096;

	private Texture1D posvelTexture; //xy = pos, zw = vel
	private Texture1D predictedPosLUTTexture; //xy = predicted pos, z = hash, w = hash start index
	private Texture1D densityViscosityTexture; //x = density, y = near density, zw = viscosity force

	private Shader waterMetaballShader;

	public SPHWaterWindow(int xOffset, int yOffset, int width, int height, Window parentWindow) {
		super(xOffset, yOffset, 1600, 900, parentWindow);
		this.init();
	}

	private void init() {
		this.uiScreen = new UIScreen();

		this.settings = new SPHWaterSettings();

		this.waterModels = new ArrayList<>();

		this.boundsMin = new Vec2(0, 0);
		this.boundsMax = new Vec2(this.getWidth() / waterPositionScale, this.getHeight() / waterPositionScale);

		this.circleRect = new FilledRectangle();
		TextureMaterial circleTexture = new TextureMaterial(new Texture("/res/circle-128.png"));
		this.circleRect.setTextureMaterial(circleTexture);

		float[] initPixels = new float[COMPUTE_NR_PARTICLES * 4];
		{
			float boundsWidth = this.boundsMax.x - this.boundsMin.x;
			float boundsHeight = this.boundsMax.y - this.boundsMin.y;
			for (int i = 0; i < COMPUTE_NR_PARTICLES; i++) {
				float pos_x = (float) Math.random() * boundsWidth + this.boundsMin.x;
				float pos_y = (float) Math.random() * boundsHeight + this.boundsMin.y;
				float vel_x = 0;
				float vel_y = 0;
				initPixels[i * 4 + 0] = pos_x;
				initPixels[i * 4 + 1] = pos_y;
				initPixels[i * 4 + 2] = vel_x;
				initPixels[i * 4 + 3] = vel_y;

				this.waterModels.add(new ModelInstance(this.circleRect, WATER_PARTICLE_SCENE));
			}
		}

		{
			AdjustableWindow adj = new AdjustableWindow(new ObjectEditorWindow(this.settings), this);
		}

		this.waterComputeShader1 = ShaderUtils.createShader("/sph_water/water1.compute", GL_COMPUTE_SHADER);
		this.waterComputeShader3 = ShaderUtils.createShader("/sph_water/water3.compute", GL_COMPUTE_SHADER);
		this.waterComputeShader4 = ShaderUtils.createShader("/sph_water/water4.compute", GL_COMPUTE_SHADER);
		this.posvelTexture = new Texture1D(GL_RGBA32F, COMPUTE_NR_PARTICLES, GL_RGBA, GL_FLOAT, initPixels);
		this.predictedPosLUTTexture = new Texture1D(GL_RGBA32F, COMPUTE_NR_PARTICLES, GL_RGBA, GL_FLOAT);
		this.densityViscosityTexture = new Texture1D(GL_RGBA32F, COMPUTE_NR_PARTICLES, GL_RGBA, GL_FLOAT);

		this.waterMetaballShader = ShaderUtils.createShader("/sph_water/water_metaball.vert", "/sph_water/water_metaball.frag");

		this._resize();
	}

	@Override
	protected void _kill() {
		this.circleRect.kill();

		this.uiScreen.kill();
		Scene.removeScene(WATER_PARTICLE_SCENE);

		this.waterMetaballShader.kill();

		this.posvelTexture.kill();
		this.predictedPosLUTTexture.kill();
		this.densityViscosityTexture.kill();

		this.waterComputeShader1.kill();
		this.waterComputeShader3.kill();
		this.waterComputeShader4.kill();
	}

	@Override
	protected void _resize() {
		this.boundsMin = new Vec2(0, 0);
		this.boundsMax = new Vec2(this.getWidth() / waterPositionScale, this.getHeight() / waterPositionScale);

		this.uiScreen.setScreenDimensions(this.getWidth(), this.getHeight());
	}

	@Override
	public String getDefaultTitle() {
		return "SPH Water";
	}

	private void updateWater(float deltaTime) {
		//PHASE 1 : 
		// - update position based on velocity, and do boundary collisions
		// - generate predicted positions, and calculate hashes based on those positions. 
		this.waterComputeShader1.enable();
		this.waterComputeShader1.setUniform1f("delta_time", deltaTime);
		this.waterComputeShader1.setUniform1f("predict_delta_time", this.settings.predictDeltaTime);
		this.waterComputeShader1.setUniform2f("bounds_min", this.boundsMin);
		this.waterComputeShader1.setUniform2f("bounds_max", this.boundsMax);
		this.waterComputeShader1.setUniform2f("gravity", this.settings.gravity);
		this.waterComputeShader1.setUniform1f("boundary_damping", this.settings.boundaryDamping);

		this.waterComputeShader1.setUniform1i("nr_particles", COMPUTE_NR_PARTICLES);
		this.waterComputeShader1.setUniform1f("smoothing_radius", smoothingRadius);
		this.waterComputeShader1.setUniform1i("LUT_P1", LUT_P1);
		this.waterComputeShader1.setUniform1i("LUT_P2", LUT_P2);
		this.waterComputeShader1.setUniform1i("LUT_P3", LUT_P3);
		this.waterComputeShader1.setUniform1i("LUT_P4", LUT_P4);
		this.waterComputeShader1.setUniform1i("LUT_P5", LUT_P5);

		glBindImageTexture(0, this.posvelTexture.getID(), 0, false, 0, GL_READ_WRITE, GL_RGBA32F);
		glBindImageTexture(1, this.predictedPosLUTTexture.getID(), 0, false, 0, GL_WRITE_ONLY, GL_RGBA32F);

		glDispatchCompute(COMPUTE_NR_PARTICLES / 64, 1, 1);
		glMemoryBarrier(GL_SHADER_IMAGE_ACCESS_BARRIER_BIT);

		//PHASE 2 : 
		// - sort particles based off of hash value, and generate lookup tables
		//for now, imma just do this on the cpu. TODO implement bitonic merge sort. 
		{
			float[] posvel = new float[COMPUTE_NR_PARTICLES * 4];
			float[] LUT = new float[COMPUTE_NR_PARTICLES * 4];
			glBindTexture(GL_TEXTURE_1D, this.posvelTexture.getID());
			glGetTexImage(GL_TEXTURE_1D, 0, GL_RGBA, GL_FLOAT, posvel);
			glBindTexture(GL_TEXTURE_1D, this.predictedPosLUTTexture.getID());
			glGetTexImage(GL_TEXTURE_1D, 0, GL_RGBA, GL_FLOAT, LUT);

			List<Pair<Integer, float[]>> data = new ArrayList<>();
			for (int i = 0; i < COMPUTE_NR_PARTICLES; i++) {
				float[] arr = new float[6];
				arr[0] = posvel[i * 4 + 0];
				arr[1] = posvel[i * 4 + 1];
				arr[2] = posvel[i * 4 + 2];
				arr[3] = posvel[i * 4 + 3];
				arr[4] = LUT[i * 4 + 0];
				arr[5] = LUT[i * 4 + 1];
				int hash = Math.round(LUT[i * 4 + 2]);
				data.add(new Pair<>(hash, arr));

				LUT[i * 4 + 3] = COMPUTE_NR_PARTICLES;
			}

			Collections.sort(data, (a, b) -> a.first - b.first);

			for (int i = 0; i < COMPUTE_NR_PARTICLES; i++) {
				float[] arr = data.get(i).second;
				int hash = data.get(i).first;
				posvel[i * 4 + 0] = arr[0];
				posvel[i * 4 + 1] = arr[1];
				posvel[i * 4 + 2] = arr[2];
				posvel[i * 4 + 3] = arr[3];
				LUT[i * 4 + 0] = arr[4];
				LUT[i * 4 + 1] = arr[5];
				LUT[i * 4 + 2] = hash;
				if (i == 0 || data.get(i - 1).first != hash) {
					LUT[hash * 4 + 3] = i;
				}
			}

			glTextureSubImage1D(this.posvelTexture.getID(), 0, 0, COMPUTE_NR_PARTICLES, GL_RGBA, GL_FLOAT, posvel);
			glTextureSubImage1D(this.predictedPosLUTTexture.getID(), 0, 0, COMPUTE_NR_PARTICLES, GL_RGBA, GL_FLOAT, LUT);
		}

		//PHASE 3 : 
		// - compute density for each particle
		// - compute viscosity force for each particle
		this.waterComputeShader3.enable();
		this.waterComputeShader3.setUniform1i("nr_particles", COMPUTE_NR_PARTICLES);
		this.waterComputeShader3.setUniform1f("smoothing_radius", smoothingRadius);
		this.waterComputeShader3.setUniform1i("LUT_P1", LUT_P1);
		this.waterComputeShader3.setUniform1i("LUT_P2", LUT_P2);
		this.waterComputeShader3.setUniform1i("LUT_P3", LUT_P3);
		this.waterComputeShader3.setUniform1i("LUT_P4", LUT_P4);
		this.waterComputeShader3.setUniform1i("LUT_P5", LUT_P5);

		this.waterComputeShader3.setUniform1f("water_mass", waterMass);
		this.waterComputeShader3.setUniform1f("density_smoothing_kernel_volume", densitySmoothingKernelVolume);
		this.waterComputeShader3.setUniform1f("near_density_smoothing_kernel_volume", nearDensitySmoothingKernelVolume);
		this.waterComputeShader3.setUniform1f("viscosity_smoothing_kernel_volume", viscositySmoothingKernelVolume);

		this.waterComputeShader3.setUniform1f("viscosity_strength", this.settings.viscosityStrength);

		glBindImageTexture(0, this.predictedPosLUTTexture.getID(), 0, false, 0, GL_READ_ONLY, GL_RGBA32F);
		glBindImageTexture(1, this.densityViscosityTexture.getID(), 0, false, 0, GL_WRITE_ONLY, GL_RGBA32F);
		glBindImageTexture(2, this.posvelTexture.getID(), 0, false, 0, GL_READ_ONLY, GL_RGBA32F);

		glDispatchCompute(COMPUTE_NR_PARTICLES / 64, 1, 1);
		glMemoryBarrier(GL_SHADER_IMAGE_ACCESS_BARRIER_BIT);

		//PHASE 4 : 
		// - compute pressure, and interaction forces
		// - apply pressure, interaction, and viscosity forces to velocity. 
		this.waterComputeShader4.enable();
		this.waterComputeShader4.setUniform1f("delta_time", deltaTime);

		this.waterComputeShader4.setUniform1i("nr_particles", COMPUTE_NR_PARTICLES);
		this.waterComputeShader4.setUniform1f("smoothing_radius", smoothingRadius);
		this.waterComputeShader4.setUniform1i("LUT_P1", LUT_P1);
		this.waterComputeShader4.setUniform1i("LUT_P2", LUT_P2);
		this.waterComputeShader4.setUniform1i("LUT_P3", LUT_P3);
		this.waterComputeShader4.setUniform1i("LUT_P4", LUT_P4);
		this.waterComputeShader4.setUniform1i("LUT_P5", LUT_P5);

		this.waterComputeShader4.setUniform1f("water_mass", waterMass);
		this.waterComputeShader4.setUniform1f("density_smoothing_kernel_volume", densitySmoothingKernelVolume);
		this.waterComputeShader4.setUniform1f("near_density_smoothing_kernel_volume", nearDensitySmoothingKernelVolume);

		this.waterComputeShader4.setUniform1f("target_density", this.settings.targetDensity);
		this.waterComputeShader4.setUniform1f("pressure_multiplier", this.settings.pressureMultiplier);
		this.waterComputeShader4.setUniform1f("near_pressure_multiplier", this.settings.nearPressureMultiplier);

		this.waterComputeShader4.setUniform2f("mouse_pos", this.getWindowMousePos().div(waterPositionScale));
		this.waterComputeShader4.setUniform1i("mouse_pressed", this.mousePressed ? 1 : 0);
		this.waterComputeShader4.setUniform1f("interaction_radius", this.settings.interactionRadius);
		this.waterComputeShader4.setUniform1f("interaction_strength", this.settings.interactionStrength * (this.mouseAttract ? 1 : -1));

		glBindImageTexture(0, this.predictedPosLUTTexture.getID(), 0, false, 0, GL_READ_ONLY, GL_RGBA32F);
		glBindImageTexture(1, this.densityViscosityTexture.getID(), 0, false, 0, GL_READ_ONLY, GL_RGBA32F);
		glBindImageTexture(2, this.posvelTexture.getID(), 0, false, 0, GL_READ_WRITE, GL_RGBA32F);

		glDispatchCompute(COMPUTE_NR_PARTICLES / 64, 1, 1);
		glMemoryBarrier(GL_SHADER_IMAGE_ACCESS_BARRIER_BIT);

		//update model transforms
		if (this.settings.renderParticles) {
			float[] pixels = new float[COMPUTE_NR_PARTICLES * 4];
			glBindTexture(GL_TEXTURE_1D, this.posvelTexture.getID());
			glGetTexImage(GL_TEXTURE_1D, 0, GL_RGBA, GL_FLOAT, pixels);

			for (int i = 0; i < COMPUTE_NR_PARTICLES; i++) {
				float pos_x = pixels[i * 4 + 0];
				float pos_y = pixels[i * 4 + 1];
				float vel_x = pixels[i * 4 + 2];
				float vel_y = pixels[i * 4 + 3];

				Vec2 pos = new Vec2(pos_x, pos_y);
				Vec2 vel = new Vec2(vel_x, vel_y);

				ModelInstance instance = this.waterModels.get(i);
				Mat4 transform = Mat4.translate(-0.5f, -0.5f, 0).mul(Mat4.scale(waterModelScale)).mul(Mat4.translate(pos.mul(waterPositionScale)));
				instance.setModelTransform(new ModelTransform(transform));

				float speed = vel.length();
				Vec3 color = MathUtils.lerp(slowParticleColor, slowParticleSpeed, fastParticleColor, fastParticleSpeed, speed);
				color.x = MathUtils.clamp(0, 1, color.x);
				color.y = MathUtils.clamp(0, 1, color.y);
				color.z = MathUtils.clamp(0, 1, color.z);
				Material material = new Material(color);
				instance.setMaterial(material);
			}
		}
	}

	@Override
	protected void _update() {
		long startMillis = System.currentTimeMillis();
		this.updateWater(16.0f / 1000.0f);

		//System.out.println("SPH UPDATE : " + (System.currentTimeMillis() - startMillis));
	}

	@Override
	protected void renderContent(Framebuffer outputBuffer) {
		if (this.settings.renderMetaballs) {
			glViewport(0, 0, this.getWidth(), this.getHeight());
			this.waterMetaballShader.enable();
			this.waterMetaballShader.setUniform1i("nr_particles", COMPUTE_NR_PARTICLES);
			this.waterMetaballShader.setUniform1f("smoothing_radius", smoothingRadius);
			this.waterMetaballShader.setUniform1i("LUT_P1", LUT_P1);
			this.waterMetaballShader.setUniform1i("LUT_P2", LUT_P2);
			this.waterMetaballShader.setUniform1i("LUT_P3", LUT_P3);
			this.waterMetaballShader.setUniform1i("LUT_P4", LUT_P4);
			this.waterMetaballShader.setUniform1i("LUT_P5", LUT_P5);

			this.waterMetaballShader.setUniform1f("window_width", this.getWidth());
			this.waterMetaballShader.setUniform1f("window_height", this.getHeight());
			this.waterMetaballShader.setUniform1f("water_position_scale", waterPositionScale);

			this.waterMetaballShader.setUniform1f("density_threshold", this.settings.targetDensity);

			this.waterMetaballShader.setUniform1f("water_mass", waterMass);
			this.waterMetaballShader.setUniform1f("density_smoothing_kernel_volume", densitySmoothingKernelVolume);
			this.waterMetaballShader.setUniform1f("near_density_smoothing_kernel_volume", nearDensitySmoothingKernelVolume);
			outputBuffer.bind();
			ScreenQuad.screenQuad.render();
		}

		if (this.settings.renderParticles) {
			this.uiScreen.setUIScene(WATER_PARTICLE_SCENE);
			this.uiScreen.render(outputBuffer);
		}
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
		switch (button) {
		case GLFW.GLFW_MOUSE_BUTTON_1:
			this.mouseAttract = true;
			break;

		case GLFW.GLFW_MOUSE_BUTTON_2:
			this.mouseAttract = false;
			break;
		}
	}

	@Override
	protected void _mouseReleased(int button) {
		this.mousePressed = false;
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

	public class SPHWaterSettings {
		public Vec2 gravity = new Vec2(0, -30);

		public float boundaryDamping = 0.5f;

		public float viscosityStrength = 0.25f;

		public float pressureMultiplier = 50f;
		public float nearPressureMultiplier = 0.1f;
		public float predictDeltaTime = 1.0f / 60.0f;

		public float targetDensity = 1f;

		public float interactionStrength = 150f;
		public float interactionRadius = 25f;

		public boolean renderMetaballs = true;
		public boolean renderParticles = false;

		public SPHWaterSettings() {
			gravity = new Vec2(0, -30);

			boundaryDamping = 0.5f;

			viscosityStrength = 0.25f;

			pressureMultiplier = 50f;
			nearPressureMultiplier = 0.1f;
			predictDeltaTime = 1.0f / 60.0f;

			targetDensity = 1f;

			interactionStrength = 150f;
			interactionRadius = 25f;

			renderMetaballs = true;
			renderParticles = false;
		}

		public Vec2 getGravity() {
			return gravity;
		}

		public void setGravity(Vec2 gravity) {
			this.gravity = gravity;
		}

		public float getBoundaryDamping() {
			return boundaryDamping;
		}

		public void setBoundaryDamping(float boundaryDamping) {
			this.boundaryDamping = boundaryDamping;
		}

		public float getViscosityStrength() {
			return viscosityStrength;
		}

		public void setViscosityStrength(float viscosityStrength) {
			this.viscosityStrength = viscosityStrength;
		}

		public float getPressureMultiplier() {
			return pressureMultiplier;
		}

		public void setPressureMultiplier(float pressureMultiplier) {
			this.pressureMultiplier = pressureMultiplier;
		}

		public float getNearPressureMultiplier() {
			return nearPressureMultiplier;
		}

		public void setNearPressureMultiplier(float nearPressureMultiplier) {
			this.nearPressureMultiplier = nearPressureMultiplier;
		}

		public float getPredictDeltaTime() {
			return predictDeltaTime;
		}

		public void setPredictDeltaTime(float predictDeltaTime) {
			this.predictDeltaTime = predictDeltaTime;
		}

		public float getTargetDensity() {
			return targetDensity;
		}

		public void setTargetDensity(float targetDensity) {
			this.targetDensity = targetDensity;
		}

		public float getInteractionStrength() {
			return interactionStrength;
		}

		public void setInteractionStrength(float interactionStrength) {
			this.interactionStrength = interactionStrength;
		}

		public float getInteractionRadius() {
			return interactionRadius;
		}

		public void setInteractionRadius(float interactionRadius) {
			this.interactionRadius = interactionRadius;
		}

		public boolean getRenderMetaballs() {
			return renderMetaballs;
		}

		public void setRenderMetaballs(boolean renderMetaballs) {
			this.renderMetaballs = renderMetaballs;
		}

		public boolean getRenderParticles() {
			return renderParticles;
		}

		public void setRenderParticles(boolean renderParticles) {
			this.renderParticles = renderParticles;
		}
	}

}
