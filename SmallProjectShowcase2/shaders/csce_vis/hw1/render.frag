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

struct HitInfo {
	bool didHit;
	float dist;
	vec3 hitPoint;
	vec3 hitNormal;
	vec3 hitMaterial;
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

HitInfo createHitInfo() {
	return HitInfo(false, 0, vec3(0), vec3(0), vec3(0), false);
}

Sphere createSphere() {
	return Sphere(vec3(0), 0, mat4(0));
}

Triangle createTriangle() {
	return Triangle(vec3(0), vec3(0), vec3(0));
}

vec3 sampleSphereMaterial(Sphere sphere, vec3 hit_pos) {
	hit_pos -= sphere.center;
	hit_pos = (vec4(hit_pos, 0) * sphere.orient).xyz;
	int cond = (hit_pos.x > 0? 1 : 0) ^ (hit_pos.y > 0? 1 : 0) ^ (hit_pos.z > 0? 1 : 0);
	return cond == 1 && render_sphere_texture? vec3(0.5) : vec3(1);
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
	ret.hitMaterial = vec3(1);

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

void main() {	
	vec3 frag_dir = normalize(in_frag_dir);
	
	vec3 light_pos = vec3(0, box_size * 0.8, 0);
	
	Ray camera_ray = Ray(camera_pos, frag_dir);
	HitInfo hit = calcRayCollision(camera_ray);
	
	vec3 light_dir = normalize(light_pos - hit.hitPoint);
	float diffuse = dot(light_dir, hit.hitNormal);
	if(isShadowed(hit.hitPoint, light_pos)) {
		diffuse = min(diffuse, 0);
	}	
	diffuse = diffuse / 2.0 + 0.5;
	
	float ambient = 0.2;
	float total = diffuse * (1.0 - ambient) + ambient;
	
	color = vec4(hit.hitMaterial * total, 1);
} 

