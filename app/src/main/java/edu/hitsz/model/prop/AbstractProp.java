package edu.hitsz.model.prop;
import android.graphics.Bitmap;
import edu.hitsz.model.AbstractFlyingObject;

/** 道具抽象基类 */
public abstract class AbstractProp extends AbstractFlyingObject {
    public enum Type { BLOOD, BOMB, BULLET }
    private final Type type;

    public AbstractProp(int x, int y, Bitmap img, Type type) {
        super(x, y, 0, 4, 1, img); // 向下漂落
        this.type = type;
    }
    public Type getType() { return type; }
    /** 出界检测 */
    public boolean isOutOfScreen(int h) { return locationY > h + imageHeight; }
}
