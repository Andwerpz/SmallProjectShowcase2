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

uniform sampler2D tex_diffuse;
uniform sampler2D tex_specular;
uniform sampler2D tex_normal;
uniform sampler2D tex_displacement;
uniform bool enableParallaxMapping;

uniform vec3 sun_dir;
uniform vec3 view_pos;
uniform samplerCube skyboxCubemap;

const float PI = 3.14159265359;

uniform sampler2D derivativeTextureLong;
uniform sampler2D derivativeTextureMed;
uniform sampler2D derivativeTextureShort;

uniform float length_scale_long;
uniform float length_scale_med;
uniform float length_scale_short;

uniform float cascadeScale0;
uniform float cascadeScale1;
uniform float cascadeScale2;

uniform bool render_normals;
uniform bool render_reflection;

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

float dot_clamped(vec3 a, vec3 b) {
	return clamp(dot(a, b), 0, 1);
}

//n1 is the material we're currently in, n2 is the one we're going into
float fresnelDielectric(vec3 incident, vec3 normal, float n1, float n2) {
	float cos_theta_i = dot(incident, normal);
	float n = n2 / n1;
	
	//incident and normal are facing opposite directions, reverse orientation of normal. 
	if(cos_theta_i < 0){
		normal *= -1;
		cos_theta_i = dot(incident, normal);
		n = n1 / n2;
	}
	
	//compute cos_theta_t
	float sin2_theta_i = max(0.0, 1.0 - cos_theta_i * cos_theta_i);
	float sin2_theta_t = sin2_theta_i / (n * n);
	if(sin2_theta_t >= 1.0){	//handle total internal reflection
		return 1.0;
	}
	float cos_theta_t = sqrt(max(0.0, 1.0 - sin2_theta_t));
	
	//compute ans
	float r_parl = (n * cos_theta_i - cos_theta_t) / (n * cos_theta_i + cos_theta_t);
    float r_perp = (cos_theta_i - n * cos_theta_t) / (cos_theta_i + n * cos_theta_t);
    return (r_parl * r_parl + r_perp * r_perp) / 2.0;
}

const float F0 = 0.02;
float fresnelSchlick(float cosTheta) {
    return F0 + (1.0 - F0) * pow(clamp(1.0 - cosTheta, 0.0, 1.0), 5.0);
} 

float SmithMaskingBeckmann(vec3 H, vec3 S, float roughness) {
	float hdots = max(0.001f, dot_clamped(H, S));
	float a = hdots / (roughness * sqrt(1.0 - hdots * hdots));
	float a2 = a * a;
	return a < 1.6 ? (1.0 - 1.259 * a + 0.396 * a2) / (3.535 * a + 2.181 * a2) : 0.0;
}

float Beckmann(float ndoth, float roughness) {
	float exp_arg = (ndoth * ndoth - 1) / (roughness * roughness * ndoth * ndoth);
	return exp(exp_arg) / (PI * roughness * roughness * ndoth * ndoth * ndoth * ndoth);
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

vec3 sampleNormal() {
	vec4 derivative_long = texture(derivativeTextureLong, frag_pos.xz / length_scale_long).xyzw;
	vec4 derivative_med = texture(derivativeTextureMed, frag_pos.xz / length_scale_med).xyzw;
	vec4 derivative_short = texture(derivativeTextureShort, frag_pos.xz / length_scale_short).xyzw;
	vec2 slope_long = vec2(derivative_long.x / (1.0 + derivative_long.z), derivative_long.y / (1.0 + derivative_long.w));
	vec2 slope_med = vec2(derivative_med.x / (1.0 + derivative_med.z), derivative_med.y / (1.0 + derivative_med.w));
	vec2 slope_short = vec2(derivative_short.x / (1.0 + derivative_short.z), derivative_short.y / (1.0 + derivative_short.w));
	slope_long *= cascadeScale0 * length_scale_long;
	slope_med *= cascadeScale1 * length_scale_med;
	slope_short *= cascadeScale2 * length_scale_short;
	vec2 slope = slope_long + slope_med + slope_short;
	return normalize(vec3(-slope.x, 1.0, -slope.y));
}

uniform float sun_irradiance_mult;
uniform float environment_light_strength;

const float roughness = 0.075;
const vec3 sun_irradiance_base = vec3(1.0, 0.7, 0.4);
const vec3 _scatter_color = vec3(0.016, 0.0736, 0.16);
const vec3 _bubble_color = vec3(0, 0.02, 0.015999999);
const float _bubble_density = 10;
const float wave_peak_scatter_strength = 10;
const float scatter_strength = 10;
const float scatter_shadow_strength = 5;
const float height_modifier = 20;

void main() {
	vec3 sun_irradiance = sun_irradiance_base * sun_irradiance_mult;

	vec3 normal = sampleNormal();
	vec3 macro_normal = vec3(0, 1, 0);
	vec3 light_dir = sun_dir;
	vec3 view_dir = normalize(view_pos - frag_pos);
	vec3 halfway_dir = normalize(light_dir + view_dir);
	
	float NdotL = dot_clamped(normal, sun_dir);
	
	float a = roughness;
	float NdotH = max(0.0001, dot(normal, halfway_dir));
	float view_mask = SmithMaskingBeckmann(halfway_dir, view_dir, a);
	float light_mask = SmithMaskingBeckmann(halfway_dir, light_dir, a);
	
	float G = 1.0 / (1.0 + view_mask + light_mask);
	float eta = 1.33f;
	float R = ((eta - 1.0) * (eta - 1.0)) / ((eta + 1.0) * (eta + 1.0));
	float thetaV = acos(view_dir.y);
	
	float numerator = pow(1.0 - dot(normal, view_dir), 5.0 * exp(-2.69 * a));
	float F = R + (1.0 - R) * numerator / (1.0 + 22.7 * pow(a, 1.5));
	F = clamp(F, 0, 1);
	
	vec3 specular = sun_irradiance * F * G * Beckmann(NdotH, a);
	specular /= 4.0 * max(0.0001, dot_clamped(macro_normal, light_dir));
	specular *= dot_clamped(normal, light_dir);
	
	vec3 env_reflection = texture(skyboxCubemap, reflect(-view_dir, normal)).rgb;
	env_reflection *= environment_light_strength;
	
	float H = max(0.0, frag_pos.y * height_modifier);
	vec3 scatter_color = _scatter_color;
	vec3 bubble_color = _bubble_color;
	float bubble_density = _bubble_density;
	
	float k1 = wave_peak_scatter_strength * H * pow(dot_clamped(light_dir, -view_dir), 4.0) * pow(0.5 - 0.5 * dot(light_dir, normal), 3.0);
	float k2 = scatter_strength * pow(dot_clamped(view_dir, normal), 2.0);
	float k3 = scatter_shadow_strength * NdotL;
	float k4 = bubble_density;
	
	vec3 scatter = (k1 + k2) * scatter_color * sun_irradiance / (1.0 + light_mask);
	scatter += k3 * scatter_color * sun_irradiance + k4 * bubble_color * sun_irradiance;
	
	//vec3 output = (1.0 - F) * scatter + specular + F * env_reflection;
	vec3 output = scatter + specular + F * env_reflection;
	output = aces_tonemap(output);

    gColor.rgba = vec4(output, 1.0);
    if(render_normals) gColor.rgba = vec4(normal, 1.0);
    if(render_reflection) gColor.rgba = vec4(env_reflection, 1.0);
    gPosition.rgb = frag_pos;
    gPosition.a = gl_FragCoord.z;
    gSpecular.rgb = scaleWithMaterial(texture(tex_specular, vec2(0.5)).rgba, frag_material_specular.rgba).rgb;
    gSpecular.a = frag_material_shininess;
    gNormal.rgb = normalize(normal);
    gNormal.a = 1;
    gColorID = vec4(frag_colorID / 255, 1);
} 

