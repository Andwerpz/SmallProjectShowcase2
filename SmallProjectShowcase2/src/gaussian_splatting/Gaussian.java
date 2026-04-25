package gaussian_splatting;

import myutils.math.Quaternion;
import myutils.math.Vec3;

public class Gaussian {
	Vec3 center;
	Quaternion orient;
	Vec3 scale;
	float alpha;
	float[] r_coeff;
	float[] g_coeff;
	float[] b_coeff;
	
	public Gaussian() {
		center = new Vec3();
		orient = new Quaternion();
		scale = new Vec3(1, 1, 1);
		alpha = 0;
		r_coeff = new float[16];
		g_coeff = new float[16];
		b_coeff = new float[16];
	}
	
	@Override
	public String toString() {
		return center + " " + scale + " " + alpha;
	}
}
