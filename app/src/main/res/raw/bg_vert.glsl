// 背景滚动顶点着色器 (Scrolling Background)
attribute vec2 a_pos;
attribute vec2 a_uv;
uniform float u_scroll;   // Y方向滚动偏移 [0,1]
varying vec2 v_uv;
void main() {
    gl_Position = vec4(a_pos, 0.999, 1.0);   // 最远处
    v_uv = vec2(a_uv.x, a_uv.y + u_scroll);
}
