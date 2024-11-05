package csce_vis.hw5.collision;

import csce_vis.hw5.Body;

public interface CollisionCallback {
	//should populate the given manifold with contact points and other relevant information
	public void handleCollision(Manifold m);
}
