#version 330 core
layout (location = 0) out vec4 gPosition;
layout (location = 1) out vec4 gNormal;
layout (location = 2) out vec4 gSpecular;
layout (location = 3) out vec4 gColor;
layout (location = 4) out vec4 gColorID;

in vec3 frag_pos;
in vec2 frag_uv;
in vec3 frag_colorID;
in mat4 frag_md_matrix;

in vec4 frag_material_diffuse;
in vec4 frag_material_specular;
in float frag_material_shininess;

uniform vec3 view_pos;
uniform sampler2D tex_diffuse;
uniform sampler2D tex_specular;
uniform sampler2D tex_normal;
uniform sampler2D tex_displacement;
uniform bool enableParallaxMapping;

uniform sampler2D tex_water_normal;
uniform float water_scale;

uniform float water_depth;

uniform samplerCube skybox;

uniform vec3 sun_dir;	//direction from the sun to the ground

vec2 ParallaxMapping(vec2 texCoords, vec3 viewDir) { 
	float height_scale = 0.2;
    //float height =  texture(tex_displacement, texCoords).r;    
    //vec2 p = viewDir.xy / viewDir.z * (height * height_scale);
    //return texCoords - p;    
    
    // number of depth layers
    const float numLayers = 8;
    // calculate the size of each layer
    float layerDepth = 1.0 / numLayers;
    // depth of current layer
    float currentLayerDepth = 0.0;
    // the amount to shift the texture coordinates per layer (from vector P)
    vec2 P = viewDir.xy * height_scale; 
    vec2 deltaTexCoords = P / numLayers;
    
    // get initial values
	vec2  currentTexCoords     = texCoords;
	float currentDepthMapValue = texture(tex_displacement, currentTexCoords).r;
	  
	while(currentLayerDepth < currentDepthMapValue)
	{
	    // shift texture coordinates along direction of P
	    currentTexCoords -= deltaTexCoords;
	    // get depthmap value at current texture coordinates
	    currentDepthMapValue = texture(tex_displacement, currentTexCoords).r;  
	    // get depth of next layer
	    currentLayerDepth += layerDepth;  
	}
	
	// get texture coordinates before collision (reverse operations)
	vec2 prevTexCoords = currentTexCoords + deltaTexCoords;
	
	// get depth after and before collision for linear interpolation
	float afterDepth  = currentDepthMapValue - currentLayerDepth;
	float beforeDepth = texture(tex_displacement, prevTexCoords).r - currentLayerDepth + layerDepth;
	 
	// interpolation of texture coordinates
	float weight = afterDepth / (afterDepth - beforeDepth);
	vec2 finalTexCoords = prevTexCoords * weight + currentTexCoords * (1.0 - weight);
	
	return finalTexCoords; 
} 

vec4 scaleWithMaterial(vec4 color, vec4 material) {
	vec4 ans = vec4(0);
	ans.x = color.r * material.r;
	ans.y = color.g * material.g;
	ans.z = color.b * material.b;
	ans.w = color.a * material.a;
	return ans;
}

mat3 sample_TBN() {
	vec3 sample_normal = texture(tex_water_normal, frag_uv).rgb;
	float dx = sample_normal.x;
	float dz = sample_normal.y;
	
	vec3 adj_tangent = normalize(vec3(0, dz, 1));
	vec3 adj_bitangent = normalize(vec3(1, dx, 0));
	vec3 adj_normal = cross(adj_tangent, adj_bitangent);
	
	mat3 normalMatrix = transpose(inverse(mat3(frag_md_matrix)));
    vec3 T = normalize(normalMatrix * adj_tangent);
    vec3 B = normalize(normalMatrix * adj_bitangent);
    vec3 N = normalize(normalMatrix * adj_normal);
    
    //convert from real to tangent space
   	mat3 TBN = transpose(mat3(T, B, N));
   	
   	return TBN;
}

//https://www.shadertoy.com/view/MdXyzX
vec3 extra_cheap_atmosphere(vec3 raydir, vec3 sundir) {
  	sundir.y = max(sundir.y, -0.07);
  	float special_trick = 1.0 / (raydir.y * 1.0 + 0.1);
  	float special_trick2 = 1.0 / (sundir.y * 11.0 + 1.0);
  	float raysundt = pow(abs(dot(sundir, raydir)), 2.0);
  	float sundt = pow(max(0.0, dot(sundir, raydir)), 8.0);
  	float mymie = sundt * special_trick * 0.2;
  	vec3 suncolor = mix(vec3(1.0), max(vec3(0.0), vec3(1.0) - vec3(5.5, 13.0, 22.4) / 22.4), special_trick2);
  	vec3 bluesky= vec3(5.5, 13.0, 22.4) / 22.4 * suncolor;
  	vec3 bluesky2 = max(vec3(0.0), bluesky - vec3(5.5, 13.0, 22.4) * 0.002 * (special_trick + -6.0 * sundir.y * sundir.y));
  	bluesky2 *= special_trick * (0.24 + raysundt * 0.24);
  	return bluesky2 * (1.0 + 1.0 * pow(1.0 - raydir.y, 3.0)) + mymie * suncolor;
} 

// Great tonemapping function from other shader: https://www.shadertoy.com/view/XsGfWV
vec3 aces_tonemap(vec3 color) {  
  mat3 m1 = mat3(
    0.59719, 0.07600, 0.02840,
    0.35458, 0.90834, 0.13383,
    0.04823, 0.01566, 0.83777
  );
  mat3 m2 = mat3(
    1.60475, -0.10208, -0.00327,
    -0.53108,  1.10813, -0.07276,
    -0.07367, -0.00605,  1.07602
  );
  vec3 v = m1 * color;  
  vec3 a = v * (v + 0.0245786) - 0.000090537;
  vec3 b = v * (0.983729 * v + 0.4329510) + 0.238081;
  return pow(clamp(m2 * (a / b), 0.0, 1.0), vec3(1.0 / 2.2));  
}

void main() {
	mat3 TBN = sample_TBN();
	mat3 invTBN = transpose(TBN);
	
	//parallax mapping done in tangent space
	vec3 tangentViewPos = TBN * view_pos;	
	vec3 tangentFragPos = TBN * frag_pos;

	//offset texture coordinates with parallax mapping
	vec3 viewDir = normalize(tangentViewPos - tangentFragPos);
	vec2 texCoords = frag_uv;
	if(enableParallaxMapping){
		texCoords = ParallaxMapping(frag_uv, viewDir);
	}
	
	//calculate normal
	vec4 fragColor = texture(tex_diffuse, texCoords);
	vec3 normal = texture(tex_normal, texCoords).xyz;
	normal = normal * 2.0 - 1.0;
	normal = normalize(invTBN * normal);	//transform normal into world space
	
	//reflections in water from skybox
	vec3 frag_dir = normalize(frag_pos - view_pos);
	vec3 reflect_dir = frag_dir - 2 * (dot(frag_dir, normal) * normal);
	float fresnel = pow(1.0 - dot(reflect_dir, normal), 5);
	//vec3 reflect_color = texture(skybox, reflect_dir).rgb;
	vec3 reflect_color = extra_cheap_atmosphere(normalize(reflect_dir), sun_dir * -1);
	
	if(fragColor.w == 0.0){	//alpha = 0
    	discard;
    }
    
    //calculate scatter coeff
    //float scatter = max(frag_pos.y - base_height, 0);	//height of the water
    //scatter *= dot(normal, sun_dir * -1);
    //scatter *= dot(normal, normalize(view_pos - frag_pos));
    //scatter = clamp(scatter, 0.2, 1);
    float scatter = (0.2 + (frag_pos.y + water_depth) / water_depth);
    
    vec3 water_color = scaleWithMaterial(texture(tex_diffuse, texCoords).rgba, frag_material_diffuse.rgba).rgb;
    water_color = water_color * scatter;
    water_color = water_color * (1.0 - fresnel) + reflect_color * fresnel;
    
    water_color = aces_tonemap(water_color);
	
    gColor.rgba = vec4(water_color, 1);
    gPosition.rgb = frag_pos;
    gPosition.a = gl_FragCoord.z;
    gSpecular.rgb = scaleWithMaterial(texture(tex_specular, texCoords).rgba, frag_material_specular.rgba).rgb;
    gSpecular.a = frag_material_shininess;
    gNormal.rgb = normalize(normal);
    gColorID = vec4(frag_colorID / 255, 1);
} 

