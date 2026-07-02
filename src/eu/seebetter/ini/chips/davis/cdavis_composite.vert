#version 120

attribute vec2 aPos;
attribute vec2 aTexCoord;
varying vec2 vTexCoord;

void main() {
    vTexCoord = aTexCoord;
    gl_Position = gl_ModelViewProjectionMatrix * vec4(aPos, 0.0, 1.0);
}
