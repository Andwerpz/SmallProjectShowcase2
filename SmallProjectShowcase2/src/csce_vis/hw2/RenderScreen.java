package csce_vis.hw2;

import lwjglengine.graphics.Framebuffer;
import lwjglengine.graphics.Texture;
import lwjglengine.screen.Screen;

public class RenderScreen extends Screen {
	//normal forward rendering pipeline. 
	//render scene geometry first, then particles (with depth checking), then skybox last. 
	//particle updates shouldn't be handled here. 

	private int world_scene;

	private Texture geometryNormalMap; // RGB: normal
	private Texture geometrySpecularMap; // RGB: specular, A: shininess
	private Texture geometryColorMap; // RGB: color, A: alpha
	private Texture geometryColorIDMap; // RGB: colorID

	public RenderScreen() {
		super();

	}

	public void setWorldScene(int scene) {
		this.world_scene = scene;
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
