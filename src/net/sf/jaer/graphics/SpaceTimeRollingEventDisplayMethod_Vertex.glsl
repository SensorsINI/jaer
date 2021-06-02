#version 130
// changes here must be saved to jar file by project build to be able to load this shader as resource
in vec3 v; // the event x,y,t, where t is expressed in negative up to aspectRatio*(max array size)
out float f, f1;

uniform mat4 mv; // modelview
uniform mat4 proj; // projection
uniform float t0; // start of time window
uniform float t1; // end of time window
uniform float pointSize; // base point size

void main() {
    float z=-v.z; // 0 at most recent time, dt at most distant past
    float dt=(t1-t0);
    f=z/dt; // fraction of total time in window, 0 at now, 1 at most distant past
    f1=1-f; 
    vec4 vh = vec4(v, 1);// transform vertex to homogeneous coordinate
    gl_PointSize = pointSize*f1+1;
    gl_Position = proj * mv * vh; // must be this order
}

