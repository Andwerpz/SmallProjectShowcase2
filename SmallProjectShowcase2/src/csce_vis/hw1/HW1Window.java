package csce_vis.hw1;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL13.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL21.*;
import static org.lwjgl.opengl.GL30.*;
import static org.lwjgl.opengl.GL31.*;
import static org.lwjgl.opengl.GL32.*;
import static org.lwjgl.opengl.GL33.*;
import static org.lwjgl.opengl.GL43.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;
import static org.lwjgl.opengl.GL31.*;
import static org.lwjgl.opengl.GL33.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.lwjgl.glfw.GLFW;

import lwjglengine.graphics.Framebuffer;
import lwjglengine.graphics.Shader;
import lwjglengine.graphics.ShaderStorageBuffer;
import lwjglengine.main.Main;
import lwjglengine.player.Camera;
import lwjglengine.player.PlayerInputController;
import lwjglengine.scene.Scene;
import lwjglengine.screen.SkyboxCube;
import lwjglengine.util.ShaderUtils;
import lwjglengine.window.ObjectEditorWindow;
import lwjglengine.window.Window;
import myutils.math.Mat4;
import myutils.math.MathUtils;
import myutils.math.Quaternion;
import myutils.math.Vec3;

public class HW1Window extends Window {

	//TODO
	// - air resistance
	// - wind?

	private PlayerInputController pc;

	private ArrayList<Sphere> spheres;

	private RenderScreen renderScreen;

	private SimulationSettings settings;
	private float timeDebt = 0;

	public HW1Window(int xOffset, int yOffset, int width, int height, Window parentWindow) {
		super(xOffset, yOffset, width, height, parentWindow);
		this.init();
	}

	private void init() {
		this.setLockCursorOnSelect(true);
		this.setDeselectOnEscPressed(true);
		this.setUnlockCursorOnEscPressed(true);

		this.pc = new PlayerInputController(new Vec3(0, 0, 75));
		this.pc.setAcceptPlayerInputs(false);

		this.spheres = new ArrayList<>();

		this.renderScreen = new RenderScreen();

		this.settings = new SimulationSettings();
		this.addChildAdjWindow(new ObjectEditorWindow(this.settings));

		this._resize();
	}

	//cube of side length 2 * size centered around the origin. 
	private void setBoxSize(float size) {
		//cube points
		Vec3 pt = new Vec3(size);
		int[][] c = { { 1, 1, 1 }, { -1, 1, 1 }, { -1, 1, -1 }, { 1, 1, -1 }, { 1, -1, 1 }, { -1, -1, 1 }, { -1, -1, -1 }, { 1, -1, -1 }, };
		Vec3[] p = new Vec3[8];
		for (int i = 0; i < 8; i++) {
			p[i] = new Vec3(size * c[i][0], size * c[i][1], size * c[i][2]);
		}

		Triangle t0 = new Triangle(p[0], p[1], p[2]); //top
		Triangle t1 = new Triangle(p[2], p[3], p[0]);

		Triangle t2 = new Triangle(p[5], p[4], p[6]); //bottom
		Triangle t3 = new Triangle(p[7], p[6], p[4]);

		Triangle t4 = new Triangle(p[4], p[0], p[3]); //left
		Triangle t5 = new Triangle(p[3], p[7], p[4]);

		Triangle t6 = new Triangle(p[5], p[6], p[2]); //right
		Triangle t7 = new Triangle(p[2], p[1], p[5]);

		Triangle t8 = new Triangle(p[1], p[0], p[4]); //front
		Triangle t9 = new Triangle(p[4], p[5], p[1]);

		Triangle t10 = new Triangle(p[3], p[2], p[6]); //back
		Triangle t11 = new Triangle(p[6], p[7], p[3]);

		List<Triangle> tris = Arrays.asList(t0, t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11);
		this.renderScreen.setTriangles(tris);

		this.renderScreen.setBoxSize(size);
	}

	@Override
	protected void _kill() {
		this.renderScreen.kill();
	}

	@Override
	protected void _resize() {
		this.renderScreen.setScreenDimensions(this.getWidth(), this.getHeight());
	}

	@Override
	public String getDefaultTitle() {
		return "Homework 1";
	}

	private Vec3 calcSurfaceVel(Vec3 _ang_vel, Vec3 _center_mass, Vec3 _pt) {
		if (_ang_vel.lengthSq() <= 1e-9) {
			return new Vec3(0);
		}

		Vec3 ang_vel = new Vec3(_ang_vel);
		Vec3 center_mass = new Vec3(_center_mass);
		Vec3 pt = new Vec3(_pt);

		Vec3 pt_proj = MathUtils.point_lineProject(pt, center_mass, ang_vel);
		Vec3 to_pt = pt.sub(pt_proj);
		Vec3 surf_vel = ang_vel.cross(to_pt);
		return surf_vel;
	}

	//contact vec is vector from center mass to contact point
	private void applyImpulse(Vec3[] accel, Vec3[] ang_accel, int ind, Sphere s, Vec3 impulse, Vec3 contact_vec) {
		//linear impulse
		accel[ind].addi(impulse.div(s.mass));

		//torque
		ang_accel[ind].addi((contact_vec.cross(impulse)).div(s.moment));
	}

	private void handleCollision(Vec3[] accel, Vec3[] ang_accel, int ind, Sphere s, Vec3 coll_pt, Vec3 relative_vel, float inv_mass_b) {
		//normal from b -> a
		Vec3 normal = s.pos.sub(coll_pt);
		normal.normalize();

		Vec3 contact_vec = coll_pt.sub(s.pos);

		float normal_vel = normal.dot(relative_vel);
		if (normal_vel < 0) { //they're separating. 
			return;
		}

		float inv_mass_sum = 1.0f / s.mass + inv_mass_b;
		float normal_impulse_scalar = (1.0f + settings.coeffRestitution) * normal_vel / inv_mass_sum;
		if (relative_vel.lengthSq() < settings.gravity.mul(settings.timeStep).lengthSq() + 1e-5) {
			normal_impulse_scalar = normal_vel / inv_mass_sum;
		}

		//apply normal impulse
		this.applyImpulse(accel, ang_accel, ind, s, normal.mul(normal_impulse_scalar), contact_vec);

		Vec3 tangent_norm = relative_vel.sub(normal.mul(normal_vel));
		tangent_norm.normalize();

		float tangent_vel = tangent_norm.dot(relative_vel);
		float tangent_impulse_scalar = tangent_vel / inv_mass_sum;

		//		System.err.println("TANGENT NORM : " + tangent_norm + " " + tangent_norm.dot(normal));
		//		System.err.println("REL VEL : " + relative_vel);
		//		System.err.println("TANGENT VEL : " + tangent_vel);
		//		System.err.println("CONTACT VEC : " + contact_vec);
		//		System.err.println("S POS : " + s.pos);
		//		System.err.println("ANG VEL : " + s.ang_vel);

		if (tangent_impulse_scalar > normal_impulse_scalar * settings.staticFriction) {
			tangent_impulse_scalar *= settings.dynamicFriction;
		}

		//apply tangent impulse
		this.applyImpulse(accel, ang_accel, ind, s, tangent_norm.mul(tangent_impulse_scalar), contact_vec);
	}

	private void physicsStep() {
		Vec3[] accel = new Vec3[this.spheres.size()];
		Vec3[] pen_correct = new Vec3[this.spheres.size()];
		Vec3[] ang_accel = new Vec3[this.spheres.size()];
		for (int i = 0; i < this.spheres.size(); i++) {
			accel[i] = new Vec3(0);
			pen_correct[i] = new Vec3(0);
			ang_accel[i] = new Vec3(0);
		}

		//basics
		for (int i = 0; i < this.spheres.size(); i++) {
			Sphere s = this.spheres.get(i);

			//wall collision
			if ((s.pos.x - s.radius < -settings.boxSize && s.vel.x < 0) || (s.pos.x + s.radius > settings.boxSize && s.vel.x > 0)) { //horizontal
				Vec3 coll_pt = new Vec3(s.pos);
				if (s.pos.x - s.radius < -settings.boxSize) {
					pen_correct[i].addi(new Vec3(-settings.boxSize - (s.pos.x - s.radius), 0, 0));
					coll_pt.x -= s.radius;
				}
				else {
					pen_correct[i].addi(new Vec3(settings.boxSize - (s.pos.x + s.radius), 0, 0));
					coll_pt.x += s.radius;
				}
				Vec3 relative_vel = new Vec3(0);
				relative_vel.subi(s.vel);
				relative_vel.subi(calcSurfaceVel(s.ang_vel, s.pos, coll_pt));
				this.handleCollision(accel, ang_accel, i, s, coll_pt, relative_vel, 0);
			}
			if ((s.pos.y - s.radius < -settings.boxSize && s.vel.y < 0) || (s.pos.y + s.radius > settings.boxSize && s.vel.y > 0)) { //vertical
				Vec3 coll_pt = new Vec3(s.pos);
				if (s.pos.y - s.radius < -settings.boxSize) {
					pen_correct[i].addi(new Vec3(0, -settings.boxSize - (s.pos.y - s.radius), 0));
					coll_pt.y -= s.radius;
				}
				else {
					pen_correct[i].addi(new Vec3(0, settings.boxSize - (s.pos.y + s.radius), 0));
					coll_pt.y += s.radius;
				}
				Vec3 relative_vel = new Vec3(0);
				relative_vel.subi(s.vel);
				relative_vel.subi(calcSurfaceVel(s.ang_vel, s.pos, coll_pt));
				this.handleCollision(accel, ang_accel, i, s, coll_pt, relative_vel, 0);
			}
			if ((s.pos.z - s.radius < -settings.boxSize && s.vel.z < 0) || (s.pos.z + s.radius > settings.boxSize && s.vel.z > 0)) { //depth?
				Vec3 coll_pt = new Vec3(s.pos);
				if (s.pos.z - s.radius < -settings.boxSize) {
					pen_correct[i].addi(new Vec3(0, 0, -settings.boxSize - (s.pos.z - s.radius)));
					coll_pt.z -= s.radius;
				}
				else {
					pen_correct[i].addi(new Vec3(0, 0, settings.boxSize - (s.pos.z + s.radius)));
					coll_pt.z += s.radius;
				}
				Vec3 relative_vel = new Vec3(0);
				relative_vel.subi(s.vel);
				relative_vel.subi(calcSurfaceVel(s.ang_vel, s.pos, coll_pt));
				this.handleCollision(accel, ang_accel, i, s, coll_pt, relative_vel, 0);
			}

			//gravity
			accel[i].addi(settings.gravity.mul(settings.timeStep));
		}

		//collisions
		for (int i = 0; i < this.spheres.size(); i++) {
			for (int j = 0; j < this.spheres.size(); j++) {
				if (i == j) {
					continue;
				}

				//considering impulse of sa due to sb. 
				Sphere sa = this.spheres.get(i);
				Sphere sb = this.spheres.get(j);

				Vec3 ba = new Vec3(sb.pos, sa.pos);
				if (ba.lengthSq() > Math.pow(sa.radius + sb.radius, 2)) { //not colliding
					continue;
				}

				Vec3 ba_norm = new Vec3(ba);
				ba_norm.normalize();

				//need to correct penetration. Move two balls equally
				if (i < j) {
					float pen = (sa.radius + sb.radius) - ba.length();
					pen = Math.max(0, pen - settings.penetrationThreshold);
					pen_correct[i].addi(ba_norm.mul(pen / 2.0f));
					pen_correct[j].subi(ba_norm.mul(pen / 2.0f));
				}

				//compute collision point
				Vec3 coll_pt = sa.pos.add(sb.pos).mul(0.5f);

				//relative vel between two contact points
				Vec3 relative_vel = new Vec3(0);
				relative_vel.addi(sb.vel);
				relative_vel.addi(calcSurfaceVel(sb.ang_vel, sb.pos, coll_pt));
				relative_vel.subi(sa.vel);
				relative_vel.subi(calcSurfaceVel(sa.ang_vel, sa.pos, coll_pt));

				this.handleCollision(accel, ang_accel, i, sa, coll_pt, relative_vel, 1.0f / sb.mass);

				/*
				//normal from b -> a
				Vec3 normal = sa.pos.sub(sb.pos);
				normal.normalize();
				
				float normal_vel = normal.dot(relative_vel);
				if (normal_vel < 0) { //they're separating. 
					continue;
				}
				
				//				System.err.println("NORMAL VEL : " + normal_vel + " " + normal);
				
				float inv_mass_sum = 1.0f / sa.mass + 1.0f / sb.mass;
				float normal_impulse_scalar = (1.0f + this.coeffRestitution) * normal_vel / inv_mass_sum;
				
				//apply normal impulse
				accel[i].addi(normal.mul(normal_impulse_scalar / sa.mass));
				
				Vec3 tangent_norm = relative_vel.sub(normal.mul(normal_vel));
				tangent_norm.normalize();
				
				float tangent_vel = tangent_norm.dot(relative_vel);
				float tangent_impulse_scalar = tangent_vel / inv_mass_sum;
				
				if (tangent_impulse_scalar > normal_impulse_scalar * this.staticFriction) {
					tangent_impulse_scalar *= this.dynamicFriction;
				}
				
				//apply tangent impulse
				accel[i].addi(tangent_norm.mul(tangent_impulse_scalar / sa.mass));
				
				//apply torque
				Vec3 rel_coll_pt = coll_pt.sub(sa.pos);
				Vec3 torque_impulse = rel_coll_pt.cross(tangent_norm);
				ang_accel[i].addi(torque_impulse.mul(-tangent_impulse_scalar / sa.moment));
				
				System.err.println("TANGENT IMPULSE SCALAR : " + tangent_impulse_scalar);
				System.err.println("ANG ACCEL : " + ang_accel[i]);
				System.err.println("REL COLL PT : " + rel_coll_pt);
				//				if (tangent_impulse_scalar != 0) {
				//					System.exit(0);
				//				}
				*/

				/*
				//consider collision from perspective of sa. Put sa at origin and velocity to 0. 
				//now, sa is standing still and sb is moving towards (?) sa. 
				Vec3 pa = new Vec3(0);
				Vec3 pb = sb.pos.sub(sa.pos);
				
				Vec3 va = new Vec3(0);
				Vec3 vb = sb.vel.sub(sa.vel);
				
				float ma = sa.mass;
				float mb = sb.mass;
				
				Vec3 unit_norm = new Vec3(ba_norm); //b -> a
				if (vb.dot(unit_norm) < 0) { //b is travelling away from a
					continue;
				}
				
				Vec3 vel_norm = unit_norm.mul(vb.dot(unit_norm));
				Vec3 vel_tan = vb.sub(vel_norm);
				
				// - find relative velocity between two contact points
				//   - vel_tan + rot_tan_a + rot_tan_b
				Vec3 cpta = new Vec3(pb).normalize().mul(sa.radius);
				Vec3 rot_tan_a = calcSurfaceVel(sa.ang_vel, pa, cpta);
				
				Vec3 cptb = new Vec3(pb).normalize().mul(-sb.radius);
				cptb.addi(pb);
				Vec3 rot_tan_b = calcSurfaceVel(sb.ang_vel, pb, cptb);
				
				vel_tan.subi(rot_tan_a);
				vel_tan.addi(rot_tan_b);
				if (vel_tan.length() > 1) {
					vel_tan.normalize();
				}
				//				vel_tan.normalize();
				
				//				System.err.println("VEL_TAN : " + vel_tan);
				//				System.err.println("ROT TAN : " + rot_tan_a + " " + rot_tan_b);
				//				System.err.println("CPT : " + cpta + " " + cptb);
				
				float norm_impulse = (1.0f + this.coeffRestitution) / (1.0f / ma + 1.0f / mb);
				
				// - find normal force (we currently have a normal velocity)
				//   - maybe velocity * mass? or just velocity. I think velocity * mass
				float frict_impulse = norm_impulse * this.coeffFriction;
				
				// - compute tangential force due to friction, and apply it to center mass and angular velocity. 
				float torque_impulse_mag = frict_impulse * sa.radius;
				
				Vec3 torque_impulse = cpta.cross(vel_tan.mul(-torque_impulse_mag));
				torque_impulse = torque_impulse.div(sa.moment);
				ang_accel[i].addi(torque_impulse);
				
				//				System.err.println("ANG ACCEL : " + ang_accel[i] + " " + sa.ang_vel);
				
				Vec3 imp = vel_norm.mul(norm_impulse);
				imp.addi(vel_tan.mul(frict_impulse));
				imp = imp.div(ma);
				accel[i].addi(imp);
				
				//				System.err.println("IMP : " + imp);
				//				System.exit(0);
				*/

			}
		}

		//assign values of next step
		for (int i = 0; i < this.spheres.size(); i++) {
			Sphere s = this.spheres.get(i);
			s.pos.addi(s.vel.mul(settings.timeStep));
			s.pos.addi(pen_correct[i]);
			s.vel.addi(accel[i]);

			s.ang_vel.addi(ang_accel[i]);
			if (settings.doAngVelDamping) {
				Vec3 norm_ang_vel = new Vec3(s.ang_vel);
				norm_ang_vel.normalize();
				s.ang_vel.subi(norm_ang_vel.muli(settings.timeStep));
				if (s.ang_vel.length() < 1e-2) {
					s.ang_vel.set(0, 0, 0);
				}
			}

			Vec3 axis = new Vec3(s.ang_vel);
			float omega = axis.length();
			axis = axis.normalize();
			Quaternion quat_rot = MathUtils.quaternionRotationAxisAngle(omega * settings.timeStep, axis.x, axis.y, axis.z);
			Quaternion n_orient = quat_rot.mul(s.orient);
			n_orient.normalize();
			s.orient = n_orient;
		}

		this.timeDebt -= settings.timeStep;
	}

	@Override
	protected void _update() {
		this.pc.update();
		this.renderScreen.setCameraPos(this.pc.getPos());
		this.renderScreen.setCameraFacing(this.pc.getFacing());

		this.timeDebt += Main.getDeltaSeconds();
		while (this.timeDebt > 0) {
			this.physicsStep();
		}

		this.renderScreen.setSpheres(this.spheres);
	}

	@Override
	protected void renderContent(Framebuffer outputBuffer) {
		this.renderScreen.render(outputBuffer);

	}

	@Override
	protected void renderOverlay(Framebuffer outputBuffer) {
		// TODO Auto-generated method stub

	}

	@Override
	protected void selected() {
		this.pc.setAcceptPlayerInputs(true);
	}

	@Override
	protected void deselected() {
		this.pc.setAcceptPlayerInputs(false);
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
		// TODO Auto-generated method stub

	}

	@Override
	protected void _mouseReleased(int button) {
		// TODO Auto-generated method stub

	}

	@Override
	protected void _mouseScrolled(float wheelOffset, float smoothOffset) {
		// TODO Auto-generated method stub

	}

	private void generateBalls() {
		this.spheres = new ArrayList<>();
		float radius = 10;
		for (int i = 0; i < settings.ballCount; i++) {
			Vec3 pos = MathUtils.random(new Vec3(-settings.boxSize + radius), new Vec3(settings.boxSize - radius));
			//				pos.z = 0;
			this.spheres.add(new Sphere(pos, radius));
		}
	}

	@Override
	protected void _keyPressed(int key) {
		switch (key) {
		case GLFW.GLFW_KEY_Q:
			this.generateBalls();
			break;
		}
	}

	@Override
	protected void _keyReleased(int key) {
		// TODO Auto-generated method stub

	}

	public class SimulationSettings {
		public float boxSize = 50;
		public Vec3 gravity = new Vec3(0, -300, 0);
		public int invTimeStep = 60;
		public float timeStep = 1.0f / this.invTimeStep;
		public float coeffRestitution = 0.9f;
		public float penetrationThreshold = 0.05f; //threshold before which we do penetration correction. 
		public float dynamicFriction = 0f;
		public float staticFriction = 0f;
		public boolean doAngVelDamping = true;
		public boolean renderSphereTexture = false;
		public int ballCount = 1;

		public SimulationSettings() {
			setBoxSize(this.boxSize);
		}

		public int getInvTimeStep() {
			return invTimeStep;
		}

		public void setInvTimeStep(int invTimeStep) {
			this.invTimeStep = invTimeStep;
			this.timeStep = 1.0f / this.invTimeStep;
		}

		public int getBallCount() {
			return ballCount;
		}

		public void setBallCount(int ballCount) {
			this.ballCount = ballCount;
		}

		public float getBoxSize() {
			return boxSize;
		}

		public void setBoxSize(float boxSize) {
			this.boxSize = boxSize;
			HW1Window.this.setBoxSize(this.boxSize);
		}

		public Vec3 getGravity() {
			return gravity;
		}

		public void setGravity(Vec3 gravity) {
			this.gravity = gravity;
		}

		public float getCoeffRestitution() {
			return coeffRestitution;
		}

		public void setCoeffRestitution(float coeffRestitution) {
			this.coeffRestitution = coeffRestitution;
		}

		public float getPenetrationThreshold() {
			return penetrationThreshold;
		}

		public void setPenetrationThreshold(float penetrationThreshold) {
			this.penetrationThreshold = penetrationThreshold;
		}

		public float getDynamicFriction() {
			return dynamicFriction;
		}

		public void setDynamicFriction(float dynamicFriction) {
			this.dynamicFriction = dynamicFriction;
		}

		public float getStaticFriction() {
			return staticFriction;
		}

		public void setStaticFriction(float staticFriction) {
			this.staticFriction = staticFriction;
		}

		public boolean getDoAngVelDamping() {
			return doAngVelDamping;
		}

		public void setDoAngVelDamping(boolean doAngVelDamping) {
			this.doAngVelDamping = doAngVelDamping;
		}

		public boolean getRenderSphereTexture() {
			return renderSphereTexture;
		}

		public void setRenderSphereTexture(boolean renderSphereTexture) {
			this.renderSphereTexture = renderSphereTexture;
			renderScreen.setRenderSphereTexture(renderSphereTexture);
		}
	}

	class Sphere {
		public Vec3 pos, vel;
		public float radius;
		public float mass, moment;

		public Vec3 ang_vel; //direction is rotation axis, magnitude is rotation vel. 

		public Quaternion orient;

		public Sphere(Vec3 _pos, float _radius) {
			this.pos = new Vec3(_pos);
			this.radius = _radius;

			this.vel = new Vec3(0);

			this.mass = (float) (Math.pow(this.radius, 3) * (4.0 / 3.0) * Math.PI);
			this.moment = (float) ((2.0 / 5.0) * this.mass * this.radius * this.radius); //of inertia

			this.ang_vel = new Vec3(0);

			this.orient = Quaternion.identity();
		}
	}

	class Triangle {
		public Vec3 p0, p1, p2;

		public Triangle(Vec3 _p0, Vec3 _p1, Vec3 _p2) {
			this.p0 = new Vec3(_p0);
			this.p1 = new Vec3(_p1);
			this.p2 = new Vec3(_p2);
		}
	}

}
