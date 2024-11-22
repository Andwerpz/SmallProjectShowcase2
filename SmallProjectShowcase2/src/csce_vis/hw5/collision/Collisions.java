package csce_vis.hw5.collision;

public class Collisions {
	// @formatter:off
	public static CollisionCallback[][] dispatch = { 
			{ CollisionKDOP_KDOP.instance },
			{ CollisionCapsule_KDOP.instance, CollisionCapsule_Capsule.instance },
	};
	// @formatter:on
}
