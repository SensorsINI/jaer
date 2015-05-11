#version 130
// changes here must be saved to jar file by project build to be able to load this shader as resource
in vec3 v; // the event x,y,t
out vec3 frag_v;

uniform mat4 mv; // modelview
uniform mat4 proj; // projection
//uniform float t0; // start of time window
//uniform float t1; // end of time window

void main() {
	vec4 vh = vec4(v, 1);// transform vertex to homogeneous coordinate
	gl_Position = proj * mv * vh; // must be this order
	gl_PointSize = 8.0;
        frag_v=v;
}

