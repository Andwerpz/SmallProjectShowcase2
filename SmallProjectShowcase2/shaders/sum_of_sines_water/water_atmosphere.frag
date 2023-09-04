#version 330 core
layout (location = 0) out vec4 color;

in vec3 frag_dir;

uniform vec3 sun_dir;

//https://www.shadertoy.com/view/MdXyzX
vec3 extra_cheap_atmosphere(vec3 raydir, vec3 sundir) {
	raydir.y = max(raydir.y, 0);
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

vec3 getSunDirection() {
	return sun_dir * -1;
}

// Get atmosphere color for given direction
vec3 getAtmosphere(vec3 dir) {
   return extra_cheap_atmosphere(dir, getSunDirection()) * 0.5;
}

// Get sun color for given direction
float getSun(vec3 dir) { 
  return pow(max(0.0, dot(dir, getSunDirection())), 720.0) * 210.0;
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
	vec3 dir = normalize(frag_dir);
	vec3 atmosphere_color = getAtmosphere(dir) + getSun(dir);
    color = vec4(aces_tonemap(atmosphere_color * 2.0), 1.0);   
} 

