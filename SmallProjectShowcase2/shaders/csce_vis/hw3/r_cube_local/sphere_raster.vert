#version 430 core
layout (location = 0) in vec3 pos;
layout (location = 1) in vec2 uv;
layout (location = 2) in vec3 normal;
layout (location = 3) in vec3 tangent;
layout (location = 4) in vec3 bitangent;

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

uniform float smoothing_radius;

noperspective out vec3 sphere_center;
out vec3 frag_pos;

void main() {
	Particle p = particleData[gl_InstanceID];
	
	vec3 vert_pos = (pos * smoothing_radius) + p.pos;
	vec4 proj_pos = pr_matrix * vw_matrix * vec4(vert_pos, 1.0);
	gl_Position = proj_pos;
	
	sphere_center = p.pos;
	frag_pos = vert_pos;
}