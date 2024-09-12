#version 430 core

layout (binding = 1) readonly buffer posBuffer {
	vec4[] posData;
};

layout (binding = 2) readonly buffer velBuffer {
	vec4[] velData;
};

layout (binding = 3) readonly buffer hueBuffer {
	vec4[] hueData;
};

out vec3 frag_pos;
out vec3 frag_vel;
out vec3 frag_hue;

uniform mat4 pr_matrix;
uniform mat4 vw_matrix;

void main() {
	frag_pos = posData[gl_VertexID].xyz;
   	frag_vel = velData[gl_VertexID].xyz;
   	frag_hue = hueData[gl_VertexID].xyz;
   	
   	gl_Position = pr_matrix * vw_matrix * vec4(frag_pos, 1);
}