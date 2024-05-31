package spectral_raytracing;

import lwjglengine.graphics.Framebuffer;
import lwjglengine.graphics.Material;
import lwjglengine.ui.UIElement;
import lwjglengine.ui.UISection;
import lwjglengine.window.AdjustableWindow;
import lwjglengine.window.Window;

public class SpectralRaytracingWindow extends Window {

	private UISection uiSection;

	private SpectralRaytracingScreen screen;

	public SpectralRaytracingWindow(int xOffset, int yOffset, int width, int height, Window parentWindow) {
		super(xOffset, yOffset, width, height, parentWindow);
		this.init();
	}

	private void init() {
		this.uiSection = new UISection();
		this.uiSection.getBackgroundRect().setFrameAlignmentStyle(UIElement.FROM_LEFT, UIElement.FROM_TOP);
		this.uiSection.getBackgroundRect().setContentAlignmentStyle(UIElement.ALIGN_LEFT, UIElement.ALIGN_TOP);
		this.uiSection.getBackgroundRect().setFillWidth(true);
		this.uiSection.getBackgroundRect().setFillHeight(true);
		this.uiSection.getBackgroundRect().setMaterial(Material.transparent());
		this.uiSection.getBackgroundRect().bind(this.rootUIElement);

		this.screen = new SpectralRaytracingScreen();

		//launch a color test window
		{
			AdjustableWindow window = new AdjustableWindow(new ColorTestWindow(null), this);
		}

		this._resize();
	}

	@Override
	protected void _kill() {
		this.uiSection.kill();
		this.screen.kill();
	}

	@Override
	protected void _resize() {
		this.uiSection.setScreenDimensions(this.getWidth(), this.getHeight());
		this.screen.setScreenDimensions(this.getWidth(), this.getHeight());
	}

	@Override
	public String getDefaultTitle() {
		return "Spectral Raytracing Window";
	}

	@Override
	protected void _update() {
		this.uiSection.update();
	}

	@Override
	protected void renderContent(Framebuffer outputBuffer) {
		this.screen.render(outputBuffer);
		this.uiSection.render(outputBuffer, this.getWindowMousePos());
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
