#version 330 core
layout (location = 0) out vec4 gColor;

in vec3 frag_pos;
in vec2 frag_uv;
in vec3 frag_colorID;
in mat3 TBN;

in vec4 frag_material_diffuse;
in vec4 frag_material_specular;
in float frag_material_shininess;

uniform vec3 view_pos;
uniform sampler2D tex_diffuse;
uniform sampler2D tex_specular;
uniform sampler2D tex_normal;
uniform sampler2D tex_displacement;
uniform bool enableParallaxMapping;

uniform sampler2D waterPositionTexture;
uniform sampler2D waterColorTexture;
uniform vec2 screen_size;
uniform vec3 sun_dir;

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
	mat3 invTBN = transpose(TBN);
	
	//calculate normal
	vec4 fragColor = texture(tex_diffuse, frag_uv);
	fragColor = scaleWithMaterial(fragColor, frag_material_diffuse);
	vec3 normal = texture(tex_normal, frag_uv).xyz;
	normal = normal * 2.0 - 1.0;
	normal = normalize(invTBN * normal);	//transform normal into world space
	
	if(fragColor.w == 0.0){	//alpha = 0
    	discard;
    }
    
    //simple cosine law lighting
	float diffuse = dot(normal, sun_dir) / 2.0 + 0.5;
	vec4 color = fragColor * diffuse;
	
	//blending with water surface
	vec2 screen_uv = gl_FragCoord.xy / screen_size;
	vec4 water_pos = texture(waterPositionTexture, screen_uv);
	if(water_pos.a != 0){
		vec3 water_color = texture(waterColorTexture, screen_uv).rgb;
		float frag_depth = length(frag_pos - view_pos);
		float water_depth = length(water_pos.xyz - view_pos);
		float optical_depth = exp(min(0.0, water_depth - frag_depth));
		optical_depth = max(optical_depth, 0.1);
		color.rgb = optical_depth * color.rgb + (1.0 - optical_depth) * water_color;
	}
	
    gColor.rgba = vec4(color.rgb, 1.0);
    //gColor.rgba = vec4(normal, 1.0);
    //gColor.rgba = vec4(screen_uv, 0.0, 1.0);
    //gColor.rgba = vec4(1, 0, 0, 1);
} 

