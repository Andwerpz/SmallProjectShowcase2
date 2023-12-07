package voxel_raytracing;

import lwjglengine.graphics.Framebuffer;
import lwjglengine.player.PlayerInputController;
import lwjglengine.window.Window;
import myutils.file.SystemUtils;
import myutils.math.Vec2;
import myutils.math.Vec3;

public class VoxelRaytracingWindow extends Window {

	//TODO
	// - implement Sparse Voxel Octree
	//   - maybe useful : https://eisenwave.github.io/voxel-compression-docs/svo/svo.html
	//   - paper covering SVO : https://www.nvidia.com/docs/IO/88972/nvr-2010-001.pdf
	//   - potentially look into DAG optimization
	//   - paper covering DAG : https://www.cse.chalmers.se/~uffe/HighResolutionSparseVoxelDAGs.pdf
	// - implement raytracing fragment shader to render out the SVO
	// - implement chunk system. 
	// - for now, use textures to give fragment shader access to SVO, later look into SSBOs
	//   - discussion regarding data streaming : https://www.reddit.com/r/VoxelGameDev/comments/hy5sx4/uploading_voxel_octree_to_opengl/

	private VoxelRaytracingScreen voxelScreen;

	private PlayerInputController pic;

	public VoxelRaytracingWindow(int xOffset, int yOffset, int width, int height, Window parentWindow) {
		super(xOffset, yOffset, width, height, parentWindow);
		this.init();
	}

	private void init() {
		this.setLockCursorOnSelect(true);
		this.setUnlockCursorOnEscPressed(true);
		this.setDeselectOnEscPressed(true);

		this.pic = new PlayerInputController(new Vec3(0));

		this.voxelScreen = new VoxelRaytracingScreen();

		this._resize();
	}

	@Override
	protected void _kill() {
		this.voxelScreen.kill();
	}

	@Override
	protected void _resize() {
		this.voxelScreen.setScreenDimensions(this.getWidth(), this.getHeight());
	}

	@Override
	public String getDefaultTitle() {
		return "Voxel Raytracing";
	}

	@Override
	protected void _update() {
		if (this.isCursorLocked()) {
			this.pic.update();
			this.voxelScreen.setCameraPos(this.pic.getPos());
			this.voxelScreen.setCameraFacing(this.pic.getFacing());
		}
	}

	@Override
	protected void renderContent(Framebuffer outputBuffer) {
		this.voxelScreen.render(outputBuffer);
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

	}

	@Override
	protected void _keyReleased(int key) {
		// TODO Auto-generated method stub

	}

}
