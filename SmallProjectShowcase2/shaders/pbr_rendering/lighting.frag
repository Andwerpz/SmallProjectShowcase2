#version 330 core
layout (location = 0) out vec4 lColor;

const int DIR_LIGHT = 0;
const int POINT_LIGHT = 1;
const int SPOT_LIGHT = 2;

in vec2 frag_uv;

uniform vec3 view_pos;
uniform sampler2D tex_position;
uniform sampler2D tex_normal;
uniform sampler2D tex_diffuse;
uniform sampler2D tex_attr;	//R: roughness, G: metalness

uniform samplerCube tex_irradiance;

//directional shadows
uniform float shadowMapNear;	// >= near
uniform float shadowMapFar;	// < far
uniform sampler2D shadowMap;
uniform sampler2D shadowBackfaceMap;	//is a backface or not
uniform mat4 lightSpace_matrix;

//point shadows
uniform samplerCube shadowCubemap;
uniform float shadowCubemapFar;

vec3 sampleOffsetDirections[20] = vec3[]
(
   vec3( 1,  1,  1), vec3( 1, -1,  1), vec3(-1, -1,  1), vec3(-1,  1,  1), 
   vec3( 1,  1, -1), vec3( 1, -1, -1), vec3(-1, -1, -1), vec3(-1,  1, -1),
   vec3( 1,  1,  0), vec3( 1, -1,  0), vec3(-1, -1,  0), vec3(-1,  1,  0),
   vec3( 1,  0,  1), vec3(-1,  0,  1), vec3( 1,  0, -1), vec3(-1,  0, -1),
   vec3( 0,  1,  1), vec3( 0, -1,  1), vec3( 0, -1, -1), vec3( 0,  1, -1)
);   

struct Light {
	int type;
	
	vec3 pos;
	vec3 dir;
	vec3 color;
	
	float ambientIntensity;
	
	float cutOff;
	float outerCutOff;
	
	float constant;
	float linear;
	float quadratic;
};	

uniform Light light;

float SampleShadowMap(sampler2D shadowMap, vec2 coords, float compare) {
	return step(texture2D(shadowMap, coords.xy).r, compare);
}

float SampleShadowMapLinear(sampler2D shadowMap, vec2 coords, float compare, vec2 texelSize) {
	vec2 pixelPos = coords/texelSize + vec2(0.5);
	vec2 fracPart = fract(pixelPos);
	vec2 startTexel = (pixelPos - fracPart) * texelSize;
	
	float blTexel = SampleShadowMap(shadowMap, startTexel, compare);
	float brTexel = SampleShadowMap(shadowMap, startTexel + vec2(texelSize.x, 0.0), compare);
	float tlTexel = SampleShadowMap(shadowMap, startTexel + vec2(0.0, texelSize.y), compare);
	float trTexel = SampleShadowMap(shadowMap, startTexel + texelSize, compare);
	
	float mixA = mix(blTexel, tlTexel, fracPart.y);
	float mixB = mix(brTexel, trTexel, fracPart.y);
	
	return mix(mixA, mixB, fracPart.x);
}

float calcShadow(vec3 frag_pos) {
	float shadow = 0.0;
	if(light.type == DIR_LIGHT){
		vec4 lightSpace_frag_pos = lightSpace_matrix * vec4(frag_pos, 1.0);
		// perform perspective divide
   	 	vec3 projCoords = lightSpace_frag_pos.xyz / lightSpace_frag_pos.w;
   	 	projCoords = projCoords * 0.5 + 0.5; //transform from [-1, 1] to [0, 1]
   	 	
   	 	float currentDepth = projCoords.z; 
   	 	float backfaceBias = texture(shadowBackfaceMap, projCoords.xy).r == 1? 0 : 0;
   	 	//float backfaceBias = 0;
   	 	
   	 	//float bias = max(0.0003 * (1.0 - dot(normal, lightDir)), 0.0005);  
   	 	float bias = 0.0001;
   	 	
   	 	vec2 texelSize = 1.0 / textureSize(shadowMap, 0);
   	 	int pcfSampleN = 2;
		for(int x = -pcfSampleN; x <= pcfSampleN; ++x) {
		    for(int y = -pcfSampleN; y <= pcfSampleN; ++y) {
				//linear soft shadows
		        shadow += SampleShadowMapLinear(shadowMap, projCoords.xy + vec2(x, y) * texelSize, currentDepth - bias, texelSize);
		    }    
		}
		shadow /= 25;
   	 	
   	 	if(projCoords.z > 1.0){
        	shadow = 0.0;
        }
	}
	
	if(light.type == POINT_LIGHT || light.type == SPOT_LIGHT){
		vec3 dir_to_frag = normalize(light.pos - frag_pos);
		float currentDepth = length(light.pos - frag_pos); 
		float sampledDepth = texture(shadowCubemap, dir_to_frag).r * shadowCubemapFar;
		
		//float bias = 0.05;
		
		//shadow = currentDepth - bias > sampledDepth? 1.0 : 0.0;
		
		float bias   = 0.1;
		int samples  = 20;
		float viewDistance = length(view_pos - frag_pos);
		float diskRadius = 0.003;
		for(int i = 0; i < samples; i++) {
		    float closestDepth = texture(shadowCubemap, dir_to_frag + sampleOffsetDirections[i] * diskRadius).r;
		    closestDepth *= shadowCubemapFar;   // undo mapping [0;1]
		    if(currentDepth - bias > closestDepth) {
		        shadow += 1.0;
		    }
		}
		shadow /= float(samples);  
	}
	
	return shadow;
}

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

//lots of implementation help from https://learnopengl.com/PBR/Lighting
void main() {	
	vec3 frag_pos = texture(tex_position, frag_uv).rgb;
	float frag_depth = texture(tex_position, frag_uv).a;
	vec3 frag_color = texture(tex_diffuse, frag_uv).rgb;
	float frag_alpha = texture(tex_diffuse, frag_uv).a;
	float frag_roughness = texture(tex_attr, frag_uv).r;
	float frag_metalness = texture(tex_attr, frag_uv).g;
	vec3 frag_normal = normalize(texture(tex_normal, frag_uv).rgb);
	
	//we do cascaded shadows in multiple passes to render the whole scene
	if(light.type == DIR_LIGHT && (frag_depth < shadowMapNear || frag_depth >= shadowMapFar)){
		discard;
	}
	
	//check if fragment isn't rendered?
	if(texture(tex_position, frag_uv).w == 0.0){
		discard;
	}
	
	vec3 to_camera = normalize(view_pos - frag_pos);
	vec3 to_light = normalize(light.pos - frag_pos);
	if(light.type == DIR_LIGHT) {
		to_light = -light.dir;
	}
    vec3 halfway_dir = normalize(to_camera + to_light);
    float distance = length(light.pos - frag_pos);
    float attenuation = 1.0 / (distance * distance);
    if(light.type == DIR_LIGHT) {
    	attenuation = 1;
    }
	float shadow = calcShadow(frag_pos);
	//attenuation *= (1.0 - shadow);
    vec3 radiance = light.color * attenuation;        
    
    // cook-torrance brdf
    float NDF = DistributionGGX(frag_normal, halfway_dir, frag_roughness);        
    float G   = GeometrySmith(frag_normal, to_camera, to_light, frag_roughness);   
    
    //non-metallic surfaces look good with F0 at 0.04, if surface is metallic, we can raise it. 
    vec3 F0 = vec3(0.04);
	F0 = mix(F0, frag_color, frag_metalness);   
    vec3 F = fresnelSchlick(max(dot(halfway_dir, to_camera), 0.0), F0);       
    
    vec3 kS = F;	//specular contribution
    vec3 kD = vec3(1.0) - kS;	//diffuse contribution
    kD *= 1.0 - frag_metalness;	  //if a surface is metallic, then diffuse light gets absorbed
    
    vec3 numerator    = NDF * G * F;
    float denominator = 4.0 * max(dot(frag_normal, to_camera), 0.0) * max(dot(frag_normal, to_light), 0.0) + 0.0001;
    vec3 specular     = numerator / denominator;  
        
    // add to outgoing radiance Lo
    float NdotL = max(dot(frag_normal, to_light), 0.0);                
    vec3 light_out = (kD * frag_color / PI + specular) * radiance * NdotL; 
    
    vec3 final_color = light_out;
    
    //-- AMBIENT LIGHTING & GAMMA CORRECTION -- 
    //this should really be in another shader, because we only want to do this step once
    {
    	kS = fresnelSchlickRoughness(max(dot(frag_normal, to_camera), 0.0), F0, frag_roughness); 
		kD = vec3(1.0) - kS;
		vec3 irradiance = texture(tex_irradiance, frag_normal).rgb;
		vec3 diffuse    = irradiance * frag_color;
		vec3 ambient    = kD * diffuse; 
		final_color += ambient;
    }
    
    //gamma correction
    final_color = final_color / (final_color + vec3(1.0));
	final_color = pow(final_color, vec3(1.0 / 2.2)); 
    
	lColor = vec4(final_color, 1);
	//lColor = vec4(1, 1, 1, 1);
} 

