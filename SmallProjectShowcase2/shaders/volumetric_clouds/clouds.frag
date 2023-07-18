#version 330 core
layout (location = 0) out vec4 color;

uniform vec3 camera_pos;
uniform vec3 cloud_pos;
uniform vec3 cloud_scale;

uniform sampler3D tex_worley_noise;

uniform vec3 sun_dir;	//make sure that this is normalized
uniform vec3 sun_color;

in vec3 frag_dir;

float density_threshold = 0.5;
float density_multiplier = 10;

float scale_main = 50;
float scale_detail = 5;

int nr_samples_main = 30;
int nr_samples_luminance = 10;

float light_absorption_towards_sun = 0.5;
float darkness_threshold = 0.6;

float cloud_light_absorption = 1;

vec4 phase_params = vec4(1, 1, 1, 1);

//returns distance to box, and length intersection. 
//if dstInsideBox = 0, then the ray misses the box. 
vec2 rayBoxDist(vec3 ray_origin, vec3 ray_dir) {
	vec3 pos = cloud_pos;
	vec3 scale = cloud_scale;

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

float sampleCloudDensity(vec3 pos) {
	float ans = 0;
	
	vec3 scaled_pos = vec3(pos.x / 0.8, pos.y / 0.7, pos.z / 1);
	
	//main cloud shape
	ans += texture(tex_worley_noise, scaled_pos / scale_main).x;
	
	//apply density threshold
	ans = max(0, ans - density_threshold);
	
	//apply density multiplier
	ans *= density_multiplier;
	
	return ans;
}

float sampleLuminance(vec3 pos) {
	vec3 dir_to_light = sun_dir;
	float dist_inside_box = rayBoxDist(pos, dir_to_light).y;
	
	float step_size = dist_inside_box / nr_samples_luminance;
	float total_density = 0;
	
	for(int i = 0; i < nr_samples_luminance; i++){
		total_density += sampleCloudDensity(pos) * step_size;
		pos += dir_to_light * step_size;
	}
	
	float transmittance = exp(-total_density * light_absorption_towards_sun);
	return darkness_threshold + transmittance * (1 - darkness_threshold);
}

// Henyey-Greenstein
float hg(float a, float g) {
    float g2 = g*g;
    return (1-g2) / (4*3.1415*pow(1+g2-2*g*(a), 1.5));
}

float phase(float a) {
    float blend = 0.5;
    float hgBlend = hg(a, phase_params.x) * (1 - blend) + hg(a, -phase_params.y) * blend;
    return phase_params.z + hgBlend * phase_params.w;
}

float random (vec2 st) {
    return fract(sin(dot(st.xy,vec2(12.9898,78.233)))*43758.5453123);
}

void main() {	
	//color = vec4(frag_dir, 1);
	vec2 ray_box = rayBoxDist(camera_pos, frag_dir);
	float ray_box_dist = ray_box.x;
	float ray_box_intersect_dist = ray_box.y;
	if(ray_box_intersect_dist == 0){
		discard;
	}
	
	// Phase function makes clouds brighter around sun
    float cos_angle = dot(frag_dir, sun_dir);
    float phase_val = phase(cos_angle);
	
	//float step_size = ray_box_intersect_dist / nr_samples_main;
	float step_size = 0.2;
	float dist_travelled = 0;
	
	float transmittance = 1;
	float light_energy = 0;
	
	while(dist_travelled < ray_box_intersect_dist){
		vec3 pos = camera_pos + frag_dir * (ray_box_dist + dist_travelled);
		float density = sampleCloudDensity(pos);
		
		if(density > 0){
			float luminance = sampleLuminance(pos);
			light_energy += density * step_size * transmittance * luminance * phase_val;
			transmittance *= exp(-density * step_size * cloud_light_absorption);
		}
		
		dist_travelled += step_size;
	}
	
	vec3 cloud_color = light_energy * sun_color;
	color = vec4(cloud_color, 1.0 - transmittance);
} 

