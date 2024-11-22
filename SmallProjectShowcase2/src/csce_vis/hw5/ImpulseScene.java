package csce_vis.hw5;

import java.util.ArrayList;

import csce_vis.hw5.bvh.KDOP;
import csce_vis.hw5.bvh.BVH;
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

	private void handleCollisions() {
		this.collisionOccurred = false;

		ArrayList<KDOP> aabb_list = new ArrayList<>();
		for (int i = 0; i < this.bodies.size(); i++) {
			aabb_list.add(this.bodies.get(i).calcBoundingBox());
		}

		BVH bvh = new BVH(aabb_list);

		int coll_cnt = 0;
		for (int i = 0; i < this.bodies.size(); i++) {
			ArrayList<Integer> isect = bvh.getIntersections(aabb_list.get(i));
			for (int j = 0; j < isect.size(); j++) {
				int next = isect.get(j);
				if (next >= i) { //already should've considered this collision
					continue;
				}
				coll_cnt++;
				Manifold m = new Manifold(this.bodies.get(i), this.bodies.get(next));
				m.apply();
				if (m.didCollide) {
					this.collisionOccurred = true;
				}
			}
		}

		int naive_cnt = (this.bodies.size() * (this.bodies.size() - 1)) / 2;
		//		System.out.println("COLL CNT : " + coll_cnt + " NAIVE CNT : " + naive_cnt);
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
