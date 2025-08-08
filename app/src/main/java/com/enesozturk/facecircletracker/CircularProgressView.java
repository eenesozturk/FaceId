package com.enesozturk.facecircletracker;

import android.animation.ObjectAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

public class CircularProgressView extends View {

    // 8 dış yön: up(0), rightUp(1), right(2), rightDown(3), down(4),
    // leftDown(5), left(6), leftUp(7)  — Saat yönünde, başlangıç -90° (tepe)
    private static final int OUTER_SEGMENTS = 8;
    private static final float GAP_DEG = 6f;          // dilimler arası boşluk
    private static final float SEG_DEG = 360f / OUTER_SEGMENTS;

    private final boolean[] segmentDone = new boolean[OUTER_SEGMENTS];
    private final float[] segmentFill = new float[OUTER_SEGMENTS]; // 0..1
    private boolean centerDone = false;
    private float centerFill = 0f;

    private final Paint basePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint guidePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint centerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private int borderColor = 0xFF00FFFF; // glow rengini buradan güncelliyoruz

    public CircularProgressView(Context c, @Nullable AttributeSet a) {
        super(c, a);
        basePaint.setStyle(Paint.Style.STROKE);
        basePaint.setStrokeWidth(16f);
        basePaint.setColor(0x33FFFFFF); // açık gri şerit

        fillPaint.setStyle(Paint.Style.STROKE);
        fillPaint.setStrokeCap(Paint.Cap.ROUND);
        fillPaint.setStrokeWidth(16f);
        fillPaint.setColor(borderColor);

        guidePaint.setStyle(Paint.Style.STROKE);
        guidePaint.setStrokeWidth(2f);
        guidePaint.setColor(0x22FFFFFF);

        centerPaint.setStyle(Paint.Style.FILL);
        centerPaint.setColor(0x3300FFFF);
    }

    public void setBorderColor(int color) {
        borderColor = color;
        fillPaint.setColor(color);
        invalidate();
    }

    public void completeDirection(int idx) {
        if (idx < 0 || idx >= OUTER_SEGMENTS) return;
        if (segmentDone[idx]) return;
        segmentDone[idx] = true;
        animateSegment(idx);
    }

    public void completeCenter() {
        if (centerDone) return;
        centerDone = true;
        ObjectAnimator.ofFloat(this, "centerFill", 0f, 1f).setDuration(350).start();
    }

    // property anim target
    @SuppressWarnings("unused")
    public void setCenterFill(float v) { centerFill = v; invalidate(); }
    @SuppressWarnings("unused")
    public float getCenterFill() { return centerFill; }

    private void animateSegment(int idx) {
        ObjectAnimator anim = ObjectAnimator.ofFloat(this, "segFill_" + idx, 0f, 1f);
        anim.setDuration(350);
        anim.addUpdateListener(a -> {
            float val = (float) a.getAnimatedValue();
            segmentFill[idx] = val;
            invalidate();
        });
        anim.start();
    }

    public boolean isAllCompleted() {
        if (!centerDone) return false;
        for (boolean b : segmentDone) if (!b) return false;
        return true;
    }

    // --- Çizim ---
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float w = getWidth();
        float h = getHeight();
        float pad = 20f;
        float stroke = fillPaint.getStrokeWidth();
        float radius = Math.min(w, h) / 2f - pad;

        // Glow (nefes alma) — radial shader
        fillPaint.setShader(new RadialGradient(
                w / 2f, h / 2f, radius + 40f,
                new int[]{borderColor, 0x00000000},
                new float[]{0.85f, 1f},
                Shader.TileMode.CLAMP
        ));

        RectF arc = new RectF(
                w / 2f - radius,
                h / 2f - radius,
                w / 2f + radius,
                h / 2f + radius
        );

        // Taban gri segmentler
        for (int i = 0; i < OUTER_SEGMENTS; i++) {
            float start = -90f + i * SEG_DEG + GAP_DEG / 2f;
            float sweep = SEG_DEG - GAP_DEG;
            canvas.drawArc(arc, start, sweep, false, basePaint);
        }

        // Dolmuş segmentler (kendi yerlerinde)
        for (int i = 0; i < OUTER_SEGMENTS; i++) {
            if (segmentFill[i] <= 0f) continue;
            float start = -90f + i * SEG_DEG + GAP_DEG / 2f;
            float sweep = (SEG_DEG - GAP_DEG) * segmentFill[i];
            canvas.drawArc(arc, start, sweep, false, fillPaint);
        }

        // İç merkez (düz bakış) — küçük disk
        float innerR = radius * 0.35f;
        if (centerFill > 0f) {
            int alpha = (int) (0x55 * centerFill);
            centerPaint.setColor((alpha << 24) | (0x00FFFF));
            canvas.drawCircle(w / 2f, h / 2f, innerR * centerFill, centerPaint);
        }

        // İç kılavuz dairesi
        canvas.drawCircle(w / 2f, h / 2f, innerR, guidePaint);
    }
}
