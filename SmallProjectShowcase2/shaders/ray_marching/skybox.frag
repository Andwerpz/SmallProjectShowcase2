#version 330 core
layout (location = 0) out vec4 color;

in vec3 in_frag_dir;

uniform samplerCube skybox;
uniform vec3 camera_pos;
uniform vec3 light_dir;	//direction towards the light.  

float dist_limit = 400;
float hit_threshold = 0.001;
float normal_espilon = 0.0001;

float Power = 8;
float Bailout = 200;
int Iterations = 100;

float DE(vec3 pos){
	vec3 z = pos;
	float dr = 1.0;
	float r = 0.0;
	for (int i = 0; i < Iterations ; i++) {
		r = length(z);
		if (r > Bailout) break;
		
		// convert to polar coordinates
		float theta = acos(z.z/r);
		float phi = atan(z.y,z.x);
		dr =  pow( r, Power-1.0)*Power*dr + 1.0;
		
		// scale and rotate the point
		float zr = pow( r,Power);
		theta = theta*Power;
		phi = phi*Power;
		
		// convert back to cartesian coordinates
		z = zr*vec3(sin(theta)*cos(phi), sin(phi)*sin(theta), cos(theta));
		z+=pos;
	}
	return 0.5*log(r)*r/dr;
}

//sample along the 3 axis to get the change. 
vec3 calcNorm(vec3 pos) {
	vec3 norm = vec3(0, 0, 0);
	float c_sample = DE(pos);
	float x_sample = DE(vec3(pos.x + normal_espilon, pos.y, pos.z));
	float y_sample = DE(vec3(pos.x, pos.y + normal_espilon, pos.z));
	float z_sample = DE(vec3(pos.x, pos.y, pos.z + normal_espilon));
	norm.x = x_sample - c_sample;
	norm.y = y_sample - c_sample;
	norm.z = z_sample - c_sample;
	return normalize(norm);
}

float calcHitDist(vec3 camera_pos, vec3 frag_dir) {
	vec3 cur_pos = camera_pos;
	float dist_travelled = 0;
	while(dist_travelled < dist_limit) {
		float next_dist = DE(cur_pos);
		cur_pos += frag_dir * next_dist;
		dist_travelled += next_dist;
		if(next_dist < hit_threshold) {
			return dist_travelled;
		}
	}
	return -1;
}

void main() {	
	vec3 frag_dir = normalize(in_frag_dir);
	float hit_dist = calcHitDist(camera_pos, frag_dir);
	
	if(hit_dist < 0){
		color = vec4(texture(skybox, in_frag_dir).rgb, 1.0);
	}
	else {
		hit_dist -= hit_threshold;	//backstep hit_pos a bit so we get better normals. 
		vec3 hit_pos = frag_dir * hit_dist + camera_pos;
		vec3 hit_normal = calcNorm(hit_pos);
		
		float ambient = 0.2;
		float diffuse = dot(hit_normal, light_dir);
		
		vec3 reflect_dir = frag_dir - 2 * (dot(frag_dir, hit_normal) * hit_normal);
		float fresnel = pow(1.0 - dot(reflect_dir, hit_normal), 5);
		vec3 reflect_color = texture(skybox, reflect_dir).rgb;
		
		float specular = fresnel * dot(reflect_dir, light_dir);
		
		vec3 diffuse_color = vec3(0.8);
		vec3 specular_color = vec3(1);
		
		color = vec4(diffuse_color * (diffuse * (1 - ambient) + ambient), 1);
		//color.rgb += specular_color * specular;
		//color.rgb += reflect_color * fresnel;
	}	
} 

