#version 130
// changes here must be saved to jar file by project build to be able to load this shader as resource
in float f, f1;
out vec4 frag_color;

void main() {
    float b=max(1-2*f,0);
    float r=max(2*(f-.5),0);
    float g=f;
    if(f>.5)
        g=1-g;
    frag_color =  (.75*f1+.25) * vec4(r, g, b, .5); 
}

