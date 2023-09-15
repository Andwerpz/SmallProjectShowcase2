package pbr_rendering;

import java.lang.reflect.Field;
import java.util.HashMap;

import lwjglengine.graphics.Framebuffer;
import lwjglengine.graphics.Texture;
import lwjglengine.screen.PerspectiveScreen;
import lwjglengine.window.AdjustableWindow;
import lwjglengine.window.ModelViewerWindow;
import lwjglengine.window.TextureViewerWindow;
import lwjglengine.window.Window;

public class PBRRenderingWindow extends Window {

	public PBRRenderingWindow(int xOffset, int yOffset, int width, int height, Window parentWindow) {
		super(xOffset, yOffset, width, height, parentWindow);
		this.init();
	}

	private void init() {
		//load model viewer
		ModelViewerWindow modelViewer = new ModelViewerWindow(0, 0, 800, 600, this);
		AdjustableWindow modelViewerAdj = new AdjustableWindow(modelViewer, this);
		modelViewerAdj.setAllowManualResizing(false);

		//get perspective screen from model viewer
		PerspectiveScreen modelViewerPerspectiveScreen = null;
		{
			Field[] fields = modelViewer.getClass().getDeclaredFields();
			for (Field f : fields) {
				if (f.getName() == "perspectiveScreen") {
					try {
						f.setAccessible(true);
						modelViewerPerspectiveScreen = (PerspectiveScreen) f.get(modelViewer);
					}
					catch (IllegalArgumentException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
					catch (IllegalAccessException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
			}
		}

		//get buffers from perspective screen, and make the viewer windows. 
		String[] textureList = new String[] { "geometryPositionMap", "geometryColorMap", "geometryNormalMap", "lightingColorMap", "lightingBrightnessMap" };
		{
			HashMap<String, Object> fieldMap = new HashMap<>();
			Field[] fields = modelViewerPerspectiveScreen.getClass().getDeclaredFields();
			for (Field f : fields) {
				try {
					f.setAccessible(true); //we do be breaking the rules here
					fieldMap.put(f.getName(), f.get(modelViewerPerspectiveScreen));
				}
				catch (IllegalArgumentException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				catch (IllegalAccessException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}

			for (String s : textureList) {
				Texture t = (Texture) fieldMap.get(s);
				AdjustableWindow tWindow = new AdjustableWindow(s, new TextureViewerWindow(t), this);
			}
		}

		this._resize();
	}

	@Override
	protected void _kill() {
		// TODO Auto-generated method stub

	}

	@Override
	protected void _resize() {
		// TODO Auto-generated method stub

	}

	@Override
	public String getDefaultTitle() {
		return "PBR Rendering";
	}

	@Override
	protected void _update() {
		// TODO Auto-generated method stub

	}

	@Override
	protected void renderContent(Framebuffer outputBuffer) {
		// TODO Auto-generated method stub

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
