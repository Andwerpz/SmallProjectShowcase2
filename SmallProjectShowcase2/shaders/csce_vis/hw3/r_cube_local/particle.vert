#version 430 core

struct Particle {
	vec3 pos;
	vec3 vel;
	int hash;
};

layout(binding = 0) buffer particleBuffer {
	Particle[] particleData;
};

uniform mat4 pr_matrix;
uniform mat4 vw_matrix;

void main() {
	Particle p = particleData[gl_VertexID];
   	gl_Position = pr_matrix * vw_matrix * vec4(p.pos, 1);
}