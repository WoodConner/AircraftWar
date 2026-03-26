// 背景片元着色器 — 纹理采样 + 轻微暗边晕影效果
precision mediump float;
uniform sampler2D uTex;
varying vec2 vUV;
void main() {
    vec4 c = texture2D(uTex, vUV);
    // 轻微暗化边缘（视觉焦点聚中）
    float vignette = 1.0 - 0.25 * pow(length(vUV - 0.5) * 1.6, 2.0);
    gl_FragColor = vec4(c.rgb * vignette, c.a);
}
