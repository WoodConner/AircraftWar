// 描边顶点着色器 (Inverted-hull Outline Pass)
// 沿法线方向膨胀网格，配合正面剔除形成黑色轮廓
precision mediump float;

attribute vec4 a_pos;
attribute vec3 a_norm;

uniform mat4  u_mvp;
uniform float u_outline_px;   // 描边宽度 (像素单位)

void main() {
    // 将顶点沿法线方向膨胀 u_outline_px 像素
    vec4 expanded = a_pos + vec4(a_norm * u_outline_px, 0.0);
    gl_Position = u_mvp * expanded;
}
