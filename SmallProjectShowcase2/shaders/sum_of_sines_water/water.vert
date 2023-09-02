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

uniform bool enableTexScaling;
uniform float texScaleFactor;

uniform sampler2D tex_water_height;
uniform float water_scale;

out vec3 frag_pos;
out vec2 frag_uv;

out vec4 frag_material_diffuse;
out vec4 frag_material_specular;
out float frag_material_shininess;

out vec3 frag_colorID;

out mat4 frag_md_matrix;

float sample_height() {
	return texture(tex_water_height, uv).y * water_scale;
}

void main() {	
	frag_md_matrix = md_matrix;

	vec3 adj_pos = vec3(pos.x, sample_height(), pos.z);
	
    gl_Position = pr_matrix * vw_matrix * md_matrix * vec4(adj_pos, 1.0);
    frag_pos = vec3(md_matrix * vec4(adj_pos, 1.0));
    frag_uv = uv;
    frag_colorID = colorID;
    if(!enableTexScaling){
    	frag_uv = uv * texScaleFactor;
    }
    
    frag_material_diffuse = material_diffuse;
    frag_material_specular = material_specular;
    frag_material_shininess = material_shininess.r;
}