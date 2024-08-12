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

uniform vec3 black_hole_pos;
uniform float black_hole_mass;

vec3 traceRay(Ray ray) {
	
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
		vec3 rayColor = traceRay(fragRay);
		
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

