package com.example.faceattendanceapp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

public class FaceOverlayView extends View {

    private Paint paint;
    private RectF rect;

    public FaceOverlayView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public FaceOverlayView(Context context) {
        super(context);
        init();
    }

    private void init() {
        paint = new Paint();
        paint.setColor(Color.WHITE);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(5f);
        paint.setAlpha(180);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float width = getWidth();
        float height = getHeight();
        float boxWidth = width * 0.65f;
        float boxHeight = height * 0.55f;
        float left = (width - boxWidth) / 2;
        float top = (height - boxHeight) / 2;
        float right = left + boxWidth;
        float bottom = top + boxHeight;

        rect = new RectF(left, top, right, bottom);
        canvas.drawRoundRect(rect, 24f, 24f, paint);
    }
}
