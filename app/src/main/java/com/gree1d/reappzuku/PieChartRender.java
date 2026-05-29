package com.gree1d.reappzuku;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;

import com.github.mikephil.charting.animation.ChartAnimator;
import com.github.mikephil.charting.charts.PieChart;
import com.github.mikephil.charting.data.PieEntry;
import com.github.mikephil.charting.interfaces.datasets.IPieDataSet;
import com.github.mikephil.charting.renderer.PieChartRenderer;
import com.github.mikephil.charting.utils.MPPointF;
import com.github.mikephil.charting.utils.ViewPortHandler;

import java.util.List;

public class PieChartRender extends PieChartRenderer {

    // Radial gradient: how dark at inner edge (0=no change, 1=black)
    private static final float INNER_DARK    = 0.50f;
    // Inner depth strip: width as fraction of slice width
    private static final float DEPTH_STRIP_F = 0.14f;
    // Inner depth strip: opacity 0..255
    private static final int   DEPTH_ALPHA   = 120;
    // Divider line: opacity 0..255
    private static final int   LINE_ALPHA    = 210;

    private final Path  mPath       = new Path();
    private final Paint mFillPaint  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mDepthPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mLinePaint  = new Paint(Paint.ANTI_ALIAS_FLAG);

    public PieChartRender(PieChart chart, ChartAnimator animator, ViewPortHandler viewPortHandler) {
        super(chart, animator, viewPortHandler);

        mFillPaint.setStyle(Paint.Style.FILL);

        // Semi-transparent black arc at inner edge — depth illusion
        mDepthPaint.setStyle(Paint.Style.STROKE);
        mDepthPaint.setColor(Color.BLACK);
        mDepthPaint.setAlpha(DEPTH_ALPHA);

        // Divider line between segments
        mLinePaint.setStyle(Paint.Style.STROKE);
        mLinePaint.setColor(Color.BLACK);
        mLinePaint.setAlpha(LINE_ALPHA);
        mLinePaint.setStrokeCap(Paint.Cap.BUTT);
    }

    @Override
    protected void drawDataSet(Canvas c, IPieDataSet dataSet) {
        final float rotationAngle = mChart.getRotationAngle();
        final float phaseX        = mAnimator.getPhaseX();
        final float phaseY        = mAnimator.getPhaseY();

        final MPPointF center  = mChart.getCenterCircleBox();
        final float    radius  = mChart.getRadius();
        final float    holeRad = radius * (mChart.getHoleRadius() / 100f);
        final float    cx      = center.x;
        final float    cy      = center.y;

        final float sliceW      = radius - holeRad;
        final float depthStripW = sliceW * DEPTH_STRIP_F;
        final float lineStrokeW = Math.max(2f, sliceW * 0.035f);

        mDepthPaint.setStrokeWidth(depthStripW);
        mLinePaint.setStrokeWidth(lineStrokeW);

        final float[]       drawAngles = mChart.getDrawAngles();
        final float         sliceSpace = dataSet.getSliceSpace();
        final List<Integer> colors     = dataSet.getColors();

        final RectF outerRect = new RectF(cx - radius,  cy - radius,  cx + radius,  cy + radius);
        final RectF innerRect = new RectF(cx - holeRad, cy - holeRad, cx + holeRad, cy + holeRad);

        // Depth strip arc sits just outside the inner edge
        final float depthR = holeRad + depthStripW / 2f;
        final RectF depthRect = new RectF(cx - depthR, cy - depthR, cx + depthR, cy + depthR);

        float angle = 0f;

        for (int j = 0; j < dataSet.getEntryCount(); j++) {
            final float sweepAngle = drawAngles[j] * phaseX;
            final PieEntry entry   = (PieEntry) dataSet.getEntryForIndex(j);

            if (entry == null || entry.getValue() == 0f) {
                angle += sweepAngle;
                continue;
            }

            final float effectiveSweep = sweepAngle * phaseY;
            final float startAngle     = rotationAngle + angle + sliceSpace / 2f;
            final float arcSweep       = Math.max(0f, effectiveSweep - sliceSpace);

            if (arcSweep <= 0f) {
                angle += sweepAngle;
                continue;
            }

            final int   color    = colors.get(j % colors.size());
            final float endAngle = startAngle + arcSweep;
            final float startRad = (float) Math.toRadians(startAngle);
            final float endRad   = (float) Math.toRadians(endAngle);

            // ── 1. Segment path ───────────────────────────────────────────
            mPath.reset();
            mPath.moveTo(cx + holeRad * (float) Math.cos(startRad),
                         cy + holeRad * (float) Math.sin(startRad));
            mPath.lineTo(cx + radius * (float) Math.cos(startRad),
                         cy + radius * (float) Math.sin(startRad));
            mPath.arcTo(outerRect, startAngle, arcSweep, false);
            mPath.lineTo(cx + holeRad * (float) Math.cos(endRad),
                         cy + holeRad * (float) Math.sin(endRad));
            mPath.arcTo(innerRect, endAngle, -arcSweep, false);
            mPath.close();

            // ── 2. Radial gradient: dark at inner edge → full colour outer ─
            // Three stops: inner edge dark, just past depth strip = full colour, outer = full colour
            float innerStop = holeRad / radius;
            float brightStop = (holeRad + depthStripW * 2f) / radius;
            brightStop = Math.min(brightStop, 0.65f);

            RadialGradient rg = new RadialGradient(
                    cx, cy, radius,
                    new int[]{
                        darken(color, INNER_DARK),  // at center (maps to inner edge via stop)
                        darken(color, INNER_DARK),  // still dark at inner edge
                        color,                       // full colour shortly past inner edge
                        color                        // full colour at outer rim
                    },
                    new float[]{
                        0f,
                        innerStop,
                        brightStop,
                        1f
                    },
                    Shader.TileMode.CLAMP
            );
            mFillPaint.setShader(rg);
            c.drawPath(mPath, mFillPaint);

            // ── 3. Inner depth strip (semi-transparent black arc) ─────────
            c.drawArc(depthRect, startAngle + 0.5f, arcSweep - 1f, false, mDepthPaint);

            // ── 4. Divider line at start edge ─────────────────────────────
            float inset = lineStrokeW * 0.5f;
            c.drawLine(
                    cx + (holeRad + inset) * (float) Math.cos(startRad),
                    cy + (holeRad + inset) * (float) Math.sin(startRad),
                    cx + (radius  - inset) * (float) Math.cos(startRad),
                    cy + (radius  - inset) * (float) Math.sin(startRad),
                    mLinePaint);

            angle += sweepAngle;
        }

        MPPointF.recycleInstance(center);
    }

    private static int darken(int color, float fraction) {
        final int r = (int)(Color.red(color)   * (1f - fraction));
        final int g = (int)(Color.green(color) * (1f - fraction));
        final int b = (int)(Color.blue(color)  * (1f - fraction));
        return Color.argb(Color.alpha(color), r, g, b);
    }
}
