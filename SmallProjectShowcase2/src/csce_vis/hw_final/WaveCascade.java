package csce_vis.hw_final;

import csce_vis.hw_final.HWFWindow.Options;
import lwjglengine.graphics.Shader;
import lwjglengine.graphics.Texture;

public class WaveCascade {
	//responsible for modelling one frequency band of the oceanographic spectrum

	public Texture gaussianNoiseTexture;
	public Texture baseSpectraTexture;
	public Texture waveInfoTexture;
	public Texture Dx_Dz, Dy_Dxz, Dyx_Dyz, Dxx_Dzz;
	public Texture dispTexture, normalTexture;

	public float lengthScale;
	public float omegaMinCutoff, omegaMaxCutoff;

	public WaveCascade() {
		//TODO
	}

	public void generateSpectrum() {
		//TODO
	}

	public void update(float time) {
		//TODO
	}
}
