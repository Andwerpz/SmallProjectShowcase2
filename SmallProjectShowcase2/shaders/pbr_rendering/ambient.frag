#version 330 core
layout (location = 0) out vec4 FragColor;

in vec2 frag_uv;

uniform vec3 view_pos;
uniform sampler2D tex_position;
uniform sampler2D tex_normal;
uniform sampler2D tex_diffuse;
uniform sampler2D tex_attr;	//R: roughness, G: metalness

uniform samplerCube tex_irradiance;
uniform samplerCube tex_prefilter;
uniform sampler2D brdfLUT;  

uniform sampler2D tex_lit_color;	//supplies the HDR lit color from the lighting pass

const float PI = 3.14159265359;

vec3 fresnelSchlick(float cosTheta, vec3 F0) {
    return F0 + (1.0 - F0) * pow(clamp(1.0 - cosTheta, 0.0, 1.0), 5.0);
} 

vec3 fresnelSchlickRoughness(float cosTheta, vec3 F0, float roughness) {
    return F0 + (max(vec3(1.0 - roughness), F0) - F0) * pow(clamp(1.0 - cosTheta, 0.0, 1.0), 5.0);
} 

float DistributionGGX(vec3 N, vec3 H, float roughness) {
    float a      = roughness*roughness;
    float a2     = a*a;
    float NdotH  = max(dot(N, H), 0.0);
    float NdotH2 = NdotH*NdotH;
	
    float num   = a2;
    float denom = (NdotH2 * (a2 - 1.0) + 1.0);
    denom = PI * denom * denom;
	
    return num / denom;
}

float GeometrySchlickGGX(float NdotV, float roughness) {
    float r = (roughness + 1.0);
    float k = (r*r) / 8.0;

    float num   = NdotV;
    float denom = NdotV * (1.0 - k) + k;
	
    return num / denom;
}

float GeometrySmith(vec3 N, vec3 V, vec3 L, float roughness) {
    float NdotV = max(dot(N, V), 0.0);
    float NdotL = max(dot(N, L), 0.0);
    float ggx2  = GeometrySchlickGGX(NdotV, roughness);
    float ggx1  = GeometrySchlickGGX(NdotL, roughness);
	
    return ggx1 * ggx2;
}

void main()  {
	vec3 frag_pos = texture(tex_position, frag_uv).rgb;
	float frag_depth = texture(tex_position, frag_uv).a;
	vec3 frag_color = texture(tex_diffuse, frag_uv).rgb;
	float frag_alpha = texture(tex_diffuse, frag_uv).a;
	float frag_roughness = texture(tex_attr, frag_uv).r;
	float frag_metalness = texture(tex_attr, frag_uv).g;
	vec3 frag_normal = normalize(texture(tex_normal, frag_uv).rgb);
	
	//check if fragment isn't rendered
	if(texture(tex_position, frag_uv).w == 0.0){
		discard;
	}
	
	vec3 to_camera = normalize(view_pos - frag_pos);
	
	//non-metallic surfaces look good with F0 at 0.04, if surface is metallic, we can raise it. 
	vec3 F0 = vec3(0.04);
	F0 = mix(F0, frag_color, frag_metalness);   
	
	vec3 final_color = texture(tex_lit_color, frag_uv).rgb;
    
    //-- AMBIENT LIGHTING -- 
    {	
    	// ambient lighting (we now use IBL as the ambient term)
    	vec3 F = fresnelSchlickRoughness(max(dot(frag_normal, to_camera), 0.0), F0, frag_roughness);
    	vec3 kS = F;
		vec3 kD = vec3(1.0) - kS;
		kD *= 1.0 - frag_metalness;
		
		vec3 irradiance = texture(tex_irradiance, frag_normal).rgb;
		vec3 diffuse = irradiance * frag_color;
		
		//reflections
		const float MAX_REFLECTION_LOD = 4.0;
		vec3 R = reflect(-to_camera, frag_normal); 
		vec3 prefilteredColor = textureLod(tex_prefilter, R,  frag_roughness * MAX_REFLECTION_LOD).rgb;   
		vec2 envBRDF  = texture(brdfLUT, vec2(max(dot(frag_normal, to_camera), 0.0), frag_roughness)).rg;
		vec3 specular = prefilteredColor * (F * envBRDF.x + envBRDF.y);
		
		vec3 ambient = kD * diffuse + specular; 
		final_color += ambient;
    }
	
	FragColor = vec4(final_color, 1);
}


