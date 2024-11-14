package csce_vis.hw5.bvh;

import myutils.math.Vec3;

public class AABB {
	//AABB for bvh. 
	public Vec3 bl, tr;

	public AABB(Vec3 _bl, Vec3 _tr) {
		this.bl = new Vec3(_bl);
		this.tr = new Vec3(_tr);
	}

	public AABB(AABB _other) {
		this.bl = new Vec3(_other.bl);
		this.tr = new Vec3(_other.tr);
	}
}
