#version 440 core
layout (location = 0) out vec4 color;

in vec3 in_frag_dir;
uniform vec3 camera_pos;
uniform float box_size;
uniform bool render_sphere_texture;

uniform int sphere_buf_sz;
layout (binding = 1) readonly buffer sphereBuffer {
	float sphereData[];
};

uniform int tri_buf_sz;
layout (binding = 2) readonly buffer triangleBuffer {
	float triangleData[];
};

struct Ray {
	vec3 origin;
	vec3 dir;
};

struct Material {
	vec3 color;
	vec3 specular;
	bool is_reflective;	//standin for roughness
	float metalness;
	float ior;	
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
	mat4 orient;
};

struct Triangle {
	vec3 a;
	vec3 b;
	vec3 c;
};

Material createMaterial() {
	return Material(vec3(0), vec3(1), false, 0, 1.5);
}

Material createMaterial(vec3 color) {
	return Material(color, vec3(1), false, 0, 1.5);
}

HitInfo createHitInfo() {
	return HitInfo(false, 0, vec3(0), vec3(0), createMaterial(), false);
}

Sphere createSphere() {
	return Sphere(vec3(0), 0, mat4(0));
}

Triangle createTriangle() {
	return Triangle(vec3(0), vec3(0), vec3(0));
}

Material sampleSphereMaterial(Sphere sphere, vec3 hit_pos) {
	Material white_mat = Material(vec3(1), vec3(1), false, 0, 1.5);
	Material gold_mat = Material(vec3(0.9), vec3(0.9), true, 0.75, 1.5);

	hit_pos -= sphere.center;
	hit_pos = (vec4(hit_pos, 0) * sphere.orient).xyz;
	int cond = (hit_pos.x > 0? 1 : 0) ^ (hit_pos.y > 0? 1 : 0) ^ (hit_pos.z > 0? 1 : 0);
	return cond == 1 && render_sphere_texture? gold_mat : white_mat;
}

Sphere readSphere(inout int offset) {
	Sphere s = createSphere();
	s.center.x = sphereData[offset ++];
	s.center.y = sphereData[offset ++];
	s.center.z = sphereData[offset ++];
	s.radius = sphereData[offset ++];
	s.orient[0].x = sphereData[offset ++];
	s.orient[1].x = sphereData[offset ++];
	s.orient[2].x = sphereData[offset ++];
	s.orient[3].x = sphereData[offset ++];
	s.orient[0].y = sphereData[offset ++];
	s.orient[1].y = sphereData[offset ++];
	s.orient[2].y = sphereData[offset ++];
	s.orient[3].y = sphereData[offset ++];
	s.orient[0].z = sphereData[offset ++];
	s.orient[1].z = sphereData[offset ++];
	s.orient[2].z = sphereData[offset ++];
	s.orient[3].z = sphereData[offset ++];
	s.orient[0].w = sphereData[offset ++];
	s.orient[1].w = sphereData[offset ++];
	s.orient[2].w = sphereData[offset ++];
	s.orient[3].w = sphereData[offset ++];
	return s;
}

Triangle readTriangle(inout int offset) {
	Triangle t = createTriangle();
	t.a.x = triangleData[offset ++];
	t.a.y = triangleData[offset ++];
	t.a.z = triangleData[offset ++];
	t.b.x = triangleData[offset ++];
	t.b.y = triangleData[offset ++];
	t.b.z = triangleData[offset ++];
	t.c.x = triangleData[offset ++];
	t.c.y = triangleData[offset ++];
	t.c.z = triangleData[offset ++];
	return t;
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
			ret.hitMaterial = sampleSphereMaterial(sphere, ret.hitPoint);
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
				ret.hitMaterial = sampleSphereMaterial(sphere, ret.hitPoint);
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
	ret.hitMaterial = createMaterial(vec3(1));

	return ret;
}

HitInfo calcRayCollision(Ray ray) {
	HitInfo ans = createHitInfo();
	ans.dist = 1e9;
	
	int sphere_buf_ptr = 0;
	while(sphere_buf_ptr != sphere_buf_sz) {
		Sphere s = readSphere(sphere_buf_ptr);
		HitInfo hit = raySphere(ray, s);
		if(hit.didHit && hit.dist < ans.dist) {
			ans = hit;
		}
	}
	
	int tri_buf_ptr = 0;
	while(tri_buf_ptr != tri_buf_sz) {
		Triangle t = readTriangle(tri_buf_ptr);
		HitInfo hit = rayTriangle(ray, t);
		if(!hit.is_internal && hit.didHit && hit.dist < ans.dist) {
			ans = hit;
		}
	}
	
	return ans;
}

//only spheres can cast shadows
bool isShadowed(vec3 pos, vec3 light_pos) {
	vec3 to_light = light_pos - pos;
	vec3 light_dir = normalize(to_light);
	
	Ray shadow_ray = Ray(pos + light_dir * 0.01, light_dir);
	HitInfo closest_hit = createHitInfo();
	closest_hit.dist = 1e9;
	
	int sphere_buf_ptr = 0;
	while(sphere_buf_ptr != sphere_buf_sz) {
		Sphere s = readSphere(sphere_buf_ptr);
		HitInfo hit = raySphere(shadow_ray, s);
		if(hit.didHit && hit.dist < closest_hit.dist) {
			closest_hit = hit;
		}
	}
	
	return closest_hit.dist * closest_hit.dist < dot(to_light, to_light);
}

//n1 is ior of current material, n2 is incident material
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

float fresnel(vec3 incident, vec3 normal, float metalness) {
	//F0 is amount of 0 angle reflectance. 
	//non-metallic surfaces look good with F0 at 0.04, if surface is metallic, we can raise it. 
	//F0 = 0.04 corresponds with ior = 1.5, porcelain has an ior of 1.504
	//as F0 tends towards 1.0, ior goes to infinity
	float F0 = mix(0.04, 0.99, metalness);   
	float n2 = (1.0 + sqrt(F0)) / (1.0 - sqrt(F0));	//index of refraction
	return fresnelDielectric(incident, normal, 1.0, n2);
}

const int max_bounces = 2;
vec3 calcColor(Ray ray, vec3 light_pos) {
	vec3 ans = vec3(0);
	vec3 throughput = vec3(1);
	
	for(int i = 0; i < max_bounces; i++){
		HitInfo hit = calcRayCollision(ray);
		
		if(!hit.didHit) {
			break;
		}
		
		Material hit_mat = hit.hitMaterial;
		float metalness = hit_mat.metalness;
		float ior = hit_mat.ior;
		
		vec3 norm_dir = hit.hitNormal;
		vec3 out_dir = -ray.dir;
		vec3 in_dir = reflect(ray.dir, norm_dir);
		
		//for now, this just represents the probability of a specular bounce
		float F = fresnel(out_dir, norm_dir, metalness);
		if(!hit_mat.is_reflective || i == max_bounces - 1) {
			F = 0;
		}
		
		//'diffuse' bounce with probability 1 - F
		//just directly go to the light source
		{
			vec3 light_dir = normalize(light_pos - hit.hitPoint);
			float diffuse = dot(light_dir, hit.hitNormal);
			if(isShadowed(hit.hitPoint, light_pos)) {
				diffuse = min(diffuse, 0);
			}
			diffuse = diffuse / 2.0 + 0.5;
			float ambient = 0.2;
			float total = diffuse * (1.0 - ambient) + ambient;
			
			ans += throughput * (1.0 - F) * hit_mat.color * total;
		}
		
		//specular bounce with probability F
		//update throughput
		throughput *= hit_mat.specular * F;
		
		ray = Ray(hit.hitPoint + in_dir * 0.001, in_dir);
	}
	
	return ans;
}

void main() {	
	vec3 frag_dir = normalize(in_frag_dir);
	
	vec3 light_pos = vec3(0, box_size * 0.8, 0);
	
	Ray camera_ray = Ray(camera_pos, frag_dir);
	
	vec3 out_color = calcColor(camera_ray, light_pos);
	color = vec4(out_color, 1);
	
	/*
	HitInfo hit = calcRayCollision(camera_ray);
	
	vec3 light_dir = normalize(light_pos - hit.hitPoint);
	float diffuse = dot(light_dir, hit.hitNormal);
	if(isShadowed(hit.hitPoint, light_pos)) {
		diffuse = min(diffuse, 0);
	}	
	diffuse = diffuse / 2.0 + 0.5;
	
	float ambient = 0.2;
	float total = diffuse * (1.0 - ambient) + ambient;
	
	color = vec4(hit.hitMaterial.color * total, 1);
	*/
} 

