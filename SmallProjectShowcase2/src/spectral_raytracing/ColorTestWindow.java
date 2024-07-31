package spectral_raytracing;

import java.awt.Color;
import java.io.IOException;

import org.lwjgl.glfw.GLFW;

import lwjglengine.graphics.Framebuffer;
import lwjglengine.graphics.Material;
import lwjglengine.model.Line;
import lwjglengine.model.ModelInstance;
import lwjglengine.ui.Text;
import lwjglengine.ui.UIElement;
import lwjglengine.ui.UIFilledRectangle;
import lwjglengine.ui.UISection;
import lwjglengine.window.AdjustableWindow;
import lwjglengine.window.ObjectEditorWindow;
import lwjglengine.window.Window;
import myutils.file.FileUtils;
import myutils.file.csv.CSVReader;
import myutils.math.Mat3;
import myutils.math.MathUtils;
import myutils.math.Vec3;

public class ColorTestWindow extends Window {
	//exists to test conversion from rgb to spectrum representation, and back again. 

	//idea is that we can accurately represent any color with a sort of quadratic spectrum
	//S(\lambda) = \sigmoid(c0 + c1 * \lambda + c2 * \lambda^2)

	//OpenGL is uses linear RGB color by default

	//some things i still need to iron out:
	// - what exactly is the spectrum power distribution for my monitor
	//   - i suppose i can find this one by testing a few spectra, as long as the math is correct. 
	// - need to find whitepoint
	// - how to do optimization?? as in finding the appropriate c0, c1, c2 coefficients
	//   - they mention newton's method

	//perhaps we don't need to do any of that, look here:
	// - http://scottburns.us/fast-rgb-to-spectrum-conversion-for-reflectances/
	// - and here, some context: http://scottburns.us/reflectance-curves-from-srgb/
	//for the sRGB color space, the idea is that we can precompute the three curves that match pure red, green, and blue
	//and take a weighted sum of the spectra for any color we want. 

	//useful conversion sheet - http://www.brucelindbloom.com/index.html?Math.html

	//ok, so i don't need to know my monitor's spectral distribution data, i only need to know what color space it
	//uses, and it's whitepoint. then, i can use a matrix to convert between the two. 
	//conveniently, this website has already calculated the matrix i need
	// - http://www.brucelindbloom.com/index.html?Eqn_RGB_XYZ_Matrix.html

	//so now the plan is
	// - take in sRGB value
	// - convert sRGB to RGB 
	//   - by RGB, i mean linear RGB, see: https://stackoverflow.com/questions/12524623/what-are-the-practical-differences-when-working-with-colors-in-a-linear-vs-a-no
	// - use scott burns's fast RGB to spectrum conversion to produce spectrum
	// - compute spectrum's XYZ values using cie XYZ matching functions
	// - convert XYZ values into sRGB using matrix

	//perhaps there is a way to get a reflectance distribution from an XYZ triplet. If that's the case, then we can
	//just convert sRGB into XYZ, and get the reflectance that way. 

	//ok, the finalized workflow is
	// - take in RGB value
	//   - since OpenGL already uses linear RGB, we can just directly use the values there
	// - use scott burns's fast RGB to spectrum conversion to produce spectrum
	// - compute spectrum's XYZ values using cie XYZ matching functions
	// - apply some scale factors to X and Z, to fix whitepoint
	// - convert XYZ values into RGB using matrix

	//use formula from this website to compute IOR
	// - https://wiki.luxcorerender.org/Glass_Material_IOR_and_Dispersion

	//database of material ior and dispersion
	// - https://refractiveindex.info/?shelf=main&book=SiO2&page=Arosa

	private UISection uiSection;
	private ColorTestSettings colorTestSettings;
	private ObjectEditorWindow settingsWindow;

	private UIFilledRectangle referenceColorRect, spectrumColorRect;
	private Text referenceText, spectrumText;

	private Vec3 RGBColor;

	//we're only interested in wavelength data from 360nm to 830nm inclusive
	private static int nm_min = 360;
	private static int nm_max = 830;
	private static int nm_range = nm_max - nm_min + 1;
	private float[][] RGBC_data = new float[3][nm_range];
	private float[][] XYZ_data = new float[3][nm_range];
	private float cie_y_int; //area under the cie y curve

	//generated spectrum using rgbc
	private float[] spectrum_data = new float[nm_range];
	private ModelInstance[] spectrum_display;

	private boolean doXZScaling = true;

	public ColorTestWindow(Window parentWindow) {
		super(parentWindow);
		this.init();
	}

	public ColorTestWindow(int width, int height, Window parentWindow) {
		super(0, 0, width, height, parentWindow);
		this.init();
	}

	private void init() {
		//read in rgb_components data, goes from 360nm to 830nm
		try {
			CSVReader csv = new CSVReader();
			csv.setReadHeader(false);
			csv.setTranspose(true);
			csv.readFileAsCSV(FileUtils.loadFileRelative("/res/spectral_raytracing/rgb_components.csv"));
			for (int i = 0; i < 3; i++) {
				for (int j = nm_min; j <= nm_max; j++) {
					this.RGBC_data[i][j - nm_min] = Float.parseFloat(csv.getData()[i][j - nm_min]);
				}
			}
		}
		catch (IOException e) {
			e.printStackTrace();
		}

		//read in cie xyz data, goes from 360nm to 830nm
		try {
			CSVReader csv = new CSVReader();
			csv.setTranspose(true);
			csv.readFileAsCSV(FileUtils.loadFileRelative("/res/spectral_raytracing/cie_xyz.csv"));
			for (int i = 0; i < 3; i++) {
				for (int j = nm_min; j <= nm_max; j++) {
					this.XYZ_data[i][j - nm_min] = Float.parseFloat(csv.getData()[i][j - nm_min]);
				}
			}
		}
		catch (IOException e) {
			e.printStackTrace();
		}

		this.cie_y_int = 0;
		for (int i = nm_min; i <= nm_max; i++) {
			this.cie_y_int += this.XYZ_data[1][i - nm_min];
		}

		this.uiSection = new UISection();
		this.uiSection.getBackgroundRect().setFrameAlignmentStyle(UIElement.FROM_LEFT, UIElement.FROM_TOP);
		this.uiSection.getBackgroundRect().setContentAlignmentStyle(UIElement.ALIGN_LEFT, UIElement.ALIGN_TOP);
		this.uiSection.getBackgroundRect().setFillWidth(true);
		this.uiSection.getBackgroundRect().setFillHeight(true);
		this.uiSection.getBackgroundRect().bind(this.rootUIElement);

		this.RGBColor = new Vec3(0);

		this.referenceColorRect = new UIFilledRectangle(0, 0, 0, 100, 100, this.uiSection.getBackgroundScene());
		this.referenceColorRect.setFrameAlignmentStyle(UIElement.FROM_LEFT, UIElement.FROM_TOP);
		this.referenceColorRect.setContentAlignmentStyle(UIElement.ALIGN_LEFT, UIElement.ALIGN_TOP);
		this.referenceColorRect.setFillHeight(true);
		this.referenceColorRect.bind(this.uiSection.getBackgroundRect());

		this.spectrumColorRect = new UIFilledRectangle(0, 0, 0, 100, 100, this.uiSection.getBackgroundScene());
		this.spectrumColorRect.setFrameAlignmentStyle(UIElement.FROM_RIGHT, UIElement.FROM_TOP);
		this.spectrumColorRect.setContentAlignmentStyle(UIElement.ALIGN_RIGHT, UIElement.ALIGN_TOP);
		this.spectrumColorRect.setFillHeight(true);
		this.spectrumColorRect.bind(this.uiSection.getBackgroundRect());

		this.referenceText = new Text(0, 0, "Reference Color", this.uiSection.getTextScene());
		this.referenceText.setFrameAlignmentStyle(UIElement.FROM_CENTER_LEFT, UIElement.FROM_TOP);
		this.referenceText.setContentAlignmentStyle(UIElement.ALIGN_CENTER, UIElement.ALIGN_TOP);
		this.referenceText.setFrameAlignmentOffset(0, 10);
		this.referenceText.bind(this.referenceColorRect);

		this.spectrumText = new Text(0, 0, "Spectrum Color", this.uiSection.getTextScene());
		this.spectrumText.setFrameAlignmentStyle(UIElement.FROM_CENTER_LEFT, UIElement.FROM_TOP);
		this.spectrumText.setContentAlignmentStyle(UIElement.ALIGN_CENTER, UIElement.ALIGN_TOP);
		this.spectrumText.setFrameAlignmentOffset(0, 10);
		this.spectrumText.bind(this.spectrumColorRect);

		{
			this.colorTestSettings = new ColorTestSettings();
			this.colorTestSettings.setRGBColor(this.RGBColor);
			this.settingsWindow = new ObjectEditorWindow(this.colorTestSettings);
			AdjustableWindow window = new AdjustableWindow(this.settingsWindow, this);
			window.setAllowUserRenesting(false);

			window.setAlignmentStyle(Window.FROM_LEFT, Window.FROM_BOTTOM);

			window.setOffset(10, 10);
			window.setDimensions(200, 120);
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
		this.referenceColorRect.setWidth(this.getWidth() / 2);
		this.spectrumColorRect.setWidth(this.getWidth() - (this.getWidth() / 2));

		this.setRGBColor(this.RGBColor);
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

	private void setDoXZScaling(boolean b) {
		this.doXZScaling = b;
		this.setRGBColor(this.RGBColor);
	}

	@Override
	protected void _keyPressed(int key) {
		switch (key) {
		case GLFW.GLFW_KEY_X:
			this.setDoXZScaling(!this.doXZScaling);
			break;
		}
	}

	@Override
	protected void _keyReleased(int key) {
		// TODO Auto-generated method stub

	}

	private static float sRGBToLinear(float a) {
		if (a < 0.04045) {
			return a / 12.92f;
		}
		return (float) Math.pow((a + 0.055) / 1.055, 2.4);
	}

	private static Vec3 sRGBToLinear(Vec3 sRGB) {
		return new Vec3(sRGBToLinear(sRGB.x), sRGBToLinear(sRGB.y), sRGBToLinear(sRGB.z));
	}

	// - take in RGB value
	//   - since OpenGL already uses linear RGB, we can just directly use the values there
	//   - by RGB, i mean linear RGB, see: https://stackoverflow.com/questions/12524623/what-are-the-practical-differences-when-working-with-colors-in-a-linear-vs-a-no
	// - use scott burns's fast RGB to spectrum conversion to produce spectrum
	// - compute spectrum's XYZ values using cie XYZ matching functions
	// - apply some scale factors to X and Z, to fix whitepoint
	// - convert XYZ values into linear RGB using matrix
	//   - i think this RGB value is inside the sRGB color space, so we can color correct later by converting RGB to sRGB
	private void setRGBColor(Vec3 color) {
		// - take in R value
		this.RGBColor.set(MathUtils.clamp(0, 1, color));
		this.referenceColorRect.setMaterial(new Material(this.RGBColor));
		Vec3 RGB = new Vec3(this.RGBColor);

		// - use scott burns's fast RGB to spectrum conversion to produce spectrum
		for (int i = nm_min; i <= nm_max; i++) {
			this.spectrum_data[i - nm_min] = RGB.x * this.RGBC_data[0][i - nm_min] + RGB.y * this.RGBC_data[1][i - nm_min] + RGB.z * this.RGBC_data[2][i - nm_min];
		}
		{
			//create spectrum display
			if (this.spectrum_display != null) {
				for (ModelInstance m : this.spectrum_display) {
					m.kill();
				}
				this.spectrum_display = null;
			}
			this.spectrum_display = new ModelInstance[nm_max - nm_min];
			float x_interval = this.getWidth() / (float) this.spectrum_display.length;
			float y_scale = this.getHeight();
			Vec3 line_color = new Vec3(1).sub(this.RGBColor);
			for (int i = nm_min; i < nm_max; i++) {
				float x0 = (i - nm_min) * x_interval;
				float x1 = (i - nm_min + 1) * x_interval;
				float y0 = this.spectrum_data[i - nm_min] * y_scale;
				float y1 = this.spectrum_data[i - nm_min + 1] * y_scale;
				ModelInstance line = Line.addDefaultLine(x0, y0, x1, y1, this.uiSection.getTextScene());
				line.setMaterial(new Material(line_color));
				this.spectrum_display[i - nm_min] = line;
			}
		}

		// - compute spectrum's XYZ values using cie XYZ matching functions
		Vec3 XYZ = new Vec3(0);
		for (int i = nm_min; i <= nm_max; i++) {
			XYZ.x += this.spectrum_data[i - nm_min] * this.XYZ_data[0][i - nm_min];
			XYZ.y += this.spectrum_data[i - nm_min] * this.XYZ_data[1][i - nm_min];
			XYZ.z += this.spectrum_data[i - nm_min] * this.XYZ_data[2][i - nm_min];
		}
		XYZ.divi(this.cie_y_int);

		// - apply some scale factors to x and z, to fix whitepoint
		if (this.doXZScaling) {
			XYZ.x *= 0.9505 / 1.000081;
			XYZ.z *= 1.0888 / 1.0003315;
		}

		// - convert XYZ values into linear RGB in the sRGB space using matrix
		//since OpenGL uses linear, this is exactly what we want
		// 3.2404542 -1.5371385 -0.4985314
		//-0.9692660  1.8760108  0.0415560
		// 0.0556434 -0.2040259  1.0572252
		Mat3 xyz_to_srgb = new Mat3(new float[][] { { 3.2404542f, -1.5371385f, -0.4985314f }, { -0.9692660f, 1.8760108f, 0.0415560f }, { 0.0556434f, -0.2040259f, 1.0572252f } });
		Vec3 n_RGB = xyz_to_srgb.mul(XYZ);

		this.spectrumColorRect.setMaterial(new Material(n_RGB));
		this.referenceText.setMaterial(new Material(new Vec3(1).sub(this.RGBColor)));
		this.spectrumText.setMaterial(new Material(new Vec3(1).sub(n_RGB)));
	}

	public class ColorTestSettings {
		Vec3 RGBColor;

		public Vec3 getRGBColor() {
			return RGBColor;
		}

		public void setRGBColor(Vec3 RGBColor) {
			this.RGBColor = RGBColor;
			ColorTestWindow.this.setRGBColor(RGBColor);
		}
	}

}
