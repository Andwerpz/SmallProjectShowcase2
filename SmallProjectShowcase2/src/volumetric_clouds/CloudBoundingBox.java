package volumetric_clouds;

import lwjglengine.model.Line;
import lwjglengine.model.ModelInstance;
import myutils.math.Vec3;

public class CloudBoundingBox {

	public int lineScene;

	public Vec3[] baseCorners = new Vec3[] { new Vec3(-0.5f, -0.5f, -0.5f), new Vec3(-0.5f, -0.5f, 0.5f), new Vec3(0.5f, -0.5f, 0.5f), new Vec3(0.5f, -0.5f, -0.5f), new Vec3(-0.5f, 0.5f, -0.5f), new Vec3(-0.5f, 0.5f, 0.5f), new Vec3(0.5f, 0.5f, 0.5f), new Vec3(0.5f, 0.5f, -0.5f), };

	public boolean drawBoundingLines = false;
	public ModelInstance[] boundingLines = null;

	public Vec3 pos; //refers to the center of the box. 
	public Vec3 scale; //individual scaling of x y z

	public float density_threshold = 0.7f;
	public float density_multiplier = 10;
	public float density_offset = -6;

	public float detail_multiplier = 0.2f;
	public float subtract_multiplier = 1f;

	public float scale_main_1 = 50;
	public float scale_main_2 = 110;
	public float scale_detail = 11;
	public float scale_subtract = 17;

	public float light_absorption_towards_sun = 1.45f;
	public float light_absorption_through_clouds = 0.7f;

	public float darkness_threshold = 0.15f;

	public float forward_scattering = 0.72f;
	public float backward_scattering = 0.33f;
	public float base_brightness = 1f;
	public float phase_factor = 0.74f;

	public CloudBoundingBox(int scene) {
		this.init();

		this.lineScene = scene;
	}

	public CloudBoundingBox(int scene, Vec3 pos, Vec3 scale) {
		this.init();
		this.setPos(pos);
		this.setScale(scale);

		this.lineScene = scene;
	}

	private void init() {
		this.pos = new Vec3(0);
		this.scale = new Vec3(1);
	}

	public void setPos(Vec3 pos) {
		this.pos.set(pos);
		this.updateBoundingLineInstances();
	}

	public void setScale(Vec3 scale) {
		this.scale.set(scale);
		this.updateBoundingLineInstances();
	}

	public void setDrawBoundingLines(boolean b) {
		this.drawBoundingLines = b;

		if (this.drawBoundingLines) {
			this.updateBoundingLineInstances();
		}
		else {
			//kill bounding lines
			if (this.boundingLines != null) {
				for (int i = 0; i < this.boundingLines.length; i++) {
					this.boundingLines[i].kill();
				}
			}
			this.boundingLines = null;
		}
	}

	private void updateBoundingLineInstances() {
		if (!this.drawBoundingLines) {
			return;
		}

		if (this.boundingLines == null) {
			this.boundingLines = new ModelInstance[12];
			for (int i = 0; i < this.boundingLines.length; i++) {
				this.boundingLines[i] = Line.addDefaultLine(new Vec3(0), new Vec3(0), this.lineScene);
			}
		}

		//enumerate all the corners. 
		Vec3[] corners = new Vec3[8];
		for (int i = 0; i < 8; i++) {
			corners[i] = this.baseCorners[i].mul(this.scale.x, this.scale.y, this.scale.z).add(this.pos);
		}

		//bottom ring
		this.boundingLines[0].setModelTransform(Line.generateLineModelTransform(corners[0], corners[1]));
		this.boundingLines[1].setModelTransform(Line.generateLineModelTransform(corners[1], corners[2]));
		this.boundingLines[2].setModelTransform(Line.generateLineModelTransform(corners[2], corners[3]));
		this.boundingLines[3].setModelTransform(Line.generateLineModelTransform(corners[3], corners[0]));

		//top ring
		this.boundingLines[4].setModelTransform(Line.generateLineModelTransform(corners[4], corners[5]));
		this.boundingLines[5].setModelTransform(Line.generateLineModelTransform(corners[5], corners[6]));
		this.boundingLines[6].setModelTransform(Line.generateLineModelTransform(corners[6], corners[7]));
		this.boundingLines[7].setModelTransform(Line.generateLineModelTransform(corners[7], corners[4]));

		//connecting edges
		this.boundingLines[8].setModelTransform(Line.generateLineModelTransform(corners[0], corners[4]));
		this.boundingLines[9].setModelTransform(Line.generateLineModelTransform(corners[1], corners[5]));
		this.boundingLines[10].setModelTransform(Line.generateLineModelTransform(corners[2], corners[6]));
		this.boundingLines[11].setModelTransform(Line.generateLineModelTransform(corners[3], corners[7]));
	}

	public float getDetail_multiplier() {
		return detail_multiplier;
	}

	public void setDetail_multiplier(float detail_multiplier) {
		this.detail_multiplier = detail_multiplier;
	}

	public float getSubtract_multiplier() {
		return subtract_multiplier;
	}

	public void setSubtract_multiplier(float subtract_multiplier) {
		this.subtract_multiplier = subtract_multiplier;
	}

	public float getDensity_threshold() {
		return density_threshold;
	}

	public void setDensity_threshold(float density_threshold) {
		this.density_threshold = density_threshold;
	}

	public float getDensity_multiplier() {
		return density_multiplier;
	}

	public void setDensity_multiplier(float density_multiplier) {
		this.density_multiplier = density_multiplier;
	}

	public float getDensity_offset() {
		return density_offset;
	}

	public void setDensity_offset(float density_offset) {
		this.density_offset = density_offset;
	}

	public float getScale_main_1() {
		return scale_main_1;
	}

	public void setScale_main_1(float scale_main_1) {
		this.scale_main_1 = scale_main_1;
	}

	public float getScale_main_2() {
		return scale_main_2;
	}

	public void setScale_main_2(float scale_main_2) {
		this.scale_main_2 = scale_main_2;
	}

	public float getScale_detail() {
		return scale_detail;
	}

	public void setScale_detail(float scale_detail) {
		this.scale_detail = scale_detail;
	}

	public float getScale_subtract() {
		return scale_subtract;
	}

	public void setScale_subtract(float scale_subtract) {
		this.scale_subtract = scale_subtract;
	}

	public float getLight_absorption_towards_sun() {
		return light_absorption_towards_sun;
	}

	public void setLight_absorption_towards_sun(float light_absorption_towards_sun) {
		this.light_absorption_towards_sun = light_absorption_towards_sun;
	}

	public float getLight_absorption_through_clouds() {
		return light_absorption_through_clouds;
	}

	public void setLight_absorption_through_clouds(float light_absorption_through_clouds) {
		this.light_absorption_through_clouds = light_absorption_through_clouds;
	}

	public float getDarkness_threshold() {
		return darkness_threshold;
	}

	public void setDarkness_threshold(float darkness_threshold) {
		this.darkness_threshold = darkness_threshold;
	}

	public float getForward_scattering() {
		return forward_scattering;
	}

	public void setForward_scattering(float forward_scattering) {
		this.forward_scattering = forward_scattering;
	}

	public float getBackward_scattering() {
		return backward_scattering;
	}

	public void setBackward_scattering(float backward_scattering) {
		this.backward_scattering = backward_scattering;
	}

	public float getBase_brightness() {
		return base_brightness;
	}

	public void setBase_brightness(float base_brightness) {
		this.base_brightness = base_brightness;
	}

	public float getPhase_factor() {
		return phase_factor;
	}

	public void setPhase_factor(float phase_factor) {
		this.phase_factor = phase_factor;
	}

	public boolean getDrawBoundingLines() {
		return drawBoundingLines;
	}

	public Vec3 getPos() {
		return pos;
	}

	public Vec3 getScale() {
		return scale;
	}

}
