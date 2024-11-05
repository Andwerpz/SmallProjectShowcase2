package csce_vis.hw5.collision;

import java.util.ArrayList;

import csce_vis.hw5.AABB;
import csce_vis.hw5.Body;
import myutils.math.MathUtils;
import myutils.math.Vec3;
import myutils.misc.Pair;

public class CollisionAABB_AABB implements CollisionCallback {

	public static final CollisionAABB_AABB instance = new CollisionAABB_AABB();

	private Body ba, bb;
	private AABB sa, sb;

	private Vec3[] axes_a, axes_b; //rotated primary axes
	private Vec3[] pts_a, pts_b; //rotated, translated vertices

	@Override
	public void handleCollision(Manifold m) {
		m.contacts = new ArrayList<>();

		this.ba = m.a;
		this.bb = m.b;

		this.sa = (AABB) ba.shape;
		this.sb = (AABB) bb.shape;

		//generate primary axes of both AABBs
		Vec3 bax = MathUtils.quaternionRotateVec3(ba.orient, new Vec3(1, 0, 0));
		Vec3 bay = MathUtils.quaternionRotateVec3(ba.orient, new Vec3(0, 1, 0));
		Vec3 baz = MathUtils.quaternionRotateVec3(ba.orient, new Vec3(0, 0, 1));
		Vec3 bbx = MathUtils.quaternionRotateVec3(bb.orient, new Vec3(1, 0, 0));
		Vec3 bby = MathUtils.quaternionRotateVec3(bb.orient, new Vec3(0, 1, 0));
		Vec3 bbz = MathUtils.quaternionRotateVec3(bb.orient, new Vec3(0, 0, 1));
		this.axes_a = new Vec3[] { bax, bay, baz };
		this.axes_b = new Vec3[] { bbx, bby, bbz };

		//generate vertices in world space of both AABBs
		this.pts_a = sa.getVertexList();
		this.pts_b = sb.getVertexList();
		for (int i = 0; i < 8; i++) {
			this.pts_a[i] = MathUtils.quaternionRotateVec3(ba.orient, this.pts_a[i]);
			this.pts_a[i].addi(ba.pos);

			this.pts_b[i] = MathUtils.quaternionRotateVec3(bb.orient, this.pts_b[i]);
			this.pts_b[i].addi(bb.pos);
		}

		//do SAT test
		Vec3 least_axis = new Vec3(0);
		float least_pen = findLeastPenetration(least_axis);
		if (least_pen < 0) {
			//no collision
			return;
		}

		//force separating axis to point from b to a
		if (MathUtils.dot(least_axis, new Vec3(this.bb.pos, this.ba.pos)) < 0) {
			least_axis.muli(-1);
		}

		m.penetration = least_pen;
		m.separating_axis = new Vec3(least_axis);

		System.out.println("COLLISION : " + m.separating_axis);

		//TODO : depending on the axis, would need to do different things. 
		//  for example, if the separating axis is along a face, then we'd want to try to make collisions with that face only. 
		//  perhaps force collision normals to be in the direction of the separating axis?

		//ok, collision. Generate all possible contacts. We'll prune bad ones out later
		float[] dim_a = new float[] { this.sa.half_dim.x, this.sa.half_dim.y, this.sa.half_dim.z };
		float[] dim_b = new float[] { this.sb.half_dim.x, this.sb.half_dim.y, this.sb.half_dim.z };

		//for each point, check if its colliding with the point in the other shape. 
		ArrayList<Contact> all_contacts = new ArrayList<>();
		for (int i = 0; i < 8; i++) { //compare points in a to b
			Vec3 pt = new Vec3(this.bb.pos, this.pts_a[i]);

			//see if the point is actually inside the other box
			boolean inside = true;
			for (int j = 0; j < 3; j++) {
				float dot = MathUtils.dot(pt, this.axes_b[j]);
				if (Math.abs(dot) > dim_b[j]) {
					inside = false;
					break;
				}
			}
			if (!inside) {
				continue;
			}

			//for each face, generate a contact. 
			for (int j = 0; j < 3; j++) {
				float dot = MathUtils.dot(pt, this.axes_b[j]);
				Vec3 normal = new Vec3(this.axes_b[j]);
				if (dot < 0) {
					normal.muli(-1);
				}
				Contact c = new Contact(this.pts_a[i], normal, dim_b[j] - Math.abs(dot));
				all_contacts.add(c);
			}
		}
		for (int i = 0; i < 8; i++) { //compare points in b to a
			Vec3 pt = new Vec3(this.ba.pos, this.pts_b[i]);

			//see if the point is actually inside the other box
			boolean inside = true;
			for (int j = 0; j < 3; j++) {
				float dot = MathUtils.dot(pt, this.axes_a[j]);
				if (Math.abs(dot) > dim_a[j]) {
					inside = false;
					break;
				}
			}
			if (!inside) {
				continue;
			}

			//for each face, generate a contact. 
			for (int j = 0; j < 3; j++) {
				float dot = MathUtils.dot(pt, this.axes_a[j]);
				Vec3 normal = new Vec3(this.axes_a[j]);
				if (dot < 0) {
					normal.muli(-1);
				}

				//force normal to be oriented from A's perspective
				normal.muli(-1);

				Contact c = new Contact(this.pts_b[i], normal, dim_b[j] - Math.abs(dot));
				all_contacts.add(c);
			}
		}

		//for each edge, find the edge on the other shape that is closest to it, 
		int[][] edges_a = this.sa.getEdgeList();
		int[][] edges_b = this.sb.getEdgeList();
		for (int eia = 0; eia < edges_a.length; eia++) {
			for (int eib = 0; eib < edges_b.length; eib++) {
				Vec3 a0 = new Vec3(this.pts_a[edges_a[eia][0]]);
				Vec3 a1 = new Vec3(this.pts_a[edges_a[eia][1]]);

				Vec3 b0 = new Vec3(this.pts_b[edges_b[eib][0]]);
				Vec3 b1 = new Vec3(this.pts_b[edges_b[eib][1]]);

				//check if they are parallel
				float parl_dot = Math.abs(MathUtils.dot(new Vec3(a0, a1).normalize(), new Vec3(b0, b1).normalize()));
				if (Math.abs(parl_dot - 1.0) < 0.001) {
					continue;
				}

				Pair<Vec3, Vec3> cpts = MathUtils.lineSegment_lineSegmentClosestPoints(a0, a1, b0, b1);

				//see if halfway between them is inside both a and b
				Vec3 coll_pt = MathUtils.lerp(cpts.first, 0, cpts.second, 1, 0.5f);
				System.out.println("COLL_PT : " + coll_pt);
				boolean inside = true;
				for (int j = 0; j < 3; j++) {
					if (Math.abs(MathUtils.dot(new Vec3(this.ba.pos, coll_pt), this.axes_a[j])) > dim_a[j] + 0.0001) {
						System.out.println("NOT INSIDE AXIS : " + new Vec3(this.ba.pos, coll_pt) + " " + this.axes_a[j] + " " + dim_a[j]);
						inside = false;
						break;
					}
					if (Math.abs(MathUtils.dot(new Vec3(this.bb.pos, coll_pt), this.axes_b[j])) > dim_b[j] + 0.0001) {
						System.out.println("NOT INSIDE AXIS : " + new Vec3(this.bb.pos, coll_pt) + " " + this.axes_b[j] + " " + dim_b[j]);
						inside = false;
						break;
					}
				}
				if (!inside) {
					System.out.println("NOT INSIDE");
					continue;
				}

				//ok, we can take this one as a collision point
				Vec3 coll_norm = MathUtils.cross(new Vec3(a0, a1), new Vec3(b0, b1));
				if (coll_norm.length() < 0.0001) {
					//edges are roughly parallel, no collision. 
					continue;
				}
				coll_norm.normalize();

				//ensure that collision normal is pointing from b -> a
				if (MathUtils.dot(coll_norm, new Vec3(bb.pos, ba.pos)) < 0) {
					coll_norm.muli(-1);
				}

				if (Float.isNaN(coll_pt.x)) {
					continue;
				}

				Contact c = new Contact(coll_pt, coll_norm, MathUtils.dist(cpts.first, cpts.second));
				all_contacts.add(c);
			}
		}

		//prune out any contacts that are not roughly facing in the correct direction
		ArrayList<Contact> pruned_contacts = new ArrayList<>();
		for (Contact c : all_contacts) {
			if (MathUtils.dot(c.norm, least_axis) < 0.75) {
				continue;
			}
			pruned_contacts.add(c);
		}

		//at this point, accept any contact. 
		//TODO 
		// - see if only accepting a few greatest penetration contacts is better. 
		// - see if setting all collision normals to axis of least separation is better
		for (Contact c : pruned_contacts) {
			m.contacts.add(c);
		}
	}

	private float findLeastPenetration(Vec3 out_axis) {
		Vec3[] sat_axes = new Vec3[15];
		sat_axes[0] = this.axes_a[0];
		sat_axes[1] = this.axes_a[1];
		sat_axes[2] = this.axes_a[2];
		sat_axes[3] = this.axes_b[0];
		sat_axes[4] = this.axes_b[1];
		sat_axes[5] = this.axes_b[2];
		sat_axes[6] = MathUtils.cross(this.axes_a[0], this.axes_b[0]);
		sat_axes[7] = MathUtils.cross(this.axes_a[0], this.axes_b[1]);
		sat_axes[8] = MathUtils.cross(this.axes_a[0], this.axes_b[2]);
		sat_axes[9] = MathUtils.cross(this.axes_a[1], this.axes_b[0]);
		sat_axes[10] = MathUtils.cross(this.axes_a[1], this.axes_b[1]);
		sat_axes[11] = MathUtils.cross(this.axes_a[1], this.axes_b[2]);
		sat_axes[12] = MathUtils.cross(this.axes_a[2], this.axes_b[0]);
		sat_axes[13] = MathUtils.cross(this.axes_a[2], this.axes_b[1]);
		sat_axes[14] = MathUtils.cross(this.axes_a[2], this.axes_b[2]);

		Vec3 origin = ba.pos.add(bb.pos).mul(0.5f);

		float ans = 1e18f;
		out_axis.set(0, 0, 0);
		for (int i = 0; i < sat_axes.length; i++) {
			Vec3 axis = sat_axes[i];
			if (axis.lengthSq() < 0.001) {
				continue;
			}
			axis.normalize();

			//project all points onto axis
			float mina = 1e18f;
			float maxa = -1e18f;
			float minb = 1e18f;
			float maxb = -1e18f;
			for (int j = 0; j < 8; j++) {
				float cur = MathUtils.dot(axis, this.pts_a[j].sub(origin));
				mina = Math.min(mina, cur);
				maxa = Math.max(maxa, cur);
			}
			for (int j = 0; j < 8; j++) {
				float cur = MathUtils.dot(axis, this.pts_b[j].sub(origin));
				minb = Math.min(minb, cur);
				maxb = Math.max(maxb, cur);
			}

			//enforce mina < minb
			if (mina > minb) {
				float tmp = mina;
				mina = minb;
				minb = tmp;
				tmp = maxa;
				maxa = maxb;
				maxb = tmp;
			}

			float pen = maxa - minb;
			if (pen < ans) {
				ans = pen;
				out_axis.set(axis);
			}
		}

		return ans;
	}

}
