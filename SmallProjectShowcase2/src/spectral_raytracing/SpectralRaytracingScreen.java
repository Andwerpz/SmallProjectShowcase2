package spectral_raytracing;

import lwjglengine.graphics.Framebuffer;
import lwjglengine.screen.Screen;

public class SpectralRaytracingScreen extends Screen {
	//woah, spectral raytracing, bueno as jonathan would say

	//actually path tracing, but raytracing sounds better. 
	//this time, we're using a more physically accurate spectrum representation of light
	//this allows us to capture phenomenon like diffraction

	//when rendering, should first sample xyz color coefficients, which then we use another shader to convert to whatever 
	//rgb color space we want. 

	//in the shader, should sample several different (fixed?) wavelengths, to figure out xyz color coefficients. 
	//perhaps in the beginning, make it completely random, then can just multiply against xyz sensitivity distributions.

	//preview rendering should maintain rgb sampling? it's much faster than spectrum rendering

	public SpectralRaytracingScreen() {

	}

	@Override
	public void buildBuffers() {
		// TODO Auto-generated method stub

	}

	@Override
	protected void _render(Framebuffer outputBuffer) {
		// TODO Auto-generated method stub

	}

	@Override
	protected void _kill() {
		// TODO Auto-generated method stub

	}

}
