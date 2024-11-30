#version 440 core
layout (location = 0) out vec4 outColor;

const float PI = 3.14159265;

in vec3 fragPos;

uniform vec3 sun_dir;
uniform float t;

uniform samplerCube spaceSkybox;

struct Ray {
	vec3 origin;
	vec3 dir;
};

struct Sphere {
	vec3 center;
	float radius;
};

//all distances expressed in meters
const float camera_height = 500;	//above earth radius level
const float earth_radius = 6360e3;
const float atm_radius = 6420e3;

const vec3 earth_center = vec3(0);
const Sphere earth_sphere = Sphere(earth_center, earth_radius);
const Sphere atmo_sphere = Sphere(earth_center, atm_radius);	//haha
const vec3 camera_pos = vec3(0, camera_height + earth_radius, 0);

//magic number
const float sun_intensity = 20;

//Rayleigh and Mie scale heights
const float Hr = 7994;
const float Hm = 1200;

//Rayleigh and Mie scattering coefficients per wavelength (rgb)
const vec3 betaR = vec3(3.8e-6, 13.5e-6, 33.1e-6);
const vec3 betaM = vec3(21e-6);

//Mie phase constant
const float g = 0.76;

const int nrSamplesMain = 16;
const int nrSamplesLight = 8;

bool raySphere(Ray ray, Sphere sphere, inout float out_close, inout float out_far) {
	out_close = -1;
	out_far = -1;
	
	vec3 sphereCenter = sphere.center.xyz;
	float sphereRadius = sphere.radius;
	vec3 offsetRayOrigin = ray.origin - sphereCenter;
	
	float a = dot(ray.dir, ray.dir);
	float b = 2.0 * dot(offsetRayOrigin, ray.dir);
	float c = dot(offsetRayOrigin, offsetRayOrigin) - sphereRadius * sphereRadius;
	float d = b * b - 4.0 * a * c;
	
	if(d >= 0){
		out_close = max(0, (-b - sqrt(d)) / (2.0 * a));
		out_far = (-b + sqrt(d)) / (2.0 * a);
		return out_far > 0;
	}
	return false;
}

bool raySphere(Ray ray, Sphere sphere) {
	float t1, t2;
	return raySphere(ray, sphere, t1, t2);
}

vec3 computeIncidentLight(Ray ray) {
	float dmin, dmax;
	if(raySphere(ray, earth_sphere, dmin, dmax)) {
		//hits earth
		return vec3(0, 0, 0);
	}
	if(!raySphere(ray, atmo_sphere, dmin, dmax)) {
		//doesn't hit atmosphere
		return vec3(0, 0, 0);
	}
	
	float seg_len = (dmax - dmin) / nrSamplesMain;	//segment length
	float dcur = dmin;
	vec3 sumR = vec3(0), sumM = vec3(0);	//rayleigh and mie contribution
	float mu = dot(ray.dir, sun_dir);
	float phaseR = 3.0 / (16.0 * PI) * (1.0 + mu * mu);
	float phaseM = (3.0 / (8.0 * PI)) * ((1 - g * g) * (1 + mu * mu)) / ((2.0 + g * g) * pow(1.0 + g * g - 2.0 * g * mu, 1.5));
	float opticalDepthR = 0, opticalDepthM = 0;
	for(int i = 0; i < nrSamplesMain; i++){
		vec3 cpos = ray.origin + ray.dir * (dcur + seg_len * 0.5);
		float height = length(earth_center - cpos) - earth_radius;
		
		//compute current optical depth
		float hr = exp(-height / Hr) * seg_len;
		float hm = exp(-height / Hm) * seg_len;
		opticalDepthR += hr;
		opticalDepthM += hm;
		
		//compute light contribution
		float t0_light, t1_light;
		raySphere(Ray(cpos, sun_dir), atmo_sphere, t0_light, t1_light);
		float seg_len_light = (t1_light - t0_light) / nrSamplesLight;
		float opticalDepthLightR = 0, opticalDepthLightM = 0;
		float dcur_light = t0_light;
		int j;
		for(j = 0; j < nrSamplesLight; j++){
			vec3 cpos_light = cpos + sun_dir * (dcur_light + seg_len_light * 0.5);
			float height_light = length(earth_center - cpos_light) - earth_radius;
			if(height_light < 0) break;	//inside the earth
			opticalDepthLightR += exp(-height_light / Hr) * seg_len_light;
			opticalDepthLightM += exp(-height_light / Hm) * seg_len_light;
			dcur_light += seg_len_light;
		}
		if(j == nrSamplesLight) {
			vec3 tau = betaR * (opticalDepthR + opticalDepthLightR) + betaM * 1.1 * (opticalDepthM + opticalDepthLightM);
			vec3 attenuation = vec3(exp(-tau.x), exp(-tau.y), exp(-tau.z));
			sumR += attenuation * hr;
			sumM += attenuation * hm;
		}
		
		dcur += seg_len;
	}
	vec3 color = (sumR * betaR * phaseR + sumM * betaM * phaseM) * sun_intensity;
	return color;
}

vec3 hash33(vec3 p) {
    p = fract(p * vec3(443.8975,397.2973, 491.1871));
    p += dot(p.zxy, p.yxz+19.27);
    return fract(vec3(p.x * p.y, p.z*p.x, p.y*p.z));
}

float hash21(in vec2 n){ return fract(sin(dot(n, vec2(12.9898, 4.1414))) * 43758.5453); }

vec2 hash21(float p) {
    vec3 p3 = fract(vec3(p) * vec3(.1031, .1030, .0973));
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.xx + p3.yz) * p3.zy);

}

vec3 hash31(float p) {
    vec3 p3 = fract(vec3(p,p,p) * vec3(.1031, .1030, .0973));
    p3 += dot(p3, p3.yzx+33.33);
    return fract((p3.xxy+p3.yzz)*p3.zyx);
}

float hash11(float p) {
    p = fract(p * .1031);
    p *= p + 33.33;
    p *= p + p;
    return fract(p);
}

float hashf(vec3 p3){
    p3 = fract(p3 * 0.1031);
    p3 += dot(p3,p3.yzx + 19.19);
    return fract((p3.x + p3.y) * p3.z);
}

float noisef(vec3 x){
    vec3 i = floor(x);
    vec3 f = fract(x);
    f = f*f*(3.0-2.0*f);
    return mix(mix(mix(hashf(i+vec3(0, 0, 0)), 
                       hashf(i+vec3(1, 0, 0)),f.x),
                   mix(hashf(i+vec3(0, 1, 0)), 
                       hashf(i+vec3(1, 1, 0)),f.x),f.y),
               mix(mix(hashf(i+vec3(0, 0, 1)), 
                       hashf(i+vec3(1, 0, 1)),f.x),
                   mix(hashf(i+vec3(0, 1, 1)), 
                       hashf(i+vec3(1, 1, 1)),f.x),f.y),f.z);
}

float remap(float x, float a, float b, float c, float d) {
    return (((x - a) / (b - a)) * (d - c)) + c;
}

float random(float p) {
    return fract(52.043*sin(p*205.429));
}
float random2(float p) {
    return random(p)*2.0-1.0;
}

mat3 rotateX(float theta) {
    float c = cos(theta);
    float s = sin(theta);
    return mat3(
        vec3(1, 0, 0),
        vec3(0, c, -s),
        vec3(0, s, c)
    );
}

// Rotation matrix around the Y axis.
mat3 rotateY(float theta) {
    float c = cos(theta);
    float s = sin(theta);
    return mat3(
        vec3(c, 0, s),
        vec3(0, 1, 0),
        vec3(-s, 0, c)
    );
}

// Rotation matrix around the Z axis.
mat3 rotateZ(float theta) {
    float c = cos(theta);
    float s = sin(theta);
    return mat3(
        vec3(c, -s, 0),
        vec3(s, c, 0),
        vec3(0, 0, 1)
    );
}

//AURORA_STUFF
mat2 mm2(in float a){float c = cos(a), s = sin(a);return mat2(c,s,-s,c);}
mat2 m2 = mat2(0.95534, 0.29552, -0.29552, 0.95534);
float tri(in float x){return clamp(abs(fract(x)-.5),0.01,0.49);}
vec2 tri2(in vec2 p){return vec2(tri(p.x)+tri(p.y),tri(p.y+tri(p.x)));}

float triNoise2d(in vec2 p, float spd) {
    float z=1.8;
    float z2=2.5;
	float rz = 0.;
    p *= mm2(p.x*0.06);
    vec2 bp = p;
	for (float i=0.; i<5.; i++ )
	{
        vec2 dg = tri2(bp*1.85)*.75;
        dg *= mm2(t*spd);
        p -= dg/z2;

        bp *= 1.3;
        z2 *= .45;
        z *= .42;
		p *= 1.21 + (rz-1.0)*.02;
        
        rz += tri(p.x+tri(p.y))*z;
        p*= -m2;
	}
    return clamp(1./pow(rz*29., 1.3),0.,.55);
}


vec3 bg(in vec3 rd) {
    float sd = dot(normalize(vec3(-0.5, -0.6, 0.9)), rd)*0.5+0.5;
    sd = pow(sd, 5.);
    vec3 col = mix(vec3(0.05,0.01,0.15), vec3(0.01,0.05,0.15), sd);
    return col*.63;
}

#define aurora_mt 1. //10
#define aurora_noise 0.06 //0.1
#define aurora_steps 32 //5.
#define aurora_height 0. //1e-5
#define aurorardy -0.05
#define aurora2 0.
#define aurorardz 0. //0
#define aurora_f 0.065 //0.065
#define aurora_sy 1. //0.065
#define aurora_col 0.043 //0.043
#define auroraINT 1.0
#define aurora_r 2.15 //2.15
#define aurora_g -1. // -0.5
#define aurora_b 1. // 1.2
#define aurora_ss 5. //5.
#define aurora_ss2 0.002 //15.
#define aurora_of 0.006 //0.006
void aurora(vec3 rayorigin, vec3 raydirection, out vec3 bpos, out vec4 col) {
	mat3 rotY = rotateX(-0.25);
    vec4 avgCol = vec4(0);
    rayorigin.z += t*aurora_height;
    raydirection.y *= aurora_sy;
	
    float mt = aurora_mt;
    float ms = 50.;
    for(int i = 0; i < aurora_steps; i++) {
        float of = aurora_of * hash21(gl_FragCoord.xy) * smoothstep(0.,15., float(i) * mt);
        float pt = ((.8 + pow(float(i),1.4) * aurora_ss2) - rayorigin.y) / (raydirection.y * 2. + 0.4);
        pt -= of;
        bpos = rayorigin + pt * raydirection ;
		//bpos *= fov;
        vec2 p = bpos.zx +sin(t*0.01);
        float rzt = triNoise2d(p, aurora_noise);
        vec4 col2 = vec4(0,0,0, rzt);
        col2.rgb = (sin(1. -vec3(aurora_r, aurora_g, aurora_b) + (float(i) * mt) * aurora_col) * 0.5 + 0.5) * rzt;
        avgCol =  mix(avgCol, col2, .5);
        col += avgCol * exp2( (-float(i)*mt) * aurora_f - 2.5) * smoothstep(0., aurora_ss, float(i)*mt);
    }
    
    col *= clamp(raydirection.y * 15. +0.4, 0., 1.);
	col *= auroraINT;
}

vec3 calcNightSky(Ray ray) {
	vec3 color = vec3(0);
	
	//contribution from space
	if(!raySphere(ray, earth_sphere)) {
		vec3 tex_color = texture(spaceSkybox, ray.dir).rgb;
		color += tex_color;
	}
	
	//aurora
	vec3 aurora_bpos = vec3(0);
	vec4 aurora_color = vec4(0);
	aurora(vec3(0), ray.dir, aurora_bpos, aurora_color);
	color = aurora_color.rgb * aurora_color.a + (1.0 - aurora_color.a) * color;
	
	return color;
}

void main() {		
	vec3 frag_dir = normalize(fragPos);
	
	vec2 attn = vec2(0);
	attn.x = max(0, sun_dir.y + 0.10);
	attn.y = max(0, -sun_dir.y + 0.05);
	attn = normalize(attn);
	vec3 day_color = computeIncidentLight(Ray(camera_pos, frag_dir));
	vec3 night_color = calcNightSky(Ray(camera_pos, frag_dir));
	
	vec3 color = attn.x * day_color + attn.y * night_color;
	outColor = vec4(pow(color, vec3(1.0/2.2)), 1.0); //gamma correct
}












