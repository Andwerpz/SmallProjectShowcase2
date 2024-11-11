package csce_vis.hw5;

import java.util.ArrayList;

import csce_vis.hw5.collision.Manifold;
import myutils.math.MathUtils;
import myutils.math.Quaternion;
import myutils.math.Vec3;

public class ImpulseScene {

	private ArrayList<Body> bodies;
	private boolean collisionOccurred = false;

	private Vec3 gravity = new Vec3(0, -10, 0);

	public ImpulseScene() {
		this.bodies = new ArrayList<>();
	}

	public ArrayList<Body> getBodies() {
		return this.bodies;
	}

	public void addBody(Body b) {
		this.bodies.add(b);
	}

	public void clearScene() {
		this.bodies.clear();
	}

	//TODO implement better broad phase
	private void handleCollisions() {
		this.collisionOccurred = false;
		for (int i = 0; i < bodies.size(); i++) {
			for (int j = i + 1; j < bodies.size(); j++) {
				Body a = this.bodies.get(i);
				Body b = this.bodies.get(j);
				Manifold m = new Manifold(a, b);
				m.apply();
				if (m.didCollide) {
					this.collisionOccurred = true;
				}
			}
		}
	}

	public void update(float dt) {
		Vec3[] accel = new Vec3[this.bodies.size()];
		for (int i = 0; i < this.bodies.size(); i++) {
			accel[i] = new Vec3(0);
		}

		//gravity
		for (int i = 0; i < this.bodies.size(); i++) {
			accel[i].addi(this.gravity);
		}

		//euler step
		for (int i = 0; i < this.bodies.size(); i++) {
			Body b = this.bodies.get(i);
			if (b.is_static) {
				continue;
			}

			Vec3 npos = b.pos.add(b.vel.mul(dt));
			Vec3 nvel = b.vel.add(accel[i].mul(dt));

			Vec3 axis = new Vec3(b.angvel);
			float omega = axis.length();
			axis = axis.normalize();
			Quaternion quat_rot = MathUtils.quaternionRotationAxisAngle(omega * dt, axis.x, axis.y, axis.z);
			Quaternion norient = quat_rot.mul(b.orient);
			norient.normalize();

			b.pos.set(npos);
			b.vel.set(nvel);
			b.orient.set(norient);
		}

		this.handleCollisions();
	}

	public void setGravity(Vec3 g) {
		this.gravity.set(g);
	}

	public Vec3 getGravity() {
		return this.gravity;
	}

	public boolean getCollisionOccurred() {
		return this.collisionOccurred;
	}

}
