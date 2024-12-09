#version 330 core
layout (location = 0) in vec3 pos;
layout (location = 1) in vec2 uv;
layout (location = 2) in vec3 normal;
layout (location = 3) in vec3 tangent;
layout (location = 4) in vec3 bitangent;
layout (location = 5) in mat4 md_matrix;
layout (location = 9) in vec3 colorID;
layout (location = 10) in vec4 material_diffuse;
layout (location = 11) in vec4 material_specular;
layout (location = 12) in vec4 material_shininess;

uniform mat4 pr_matrix;	//projection
uniform mat4 vw_matrix;	//view

out vec3 frag_pos;
out vec3 frag_opos;

out vec4 frag_material_diffuse;
out vec4 frag_material_specular;
out float frag_material_shininess;

out vec3 frag_colorID;

uniform sampler2D dispTextureLong;
uniform sampler2D dispTextureMed;
uniform sampler2D dispTextureShort;

uniform float length_scale_long;
uniform float length_scale_med;
uniform float length_scale_short;

uniform float cascadeScale0;
uniform float cascadeScale1;
uniform float cascadeScale2;

vec3 sampleDisplacement(vec3 pt) {
	vec3 disp0 = texture(dispTextureLong, pt.xz / length_scale_long).xyz * length_scale_long;
	vec3 disp1 = texture(dispTextureMed, pt.xz / length_scale_med).xyz * length_scale_med;
	vec3 disp2 = texture(dispTextureShort, pt.xz / length_scale_short).xyz * length_scale_short;
	disp0 *= cascadeScale0;
	disp1 *= cascadeScale1;
	disp2 *= cascadeScale2;
	vec3 disp = disp0 + disp1 + disp2;
	disp.y *= -1;
	return disp;
}

void main() {	
	vec3 world_pos = vec3(md_matrix * vec4(pos, 1.0));
	frag_opos = world_pos;
	vec3 disp = sampleDisplacement(world_pos);
	vec3 adj_pos = world_pos + disp;
	
	frag_pos = adj_pos;
	gl_Position = pr_matrix * vw_matrix * vec4(adj_pos, 1.0);
	
    frag_colorID = colorID;
    frag_material_diffuse = material_diffuse;
    frag_material_specular = material_specular;
    frag_material_shininess = material_shininess.r;
}