package com.enesozturk.facecircletracker;

import android.animation.ObjectAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

public class CircularProgressView extends View {

    private int progress = 0;
    private final Paint paint;
    private boolean glow = false;
    private int borderColor = 0xFFFFFFFF; // Varsayılan beyaz

    public CircularProgressView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        paint = new Paint();
        paint.setStrokeWidth(20f);
        paint.setStyle(Paint.Style.STROKE);
        paint.setAntiAlias(true);
        paint.setColor(borderColor);
    }

    public void setProgress(int progress) {
        this.progress = progress;
        invalidate();
    }

    public void setProgressAnimated(int progress) {
        ObjectAnimator animator = ObjectAnimator.ofInt(this, "progress", this.progress, progress);
        animator.setDuration(300);
        animator.start();

        // Fade-in efekti
        ObjectAnimator alphaAnim = ObjectAnimator.ofFloat(this, "alpha", 0f, 1f);
        alphaAnim.setDuration(300);
        alphaAnim.start();
    }

    public int getProgress() {
        return progress;
    }

    public void setGlowEffect(boolean enabled) {
        this.glow = enabled;
        invalidate();
    }

    public void setBorderColor(int color) {
        this.borderColor = color;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float angle = 360f * progress / 100f;
        float strokePadding = paint.getStrokeWidth() / 2;

        if (glow) {
            Shader shader = new RadialGradient(
                    getWidth() / 2f, getHeight() / 2f,
                    getWidth() / 2f,
                    new int[]{borderColor, 0x00000000}, // borderColor'dan saydamlığa geçiş
                    new float[]{0.7f, 1f},
                    Shader.TileMode.CLAMP);
            paint.setShader(shader);
        } else {
            paint.setShader(null);
            paint.setColor(borderColor);
        }

        canvas.drawArc(strokePadding, strokePadding,
                getWidth() - strokePadding,
                getHeight() - strokePadding,
                -90, angle, false, paint);
    }
}
