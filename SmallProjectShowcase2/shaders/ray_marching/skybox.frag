#version 330 core
layout (location = 0) out vec4 color;

in vec3 frag_dir;

uniform samplerCube skybox;
uniform vec3 camera_pos;
uniform vec3 light_dir;	//direction towards the light.  

float dist_limit = 4000;
float hit_threshold = 0.001;
float normal_espilon = 0.0001;

float sphereDE(vec3 pos){
	float radius = 10;
	vec3 center = vec3(0, 0, 0);
	pos.x = mod(pos.x, radius * 3) - radius * 1.5;
	pos.y = mod(pos.y, radius * 3) - radius * 1.5;
	pos.z = mod(pos.z, radius * 3) - radius * 1.5;
	vec3 to_center = pos - center;
	return abs(length(to_center) - radius);
}

//sample along the 3 axis to get the change. 
vec3 sphereNorm(vec3 pos) {
	vec3 norm = vec3(0, 0, 0);
	float c_sample = sphereDE(pos);
	float x_sample = sphereDE(vec3(pos.x + normal_espilon, pos.y, pos.z));
	float y_sample = sphereDE(vec3(pos.x, pos.y + normal_espilon, pos.z));
	float z_sample = sphereDE(vec3(pos.x, pos.y, pos.z + normal_espilon));
	norm.x = x_sample - c_sample;
	norm.y = y_sample - c_sample;
	norm.z = z_sample - c_sample;
	return normalize(norm);
}

float calcHitDist(vec3 camera_pos, vec3 frag_dir) {
	vec3 cur_pos = camera_pos;
	float dist_travelled = 0;
	while(dist_travelled < dist_limit) {
		float next_dist = sphereDE(cur_pos);
		cur_pos += frag_dir * next_dist;
		dist_travelled += next_dist;
		if(next_dist < hit_threshold) {
			return dist_travelled;
		}
	}
	return -1;
}

void main() {	
	float hit_dist = calcHitDist(camera_pos, normalize(frag_dir));
	
	if(hit_dist < 0){
		color = vec4(texture(skybox, frag_dir).rgb, 1.0);
	}
	else {
		hit_dist -= hit_threshold;	//backstep hit_pos a bit so we get better normals. 
		vec3 hit_pos = normalize(frag_dir) * hit_dist + camera_pos;
		vec3 hit_normal = sphereNorm(hit_pos);
		
		float ambient = 0.2;
		float diffuse = dot(hit_normal, light_dir);
		
		
		vec3 albedo = vec3(0.8);
		vec3 specular = vec3(1);
		
		color = vec4(albedo * (diffuse * (1 - ambient) + ambient), 1);
	}	
} 

