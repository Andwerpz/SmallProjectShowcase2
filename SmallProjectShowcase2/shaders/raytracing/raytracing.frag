#version 440 core
layout (location = 0) out vec4 out_tex_0;

uniform sampler2D render_tex_0;
uniform samplerCube skybox_tex;

uniform vec3 camera_pos;

in vec3 frag_dir;

struct Ray {
	vec3 origin;
	vec3 dir;
};

struct Material {
	vec4 diffuse;
	vec4 specular;
	vec4 emissive;
	vec4 attr;	//x = shininess, y = roughness, z = specularProbability
};

struct HitInfo {
	bool didHit;
	float dist;
	vec3 hitPoint;
	vec3 hitNormal;
	Material hitMaterial;
};

struct Sphere {
	vec3 center;
	float radius;
	Material material;
};

struct Triangle {
	vec3 a;
	vec3 b;
	vec3 c;
	Material material;
};

struct BoundingBox {
	vec3 b_min;
	vec3 b_max;
};

layout (binding = 1) readonly buffer bvhBuffer {
	int bvhData[];
};

layout (binding = 2) readonly buffer boundingBoxBuffer {
	float boundingBoxData[];
};

layout (binding = 3) readonly buffer primitiveBuffer {
	float primitiveData[];
};

layout (binding = 4) readonly buffer materialBuffer {
	float materialData[];
};

uniform int num_rendered_frames;
uniform int window_width;
uniform int window_height;

uint rngState = uint(gl_FragCoord.x * window_width) * window_width * 13 + uint(gl_FragCoord.y * window_height) * 1203 + num_rendered_frames * 1838411;
//https://www.shadertoy.com/view/XlGcRh
float randomValue() {
	rngState = rngState * 747796405 + 2891336453;
	uint result = ((rngState >> ((rngState >> 28u) + 4u)) ^ rngState) * 277803737u;
	result = (result >> 22) ^ result;
	return result / 4294967295.0;
}

float randomValueNormal() {
	float theta = 2 * 3.1415926 * randomValue();
	float rho = sqrt(-2 * log(randomValue()));
	return rho * cos(theta);
}

vec3 randomDirection() {
	float x = randomValueNormal();
	float y = randomValueNormal();
	float z = randomValueNormal();
	return normalize(vec3(x, y, z));
}

vec3 randomPointInSphere() {
	vec3 ret = randomDirection();
	ret *= sqrt(randomValue());
	return ret;
}

vec3 randomHemisphereDirection(vec3 normal) {
	vec3 dir = randomDirection();
	return dir * sign(dot(normal, dir));
}

vec2 randomPointInCircle() {
	float angle = randomValue() * 2 * 3.1415926;
	vec2 pointOnCircle = vec2(cos(angle), sin(angle));
	pointOnCircle *= sqrt(randomValue());
	return pointOnCircle;
}

Material createMaterial() {
	return Material(vec4(0), vec4(0), vec4(0), vec4(0));
}

BoundingBox createBoundingBox() {
	return BoundingBox(vec3(0), vec3(0));
}

HitInfo createHitInfo() {
	return HitInfo(false, 0, vec3(0), vec3(0), createMaterial());
}

Sphere createSphere() {
	return Sphere(vec3(0), 0, createMaterial());
}

Triangle createTriangle() {
	return Triangle(vec3(0), vec3(0), vec3(0), createMaterial());
}

Material parseMaterial(inout int materialOffset) {
	Material mat = createMaterial();
	mat.diffuse.r = materialData[materialOffset ++];
	mat.diffuse.g = materialData[materialOffset ++];
	mat.diffuse.b = materialData[materialOffset ++];
	mat.diffuse.a = materialData[materialOffset ++];
	mat.specular.r = materialData[materialOffset ++];
	mat.specular.g = materialData[materialOffset ++];
	mat.specular.b = materialData[materialOffset ++];
	mat.specular.a = materialData[materialOffset ++];
	mat.emissive.r = materialData[materialOffset ++];
	mat.emissive.g = materialData[materialOffset ++];
	mat.emissive.b = materialData[materialOffset ++];
	mat.emissive.a = materialData[materialOffset ++];
	mat.attr.r = materialData[materialOffset ++];
	mat.attr.g = materialData[materialOffset ++];
	mat.attr.b = materialData[materialOffset ++];
	return mat;
}

BoundingBox parseBoundingBox(inout int boundingBoxOffset) {
	BoundingBox box = createBoundingBox();
	box.b_min.x = boundingBoxData[boundingBoxOffset ++];
	box.b_min.y = boundingBoxData[boundingBoxOffset ++];
	box.b_min.z = boundingBoxData[boundingBoxOffset ++];
	box.b_max.x = boundingBoxData[boundingBoxOffset ++];
	box.b_max.y = boundingBoxData[boundingBoxOffset ++];
	box.b_max.z = boundingBoxData[boundingBoxOffset ++];
	return box;
}

Sphere parseSphere(inout int primitiveOffset, inout int materialOffset) {
	Sphere ret = createSphere();
	ret.center.x = primitiveData[primitiveOffset ++];
	ret.center.y = primitiveData[primitiveOffset ++];
	ret.center.z = primitiveData[primitiveOffset ++];
	ret.radius = primitiveData[primitiveOffset ++];
	ret.material = parseMaterial(materialOffset);
	return ret;
}	

Triangle parseTriangle(inout int primitiveOffset, inout int materialOffset) {
	Triangle ret = createTriangle();
	ret.a.x = primitiveData[primitiveOffset ++];
	ret.a.y = primitiveData[primitiveOffset ++];
	ret.a.z = primitiveData[primitiveOffset ++];
	ret.b.x = primitiveData[primitiveOffset ++];
	ret.b.y = primitiveData[primitiveOffset ++];
	ret.b.z = primitiveData[primitiveOffset ++];
	ret.c.x = primitiveData[primitiveOffset ++];
	ret.c.y = primitiveData[primitiveOffset ++];
	ret.c.z = primitiveData[primitiveOffset ++];
	ret.material = parseMaterial(materialOffset);
	return ret;
}

bool pointInsideBoundingBox(vec3 pt, BoundingBox box) {
	float epsilon = 0.0001;
	vec3 b_min = box.b_min;
	vec3 b_max = box.b_max;
	return 
		pt.x + epsilon > b_min.x && 
		pt.y + epsilon > b_min.y && 
		pt.z + epsilon > b_min.z && 
		pt.x - epsilon < b_max.x && 
		pt.y - epsilon < b_max.y && 
		pt.z - epsilon < b_max.z;
}

bool rayBoundingBox(Ray ray, BoundingBox box) {
	vec3 b_min = box.b_min;
	vec3 b_max = box.b_max;
	if(pointInsideBoundingBox(ray.origin, box)) {
		return true;
	}	
	
	bool hits = false;
	float dir_component[3] = float[](ray.dir.x, ray.dir.y, ray.dir.z);
	float pos_component[3] = float[](ray.origin.x, ray.origin.y, ray.origin.z);
	float min_component[3] = float[](b_min.x, b_min.y, b_min.z);
	float max_component[3] = float[](b_max.x, b_max.y, b_max.z);
	for(int i = 0; i < 3; i++){
		if(dir_component[i] == 0) {
			continue;
		}
		float tgt = (dir_component[i] < 0) ? max_component[i] : min_component[i];
		float dist = tgt - pos_component[i];
		float ray_mul = dist / dir_component[i];
		vec3 test_pos = ray.origin + ray.dir * ray_mul;
		hits = hits || (pointInsideBoundingBox(test_pos, box) && ray_mul >= 0);
	}
	return hits;
}

HitInfo raySphere(Ray ray, Sphere sphere) {
	HitInfo ret = createHitInfo();
	vec3 sphereCenter = sphere.center.xyz;
	float sphereRadius = sphere.radius;
	vec3 offsetRayOrigin = ray.origin - sphereCenter;
	
	float a = dot(ray.dir, ray.dir);
	float b = 2 * dot(offsetRayOrigin, ray.dir);
	float c = dot(offsetRayOrigin, offsetRayOrigin) - sphereRadius * sphereRadius;
	
	float d = b * b - 4 * a * c;
	
	if(d >= 0){
		float dist = (-b - sqrt(d)) / (2 * a);
		
		if(dist > 0) {
			ret.didHit = true;
			ret.dist = dist;
			ret.hitPoint = ray.origin + ray.dir * dist;
			ret.hitNormal = normalize(ret.hitPoint - sphereCenter);
			ret.hitMaterial = sphere.material;
		}
	}
	return ret;
}

HitInfo rayTriangle(Ray ray, Triangle triangle) {
	HitInfo ret = createHitInfo();
	
	vec3 t0 = triangle.a.xyz;
	vec3 t1 = triangle.b.xyz;
	vec3 t2 = triangle.c.xyz;
	
	vec3 d0 = normalize(t1 - t0);
	vec3 d1 = normalize(t2 - t1);
	vec3 d2 = normalize(t0 - t2);
	
	vec3 plane_origin = t0;
	vec3 plane_normal = normalize(cross(d0, d1));
	
	if(dot(plane_normal, ray.dir) > 0) {
		//ray dir and plane normal are facing in the same direction, so we shouldn't be able to see this 
		return ret;
	}

	//calculate intersection point between ray and plane defined by triangle
	float ray_dirStepRatio = dot(plane_normal, ray.dir);	// for each step in ray_dir, you go ray_dirStepRatio steps towards the plane
	// in plane_normal
	if (ray_dirStepRatio == 0) {
		// ray is parallel to plane, no intersection
		return ret;
	}
	
	float t = dot(plane_origin - ray.origin, plane_normal) / ray_dirStepRatio;
	if (t < 0) {
		// the plane intersection is behind the ray origin
		return ret;
	}
	
	vec3 plane_intersect = ray.origin + (ray.dir * t);

	// now, we just have to make sure that the intersection point is inside the triangle.
	vec3 n0 = cross(d0, plane_normal);
	vec3 n1 = cross(d1, plane_normal);
	vec3 n2 = cross(d2, plane_normal);
	
	if(dot(n0, t0 - plane_intersect) < 0 || dot(n1, t1 - plane_intersect) < 0 || dot(n2, t2 - plane_intersect) < 0) {
		//intersection point is outside of the triangle
		return ret;
	}
	
	ret.didHit = true;
	ret.dist = t;	//ray.dir has to be normalized on function call for this to work
	ret.hitPoint = plane_intersect;
	ret.hitNormal = plane_normal;
	ret.hitMaterial = triangle.material;

	return ret;
}

//TODO : prune nodes if they are farther away than the current closest hit
HitInfo calculateRayCollision(Ray ray, inout int max_depth) {
	HitInfo closest_hit = createHitInfo();
	closest_hit.dist = 10000000;	//very large number
	ray.dir = normalize(ray.dir);
	
	int root_offset = bvhData[0];
	
	const int max_stack_size = 64;
	int ind_stack[max_stack_size];	//keeps track of what we still need to visit
	
	int stack_ind = 0;
	ind_stack[0] = root_offset;
	
	max_depth = 0;
	
	while(stack_ind >= 0) {
		max_depth = max(max_depth, stack_ind);
		
		//read current node from stack
		int cur_bvh_ind = ind_stack[stack_ind];	
		bool is_leaf = bvhData[cur_bvh_ind + 1] == -1;
		stack_ind --;
		
		//check if ray intersects with outside bounding box
		int cur_box_ind = bvhData[cur_bvh_ind + 0];
		BoundingBox box = parseBoundingBox(cur_box_ind);
		if(!rayBoundingBox(ray, box)) {
			//ray doesn't hit box, get next node
			continue;
		}
		
		if(is_leaf) {
			//process all primitives that come with this leaf
			//for now, if there is a bvh instance primitive, don't process it
			//later, will have to push to stack to process
			
			int primitivePtr = bvhData[cur_bvh_ind + 2];
			int materialPtr = bvhData[cur_bvh_ind + 3];
			int nr_primitives = bvhData[cur_bvh_ind + 4];
			int bvhPtr = cur_bvh_ind + 5;
			for(int i = 0; i < nr_primitives; i++){
				int type = bvhData[bvhPtr ++];
				HitInfo hit = createHitInfo();
				if(type == 1) {
					//sphere
					Sphere sphere = parseSphere(primitivePtr, materialPtr);
					hit = raySphere(ray, sphere);
				}
				else if(type == 2) {
					//triangle
					Triangle triangle = parseTriangle(primitivePtr, materialPtr);
					hit = rayTriangle(ray, triangle);
				}
				if(hit.didHit && hit.dist < closest_hit.dist) {
					closest_hit = hit;
				}
			}
			continue;
		}
		
		//node is not a leaf
		//push both children to stack
		int first_child_ind = cur_bvh_ind + 2;
		int second_child_ind = bvhData[cur_bvh_ind + 1];
		
		stack_ind ++;
		ind_stack[stack_ind] = first_child_ind;
			
		stack_ind ++;
		ind_stack[stack_ind] = second_child_ind;
	}
	
	return closest_hit;
}

uniform int max_bounce_count;

uniform vec3 sun_dir;	//which direction do you have to face to see the sun
uniform float sun_strength; //how big and powerful is the sun? owo

uniform float ambient_strength;

vec3 traceRay(Ray ray) {
	vec3 incomingLight = vec3(0);
	vec3 rayColor = vec3(1);

	for(int i = 0; i < max_bounce_count; i++){
		int max_depth = 0;
		HitInfo hit = calculateRayCollision(ray, max_depth);
		//if(i == 0){
		//	incomingLight.r += (1.0 / 20.0) * max_depth;
		//}
		if(hit.didHit) {
			ray.origin = hit.hitPoint;
			
			vec3 diffuseDir = normalize(hit.hitNormal + randomDirection());
			vec3 specularDir = reflect(ray.dir, hit.hitNormal);
			
			Material m = hit.hitMaterial;
			float roughness = m.attr.y;
			float specularProbability = m.attr.z;
			
			bool isSpecularBounce = specularProbability >= randomValue();
			
			ray.dir = mix(diffuseDir, specularDir, (1.0 - roughness) * float(isSpecularBounce));
			
			vec3 emittedLight = m.emissive.xyz * m.emissive.w;
			incomingLight += emittedLight * rayColor;
			if(isSpecularBounce) {
				rayColor *= m.specular.xyz;
			} else {
				rayColor *= m.diffuse.xyz;
			}
		}
		else {
			//sample skybox texture
			vec3 emittedLight = texture(skybox_tex, ray.dir).xyz * ambient_strength;	//skybox 'emits' ambient light
			
			vec3 sunLight = max(0, (dot(ray.dir, sun_dir) - 0.99) * sun_strength) * vec3(1);
			emittedLight += sunLight;
			
			incomingLight += emittedLight * rayColor;
			break;
		}
		
	}	
	
	return incomingLight;
}

uniform int num_rays_per_pixel;
uniform float blur_strength;
uniform float defocus_strength;
uniform float focus_dist;
uniform vec3 camera_right;
uniform vec3 camera_up;
void main() {   
	vec3 traceColor = vec3(0);
	for(int i = 0; i < num_rays_per_pixel; i++) {
		vec3 focusPos = camera_pos + frag_dir * focus_dist; 
		
		vec2 defocusJitter = randomPointInCircle() * defocus_strength / window_width;
		vec3 rayOrigin = camera_pos + camera_right * defocusJitter.x + camera_up * defocusJitter.y;
		
		vec2 blurJitter = randomPointInCircle() * blur_strength / window_width;
		vec3 rayDir = normalize(focusPos - rayOrigin) + camera_right * blurJitter.x + camera_up * blurJitter.y;
		
		Ray fragRay = Ray(rayOrigin, rayDir);
		
		traceColor += traceRay(fragRay);
	}
	traceColor /= num_rays_per_pixel;
	
	vec4 oldColor = texture(render_tex_0, vec2(gl_FragCoord.x / window_width, gl_FragCoord.y / window_height)).xyzw;
	
	vec4 newColor = vec4(traceColor, 1);
	
	float weight = 1.0 / (num_rendered_frames + 1);
	vec4 avg = oldColor * (1.0 - weight) + newColor * weight;
	
	out_tex_0.rgba = avg;
} 

