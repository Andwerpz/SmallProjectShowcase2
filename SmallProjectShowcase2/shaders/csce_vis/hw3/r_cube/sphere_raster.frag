#version 430 core
layout (location = 0) out vec4 out_color;
layout (location = 1) out vec4 out_pos;

layout(depth_greater) out float gl_FragDepth;

uniform mat4 pr_matrix;
uniform mat4 vw_matrix;

uniform float smoothing_radius;
uniform vec3 view_pos;

noperspective in vec3 sphere_center;
in vec3 frag_pos;

//only care about collisions with 'exterior' of sphere
bool raySphere(vec3 ray_origin, vec3 ray_dir, vec3 sphere_pos, float sphere_radius, inout vec3 ans) {
	vec3 off_ray_origin = ray_origin - sphere_pos;
	
	float a = dot(ray_dir, ray_dir);
	float b = 2.0 * dot(off_ray_origin, ray_dir);
	float c = dot(off_ray_origin, off_ray_origin) - sphere_radius * sphere_radius;
	
	float d = b * b - 4.0 * a * c;
	float dist = (-b - sqrt(abs(d))) / (2.0 * a);
	ans = ray_origin + ray_dir * dist;
	return d >= 0 && dist >= 0;
}

void main() {
    vec3 ray_dir = normalize(frag_pos - view_pos);
    vec3 hit_pos = vec3(0);
    bool did_hit = raySphere(view_pos, ray_dir, sphere_center, smoothing_radius, hit_pos);
    if(!did_hit){
    	discard;
    }
    
    //vec3 normal = normalize(hit_pos - sphere_center);
    out_pos = vec4(hit_pos, 1.0);
    
    //compute depth
    vec4 proj_pos = pr_matrix * vw_matrix * vec4(hit_pos, 1.0);
    float depth = proj_pos.z / proj_pos.w;
    depth = depth * 0.5 + 0.5;
    gl_FragDepth = depth;
} 

