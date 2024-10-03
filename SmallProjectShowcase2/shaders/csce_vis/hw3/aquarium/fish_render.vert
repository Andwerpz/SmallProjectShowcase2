#version 430 core

struct Fish {
	vec2 pos;
	vec2 vel;
	float facing;
};

layout(binding = 0) buffer fishBuffer {
	Fish[] fishData;
};

out vec3 fish_color;

uniform mat4 pr_matrix;

vec3 lerp(vec3 x0, vec3 x1, float t0, float t1, float t){
	return mix(x0, x1, (t - t0) / (t1 - t0));
}

void main() {
	Fish f = fishData[gl_VertexID];
	gl_Position = pr_matrix * vec4(f.pos, 0, 1);
	
	if(isnan(f.pos.x) || isinf(f.pos.x) || isnan(f.vel.x) || isinf(f.vel.x)) {
		fish_color = vec3(0, 0, 0);
	}
	else {
		fish_color = vec3(1, 0, 0);
	}
}