#version 130
// changes here must be saved to jar file by project build to be able to load this shader as resource
in vec3 v; // the event x,y,t
out vec3 frag_v;
out float frag_t0;
out float frag_t1;

uniform mat4 mv; // modelview
uniform mat4 proj; // projection
uniform float t0; // start of time window
uniform float t1; // end of time window

void main() {
	vec4 vh = vec4(v, 1);// transform vertex to homogeneous coordinate
	gl_Position = proj * mv * vh; // must be this order
	gl_PointSize = 4.0;
        frag_v=v;
        frag_t0=t0;
        frag_t1=t1;
}

