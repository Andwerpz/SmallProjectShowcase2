package terrain_shadow_casting;

import static org.lwjgl.glfw.GLFW.GLFW_CONTEXT_VERSION_MAJOR;
import static org.lwjgl.glfw.GLFW.GLFW_CONTEXT_VERSION_MINOR;
import static org.lwjgl.glfw.GLFW.GLFW_RESIZABLE;
import static org.lwjgl.glfw.GLFW.GLFW_VISIBLE;
import static org.lwjgl.glfw.GLFW.glfwCreateWindow;
import static org.lwjgl.glfw.GLFW.glfwGetPrimaryMonitor;
import static org.lwjgl.glfw.GLFW.glfwGetVideoMode;
import static org.lwjgl.glfw.GLFW.glfwInit;
import static org.lwjgl.glfw.GLFW.glfwMakeContextCurrent;
import static org.lwjgl.glfw.GLFW.glfwSetKeyCallback;
import static org.lwjgl.glfw.GLFW.glfwSetMouseButtonCallback;
import static org.lwjgl.glfw.GLFW.glfwSetScrollCallback;
import static org.lwjgl.glfw.GLFW.glfwSetWindowPos;
import static org.lwjgl.glfw.GLFW.glfwSetWindowSizeCallback;
import static org.lwjgl.glfw.GLFW.glfwShowWindow;
import static org.lwjgl.glfw.GLFW.glfwWindowHint;
import static org.lwjgl.opengl.GL.createCapabilities;
import static org.lwjgl.opengl.GL11.GL_BACK;
import static org.lwjgl.opengl.GL11.GL_BLEND;
import static org.lwjgl.opengl.GL11.GL_CULL_FACE;
import static org.lwjgl.opengl.GL11.GL_DEPTH_TEST;
import static org.lwjgl.opengl.GL11.GL_FALSE;
import static org.lwjgl.opengl.GL11.GL_FLOAT;
import static org.lwjgl.opengl.GL11.GL_NEAREST;
import static org.lwjgl.opengl.GL11.GL_ONE;
import static org.lwjgl.opengl.GL11.GL_RGBA;
import static org.lwjgl.opengl.GL11.GL_RGBA8;
import static org.lwjgl.opengl.GL11.GL_SRC_ALPHA;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_2D;
import static org.lwjgl.opengl.GL11.GL_TRUE;
import static org.lwjgl.opengl.GL11.GL_UNSIGNED_BYTE;
import static org.lwjgl.opengl.GL11.GL_VERSION;
import static org.lwjgl.opengl.GL11.glBlendFunc;
import static org.lwjgl.opengl.GL11.glClearColor;
import static org.lwjgl.opengl.GL11.glCullFace;
import static org.lwjgl.opengl.GL11.glDisable;
import static org.lwjgl.opengl.GL11.glEnable;
import static org.lwjgl.opengl.GL11.glGetString;
import static org.lwjgl.opengl.GL11.glViewport;
import static org.lwjgl.opengl.GL13.GL_TEXTURE0;
import static org.lwjgl.opengl.GL13.GL_TEXTURE1;
import static org.lwjgl.opengl.GL13.GL_TEXTURE2;
import static org.lwjgl.opengl.GL30.GL_COLOR_ATTACHMENT0;
import static org.lwjgl.opengl.GL30.GL_COLOR_ATTACHMENT1;
import static org.lwjgl.opengl.GL30.GL_RGBA32F;
import static org.lwjgl.opengl.GL32.GL_TEXTURE_CUBE_MAP_SEAMLESS;
import static org.lwjgl.system.MemoryUtil.NULL;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;

import javax.imageio.ImageIO;

import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWVidMode;

import lwjglengine.graphics.Framebuffer;
import lwjglengine.graphics.Shader;
import lwjglengine.graphics.Texture;
import lwjglengine.input.KeyboardInput;
import lwjglengine.input.MouseInput;
import lwjglengine.input.ScrollInput;
import lwjglengine.main.Main;
import lwjglengine.scene.Scene;
import lwjglengine.screen.ScreenQuad;
import lwjglengine.ui.Text;
import lwjglengine.ui.UIElement;
import lwjglengine.ui.UISection;
import lwjglengine.util.ShaderUtils;
import lwjglengine.window.FileCreatorWindow;
import lwjglengine.window.Window;
import myutils.math.MathUtils;
import myutils.math.Vec2;
import myutils.math.Vec3;
import myutils.misc.Pair;

public class TerrainShadowCastingRenderWindow extends Window {

	private Texture generatedTexture = null;
	private final int RENDER_SCENE = Scene.generateScene();

	private UISection uiSection;

	private int zoom, width, height;
	private float lat, lon;

	public TerrainShadowCastingRenderWindow(int zoom, float lat, float lon, int width, int height) {
		super(null);
		this.init(zoom, lat, lon, width, height);
	}

	private void init(int _zoom, float _lat, float _lon, int _width, int _height) {
		this.zoom = _zoom;
		this.lat = _lat;
		this.lon = _lon;
		this.width = _width;
		this.height = _height;

		this.uiSection = new UISection();
		this.uiSection.getBackgroundRect().setFillWidth(true);
		this.uiSection.getBackgroundRect().setFillHeight(true);
		this.uiSection.getBackgroundRect().bind(this.rootUIElement);

		Text rendering_text = new Text("Currently Rendering", this.uiSection.getTextScene());
		rendering_text.setFrameAlignmentStyle(UIElement.FROM_CENTER_LEFT, UIElement.FROM_CENTER_TOP);
		rendering_text.setContentAlignmentStyle(UIElement.ALIGN_CENTER, UIElement.ALIGN_CENTER);
		rendering_text.bind(this.uiSection.getBackgroundRect());

		RenderThread r = new RenderThread(zoom, lat, lon, width, height, this);

		this.setDimensions(this.width, this.height);
		this._resize();
	}

	@Override
	protected void _kill() {
		this.uiSection.kill();
		Scene.removeScene(RENDER_SCENE);

		if (this.generatedTexture != null) {
			this.generatedTexture.kill();
		}
	}

	@Override
	protected void _resize() {

	}

	@Override
	public String getDefaultTitle() {
		String ans = "(Lat, Lng) : (" + lat + ", " + lon + ")";
		return ans;
	}

	private void setGeneratedTexture(Texture t) {
		this.generatedTexture = t;
		//		this.setDimensions(this.width, this.height);

		this.setContextMenuRightClick(true);
		this.setContextMenuActions(new String[] { "Save Image As ..." });

		/*
		 * switch (key) {
		case GLFW.GLFW_KEY_S: {
			FileCreatorWindow fc = new FileCreatorWindow(this.renderTexture.toBufferedImage());
			this.addChildAdjWindow(fc);
			break;
		}
		}
		 */
	}

	@Override
	protected void _update() {
		// TODO Auto-generated method stub

	}

	@Override
	protected void renderContent(Framebuffer outputBuffer) {
		this.uiSection.render(outputBuffer, this.getWindowMousePos());

		if (this.generatedTexture != null) {
			glViewport(0, 0, this.getWidth(), this.getHeight());
			this.generatedTexture.bind(GL_TEXTURE0);
			outputBuffer.bind();
			Shader.SPLASH.enable();
			Shader.SPLASH.setUniform1f("alpha", 1);
			ScreenQuad.screenQuad.render();
		}
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

	class RenderThread implements Runnable {

		TerrainShadowCastingRenderWindow callback_window;
		private int zoom, width, height;
		private float lat, lon;

		private ScreenQuad sq;

		public RenderThread(int zoom, float lat, float lon, int width, int height, TerrainShadowCastingRenderWindow window) {
			this.zoom = zoom;
			this.lat = lat;
			this.lon = lon;
			this.width = width;
			this.height = height;
			this.callback_window = window;

			Thread t = new Thread(this, "render");
			t.start();
		}

		@Override
		public void run() {
			if (!glfwInit()) {
				// window failed to init
				System.err.println("GLFW failed init");
				return;
			}

			glfwWindowHint(GLFW_RESIZABLE, Main.allowWindowResizing ? GL_TRUE : GL_FALSE);
			glfwWindowHint(GLFW_VISIBLE, GL_FALSE);
			long primaryMonitor = glfwGetPrimaryMonitor();
			long window = glfwCreateWindow(width, height, "render", NULL, NULL);

			glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 4);
			glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 5);

			if (window == NULL) {
				System.err.println("Window failed to create");
				return;
			}

			glfwMakeContextCurrent(window);

			createCapabilities();
			glClearColor(0f, 0f, 0f, 0f);
			glEnable(GL_DEPTH_TEST);
			glEnable(GL_CULL_FACE);
			glEnable(GL_TEXTURE_CUBE_MAP_SEAMLESS);
			glCullFace(GL_BACK);
			System.out.println("OpenGL : " + glGetString(GL_VERSION));

			this.sq = new ScreenQuad();

			Texture result = generateCenteredImage(zoom, lat, lon, width, height);
			if (this.callback_window.isAlive()) {
				this.callback_window.setGeneratedTexture(result);
			}
			else {
				result.kill();
			}
		}

		//for free!!
		private static final String API_KEY = "wuHJ0xDcLkjvM20HPGA7";

		private static final int TILE_RESOLUTION = 512;

		//api request format:
		//https://api.maptiler.com/tiles/{map_name}/{zoom}/{x}/{y}.{format}
		//note that what formats are supported is dependent on the selected map

		private BufferedImage tileQuery(String map_name, int zoom, int x, int y, String format) {
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
		private Pair<Float, Float> zxyToLL(int zoom, int x, int y) {
			float n = (float) Math.pow(2, zoom);
			float lon_deg = (float) (x / n * 360.0 - 180.0);
			float lon_rad = (float) Math.toRadians(lon_deg);
			float lat_rad = (float) Math.atan(Math.sinh(Math.PI * (1 - 2 * y / n)));
			float lat_deg = (float) Math.toDegrees(lat_rad);
			return new Pair<>(lat_rad, lon_rad);
		}

		private Vec2 zllRadToXY(int zoom, float lat_rad, float lon_rad) {
			float n = (float) Math.pow(2, zoom);
			float lon_deg = (float) Math.toDegrees(lon_rad);
			float xtile = n * ((lon_deg + 180) / 360);
			float ytile = (float) (n * (1.0 - (Math.log(Math.tan(lat_rad) + 1.0 / Math.cos(lat_rad)) / Math.PI)) / 2.0);
			return new Vec2(xtile, ytile);
		}

		private Vec2 zllDegToXY(int zoom, float lat_deg, float lon_deg) {
			return zllRadToXY(zoom, (float) Math.toRadians(lat_deg), (float) Math.toRadians(lon_deg));
		}

		//queries the color and height of the tile in question, and just the height of the surrounding 8 tiles. 
		//renders it, and crops out the original tile, and returns it as a texture. 
		private Texture generateShadedTile(int zoom, int x, int y) {
			System.out.println("Generating shaded tile texture : " + zoom + " " + x + " " + y);

			//dimensions of each pixel in meters at the current longitude
			//compute the longitudinal width of tile, and divide by tile resolution. 
			//this is an approximation, as it only measures the scale at the top of the center tile. 
			float lon0 = zxyToLL(zoom, x, y).second;
			float lon1 = zxyToLL(zoom, x + 1, y).second;
			float pixel_scale = 6371000 * (lon1 - lon0) / TILE_RESOLUTION; //6371000 meters is avg radius of earth.

			int nr_rays = 256;
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
						this.sq.render();
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
				this.sq.render();

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
				this.sq.render();

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
					this.sq.render();
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
				this.sq.render();

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

		private Texture generateCenteredImage(int zoom, float lat, float lon, int width, int height) {
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
					this.sq.render();

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

	}

}
