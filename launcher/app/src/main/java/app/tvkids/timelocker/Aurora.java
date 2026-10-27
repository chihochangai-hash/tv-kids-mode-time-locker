package app.tvkids.timelocker;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.view.View;

/** Teal and violet light on navy, darker toward the bottom. */
public final class Aurora extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int drawnW;
    private int drawnH;

    public Aurora(Context context) {
        super(context);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;
        canvas.drawColor(0xFF070A14);
        if (w != drawnW || h != drawnH) {
            drawnW = w;
            drawnH = h;
        }
        paint.setShader(new RadialGradient(w * 0.32f, 0f, w * 0.62f,
                new int[]{0xCC1A8A96, 0x55124E68, 0x00070A14},
                new float[]{0f, 0.42f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, w, h, paint);
        paint.setShader(new RadialGradient(w * 0.86f, h * 0.22f, w * 0.5f,
                new int[]{0xAA3C2C78, 0x44301E58, 0x00070A14},
                new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, w, h, paint);
        paint.setShader(new LinearGradient(0, h * 0.42f, 0, h, 0x00000000, 0xE005070E, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, w, h, paint);
        paint.setShader(null);
    }
}
