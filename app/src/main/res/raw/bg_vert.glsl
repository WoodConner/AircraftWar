precision highp float;
attribute vec2 aPosition;
attribute vec2 aTexCoord;
uniform mat4 uMVP;
varying vec2 vUV;
void main() {
    gl_Position = uMVP * vec4(aPosition, 0.0, 1.0);
    vUV = aTexCoord;
}
