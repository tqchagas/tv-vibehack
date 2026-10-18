package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

/** A single selection outline, with the same corner geometry as the scaled app card. */
public class GlassView extends View {

    private final Paint rim = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final float density;
    private float radius;

    public GlassView(Context context, AttributeSet attrs) {
        super(context, attrs);
        density = getResources().getDisplayMetrics().density;
        radius = 9f * density;
        rim.setStyle(Paint.Style.STROKE);
        rim.setStrokeWidth(1.25f * density);
        rim.setColor(0xE6FFFFFF);
    }

    void setRadius(float px) {
        radius = px;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        // MainActivity reserves this transparent inset outside the scaled card bounds.
        float inset = 6f * density;
        float halfStroke = rim.getStrokeWidth() / 2f;
        // Keep the stroke inside the card edge; use the matching inset corner radius.
        rect.set(inset + halfStroke, inset + halfStroke,
                getWidth() - inset - halfStroke, getHeight() - inset - halfStroke);
        float innerRadius = Math.max(0f, radius - halfStroke);
        canvas.drawRoundRect(rect, innerRadius, innerRadius, rim);
    }
}
