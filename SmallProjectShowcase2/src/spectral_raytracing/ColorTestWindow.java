package spectral_raytracing;

import lwjglengine.graphics.Framebuffer;
import lwjglengine.graphics.Material;
import lwjglengine.ui.UIElement;
import lwjglengine.ui.UIFilledRectangle;
import lwjglengine.ui.UISection;
import lwjglengine.window.AdjustableWindow;
import lwjglengine.window.ObjectEditorWindow;
import lwjglengine.window.Window;
import myutils.math.Vec3;

public class ColorTestWindow extends Window {
	//exists to test conversion from rgb to spectrum representation, and back again. 

	private UISection uiSection;
	private ColorTestSettings colorTestSettings;
	private ObjectEditorWindow settingsWindow;

	private UIFilledRectangle referenceColorRect, spectrumColorRect;

	private Vec3 rgbColor;

	public ColorTestWindow(Window parentWindow) {
		super(parentWindow);
		this.init();
	}

	private void init() {
		this.uiSection = new UISection();
		this.uiSection.getBackgroundRect().setFrameAlignmentStyle(UIElement.FROM_LEFT, UIElement.FROM_TOP);
		this.uiSection.getBackgroundRect().setContentAlignmentStyle(UIElement.ALIGN_LEFT, UIElement.ALIGN_TOP);
		this.uiSection.getBackgroundRect().setFillWidth(true);
		this.uiSection.getBackgroundRect().setFillHeight(true);
		this.uiSection.getBackgroundRect().bind(this.rootUIElement);

		this.rgbColor = new Vec3(1, 0, 1);

		this.referenceColorRect = new UIFilledRectangle(0, 0, 0, 100, 100, this.uiSection.getBackgroundScene());
		this.referenceColorRect.setFrameAlignmentStyle(UIElement.FROM_LEFT, UIElement.FROM_TOP);
		this.referenceColorRect.setContentAlignmentStyle(UIElement.ALIGN_LEFT, UIElement.ALIGN_TOP);
		this.referenceColorRect.setFillWidth(true);
		this.referenceColorRect.setFillHeight(true);
		this.referenceColorRect.bind(this.uiSection.getBackgroundRect());

		this.spectrumColorRect = new UIFilledRectangle(0, 0, 0, 100, 100, this.uiSection.getBackgroundScene());
		this.spectrumColorRect.setFrameAlignmentStyle(UIElement.FROM_RIGHT, UIElement.FROM_TOP);
		this.spectrumColorRect.setContentAlignmentStyle(UIElement.ALIGN_RIGHT, UIElement.ALIGN_TOP);
		this.spectrumColorRect.setFillWidth(true);
		this.spectrumColorRect.bind(this.uiSection.getBackgroundRect());

		{
			this.colorTestSettings = new ColorTestSettings();
			this.colorTestSettings.setRgbColor(this.rgbColor);
			this.settingsWindow = new ObjectEditorWindow(this.colorTestSettings);
			AdjustableWindow window = new AdjustableWindow(this.settingsWindow, this);
			window.setAllowUserRenesting(false);
		}

		this._resize();
	}

	@Override
	protected void _kill() {
		this.uiSection.kill();
	}

	@Override
	protected void _resize() {
		this.uiSection.setScreenDimensions(this.getWidth(), this.getHeight());
		this.spectrumColorRect.setWidth(this.getWidth() / 2);
	}

	@Override
	public String getDefaultTitle() {
		return "Color Test Window";
	}

	@Override
	protected void _update() {

	}

	@Override
	protected void renderContent(Framebuffer outputBuffer) {
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

	private void setRGBColor(Vec3 color) {
		this.rgbColor.set(color);
		this.referenceColorRect.setMaterial(new Material(this.rgbColor));
	}

	public class ColorTestSettings {
		Vec3 rgbColor;

		public ColorTestSettings() {
			this.rgbColor = new Vec3(1, 0, 1);
		}

		public Vec3 getRgbColor() {
			return rgbColor;
		}

		public void setRgbColor(Vec3 rgbColor) {
			this.rgbColor.set(rgbColor);
			setRGBColor(rgbColor);
		}
	}

}
