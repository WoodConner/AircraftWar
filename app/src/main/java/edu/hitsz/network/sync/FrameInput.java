package edu.hitsz.network.sync;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * 帧同步输入数据包
 * 每个 tick 由本地/远端各发送一个，包含该帧的完整玩家输入
 */
public class FrameInput {
    public final long   tick;       // 帧序号（两端对齐用）
    public final String playerId;   // 发送方ID
    public final float  touchX;     // 触屏X坐标（-1=未触屏）
    public final float  touchY;     // 触屏Y坐标
    public final int    score;      // 发送方当前分数（用于对局结算校验）

    public FrameInput(long tick, String playerId, float touchX, float touchY, int score) {
        this.tick     = tick;
        this.playerId = playerId;
        this.touchX   = touchX;
        this.touchY   = touchY;
        this.score    = score;
    }

    /** 序列化为JSON字符串（发送给服务器） */
    public JSONObject toJson() {
        try {
            JSONObject obj = new JSONObject();
            obj.put("type",     "input");
            obj.put("tick",     tick);
            obj.put("playerId", playerId);
            obj.put("tx",       touchX);
            obj.put("ty",       touchY);
            obj.put("score",    score);
            return obj;
        } catch (JSONException e) {
            return new JSONObject();
        }
    }

    /** 从JSON解析（接收远端数据） */
    public static FrameInput fromJson(JSONObject obj) throws JSONException {
        return new FrameInput(
                obj.getLong("tick"),
                obj.optString("playerId", "remote"),
                (float) obj.optDouble("tx", -1),
                (float) obj.optDouble("ty", -1),
                obj.optInt("score", 0)
        );
    }

    @Override
    public String toString() {
        return "FrameInput{tick=" + tick + ",player=" + playerId
                + ",x=" + touchX + ",y=" + touchY + ",score=" + score + "}";
    }
}
