package csce_vis.hw_final;

import static org.lwjgl.opengl.GL11.GL_FLOAT;
import static org.lwjgl.opengl.GL11.GL_LINEAR;
import static org.lwjgl.opengl.GL11.GL_LINEAR_MIPMAP_LINEAR;
import static org.lwjgl.opengl.GL11.GL_NEAREST;
import static org.lwjgl.opengl.GL11.GL_REPEAT;
import static org.lwjgl.opengl.GL11.GL_RGBA;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_2D;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_MAG_FILTER;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_MIN_FILTER;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_WRAP_S;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_WRAP_T;
import static org.lwjgl.opengl.GL11.glBindTexture;
import static org.lwjgl.opengl.GL11.glGenTextures;
import static org.lwjgl.opengl.GL11.glTexParameteri;
import static org.lwjgl.opengl.GL11.glTexSubImage2D;
import static org.lwjgl.opengl.GL15.GL_READ_ONLY;
import static org.lwjgl.opengl.GL15.GL_READ_WRITE;
import static org.lwjgl.opengl.GL15.GL_WRITE_ONLY;
import static org.lwjgl.opengl.GL30.GL_RGBA32F;
import static org.lwjgl.opengl.GL30.glGenerateMipmap;
import static org.lwjgl.opengl.GL42.GL_SHADER_IMAGE_ACCESS_BARRIER_BIT;
import static org.lwjgl.opengl.GL42.glBindImageTexture;
import static org.lwjgl.opengl.GL42.glMemoryBarrier;
import static org.lwjgl.opengl.GL42.glTexStorage2D;
import static org.lwjgl.opengl.GL43.GL_COMPUTE_SHADER;
import static org.lwjgl.opengl.GL43.glDispatchCompute;

import csce_vis.hw_final.HWFWindow.Options;
import lwjglengine.graphics.Shader;
import lwjglengine.graphics.Texture;
import lwjglengine.util.ShaderUtils;

public class WaveCascade {
	//responsible for modelling one frequency band of the oceanographic spectrum

	public static final int WATER_RESOLUTION = 256;

	public HWFWindow.Options options; //shared among all cascades

	private Shader generateSpectraShader;
	private Shader evolveSpectraShader;
	private Shader fftShader, waveTexMergerShader;

	public Texture gaussianNoiseTexture;
	public Texture baseSpectraTexture;
	public Texture waveInfoTexture;
	public Texture Dx_Dz, Dy_Dxz, Dyx_Dyz, Dxx_Dzz;
	public Texture dispTexture, derivativeTexture;

	public float lengthScale;
	public float omegaMinCutoff, omegaMaxCutoff;

	public WaveCascade(float _lengthScale, float _omegaMinCutoff, float _omegaMaxCutoff, HWFWindow.Options _options) {
		this.lengthScale = _lengthScale;
		this.omegaMinCutoff = _omegaMinCutoff;
		this.omegaMaxCutoff = _omegaMaxCutoff;
		this.options = _options;

		this.gaussianNoiseTexture = this.generateGaussianNoiseTexture();
		glMemoryBarrier(GL_SHADER_IMAGE_ACCESS_BARRIER_BIT);

		this.generateSpectraShader = ShaderUtils.createShader("/csce_vis/hw_final/gen_spectra.compute", GL_COMPUTE_SHADER);
		this.baseSpectraTexture = new Texture(WATER_RESOLUTION, WATER_RESOLUTION, GL_RGBA32F, GL_RGBA, GL_FLOAT, GL_NEAREST);
		this.waveInfoTexture = new Texture(WATER_RESOLUTION, WATER_RESOLUTION, GL_RGBA32F, GL_RGBA, GL_FLOAT, GL_NEAREST);

		this.evolveSpectraShader = ShaderUtils.createShader("/csce_vis/hw_final/evolve_spectra.compute", GL_COMPUTE_SHADER);
		this.Dx_Dz = new Texture(WATER_RESOLUTION, WATER_RESOLUTION, GL_RGBA32F, GL_RGBA, GL_FLOAT, GL_NEAREST);
		this.Dy_Dxz = new Texture(WATER_RESOLUTION, WATER_RESOLUTION, GL_RGBA32F, GL_RGBA, GL_FLOAT, GL_NEAREST);
		this.Dyx_Dyz = new Texture(WATER_RESOLUTION, WATER_RESOLUTION, GL_RGBA32F, GL_RGBA, GL_FLOAT, GL_NEAREST);
		this.Dxx_Dzz = new Texture(WATER_RESOLUTION, WATER_RESOLUTION, GL_RGBA32F, GL_RGBA, GL_FLOAT, GL_NEAREST);

		this.fftShader = ShaderUtils.createShader("/csce_vis/hw_final/fft.compute", GL_COMPUTE_SHADER);
		this.waveTexMergerShader = ShaderUtils.createShader("/csce_vis/hw_final/waves_tex_merger.compute", GL_COMPUTE_SHADER);
		this.dispTexture = new Texture(WATER_RESOLUTION, WATER_RESOLUTION, GL_RGBA32F, GL_RGBA, GL_FLOAT, GL_LINEAR_MIPMAP_LINEAR, GL_LINEAR, 6, null);
		this.derivativeTexture = new Texture(WATER_RESOLUTION, WATER_RESOLUTION, GL_RGBA32F, GL_RGBA, GL_FLOAT, GL_LINEAR_MIPMAP_LINEAR, GL_LINEAR, 6, null);

		this.generateSpectrum();
	}

	public void kill() {
		this.generateSpectraShader.kill();
		this.evolveSpectraShader.kill();
		this.fftShader.kill();
		this.waveTexMergerShader.kill();

		this.gaussianNoiseTexture.kill();
		this.baseSpectraTexture.kill();
		this.waveInfoTexture.kill();
		this.Dx_Dz.kill();
		this.Dy_Dxz.kill();
		this.Dyx_Dyz.kill();
		this.Dxx_Dzz.kill();
		this.dispTexture.kill();
		this.derivativeTexture.kill();
	}

	public void generateSpectrum() {
		this.generateSpectraShader.enable();
		this.generateSpectraShader.setUniform1i("spectra_sz", WATER_RESOLUTION);
		this.generateSpectraShader.setUniform2f("wind_dir", this.options.getWindDir().normalize());
		this.generateSpectraShader.setUniform1f("h", this.options.getWaterDepth());
		this.generateSpectraShader.setUniform1f("U", this.options.getWindSpeed());
		this.generateSpectraShader.setUniform1f("F", this.options.getFetch());
		this.generateSpectraShader.setUniform1f("omega_p", (float) (22.0 * Math.pow(9.81 * 9.81 / (this.options.getWindSpeed() * this.options.getFetch()), 1.0 / 3.0)));
		this.generateSpectraShader.setUniform1f("multiplier", this.options.getSpectraMultiplier());
		this.generateSpectraShader.setUniform1f("length_scale", this.lengthScale);
		this.generateSpectraShader.setUniform1f("omega_min_cutoff", this.omegaMinCutoff);
		this.generateSpectraShader.setUniform1f("omega_max_cutoff", this.omegaMaxCutoff);
		this.generateSpectraShader.setUniform1f("swell", this.options.getSwell());
		this.generateSpectraShader.setUniform1f("spread_blend", this.options.getSpreadBlend());
		glBindImageTexture(0, this.baseSpectraTexture.getID(), 0, false, 0, GL_WRITE_ONLY, GL_RGBA32F);
		glBindImageTexture(1, this.gaussianNoiseTexture.getID(), 0, false, 0, GL_READ_ONLY, GL_RGBA32F);
		glBindImageTexture(2, this.waveInfoTexture.getID(), 0, false, 0, GL_WRITE_ONLY, GL_RGBA32F);
		glDispatchCompute(WATER_RESOLUTION, WATER_RESOLUTION, 1);
		glMemoryBarrier(GL_SHADER_IMAGE_ACCESS_BARRIER_BIT);
	}

	public void update(float time) {
		// -- generate evolved spectra --
		{
			this.evolveSpectraShader.enable();
			this.evolveSpectraShader.setUniform1i("spectra_sz", WATER_RESOLUTION);
			this.evolveSpectraShader.setUniform1f("t", time);
			glBindImageTexture(0, this.baseSpectraTexture.getID(), 0, false, 0, GL_READ_ONLY, GL_RGBA32F);
			glBindImageTexture(1, this.waveInfoTexture.getID(), 0, false, 0, GL_READ_ONLY, GL_RGBA32F);

			glBindImageTexture(2, this.Dx_Dz.getID(), 0, false, 0, GL_WRITE_ONLY, GL_RGBA32F);
			glBindImageTexture(3, this.Dy_Dxz.getID(), 0, false, 0, GL_WRITE_ONLY, GL_RGBA32F);
			glBindImageTexture(4, this.Dyx_Dyz.getID(), 0, false, 0, GL_WRITE_ONLY, GL_RGBA32F);
			glBindImageTexture(5, this.Dxx_Dzz.getID(), 0, false, 0, GL_WRITE_ONLY, GL_RGBA32F);
			glDispatchCompute(WATER_RESOLUTION, WATER_RESOLUTION, 1);
			glMemoryBarrier(GL_SHADER_IMAGE_ACCESS_BARRIER_BIT);
		}

		// -- apply fft --
		if (true) {
			Texture[] to_fft = new Texture[] { this.Dx_Dz, this.Dy_Dxz, this.Dyx_Dyz, this.Dxx_Dzz };
			//			Texture[] to_fft = new Texture[] { this.Dx_Dz };
			this.fftShader.enable();
			for (Texture t : to_fft) {
				this.apply2DFFT(t, WATER_RESOLUTION, true);
			}
		}

		// -- compute displacement and normals --
		if (true) {
			this.waveTexMergerShader.enable();
			this.waveTexMergerShader.setUniform1f("lambda", this.options.getLambda());
			glBindImageTexture(0, this.Dx_Dz.getID(), 0, false, 0, GL_READ_ONLY, GL_RGBA32F);
			glBindImageTexture(1, this.Dy_Dxz.getID(), 0, false, 0, GL_READ_ONLY, GL_RGBA32F);
			glBindImageTexture(2, this.Dyx_Dyz.getID(), 0, false, 0, GL_READ_ONLY, GL_RGBA32F);
			glBindImageTexture(3, this.Dxx_Dzz.getID(), 0, false, 0, GL_READ_ONLY, GL_RGBA32F);

			glBindImageTexture(4, this.dispTexture.getID(), 0, false, 0, GL_WRITE_ONLY, GL_RGBA32F);
			glBindImageTexture(5, this.derivativeTexture.getID(), 0, false, 0, GL_WRITE_ONLY, GL_RGBA32F);
			glDispatchCompute(WATER_RESOLUTION, WATER_RESOLUTION, 1);
			glMemoryBarrier(GL_SHADER_IMAGE_ACCESS_BARRIER_BIT);

			//generate mipmaps
			this.derivativeTexture.bind();
			glGenerateMipmap(GL_TEXTURE_2D);
			this.dispTexture.bind();
			glGenerateMipmap(GL_TEXTURE_2D);
		}
	}

	private void apply2DFFT(Texture t, int resolution, boolean invert) {
		this.fftShader.setUniform1i("invert", invert ? 1 : 0);
		glBindImageTexture(0, t.getID(), 0, false, 0, GL_READ_WRITE, GL_RGBA32F);

		this.fftShader.setUniform1i("workRow", 1);
		glDispatchCompute(resolution, 1, 1);
		glMemoryBarrier(GL_SHADER_IMAGE_ACCESS_BARRIER_BIT);
		this.fftShader.setUniform1i("workRow", 0);
		glDispatchCompute(resolution, 1, 1);
		glMemoryBarrier(GL_SHADER_IMAGE_ACCESS_BARRIER_BIT);
	}

	private Texture generateGaussianNoiseTexture() {
		float[] data = new float[WATER_RESOLUTION * WATER_RESOLUTION * 4];
		for (int i = 0; i < data.length; i++) {
			float u1 = (float) Math.random();
			float u2 = (float) Math.random();
			data[i] = (float) (Math.sqrt(-2.0 * Math.log(u1)) * Math.cos(Math.PI * 2.0 * u2));
		}

		int textureID = glGenTextures(); //create texture handle
		glBindTexture(GL_TEXTURE_2D, textureID); //set as active texture
		glTexStorage2D(GL_TEXTURE_2D, 1, GL_RGBA32F, WATER_RESOLUTION, WATER_RESOLUTION); //allocate storage for texture
		if (data != null) {
			glTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, WATER_RESOLUTION, WATER_RESOLUTION, GL_RGBA, GL_FLOAT, data); //initialize 0th mipmap layer of texture
		}

		//set interpolation filters
		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
		//set border behaviour
		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_REPEAT);
		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_REPEAT);
		glBindTexture(GL_TEXTURE_2D, 0);

		return new Texture(textureID);
	}
}
