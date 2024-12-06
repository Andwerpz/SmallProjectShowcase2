#version 330 core
layout (location = 0) out vec4 gPosition;
layout (location = 1) out vec4 gNormal;
layout (location = 2) out vec4 gSpecular;
layout (location = 3) out vec4 gColor;
layout (location = 4) out vec4 gColorID;

in vec3 frag_pos;

in vec4 frag_material_diffuse;
in vec4 frag_material_specular;
in float frag_material_shininess;

in vec3 frag_colorID;

uniform vec3 view_pos;
uniform sampler2D tex_diffuse;
uniform sampler2D tex_specular;
uniform sampler2D tex_normal;
uniform sampler2D tex_displacement;
uniform bool enableParallaxMapping;

uniform sampler2D dispTexture;
uniform sampler2D normalTexture;

vec4 scaleWithMaterial(vec4 color, vec4 material) {
	vec4 ans = vec4(0);
	ans.x = color.r * material.r;
	ans.y = color.g * material.g;
	ans.z = color.b * material.b;
	ans.w = color.a * material.a;
	
	//premultiply alpha, texture already has premultiplied
	ans.x *= material.a;
	ans.y *= material.a;
	ans.z *= material.a;
	
	return ans;
}

void main() {
	float scale = 64;
	vec3 normal = texture(normalTexture, frag_pos.xz / scale).xyz;

    //gColor.rgba = vec4(normal / 0.5 + 0.5, 1);
    gColor.rgba = scaleWithMaterial(texture(tex_diffuse, vec2(0.5)).rgba, frag_material_diffuse.rgba).rgba;
    gPosition.rgb = frag_pos;
    gPosition.a = gl_FragCoord.z;
    gSpecular.rgb = scaleWithMaterial(texture(tex_specular, vec2(0.5)).rgba, frag_material_specular.rgba).rgb;
    gSpecular.a = frag_material_shininess;
    gNormal.rgb = normalize(normal);
    gNormal.a = 1;
    gColorID = vec4(frag_colorID / 255, 1);
} 

