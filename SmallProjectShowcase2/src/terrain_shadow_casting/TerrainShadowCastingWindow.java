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

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

import javax.imageio.ImageIO;

import org.lwjgl.glfw.GLFW;

import lwjglengine.graphics.Framebuffer;
import lwjglengine.graphics.Material;
import lwjglengine.graphics.Shader;
import lwjglengine.graphics.Texture;
import lwjglengine.input.Button;
import lwjglengine.input.Input.InputCallback;
import lwjglengine.scene.Scene;
import lwjglengine.screen.ScreenQuad;
import lwjglengine.ui.ObjectEditor;
import lwjglengine.ui.UIElement;
import lwjglengine.ui.UISection;
import lwjglengine.util.ShaderUtils;
import lwjglengine.window.AdjustableWindow;
import lwjglengine.window.FileCreatorWindow;
import lwjglengine.window.Window;
import myutils.math.MathUtils;
import myutils.math.Vec2;
import myutils.math.Vec3;
import myutils.misc.Pair;

public class TerrainShadowCastingWindow extends Window implements InputCallback {
	// - 2D Terrain Shadow Casting
	//   - https://www.youtube.com/watch?v=bMTeCqNkId8
	//   - UPDATE : https://www.youtube.com/watch?v=6bnFfE82AJg
	// - https://wwwtyro.net/2019/03/21/advanced-map-shading.html
	//   - pull satellite images and terrain elevation. Much more interesting than noise generated terrain. 
	//   - also explains some techniques for soft shadowing. 

	//lat lon to xyz map tile conversion
	//https://gis.stackexchange.com/questions/133205/wmts-convert-geolocation-lat-long-to-tile-index-at-a-given-zoom-level

	//slippy map convention
	//https://wiki.openstreetmap.org/wiki/Slippy_map_tilenames

	//TODO
	// - handle rendering in a seperate thread, and have a fancy progress readout on the gui. 
	//   - use this window as a lat, lon, zoom selector for the final render. 
	//   - also give some presets to render. 
	// - optimize shadowing portion of rendering. 
	//   - can build a quadtree, and somehow traverse that. Quadtree might be able to be generated using opengl mipmaps?
	// - allow to pick planet??
	//   - https://www.openplanetary.org/opm/basemaps
	//   - can generate images from mars and moon
	//   - annoying problem: they have elevation as an arbitrary color gradient. Either i need to reverse engineer
	//     the color gradient used and apply the reverse transformation, or i need to find somewhere that 
	//     provides the DEM (digital elevation model) tiles. 
	//   - one idea to reverse engineer the gradient is to just guess :3 Just create a gradient that looks good enough,
	//     then map the heights linearly onto the gradient. 
	//   - another idea is to get the DEM data, and sample color, height pairs. Then create the gradient that way. 
	//   - ok, mars is paused for now. getting DEM data proved to be too annoying. 

	private UISection uiSection;
	private RenderOptions renderOptions;

	private Button renderButton;

	public TerrainShadowCastingWindow(int xOffset, int yOffset, int width, int height, Window parentWindow) {
		super(xOffset, yOffset, width, height, parentWindow);
		this.init();
	}

	private void init() {
		this.renderOptions = new RenderOptions();

		this.uiSection = new UISection();
		this.uiSection.getBackgroundRect().setFrameAlignmentStyle(UIElement.FROM_LEFT, UIElement.FROM_TOP);
		this.uiSection.getBackgroundRect().setContentAlignmentStyle(UIElement.ALIGN_LEFT, UIElement.ALIGN_TOP);
		this.uiSection.getBackgroundRect().setMaterial(Material.TOP_BAR_DEFAULT_MATERIAL);
		this.uiSection.getBackgroundRect().setFillWidth(true);
		this.uiSection.getBackgroundRect().setFillHeight(true);
		this.uiSection.getBackgroundRect().bind(this.rootUIElement);

		this.renderButton = new Button(5, 5, 100, 20, "btn_render", "Render", this, this.uiSection.getSelectionScene(), this.uiSection.getTextScene());
		this.renderButton.setFrameAlignmentStyle(UIElement.FROM_LEFT, UIElement.FROM_TOP);
		this.renderButton.setContentAlignmentStyle(UIElement.ALIGN_LEFT, UIElement.ALIGN_TOP);
		this.renderButton.bind(this.uiSection.getBackgroundRect());

		ObjectEditor oe = new ObjectEditor(this.renderOptions, this.uiSection);
		oe.setFrameAlignmentOffset(0, 30);
		oe.setFrameAlignmentStyle(UIElement.FROM_LEFT, UIElement.FROM_TOP);
		oe.setContentAlignmentStyle(UIElement.ALIGN_LEFT, UIElement.ALIGN_TOP);
		oe.setFillWidth(true);
		oe.setFillHeight(true);
		oe.setMaterial(Material.TOP_BAR_DEFAULT_MATERIAL);
		oe.bind(this.uiSection.getBackgroundRect());

		this.setDimensions(400, 300);

		this._resize();
	}

	public class RenderOptions {
		int zoom, width, height;
		float lat, lon;

		public RenderOptions() {
			//sanfrancisco
			//		float lat = 37.774929f;
			//		float lon = -122.419418f;
			//		int zoom = 11;

			//grand canyon
			//			float lat = 36.230940f;
			//			float lon = -112.410221f;
			//			int zoom = 8;

			//mt everest
			//		float lat = 27.9881f;
			//		float lon = 86.9250f;
			//		int zoom = 6;

			//westside el paso
			//			float lat = 31.8347f;
			//			float lon = -106.5650f;
			//			int zoom = 9;

			//austin tx
			//		float lat = 30.418415f;
			//		float lon = -97.831395f;
			//		int zoom = 10;

			//Uncompahgre peak
			float lat = 38.0696868f;
			float lon = -107.4662279f;
			int zoom = 13;

			this.zoom = zoom;
			this.lat = lat;
			this.lon = lon;
			this.width = 400;
			this.height = 300;
		}

		public int getZoom() {
			return zoom;
		}

		public void setZoom(int zoom) {
			this.zoom = zoom;
		}

		public int getWidth() {
			return width;
		}

		public void setWidth(int width) {
			this.width = width;
		}

		public int getHeight() {
			return height;
		}

		public void setHeight(int height) {
			this.height = height;
		}

		public float getLat() {
			return lat;
		}

		public void setLat(float lat) {
			this.lat = lat;
		}

		public float getLon() {
			return lon;
		}

		public void setLon(float lon) {
			this.lon = lon;
		}

	}

	private void createRenderWindow(int zoom, float lat, float lon, int width, int height) {
		TerrainShadowCastingRenderWindow r = new TerrainShadowCastingRenderWindow(zoom, lat, lon, width, height);
		AdjustableWindow adj = this.addChildAdjWindow(r);
		adj.setAllowManualResizing(false);
	}

	@Override
	protected void _kill() {
		this.uiSection.kill();
	}

	@Override
	protected void _resize() {
		this.uiSection.setScreenDimensions(this.getWidth(), this.getHeight());
	}

	@Override
	public String getDefaultTitle() {
		return "Terrain Shadow Casting";
	}

	@Override
	protected void _update() {
		this.uiSection.update();
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
		this.uiSection.mousePressed(button);
	}

	@Override
	protected void _mouseReleased(int button) {
		this.uiSection.mouseReleased(button);
	}

	@Override
	protected void _mouseScrolled(float wheelOffset, float smoothOffset) {
		// TODO Auto-generated method stub

	}

	@Override
	protected void _keyPressed(int key) {
		this.uiSection.keyPressed(key);
	}

	@Override
	protected void _keyReleased(int key) {
		this.uiSection.keyReleased(key);
	}

	@Override
	public void inputClicked(String sID) {
		switch (sID) {
		case "btn_render":
			this.createRenderWindow(this.renderOptions.zoom, this.renderOptions.lat, this.renderOptions.lon, this.renderOptions.width, this.renderOptions.height);
			break;
		}
	}

	@Override
	public void inputChanged(String sID) {
		// TODO Auto-generated method stub

	}

	//for reference if i ever break it D:
	/*
	//for free!!
	private static final String API_KEY = "wuHJ0xDcLkjvM20HPGA7";
	
	private static final int TILE_RESOLUTION = 512;
	
	//api request format:
	//https://api.maptiler.com/tiles/{map_name}/{zoom}/{x}/{y}.{format}
	//note that what formats are supported is dependent on the selected map
	
	private static BufferedImage tileQuery(String map_name, int zoom, int x, int y, String format) {
		System.out.println("Tile Query : " + map_name + " " + zoom + " " + x + " " + y);
		try {
			String url_string = "https://api.maptiler.com/tiles/" + map_name + "/" + zoom + "/" + x + "/" + y + "." + format + "?key=" + API_KEY;
			URL url = new URL(url_string);
	
			HttpURLConnection connection = (HttpURLConnection) url.openConnection();
			connection.setRequestMethod("GET");
			BufferedImage img = ImageIO.read(connection.getInputStream());
			connection.disconnect();
	
			if (connection.getResponseCode() != 200) {
				System.err.println("Retrieving map tile " + zoom + " " + x + " " + y + " response " + connection.getResponseCode());
			}
			return img;
		}
		catch (IOException e) {
			System.err.println("Error when retrieving tile from api");
			return new BufferedImage(TILE_RESOLUTION, TILE_RESOLUTION, BufferedImage.TYPE_INT_ARGB);
		}
	}
	
	//returns in radians
	private static Pair<Float, Float> zxyToLL(int zoom, int x, int y) {
		float n = (float) Math.pow(2, zoom);
		float lon_deg = (float) (x / n * 360.0 - 180.0);
		float lon_rad = (float) Math.toRadians(lon_deg);
		float lat_rad = (float) Math.atan(Math.sinh(Math.PI * (1 - 2 * y / n)));
		float lat_deg = (float) Math.toDegrees(lat_rad);
		return new Pair<>(lat_rad, lon_rad);
	}
	
	private static Vec2 zllRadToXY(int zoom, float lat_rad, float lon_rad) {
		float n = (float) Math.pow(2, zoom);
		float lon_deg = (float) Math.toDegrees(lon_rad);
		float xtile = n * ((lon_deg + 180) / 360);
		float ytile = (float) (n * (1.0 - (Math.log(Math.tan(lat_rad) + 1.0 / Math.cos(lat_rad)) / Math.PI)) / 2.0);
		return new Vec2(xtile, ytile);
	}
	
	private static Vec2 zllDegToXY(int zoom, float lat_deg, float lon_deg) {
		return zllRadToXY(zoom, (float) Math.toRadians(lat_deg), (float) Math.toRadians(lon_deg));
	}
	
	//queries the color and height of the tile in question, and just the height of the surrounding 8 tiles. 
	//renders it, and crops out the original tile, and returns it as a texture. 
	private static Texture generateShadedTile(int zoom, int x, int y) {
		System.out.println("Generating shaded tile texture : " + zoom + " " + x + " " + y);
	
		//dimensions of each pixel in meters at the current longitude
		//compute the longitudinal width of tile, and divide by tile resolution. 
		//this is an approximation, as it only measures the scale at the top of the center tile. 
		float lon0 = zxyToLL(zoom, x, y).second;
		float lon1 = zxyToLL(zoom, x + 1, y).second;
		float pixel_scale = 6371000 * (lon1 - lon0) / TILE_RESOLUTION; //6371000 meters is avg radius of earth.
	
		int nr_rays = 1;
		Vec3 sun_dir = new Vec3(1, 1, 0.5).normalize();
	
		//create textures by patching together a bunch of tiles
		System.out.println("Retrieving tiles from api");
		Texture color_tex = new Texture(TILE_RESOLUTION * 3, TILE_RESOLUTION * 3, GL_RGBA8, GL_RGBA, GL_UNSIGNED_BYTE, GL_NEAREST, GL_NEAREST, 1, null);
		Texture rgb_height_tex = new Texture(TILE_RESOLUTION * 3, TILE_RESOLUTION * 3, GL_RGBA8, GL_RGBA, GL_UNSIGNED_BYTE, GL_NEAREST, GL_NEAREST, 1, null);
		{
			Framebuffer fb = new Framebuffer(TILE_RESOLUTION * 3, TILE_RESOLUTION * 3);
			fb.bindTextureToBuffer(GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, color_tex.getID());
			fb.bindTextureToBuffer(GL_COLOR_ATTACHMENT1, GL_TEXTURE_2D, rgb_height_tex.getID());
			fb.setDrawBuffers(new int[] { GL_COLOR_ATTACHMENT0, GL_COLOR_ATTACHMENT1 });
			fb.isComplete();
	
			Shader combine_tile_shader = ShaderUtils.createShader("/terrain_shadow_casting/combine_tile.vert", "/terrain_shadow_casting/combine_tile.frag");
			combine_tile_shader.setUniform1i("tex_color", 0);
			combine_tile_shader.setUniform1i("tex_rgb_height", 1);
	
			glDisable(GL_DEPTH_TEST);
			glDisable(GL_BLEND);
			glDisable(GL_CULL_FACE);
	
			for (int i = 0; i < 3; i++) {
				for (int j = 0; j < 3; j++) {
					Texture color_tile = new Texture(0, 0, 0, 0);
					if (i == 1 && j == 1) {
						color_tile.kill();
						color_tile = new Texture(tileQuery("satellite-v2", zoom, x - 1 + i, y + 1 - j, "jpg"));
					}
					Texture rgb_height_tile = new Texture(tileQuery("terrain-rgb-v2", zoom, x - 1 + i, y + 1 - j, "webp"));
	
					//render tile to appropriate position on texture
					glViewport(TILE_RESOLUTION * i, TILE_RESOLUTION * j, TILE_RESOLUTION, TILE_RESOLUTION);
					color_tile.bind(GL_TEXTURE0);
					rgb_height_tile.bind(GL_TEXTURE1);
	
					fb.bind();
					combine_tile_shader.enable();
					ScreenQuad.screenQuad.render();
				}
			}
	
			combine_tile_shader.kill();
			fb.unbindTextureAtBuffer(GL_COLOR_ATTACHMENT0);
			fb.unbindTextureAtBuffer(GL_COLOR_ATTACHMENT1);
			fb.kill();
		}
	
		//derive height map from rgb height
		System.out.println("Generating height map");
		Texture height_tex = new Texture(TILE_RESOLUTION * 3, TILE_RESOLUTION * 3, GL_RGBA32F, GL_RGBA, GL_FLOAT, GL_NEAREST, GL_NEAREST, 1, null);
		{
			glViewport(0, 0, TILE_RESOLUTION * 3, TILE_RESOLUTION * 3);
			glDisable(GL_DEPTH_TEST);
			glDisable(GL_BLEND);
			glDisable(GL_CULL_FACE);
	
			Framebuffer fb = new Framebuffer(TILE_RESOLUTION * 3, TILE_RESOLUTION * 3);
			fb.bindTextureToBuffer(GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, height_tex.getID());
	
			Shader height_shader = ShaderUtils.createShader("/terrain_shadow_casting/gen_height.vert", "/terrain_shadow_casting/gen_height.frag");
	
			height_shader.setUniform1i("tex_rgb_height", 0);
			rgb_height_tex.bind(GL_TEXTURE0);
	
			fb.bind();
			height_shader.enable();
			ScreenQuad.screenQuad.render();
	
			height_shader.kill();
			fb.unbindTextureAtBuffer(GL_COLOR_ATTACHMENT0);
			fb.kill();
		}
	
		//derive normal map from height map. 
		System.out.println("Generating normal map");
		Texture normal_tex = new Texture(TILE_RESOLUTION * 3, TILE_RESOLUTION * 3, GL_RGBA8, GL_RGBA, GL_UNSIGNED_BYTE, GL_NEAREST, GL_NEAREST, 1, null);
		{
			glViewport(TILE_RESOLUTION, TILE_RESOLUTION, TILE_RESOLUTION, TILE_RESOLUTION);
			glDisable(GL_DEPTH_TEST);
			glDisable(GL_BLEND);
			glDisable(GL_CULL_FACE);
	
			Framebuffer fb = new Framebuffer(TILE_RESOLUTION * 3, TILE_RESOLUTION * 3);
			fb.bindTextureToBuffer(GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, normal_tex.getID());
	
			Shader normal_shader = ShaderUtils.createShader("/terrain_shadow_casting/gen_normal.vert", "/terrain_shadow_casting/gen_normal.frag");
	
			normal_shader.setUniform1i("tex_height", 0);
			height_tex.bind(GL_TEXTURE0);
			normal_shader.setUniform1i("tile_resolution", TILE_RESOLUTION * 3);
			normal_shader.setUniform1f("pixel_scale", pixel_scale);
	
			fb.bind();
			normal_shader.enable();
			ScreenQuad.screenQuad.render();
	
			normal_shader.kill();
			fb.unbindTextureAtBuffer(GL_COLOR_ATTACHMENT0);
			fb.kill();
		}
	
		//generate shadow and ambient occlusion texture
		System.out.println("Generating shadows and ambient occlusion");
		Texture shadow_tex = new Texture(TILE_RESOLUTION * 3, TILE_RESOLUTION * 3, GL_RGBA32F, GL_RGBA, GL_FLOAT, GL_NEAREST, GL_NEAREST, 1, null);
		{
			glViewport(TILE_RESOLUTION, TILE_RESOLUTION, TILE_RESOLUTION, TILE_RESOLUTION);
			glDisable(GL_DEPTH_TEST);
			glDisable(GL_CULL_FACE);
			glEnable(GL_BLEND);
			glBlendFunc(GL_SRC_ALPHA, GL_ONE);
	
			Framebuffer fb = new Framebuffer(TILE_RESOLUTION * 3, TILE_RESOLUTION * 3);
			fb.bindTextureToBuffer(GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, shadow_tex.getID());
	
			Shader shadow_shader = ShaderUtils.createShader("/terrain_shadow_casting/gen_shadow.vert", "/terrain_shadow_casting/gen_shadow.frag");
	
			shadow_shader.setUniform1i("tex_height", 0);
			shadow_shader.setUniform1i("tex_normal", 1);
			height_tex.bind(GL_TEXTURE0);
			normal_tex.bind(GL_TEXTURE1);
			shadow_shader.setUniform1i("tile_resolution", TILE_RESOLUTION * 3);
			shadow_shader.setUniform1f("pixel_scale", pixel_scale);
			shadow_shader.setUniform1i("nr_rays", nr_rays);
			shadow_shader.setUniform3f("sun_dir", sun_dir);
	
			for (int i = 0; i < nr_rays; i++) {
				System.out.println("Casting ray " + i);
				fb.sampleColorAtPoint(0, 0, GL_COLOR_ATTACHMENT0);
	
				shadow_shader.setUniform1i("ray_no", i);
				fb.bind();
				shadow_shader.enable();
				ScreenQuad.screenQuad.render();
			}
	
			shadow_shader.kill();
			fb.unbindTextureAtBuffer(GL_COLOR_ATTACHMENT0);
			fb.kill();
		}
	
		System.out.println("Extracting tile texture");
		Texture tile_tex = new Texture(TILE_RESOLUTION, TILE_RESOLUTION);
		{
			glViewport(0, 0, TILE_RESOLUTION, TILE_RESOLUTION);
			glDisable(GL_DEPTH_TEST);
			glDisable(GL_BLEND);
			glDisable(GL_CULL_FACE);
	
			Framebuffer fb = new Framebuffer(TILE_RESOLUTION, TILE_RESOLUTION);
			fb.bindTextureToBuffer(GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, tile_tex.getID());
	
			Shader extract_tile_shader = ShaderUtils.createShader("/terrain_shadow_casting/extract_tile.vert", "/terrain_shadow_casting/extract_tile.frag");
	
			extract_tile_shader.setUniform1i("tex_color", 0);
			extract_tile_shader.setUniform1i("tex_normal", 1);
			extract_tile_shader.setUniform1i("tex_shadow", 2);
			color_tex.bind(GL_TEXTURE0);
			normal_tex.bind(GL_TEXTURE1);
			shadow_tex.bind(GL_TEXTURE2);
			extract_tile_shader.setUniform1i("tile_resolution", TILE_RESOLUTION);
			extract_tile_shader.setUniform3f("sun_dir", sun_dir);
	
			fb.bind();
			ScreenQuad.screenQuad.render();
	
			extract_tile_shader.kill();
			fb.unbindTextureAtBuffer(GL_COLOR_ATTACHMENT0);
			fb.kill();
		}
	
		color_tex.kill();
		rgb_height_tex.kill();
		height_tex.kill();
		normal_tex.kill();
		shadow_tex.kill();
	
		tile_tex.generateMipmaps();
		return tile_tex;
	}
	
	private static Texture generateCenteredImage(int zoom, float lat, float lon, int width, int height) {
		Vec2 xy = zllDegToXY(zoom, lat, lon);
		int minXOff = (width / 2) - (int) ((xy.x - MathUtils.floor(xy.x)) * TILE_RESOLUTION);
		int minYOff = (height / 2) - (int) ((MathUtils.ceil(xy.y) - xy.y) * TILE_RESOLUTION);
		int minX = MathUtils.floor(xy.x);
		int maxY = MathUtils.floor(xy.y); //max because y is inverted from opengl to tile coords. 
		while (minXOff > 0) {
			minXOff -= TILE_RESOLUTION;
			minX--;
		}
		while (minYOff > 0) {
			minYOff -= TILE_RESOLUTION;
			maxY++;
		}
	
		Texture res_tex = new Texture(width, height);
	
		Framebuffer fb = new Framebuffer(width, height);
		fb.bindTextureToBuffer(GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, res_tex.getID());
	
		Shader render_tile_shader = ShaderUtils.createShader("/terrain_shadow_casting/render_tile.vert", "/terrain_shadow_casting/render_tile.frag");
		render_tile_shader.setUniform1i("tex_color", 0);
	
		glDisable(GL_DEPTH_TEST);
		glDisable(GL_BLEND);
		glDisable(GL_CULL_FACE);
	
		int xtile = minX;
		for (int x = minXOff; x < width; x += TILE_RESOLUTION) {
			int ytile = maxY;
			for (int y = minYOff; y < height; y += TILE_RESOLUTION) {
				Texture tile = generateShadedTile(zoom, xtile, ytile);
	
				glViewport(x, y, TILE_RESOLUTION, TILE_RESOLUTION);
				tile.bind(GL_TEXTURE0);
	
				fb.bind();
				render_tile_shader.enable();
				ScreenQuad.screenQuad.render();
	
				tile.kill();
				ytile--;
			}
			xtile++;
		}
	
		render_tile_shader.kill();
		fb.unbindTextureAtBuffer(GL_COLOR_ATTACHMENT0);
		fb.kill();
	
		res_tex.generateMipmaps();
		return res_tex;
	}
	*/
}
