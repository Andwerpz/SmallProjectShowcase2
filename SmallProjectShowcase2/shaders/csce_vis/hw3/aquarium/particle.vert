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

uniform mat4 pr_matrix;

void main() {
	Particle p = particleData[gl_VertexID];
   	gl_Position = pr_matrix * vec4(p.pos, 0, 1);
}