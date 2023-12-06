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

		System.out.print("BUILDING VOXEL OCTREE : ");
		long startMillis = System.currentTimeMillis();
		int size = 64;
		float prob = 1f;
		int nrRemove = 0;
		float radius = 20;
		VoxelOctreeNode root = new VoxelOctreeNode(size);
		for (int i = 0; i < size; i++) {
			for (int j = 0; j < size; j++) {
				for (int k = 0; k < size; k++) {
					Vec3 v = new Vec3(i, j, k);
					if (Math.random() < prob && v.length() < radius) {
						int r = (int) (Math.random() * 256);
						int g = (int) (Math.random() * 256);
						int b = (int) (Math.random() * 256);
						root.addVoxel(i, j, k, r, g, b);
					}
				}
			}
		}
		for (int i = 0; i < nrRemove; i++) {
			int x = (int) (Math.random() * size);
			int y = (int) (Math.random() * size);
			int z = (int) (Math.random() * size);
			root.removeVoxel(x, y, z);
		}
		System.out.println(System.currentTimeMillis() - startMillis);
		System.out.print("SERIALIZING OCTREE : ");
		startMillis = System.currentTimeMillis();
		boolean[] bits = VoxelOctreeNode.serialize(root);
		System.out.println(System.currentTimeMillis() - startMillis);
		System.out.println("BITS LENGTH : " + bits.length);

		System.out.print("DESERIALIZING OCTREE : ");
		startMillis = System.currentTimeMillis();
		VoxelOctreeNode root_cpy = VoxelOctreeNode.deserialize(bits);
		System.out.println(System.currentTimeMillis() - startMillis);

		System.out.println("ARE EQUAL : " + (root.equals(root_cpy)));
		System.out.println("ESTIMATED REQUIRED BITS : " + VoxelOctreeNode.countRequiredBits(root));

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
