package animation;

import java.awt.Color;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Queue;

import org.lwjgl.PointerBuffer;
import org.lwjgl.assimp.AIAnimation;
import org.lwjgl.assimp.AIBone;
import org.lwjgl.assimp.AIMatrix4x4;
import org.lwjgl.assimp.AINode;
import org.lwjgl.assimp.AINodeAnim;
import org.lwjgl.assimp.AIQuatKey;
import org.lwjgl.assimp.AIQuaternion;
import org.lwjgl.assimp.AIScene;
import org.lwjgl.assimp.AIVector3D;
import org.lwjgl.assimp.AIVectorKey;
import org.lwjgl.assimp.AIVertexWeight;

import lwjglengine.graphics.Material;
import lwjglengine.model.Line;
import lwjglengine.model.ModelInstance;
import lwjglengine.model.ModelTransform;
import myutils.math.Mat4;
import myutils.math.MathUtils;
import myutils.math.Quaternion;
import myutils.math.Vec3;
import myutils.misc.Pair;

public class AnimationHandler {
	//keeps track of the current animation being played, and the respective pointers. 
	
	final int WORLD_SCENE;
	
	boolean is_valid = false;
	
	private Node[] nodes;	//root is nodes[0]
	private Mat4[] nodeTransforms;
	private Animation[] animations;
	
	private boolean doLooping = false;
	
	//if curAnimation != -1, then we're currently playing one.
	private int curAnimation = -1;
	private long prevTimeMillis;
	private float animationTime;
	
	//while playing an animation, these are what keep track of what keyframes to interpolate
	private int[] posptrs, orientptrs, scaleptrs;
	
	//debug skeleton rendering. 
	private boolean renderSkeleton = false;
	private ModelInstance[] skeletonInstances;
	
	public AnimationHandler(AIScene aiscene, int _WORLD_SCENE) {
		this.WORLD_SCENE = _WORLD_SCENE;
		
		if(aiscene.mRootNode() == null) {	//doesn't have skeleton
			return;
		}
		
		//extract nodes
		HashMap<String, Integer> name_to_id = new HashMap<>();
		{
			AINode airoot = aiscene.mRootNode();
			Node root = new Node(airoot, null, name_to_id);
			this.nodes = new Node[name_to_id.size()];
			this.nodeTransforms = new Mat4[name_to_id.size()];
			this.posptrs = new int[name_to_id.size()];
			this.orientptrs = new int[name_to_id.size()];
			this.scaleptrs = new int[name_to_id.size()];
			Queue<Node> q = new ArrayDeque<>();
			q.add(root);
			while(q.size() != 0){
				Node cur = q.poll();
				this.nodes[cur.id] = cur;
				for(Node next : cur.children) {
					q.add(next);
				}
			}
			this.computeNodeTransforms();
		}
		
		//extract animations
		if(aiscene.mAnimations() != null) {
			PointerBuffer aianimations = aiscene.mAnimations();
			this.animations = new Animation[aianimations.limit()];
			System.out.println("NR ANIMATIONS : " + aianimations.limit());
			for(int i = 0; i < aianimations.limit(); i++) {
				AIAnimation anim = AIAnimation.create(aianimations.get(i));
				this.animations[i] = new Animation(anim, name_to_id);
				System.out.println("Animation " + i + ", duration : " + anim.mDuration() + ", tps : " + anim.mTicksPerSecond());
			}
		}
		else {
			this.animations = new Animation[0];
		}
		
		//extract bones
		//TODO
		
		this.is_valid = true;
	}
	
	public void stopAnimation() {
		this.curAnimation = -1;
		this.computeNodeTransforms();
	}
	
	public void playAnimation(int ind) {
		if(!this.is_valid) {
			System.err.println("AnimationHandler : Tried to play animation while invalid");
			return;
		}
		if(ind < 0 || ind > this.animations.length) {
			System.err.println("AnimationHandler : playAnimation index out of bounds, " + ind);
			return;
		}
		
		this.curAnimation = ind;
		this.prevTimeMillis = System.currentTimeMillis();
		this.animationTime = 0;
		
		//reset all keyframe pointers
		for(int i = 0; i < this.nodes.length; i++) {
			this.posptrs[i] = 0;
			this.orientptrs[i] = 0;
			this.scaleptrs[i] = 0;
		}
	
		this.computeNodeTransforms();
	}
	
	public void setDoLooping(boolean b) {
		this.doLooping = b;
	}
	
	public void setRenderSkeleton(boolean b) {
		if(this.renderSkeleton == b) {
			return;
		}
		this.renderSkeleton = b;
		if(this.renderSkeleton) {
			this.skeletonInstances = new ModelInstance[this.nodes.length];
			for(int i = 0; i < this.nodes.length; i++) {
				this.skeletonInstances[i] = Line.addDefaultLine(WORLD_SCENE);
				this.skeletonInstances[i].setMaterial(new Material(Color.WHITE));
			}
			this.generateSkeleton();
		}
		else {
			for(ModelInstance m : this.skeletonInstances) {
				m.kill();
			}
			this.skeletonInstances = null;
		}
	}
	
	private void generateSkeleton() {
		if(this.skeletonInstances == null ) {
			System.err.println("AnimationHandler : tried to generate skeleton while skeletonInstances == null");
			return;
		}
		for(int i = 0; i < this.nodes.length; i++) {
			Vec3 cpos = this.nodeTransforms[i].mul(new Vec3(0), 1);
			Vec3 ppos = new Vec3(cpos);
			if(i != 0) {
				ppos = this.nodeTransforms[this.nodes[i].parent.id].mul(new Vec3(0), 1);
			}
			ModelTransform t = Line.generateLineModelTransform(ppos, cpos);
			this.skeletonInstances[i].setModelTransform(t);
		}
	}
	
	public void update() {
		if(this.curAnimation != -1) {
			long cur_time_millis = System.currentTimeMillis();
			long delta_millis = cur_time_millis - this.prevTimeMillis;
			float delta_seconds = ((float) delta_millis) / 1000.0f;
			this.prevTimeMillis = cur_time_millis;
			this.advance(delta_seconds);
		}
	}
	
	private void computeNodeTransforms() {
		this.nodes[0].computeTransform();
		if(this.renderSkeleton) {
			this.generateSkeleton();
		}
	}
	
	//advances the current animation, if current animation runs out of duration, stops it. 
	private void advance(float delta_seconds) {
		if(!this.is_valid) {
			System.err.println("AnimationHandler : Tried to advance animation while invalid");
			return;
		}
		
		if(this.curAnimation == -1) {
			return;
		}
		
		this.animationTime += delta_seconds;
		Animation a = this.animations[this.curAnimation];
		
		//see if we need to stop animation
		if(this.animationTime > a.duration) {
			if(!this.doLooping) {
				this.stopAnimation();
				return;
			}
			
			//update animation time, reset all keyframe pointers
			while(this.animationTime > a.duration) {
				this.animationTime -= a.duration;
			}
			for(int i = 0; i < this.nodes.length; i++) {
				this.posptrs[i] = 0;
				this.orientptrs[i] = 0;
				this.scaleptrs[i] = 0;
			}
		}
		
		//for each node, figure out new positions of pointers
		NodeAnimation[] channels = a.channels;
		for(int i = 0; i < this.nodes.length; i++) {
			NodeAnimation na = channels[i];
			while(this.posptrs[i] + 1 < na.poskeys.length && this.animationTime > na.poskeys[this.posptrs[i] + 1].first) {
				this.posptrs[i] ++;
			}
			while(this.orientptrs[i] + 1 < na.orientkeys.length && this.animationTime > na.orientkeys[this.orientptrs[i] + 1].first) {
				this.orientptrs[i] ++;
			}
			while(this.scaleptrs[i] + 1 < na.scalekeys.length && this.animationTime > na.scalekeys[this.scaleptrs[i] + 1].first) {
				this.scaleptrs[i] ++;
			}
		}
		
		this.computeNodeTransforms();
	}
	
	class Animation {
		float duration, tps;
		NodeAnimation[] channels;	//one channel per node
		
		public Animation(AIAnimation aianimation, HashMap<String, Integer> name_to_id) {
			this.tps = (float) aianimation.mTicksPerSecond();
			if(this.tps <= 0) this.tps = 1;
			this.duration = (float) aianimation.mDuration() / this.tps;
			PointerBuffer aichannels = aianimation.mChannels();
			this.channels = new NodeAnimation[nodes.length];
			for(int i = 0; i < aichannels.limit(); i++) {
				AINodeAnim ainodeanim = AINodeAnim.create(aichannels.get(i));
				String name = ainodeanim.mNodeName().dataString();
				this.channels[name_to_id.get(name)] = new NodeAnimation(ainodeanim, this.tps, name_to_id.get(name));
			}
			for(int i = 0; i < nodes.length; i++) {
				if(this.channels[i] == null) {
					this.channels[i] = new NodeAnimation(i);
				}
			}
		}
	}
	
	class NodeAnimation {
		int node_id;
		Pair<Float, Vec3>[] poskeys, scalekeys;
		Pair<Float, Quaternion>[] orientkeys;
		
		public NodeAnimation(AINodeAnim ainodeanimation, float tps, int _node_id) {
			this.node_id = _node_id;
			AIVectorKey.Buffer aiposkeys = ainodeanimation.mPositionKeys();
			this.poskeys = new Pair[aiposkeys.limit()];
			for(int i = 0; i < aiposkeys.limit(); i++) {
				AIVectorKey aiposkey = aiposkeys.get(i);
				AIVector3D aivec = aiposkey.mValue();
				this.poskeys[i] = new Pair<Float, Vec3>((float) aiposkey.mTime() / tps, new Vec3(aivec.x(), aivec.y(), aivec.z()));
			}
			AIQuatKey.Buffer aiorientkeys = ainodeanimation.mRotationKeys();
			this.orientkeys = new Pair[aiorientkeys.limit()];
			for(int i = 0; i < aiorientkeys.limit(); i++) {
				AIQuatKey aiquatkey = aiorientkeys.get(i);
				AIQuaternion aiquat = aiquatkey.mValue();
				this.orientkeys[i] = new Pair<Float, Quaternion>((float) aiquatkey.mTime() / tps, new Quaternion(aiquat.x(), aiquat.y(), aiquat.z(), aiquat.w()));
			}
			AIVectorKey.Buffer aiscalekeys = ainodeanimation.mScalingKeys();
			this.scalekeys = new Pair[aiscalekeys.limit()];
			for(int i = 0; i < aiscalekeys.limit(); i++) {
				AIVectorKey aiscalekey = aiscalekeys.get(i);
				AIVector3D aivec = aiscalekey.mValue();
				this.scalekeys[i] = new Pair<Float, Vec3>((float) aiscalekey.mTime() / tps, new Vec3(aivec.x(), aivec.y(), aivec.z()));
			}
		}
		
		//just initializes an identity animation
		public NodeAnimation(int _node_id) {
			this.node_id = _node_id;
			this.poskeys = new Pair[1];
			this.poskeys[0] = new Pair<Float, Vec3>(0f, new Vec3(0));
			this.orientkeys = new Pair[1];
			this.orientkeys[0] = new Pair<Float, Quaternion>(0f, Quaternion.identity());
			this.scalekeys = new Pair[1];
			this.scalekeys[0] = new Pair<Float, Vec3>(0f, new Vec3(1));
		}
	}
	
	class Node {
		int id;
		Node parent;
		Node[] children;
		Mat4 defaultTransform;
		
		public Node(AINode ainode, Node _parent, HashMap<String, Integer> name_to_id) {
			System.out.println("AINODE : " + ainode.mName().dataString());
			this.id = name_to_id.size();
			this.parent = _parent;
			name_to_id.put(ainode.mName().dataString(), this.id);
			
			this.defaultTransform = new Mat4();
			AIMatrix4x4 aimat = ainode.mTransformation();
			this.defaultTransform.mat[0][0] = aimat.a1();
			this.defaultTransform.mat[0][1] = aimat.a2();
			this.defaultTransform.mat[0][2] = aimat.a3();
			this.defaultTransform.mat[0][3] = aimat.a4();
			
			this.defaultTransform.mat[1][0] = aimat.b1();
			this.defaultTransform.mat[1][1] = aimat.b2();
			this.defaultTransform.mat[1][2] = aimat.b3();
			this.defaultTransform.mat[1][3] = aimat.b4();
			
			this.defaultTransform.mat[2][0] = aimat.c1();
			this.defaultTransform.mat[2][1] = aimat.c2();
			this.defaultTransform.mat[2][2] = aimat.c3();
			this.defaultTransform.mat[2][3] = aimat.c4();
			
			this.defaultTransform.mat[3][0] = aimat.d1();
			this.defaultTransform.mat[3][1] = aimat.d2();
			this.defaultTransform.mat[3][2] = aimat.d3();
			this.defaultTransform.mat[3][3] = aimat.d4();
			
			PointerBuffer aichildren = ainode.mChildren();
			if(aichildren != null) {
				this.children = new Node[aichildren.limit()];
				for(int i = 0; i < aichildren.limit(); i++) {
					this.children[i] = new Node(AINode.create(aichildren.get(i)), this, name_to_id);
				}
			}
			else {
				this.children = new Node[0];
			}
		}
		
		//recompute node positions. 
		//position of node is calculated by
		// - start with node default transform, currently in limb space
		// - then apply the animation transform in limb space
		// - finally, apply the parent transform, bringing node into model space
		//animation transform is calculated by first scaling, then rotating, then translating. 
		//before first keyframe and after last one, we just use the keyframe as anim transform
		//everywhere else, we will interpolate
		public void computeTransform() {
			//default transform
			Mat4 transform = new Mat4(this.defaultTransform);
			
			//compute animation transform
			if(curAnimation != -1){
				NodeAnimation na = animations[curAnimation].channels[this.id];
				Vec3 scale = new Vec3(na.scalekeys[scaleptrs[this.id]].second);
				if(animationTime >= na.scalekeys[0].first && scaleptrs[this.id] + 1 < na.scalekeys.length) {
					float t1 = na.scalekeys[scaleptrs[this.id]].first;
					float t2 = na.scalekeys[scaleptrs[this.id] + 1].first;
					Vec3 s1 = na.scalekeys[scaleptrs[this.id]].second;
					Vec3 s2 = na.scalekeys[scaleptrs[this.id] + 1].second;
					scale = MathUtils.lerp(s1, t1, s2, t2, animationTime);
				}
				Quaternion orient = new Quaternion(na.orientkeys[orientptrs[this.id]].second);
				if(animationTime >= na.orientkeys[0].first && orientptrs[this.id] + 1 < na.orientkeys.length) {
					float t1 = na.orientkeys[orientptrs[this.id]].first;
					float t2 = na.orientkeys[orientptrs[this.id] + 1].first;
					Quaternion q1 = na.orientkeys[orientptrs[this.id]].second;
					Quaternion q2 = na.orientkeys[orientptrs[this.id] + 1].second;
					orient = MathUtils.slerp(q1, t1, q2, t2, animationTime);
				}
				Vec3 pos = new Vec3(na.poskeys[posptrs[this.id]].second);
				if(animationTime >= na.poskeys[0].first && posptrs[this.id] + 1 < na.poskeys.length) {
					float t1 = na.poskeys[posptrs[this.id]].first;
					float t2 = na.poskeys[posptrs[this.id] + 1].first;
					Vec3 v1 = na.poskeys[posptrs[this.id]].second;
					Vec3 v2 = na.poskeys[posptrs[this.id] + 1].second;
					pos = MathUtils.lerp(v1, t1, v2, t2, animationTime);
				}
				Mat4 anim_transform = Mat4.scale(scale);
				anim_transform.muli(MathUtils.quaternionToRotationMat4(orient));
				anim_transform.muli(Mat4.translate(pos));
				transform.muli(anim_transform);
			}

			
			//parent transform
			if(this.parent != null) {
				transform.muli(nodeTransforms[parent.id]);
			}
			
			nodeTransforms[this.id] = transform;
			
			for(Node next : this.children) {
				next.computeTransform();
			}
		}
	}
	
	class Bone {
		int node_id;
		Bone[] children;
		AIVertexWeight[] weights;
		
		public Bone(AIBone aibone, HashMap<String, Integer> name_to_id) {
			this.node_id = name_to_id.get(aibone.mName().dataString());
			AIVertexWeight.Buffer weights = aibone.mWeights();
			this.weights = new AIVertexWeight[weights.limit()];
			for(int k = 0; k < weights.limit(); k++) {
				this.weights[k] = weights.get(k);
			}
		}
	}
}
