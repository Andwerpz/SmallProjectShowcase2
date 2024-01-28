#version 440 core
layout (location = 0) out vec4 out_tex_0;

const float PI = 3.14159265;
const float INV_PI = 1.0 / PI;

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
	vec4 attr;	//x = shininess, y = roughness, z = metalness, w = refractive index
};

struct HitInfo {
	bool didHit;
	float dist;
	vec3 hitPoint;
	vec3 hitNormal;
	Material hitMaterial;
	bool is_internal;
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

vec2 randomPointInCircle() {
	float angle = randomValue() * 2 * 3.1415926;
	vec2 pointOnCircle = vec2(cos(angle), sin(angle));
	pointOnCircle *= sqrt(randomValue());
	return pointOnCircle;
}

float lengthSq(vec3 v) {
	return v.x * v.x + v.y * v.y + v.z * v.z;
}

Material createMaterial() {
	return Material(vec4(0), vec4(0), vec4(0), vec4(0));
}

BoundingBox createBoundingBox() {
	return BoundingBox(vec3(0), vec3(0));
}

HitInfo createHitInfo() {
	return HitInfo(false, 0, vec3(0), vec3(0), createMaterial(), false);
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
	mat.attr.a = materialData[materialOffset ++];
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
	float b = 2.0 * dot(offsetRayOrigin, ray.dir);
	float c = dot(offsetRayOrigin, offsetRayOrigin) - sphereRadius * sphereRadius;
	
	float d = b * b - 4.0 * a * c;
	
	if(d >= 0){
		float dist = (-b - sqrt(d)) / (2.0 * a);
		
		if(dist > 0.0001){
			//we must be outside the sphere
			
			ret.didHit = true;
			ret.dist = dist;
			ret.hitPoint = ray.origin + ray.dir * dist;
			ret.hitNormal = normalize(ret.hitPoint - sphereCenter);
			ret.hitMaterial = sphere.material;
		}
		else {
			//we are inside the sphere
			dist = (-b + sqrt(d)) / (2.0 * a);
			
			if(dist > 0.0001) {
				ret.is_internal = true;
				ret.didHit = true;
				ret.dist = dist;
				ret.hitPoint = ray.origin + ray.dir * dist;
				ret.hitNormal = -normalize(ret.hitPoint - sphereCenter);
				ret.hitMaterial = sphere.material;
			}
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
		//ray dir and plane normal are facing in the same direction, so it must be an internal hit
		ret.is_internal = true;
		
		//reverse plane normal so that the rest of calculations work
		t0 = triangle.a.xyz;
		t1 = triangle.c.xyz;
		t2 = triangle.b.xyz;
		
		d0 = normalize(t1 - t0);
		d1 = normalize(t2 - t1);
		d2 = normalize(t0 - t2);
		
		plane_origin = t0;
		plane_normal = normalize(cross(d0, d1));
	}
	
	//see if ray origin is already past the plane
	if(dot(plane_normal, ray.origin - plane_origin) < 0.0001) {
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

//v is relative to normal, and normal is relative to world. Transform v such that is is relative to world. 
vec3 transformToWorld(vec3 a, vec3 normal) {
	//find axis that is not parallel to normal
	vec3 majorAxis;
	if(abs(normal.x) < 0.57735026919) {	//1 / sqrt(3)
		majorAxis = vec3(1, 0, 0);
	}	
	else if(abs(normal.y) < 0.57735026919) {
		majorAxis = vec3(0, 1, 0);
	}
	else {
		majorAxis = vec3(0, 0, 1);
	}	
	
	//use major axis to create coordinate system relative to world space
	vec3 u = normalize(cross(normal, majorAxis));
	vec3 v = cross(normal, u);
	vec3 w = normal;
	
	//transform from local to world coordinates
	return a.x * u + a.y * v + a.z * w;
}

float lambertBRDF() {
	return 1.0 / (2.0 * PI);
}

//vectors returned are more likely to be facing in the direction of the normal.
//lambertian diffuse assumes that any light incident on a point will be reflected equally in all directions. 
vec3 lambertBRDF_Dir(vec3 normal) {
	float rand = randomValue();
	float r = sqrt(rand);
	float theta = randomValue() * 2.0 * PI;
	
	float x = r * cos(theta);
	float y = r * sin(theta);
	float z = sqrt(1.0 - x * x - y * y);
	
	return normalize(transformToWorld(vec3(x, y, z), normal));
}

float lambertBRDF_PDF(vec3 normal, vec3 in_dir) {
	return dot(in_dir, normal) * INV_PI;
}

//n1 is the material we're currently in, n2 is the one we're going into
float fresnelDielectric(vec3 incident, vec3 normal, float n1, float n2) {
	float cos_theta_i = dot(incident, normal);
	float n = n2 / n1;
	
	//incident and normal are facing opposite directions, reverse orientation of normal. 
	if(cos_theta_i < 0){
		normal *= -1;
		cos_theta_i = dot(incident, normal);
		n = n1 / n2;
	}
	
	//compute cos_theta_t
	float sin2_theta_i = max(0.0, 1.0 - cos_theta_i * cos_theta_i);
	float sin2_theta_t = sin2_theta_i / (n * n);
	if(sin2_theta_t >= 1.0){	//handle total internal reflection
		return 1.0;
	}
	float cos_theta_t = sqrt(max(0.0, 1.0 - sin2_theta_t));
	
	//compute ans
	float r_parl = (n * cos_theta_i - cos_theta_t) / (n * cos_theta_i + cos_theta_t);
    float r_perp = (cos_theta_i - n * cos_theta_t) / (cos_theta_i + n * cos_theta_t);
    return (r_parl * r_parl + r_perp * r_perp) / 2.0;
}

//ior is now defined as n - ik; n is our index of refraction, and k is called the absorption coefficient. 
//depending on the wavelength, n and k can change drastically, and this is what gives conductors their different colors. 
//float fresnelMetallic(vec3 incident, vec3 normal, float n1, float n2) {}

//assume that the material we come from has ior of 1
//for now, just use dielectric fresnel for metallics
float fresnelOpaque(vec3 incident, vec3 normal, float metalness) {
	//F0 is amount of 0 angle reflectance. 
	//non-metallic surfaces look good with F0 at 0.04, if surface is metallic, we can raise it. 
	//F0 = 0.04 corresponds with ior = 1.5, porcelain has an ior of 1.504
	//as F0 tends towards 1.0, ior goes to infinity
	float F0 = mix(0.04, 0.99, metalness);   
	float n2 = (1.0 + sqrt(F0)) / (1.0 - sqrt(F0));	//index of refraction
	return fresnelDielectric(incident, normal, 1.0, n2);
}

//returns true if can transmit
bool refract(vec3 incident, vec3 normal, float n1, float n2, inout vec3 ans) {
	float cos_theta_i = dot(normal, incident);
	float n = n2 / n1;
	
	//potentially flip orientation for snells law
	if(cos_theta_i < 0){
		n = n1 / n2;
		cos_theta_i = -cos_theta_i;
		normal *= -1;
	}
	
	//compute cos_theta_t
	float sin2_theta_i = max(0.0, 1.0 - cos_theta_i * cos_theta_i);
	float sin2_theta_t = sin2_theta_i / (n * n);
	
	//handle total internal reflection
	if(sin2_theta_t >= 1.0){
		return false;
	}
	float cos_theta_t = sqrt(max(0.0, 1.0 - sin2_theta_t));
	
	ans = -incident / n + (dot(incident, normal) / n - cos_theta_t) * normal;
	return true;
}

float geometry1Beckmann(vec3 v, vec3 half_dir, vec3 normal, float roughness) {
	float theta_v = acos(dot(v, half_dir));
	float a = 1.0 / (roughness * tan(theta_v));
	
	float ans = 1.0;
	if(dot(v, half_dir) / dot(v, normal) <= 0) {
		ans = 0;
	}
	if(a < 1.6) {
		ans *= (3.535 * a + 2.181 * a * a) / (1.0 + 2.276 * a + 2.577 * a * a);
	}
	
	return ans;
}

float geometrySmith(vec3 in_dir, vec3 out_dir, vec3 half_dir, vec3 normal, float roughness) {
	return geometry1Beckmann(in_dir, half_dir, normal, roughness) * geometry1Beckmann(out_dir, half_dir, normal, roughness);
}

void geometrySchlickGGX_Cancel(float NdotV, float roughness, inout float num, inout float denom) {
    float r = (roughness + 1.0);
    float k = (r * r) / 8.0;
    num = NdotV;
    denom = NdotV * (1.0 - k) + k;
}

void geometrySmithGGX_Cancel(vec3 in_dir, vec3 out_dir, vec3 normal, float roughness, inout float num1, inout float num2, inout float denom1, inout float denom2) {
    float NdotV = max(dot(normal, out_dir), 0.0);
    float NdotL = max(dot(normal, in_dir), 0.0);
  	geometrySchlickGGX_Cancel(NdotV, roughness, num1, denom1);
    geometrySchlickGGX_Cancel(NdotL, roughness, num2, denom2);
}

float geometrySchlickGGX(float NdotV, float roughness) {
    float r = (roughness + 1.0);
    float k = (r*r) / 8.0;

    float num   = NdotV;
    float denom = NdotV * (1.0 - k) + k;
	
    return num / denom;
}

float geometrySmithGGX(vec3 in_dir, vec3 out_dir, vec3 normal, float roughness) {
    float NdotV = max(dot(normal, out_dir), 0.0);
    float NdotL = max(dot(normal, in_dir), 0.0);
    float ggx2  = geometrySchlickGGX(NdotV, roughness);
    float ggx1  = geometrySchlickGGX(NdotL, roughness);
    return ggx1 * ggx2;
}

void distribution(vec3 half_dir, vec3 normal, float roughness, inout float num, inout float denom1, inout float denom2) {
	if(roughness == 0.0){
		num = 1.0;
		denom1 = 1.0;
		denom2 = 1.0;
		return;
	}

	float cos_theta_m = dot(half_dir, normal);
	float tan_theta_m = tan(acos(cos_theta_m));
	
	num = 1.0;
	denom1 = exp(pow(tan_theta_m, 2.0) / pow(roughness, 2.0));
	denom2 = (PI * pow(roughness, 2.0) * pow(cos_theta_m, 4.0));
	
	if(cos_theta_m <= 0){
		num = 0.0;
		denom1 = 1.0;
		denom2 = 1.0;
	}
}

vec3 cookTorranceBRDF_Dir(vec3 out_dir, vec3 normal, float roughness, float metalness, inout vec3 half_dir) {
	//cook torrance importance sampling
	float r1 = randomValue();
	float r2 = randomValue();
	float phi = atan(sqrt(-(roughness * roughness) * log(1.0 - r1)));
	float theta = 2.0 * PI * r2;
	
	half_dir = vec3(sin(phi) * cos(theta), sin(phi) * sin(theta), cos(phi));
	half_dir = normalize(transformToWorld(half_dir, normal));
	vec3 in_dir = 2.0 * dot(half_dir, out_dir) * half_dir - out_dir;
	
	return in_dir;
}

void cookTorranceBRDF_PDF(vec3 in_dir, vec3 out_dir, vec3 normal, float roughness, inout float num, inout float denom) {
	float ans = 1.0;
	
	//cook torrance PDF
	vec3 half_dir = normal;	//halfway vector
	if(lengthSq(in_dir + out_dir) != 0){
		half_dir = normalize(in_dir + out_dir);
	}
	
	float D_num, D_denom1, D_denom2;
	distribution(half_dir, normal, roughness, D_num, D_denom1, D_denom2);
	
	num = dot(half_dir, normal);
	denom = D_denom1 * D_denom2 * 4.0 * dot(in_dir, half_dir);
}

//cook-torrance with importance sampling
//TODO
// - read theory and understand what's going on with my BSDF
//   - especially how does light interact with conductors. Fresnel with conductors is weird D:
// - circular artifacts on specular reflections when surface is extremely smooth. 
// - wavelength dependent IOR
//   - hero sampling? https://dl.acm.org/doi/10.1111/cgf.12419
vec3 traceRay2(Ray ray) {
	vec3 outputColor = vec3(0);
	vec3 throughput = vec3(1);
	float prev_refractive_index = 1.0;
	
	for(int i = 0; i < max_bounce_count; i++){
		int max_depth = 0;
		HitInfo hit = calculateRayCollision(ray, max_depth);
		if(!hit.didHit) {
			//sample skybox
			vec3 emittedLight = texture(skybox_tex, ray.dir).xyz * ambient_strength;	//skybox 'emits' ambient light
			
			vec3 sunLight = max(0, (dot(ray.dir, sun_dir) - 0.99) * sun_strength) * vec3(1);
			emittedLight += sunLight;
			
			outputColor += emittedLight * throughput;
			break;
		}
		
		//otherwise, we hit some object
		Material m = hit.hitMaterial;
		float roughness = max(0.001, m.attr.y);	//to prevent divide by 0
		float metalness = m.attr.z;
		float next_refractive_index = m.attr.w;
		
		//for now, assume that transmissive materials can't contain each other. 
		//later, we should check what material the ray is exiting into
		if(hit.is_internal) {
			next_refractive_index = 1.0;
		}
		
		vec3 normal = hit.hitNormal;
		vec3 half_dir = vec3(0);	//which way is the microfacet facing?
		vec3 out_dir = -ray.dir;
		vec3 in_dir = cookTorranceBRDF_Dir(out_dir, normal, roughness, metalness, half_dir);
		
		//send emitted light back to camera
		vec3 emittedLight = m.emissive.xyz * m.emissive.w;
		outputColor += emittedLight * throughput;
		
		float B = 0.0;
		float P = 1.0;
		
		if(next_refractive_index < 1.0) {
			//material is opaque
			float F = fresnelOpaque(out_dir, half_dir, metalness);
			
			if(randomValue() < F) {
				//do a specular bounce
				throughput *= m.specular.xyz;
				
				float D_num, D_denom1, D_denom2;
				float G_num1, G_num2, G_denom1, G_denom2;
				float P_num, P_denom;
				
				geometrySmithGGX_Cancel(in_dir, out_dir, normal, roughness, G_num1, G_num2, G_denom1, G_denom2);
				distribution(half_dir, normal, roughness, D_num, D_denom1, D_denom2);
				cookTorranceBRDF_PDF(in_dir, out_dir, normal, roughness, P_num, P_denom);
				
				float NdotI = max(dot(normal, in_dir), 0.0);
				float NdotO = max(dot(normal, out_dir), 0.0);
				
				if(NdotI > 0 && NdotO > 0) {
					//B = G * D / (4.0 * dot(in_dir, normal) * dot(out_dir, normal));
					
					float num = D_num;
					float denom = (4.0) * D_denom2 * D_denom1 * (G_denom1 * G_denom2);
					
					B = num / denom;
					P = P_num / P_denom;
					
					if(isinf(B) || isnan(B)) {
						B = 0;
					}
				}
			}
			else {
				//do a diffuse bounce
				throughput *= m.diffuse.xyz * (1 - metalness);
				
				in_dir = lambertBRDF_Dir(normal);
				B = lambertBRDF();
				P = max(0.0001, lambertBRDF_PDF(normal, in_dir));
			}
			
			float mult = B / P;
			throughput *= dot(normal, in_dir) * mult;
			
			ray.dir = in_dir;
			ray.origin = hit.hitPoint;
		}
		else {
			//material is transmissive
			float F = fresnelDielectric(out_dir, half_dir, prev_refractive_index, next_refractive_index);
			
			vec3 transmit_dir;
			bool can_transmit = refract(out_dir, half_dir, prev_refractive_index, next_refractive_index, transmit_dir);
			
			if(!can_transmit || randomValue() < F) {
				
				//do a specular bounce
				throughput *= m.specular.xyz;
				
				float D_num, D_denom1, D_denom2;
				float G_num1, G_num2, G_denom1, G_denom2;
				float P_num, P_denom;
				
				geometrySmithGGX_Cancel(in_dir, out_dir, normal, roughness, G_num1, G_num2, G_denom1, G_denom2);
				distribution(half_dir, normal, roughness, D_num, D_denom1, D_denom2);
				cookTorranceBRDF_PDF(in_dir, out_dir, normal, roughness, P_num, P_denom);
				
				float NdotI = max(dot(normal, in_dir), 0.0);
				float NdotO = max(dot(normal, out_dir), 0.0);
				
				if(NdotI > 0 && NdotO > 0) {
					//B = G * D / (4.0 * dot(in_dir, normal) * dot(out_dir, normal));
					
					float num = D_num;
					float denom = (4.0) * D_denom2 * D_denom1 * (G_denom1 * G_denom2);
					
					B = num / denom;
					P = P_num / P_denom;
					
					if(isinf(B) || isnan(B)) {
						B = 0;
					}
				}
				
				float mult = B / P;
				throughput *= dot(normal, in_dir) * mult;
				
				ray.dir = in_dir;
				ray.origin = hit.hitPoint;
			}
			else {
				//transmit light
				//TODO : make sure this is correct
				
				//note that fresnel is already accounted for here
				throughput *= 1.0f;
				
				float P_num, P_denom;
				cookTorranceBRDF_PDF(in_dir, out_dir, normal, roughness, P_num, P_denom);
				
				B = 1.0;
				P = P_num / P_denom;
				
				//TODO : why don't we multiply throughput by P or dot(transmit_dir, normal)?
				//       is it because no energy is lost when transmitting, and all i'm doing is just changing the direction?				
				ray.dir = transmit_dir;
				ray.origin = hit.hitPoint;
				prev_refractive_index = next_refractive_index;
			}
		}
	}
	
	return outputColor;
}

//lambertian diffuse with importance sampling
vec3 traceRay(Ray ray) {
	vec3 outputColor = vec3(0);
	vec3 throughput = vec3(1);
	
	for(int i = 0; i < max_bounce_count; i++){
		int max_depth = 0;
		HitInfo hit = calculateRayCollision(ray, max_depth);
		if(!hit.didHit) {
			//sample skybox
			vec3 emittedLight = texture(skybox_tex, ray.dir).xyz * ambient_strength;	//skybox 'emits' ambient light
			
			vec3 sunLight = max(0, (dot(ray.dir, sun_dir) - 0.99) * sun_strength) * vec3(1);
			emittedLight += sunLight;
			
			outputColor += emittedLight * throughput;
			break;
		}
		
		//otherwise, we hit some object
		Material m = hit.hitMaterial;
		float roughness = m.attr.y;
		
		vec3 emittedLight = m.emissive.xyz * m.emissive.w;
		outputColor += emittedLight * throughput;
		throughput *= m.diffuse.xyz;
		
		vec3 in_dir = lambertBRDF_Dir(hit.hitNormal);
		
		float F = lambertBRDF();
		float P = max(0.00001, lambertBRDF_PDF(hit.hitNormal, in_dir));
		throughput *= dot(hit.hitNormal, in_dir) * (F / P);
		
		ray.dir = in_dir;
		ray.origin = hit.hitPoint;
	}
	
	return outputColor;
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
		vec3 rayColor = traceRay2(fragRay);
		
		if(isnan(rayColor.x) || isinf(rayColor.x)) {
			continue;
		}
		
		traceColor += rayColor;
	}
	traceColor /= num_rays_per_pixel;
	
	vec4 oldColor = texture(render_tex_0, vec2(gl_FragCoord.x / window_width, gl_FragCoord.y / window_height)).xyzw;
	vec4 newColor = vec4(traceColor, 1);
	
	float weight = 1.0 / (num_rendered_frames + 1.0);
	vec4 avg = oldColor * (1.0 - weight) + newColor * weight;
	
	out_tex_0.rgba = avg;
} 

