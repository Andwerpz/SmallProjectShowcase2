#version 330 core
layout (location = 0) out vec4 color;

uniform vec3 camera_pos;
uniform vec3 cloud_pos;
uniform vec3 cloud_scale;

in vec3 frag_dir;

//returns distance to box, and length intersection. 
//if dstInsideBox = 0, then the ray misses the box. 
vec2 rayBoxDist(vec3 pos, vec3 scale, vec3 ray_origin, vec3 ray_dir) {
	vec3 bmin = vec3(pos.x - scale.x / 2, pos.y - scale.y / 2, pos.z - scale.z / 2);
	vec3 bmax = vec3(pos.x + scale.x / 2, pos.y + scale.y / 2, pos.z + scale.z / 2);
	
	vec3 t0 = (bmin - ray_origin) / ray_dir;
	vec3 t1 = (bmax - ray_origin) / ray_dir;
	vec3 tmin = min(t0, t1);
	vec3 tmax = max(t0, t1);
	
	float dstA = max(max(tmin.x, tmin.y), tmin.z);
	float dstB = min(min(tmax.x, tmax.y), tmax.z);
	
	float dstToBox = max(0, dstA);
	float dstInsideBox = max(0, dstB - dstToBox);
	return vec2(dstToBox, dstInsideBox);
}

void main() {	
	//color = vec4(frag_dir, 1);
	vec2 ray_box = rayBoxDist(cloud_pos, cloud_scale, camera_pos, frag_dir);
	if(ray_box.y != 0){
		color = vec4(0, 0, 0, 1);
	}
} 

