// 2.5D Sprite Vertex Shader
// 支持翻滚（Z轴旋转）+ Y轴透视拉伸 → 产生3D飞机侧翻效果
precision highp float;

attribute vec2 aPosition;   // 归一化四边形坐标 (-0.5 ~ 0.5)
attribute vec2 aTexCoord;   // UV坐标 (0 ~ 1)

uniform mat4  uMVP;         // Projection * View * Model
uniform float uBankRad;     // 翻滚角度（弧度，正=右转，负=左转）
uniform float uBankY;       // Y轴透视拉伸系数 (0~1)

varying vec2  vUV;
varying float vLight;       // 传递给片元着色器的伪光照系数

void main() {
    // ── 1. 飞机翻滚：先绕Z轴旋转（产生roll效果）
    float cosZ = cos(uBankRad);
    float sinZ = sin(uBankRad);
    vec2 rolled;
    rolled.x = aPosition.x * cosZ - aPosition.y * sinZ;
    rolled.y = aPosition.x * sinZ + aPosition.y * cosZ;

    // ── 2. Y轴透视挤压（模拟3D侧视时贴图变窄）
    float px = rolled.x * (1.0 - abs(uBankY) * 0.5);
    float py = rolled.y;

    // ── 3. MVP变换
    gl_Position = uMVP * vec4(px, py, 0.0, 1.0);

    // ── 4. 伪光照：顶部亮（cos(bankY)影响），翻转时稍暗
    float bankFace = cos(uBankY);                  // 正面时=1，侧转时<1
    float topGlow  = 0.85 + 0.15 * (0.5 - aPosition.y); // 顶部亮
    vLight = bankFace * topGlow;

    vUV = aTexCoord;
}
