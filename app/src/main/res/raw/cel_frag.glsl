// 三渲二片段着色器 (Three-tone Cel Shading Fragment Shader)
// 将光照量化为三档: 亮部/中间调/暗部
precision mediump float;

varying vec3 v_norm;
varying vec4 v_color;

// 光线方向 (来自左上方+屏幕外): 归一化向量
const vec3 LIGHT = vec3(-0.35, -0.50, 0.80);

void main() {
    float NdotL = max(dot(normalize(v_norm), normalize(LIGHT)), 0.0);

    // === 三渲二核心: 3档量化 ===
    float toon;
    if (NdotL > 0.65) {
        toon = 1.00;     // 亮部 (highlight)
    } else if (NdotL > 0.25) {
        toon = 0.55;     // 中间调 (midtone)
    } else {
        toon = 0.15;     // 暗部 (shadow)
    }

    // 边缘光 (rim light) — 增强动漫感
    float rim = 1.0 - NdotL;
    rim = (rim > 0.82) ? 0.30 : 0.0;

    vec3 final_color = v_color.rgb * toon + rim;
    gl_FragColor = vec4(clamp(final_color, 0.0, 1.0), v_color.a);
}
