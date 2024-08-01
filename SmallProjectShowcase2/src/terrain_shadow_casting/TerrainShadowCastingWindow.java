package terrain_shadow_casting;

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
import lwjglengine.graphics.Texture;
import lwjglengine.scene.Scene;
import lwjglengine.screen.ScreenQuad;
import lwjglengine.util.ShaderUtils;
import lwjglengine.window.Window;

public class TerrainShadowCastingWindow extends Window {
	// - 2D Terrain Shadow Casting
	//   - https://www.youtube.com/watch?v=bMTeCqNkId8
	//   - UPDATE : https://www.youtube.com/watch?v=6bnFfE82AJg

	private Texture terrainColorTexture, terrainHeightTexture;
	private final int TERRAIN_SCENE = Scene.generateScene();
	private Shader terrainShader;

	public TerrainShadowCastingWindow(int xOffset, int yOffset, int width, int height, Window parentWindow) {
		super(xOffset, yOffset, width, height, parentWindow);
		this.init();
	}

	private void init() {
		this.terrainColorTexture = new Texture(this.getWidth(), this.getHeight());
		this.terrainHeightTexture = new Texture(this.getWidth(), this.getHeight());
		
		this.terrainShader = ShaderUtils.createShader("/terrain_shadow_casting/terrain.vert", "/terrain_shadow_casting/terrain.frag");
		this.terrainShader.setUniform1i("tex_terrain_color", 0);
		
		this._resize();
	}

	@Override
	protected void _kill() {
		Scene.removeScene(TERRAIN_SCENE);
		this.terrainShader.kill();
		this.terrainColorTexture.kill();
		this.terrainHeightTexture.kill();
	}

	@Override
	protected void _resize() {
		//this shouldn't happen, at least for now :))
	}

	@Override
	public String getDefaultTitle() {
		return "Terrain Shadow Casting";
	}
	
	private void generateTerrain() {
		
	}

	@Override
	protected void _update() {
		// TODO Auto-generated method stub

	}

	@Override
	protected void renderContent(Framebuffer outputBuffer) {
		glViewport(0, 0, this.getWidth(), this.getHeight());
		this.terrainColorTexture.bind(GL_TEXTURE0);
		outputBuffer.bind();
		ScreenQuad.screenQuad.render();
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
