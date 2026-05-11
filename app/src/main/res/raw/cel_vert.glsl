// 三渲二顶点着色器 (Toon Rendering Vertex Shader)
precision mediump float;

attribute vec4 a_pos;
attribute vec3 a_norm;
attribute vec4 a_color;

uniform mat4 u_mvp;

varying vec3 v_norm;
varying vec4 v_color;

void main() {
    gl_Position = u_mvp * a_pos;
    v_norm  = a_norm;
    v_color = a_color;
}
