package window;

import lwjglengine.graphics.Framebuffer;
import lwjglengine.window.ListViewerWindow;
import lwjglengine.window.Window;

public class ProjectPickerWindow extends ListViewerWindow {

	public ProjectPickerWindow(int xOffset, int yOffset, int width, int height, ListViewerCallback callback, Window parentWindow) {
		super(xOffset, yOffset, width, height, callback, parentWindow);
		this.init();
	}

	private void init() {
		this.setSubmitOnClickingSelectedListEntry(true);
		this.setCloseOnSubmit(false);
		this.setSortEntries(true);
		this.setDisplaySelectedEntryOnTopBar(true);
		this.setSingleEntrySelection(true);
		this.setRenderBottomBar(false);
	}

}
