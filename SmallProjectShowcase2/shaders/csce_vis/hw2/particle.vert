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

layout (binding = 4) readonly buffer attrBuffer {
	vec4[] attrData;
};	

out vec3 frag_pos;
out vec3 frag_vel;
out vec3 frag_hue;
out float frag_lifespan;

out vec2 frag_uv;
out float frag_depth;

uniform mat4 pr_matrix;
uniform mat4 vw_matrix;

uniform vec3 view_pos;

void main() {
	frag_pos = posData[gl_VertexID].xyz;
   	frag_vel = velData[gl_VertexID].xyz;
   	frag_hue = hueData[gl_VertexID].xyz;
   	frag_lifespan = posData[gl_VertexID].w;
   	bool proximity_hue = attrData[gl_VertexID].x > 0;
   	
   	if(proximity_hue) {
   		float ramp_min = 1f;
   		float ramp_max = 8f;
   		float cam_dist = length(view_pos - frag_pos);
   		vec3 color_min = vec3(255, 165, 0) / 255.0;
   		vec3 color_max = vec3(0, 0, 139) / 255.0;
   		
   		frag_hue = mix(color_min, color_max, clamp(cam_dist, ramp_min, ramp_max) / (ramp_max - ramp_min));
   	}	
   	
   	vec4 proj_pos = pr_matrix * vw_matrix * vec4(frag_pos, 1);
   	frag_uv = proj_pos.xy / proj_pos.w;
   	frag_depth = proj_pos.z / proj_pos.w;
   	
   	//remap from [-1, 1] to [0, 1]
   	frag_uv = frag_uv * 0.5 + 0.5;
   	frag_depth = frag_depth * 0.5 + 0.5;
   	
   	gl_PointSize = max(1.0, 5.0 / proj_pos.w);
   	
   	gl_Position = proj_pos;
}