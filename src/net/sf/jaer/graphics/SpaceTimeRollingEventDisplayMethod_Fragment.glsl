#version 120
// macOS jAER contexts are GL 2.1 (GLSL 1.20). #version 130 does not compile there.
// changes here must be saved to jar file by project build to be able to load this shader as resource
varying float f;
varying float f1;

void main() {
    float b = max(1.0 - 2.0 * f, 0.0);
    float r = max(2.0 * (f - 0.5), 0.0);
    float g = f;
    if (f > 0.5)
        g = 1.0 - g;
    gl_FragColor = (0.4 * f1 + 0.6) * vec4(r, g, b, 1.0);
}
