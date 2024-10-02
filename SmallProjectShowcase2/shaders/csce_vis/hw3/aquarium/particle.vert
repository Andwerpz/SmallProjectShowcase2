#version 430 core

struct Particle {
	vec2 pos;
	vec2 pred_pos;
	vec2 vel;
	int hash;
};

layout(binding = 0) buffer particleBuffer {
	Particle[] particleData;
};

out vec3 particle_color;

uniform mat4 pr_matrix;

vec3 lerp(vec3 x0, vec3 x1, float t0, float t1, float t){
	return mix(x0, x1, (t - t0) / (t1 - t0));
}

void main() {
	Particle p = particleData[gl_VertexID];
	
	vec3 slow_color = vec3(0, 0, 1);
	vec3 fast_color = vec3(1, 0, 0);
	
	float slow_speed = 0;
	float fast_speed = 20;
	
	float vel = length(p.vel);
	particle_color = lerp(slow_color, fast_color, slow_speed, fast_speed, clamp(slow_speed, fast_speed, vel));
	
   	gl_Position = pr_matrix * vec4(p.pos, 0, 1);
}