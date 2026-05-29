package com.gree1d.reappzuku;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.SweepGradient;

import com.github.mikephil.charting.animation.ChartAnimator;
import com.github.mikephil.charting.charts.PieChart;
import com.github.mikephil.charting.data.PieEntry;
import com.github.mikephil.charting.interfaces.datasets.IPieDataSet;
import com.github.mikephil.charting.renderer.PieChartRenderer;
import com.github.mikephil.charting.utils.MPPointF;
import com.github.mikephil.charting.utils.ViewPortHandler;

import java.util.List;

public class PieChartRender extends PieChartRenderer {

    // Darkness at the start edge (shadow imitation)
    private static final float EDGE_DARK   = 0.45f;
    // Darkness at the inner rim (radial depth)
    private static final float INNER_DARK  = 0.30f;

    private final Path  mPath      = new Path();
    private final Paint mFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mEdgePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public PieChartRender(PieChart chart, ChartAnimator animator, ViewPortHandler viewPortHandler) {
        super(chart, animator, viewPortHandler);
        mFillPaint.setStyle(Paint.Style.FILL);
        mEdgePaint.setStyle(Paint.Style.STROKE);
        mEdgePaint.setColor(Color.BLACK);
        mEdgePaint.setStrokeCap(Paint.Cap.BUTT);
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

        final float sliceW     = radius - holeRad;
        final float lineStroke = Math.max(2f, sliceW * 0.05f);
        mEdgePaint.setStrokeWidth(lineStroke);

        final float[]       drawAngles = mChart.getDrawAngles();
        final float         sliceSpace = dataSet.getSliceSpace();
        final List<Integer> colors     = dataSet.getColors();

        final RectF outerRect = new RectF(cx - radius,  cy - radius,  cx + radius,  cy + radius);
        final RectF innerRect = new RectF(cx - holeRad, cy - holeRad, cx + holeRad, cy + holeRad);

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
            final float midAngle = startAngle + arcSweep / 2f;

            final float startRad = (float) Math.toRadians(startAngle);
            final float endRad   = (float) Math.toRadians(endAngle);
            final float midRad   = (float) Math.toRadians(midAngle);

            // ── Segment path (clean donut) ────────────────────────────────
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

            // ── Gradient along the arc (sweep direction) ──────────────────
            // The gradient goes: dark at start edge → full color at ~25% → full at end
            // Combined with a radial darkening at inner rim to fake depth.
            //
            // Strategy: use a LinearGradient oriented perpendicular to the
            // start-edge (i.e. along the sweep direction from start to mid).
            // We align it from the start-edge midpoint to the opposite side.

            // Start-edge midpoint (between inner and outer rim)
            float midR    = (holeRad + radius) / 2f;
            float gradStartX = cx + holeRad * (float) Math.cos(startRad);
            float gradStartY = cy + holeRad * (float) Math.sin(startRad);
            // Point well past the mid-angle (far side of segment)
            float gradEndX   = cx + radius * (float) Math.cos(midRad);
            float gradEndY   = cy + radius * (float) Math.sin(midRad);

            // SweepGradient centered at cx,cy:
            // - at startAngle: dark (shadow of the cut edge)
            // - at startAngle + ~15% sweep: full color
            // - rest of sweep: full color
            // We rotate the sweep gradient so angle 0 = startAngle.
            //
            // Colors array for SweepGradient covers 0..360 degrees.
            // We need: dark at startAngle, bright shortly after, bright until endAngle.
            // Pack the gradient into a small angular band.

            float shadowFraction = Math.min(0.18f, 15f / arcSweep); // shadow takes ~15° or 18% of sweep
            // positions in 0..1 relative to full circle
            float p0 = startAngle / 360f;
            float p1 = (startAngle + arcSweep * shadowFraction) / 360f;
            float p2 = (startAngle + arcSweep * 0.5f) / 360f;
            float p3 = endAngle / 360f;

            // Clamp positions to [0,1] and ensure monotonic
            p0 = ((p0 % 1f) + 1f) % 1f;
            p1 = p0 + (arcSweep * shadowFraction) / 360f;
            p2 = p0 + (arcSweep * 0.5f) / 360f;
            p3 = p0 + arcSweep / 360f;

            int darkColor  = darken(color, EDGE_DARK);
            int fullColor  = color;

            SweepGradient sweepGrad = new SweepGradient(
                    cx, cy,
                    new int[]  { darkColor, fullColor, fullColor, darkColor },
                    new float[]{ p0,        p1,        p3,        1f        }
            );

            // Rotate the sweep gradient so position 0 aligns with our startAngle
            Matrix m = new Matrix();
            m.postRotate(startAngle, cx, cy);
            sweepGrad.setLocalMatrix(m);

            // Also apply inner-rim darkening via a second radial layer drawn on top.
            // For simplicity we combine: draw segment with sweep gradient first,
            // then overlay a radial darkening strip near the inner rim.
            mFillPaint.setShader(sweepGrad);
            c.drawPath(mPath, mFillPaint);

            // Inner-rim darkening strip (fake depth / thickness)
            // Draw a thin arc along the inner edge with a semi-transparent dark paint
            Paint innerDark = new Paint(Paint.ANTI_ALIAS_FLAG);
            innerDark.setStyle(Paint.Style.STROKE);
            innerDark.setColor(Color.BLACK);
            innerDark.setAlpha((int)(255 * INNER_DARK));
            innerDark.setStrokeWidth(sliceW * 0.22f);
            float innerStripR = holeRad + sliceW * 0.11f;
            RectF innerStripRect = new RectF(cx - innerStripR, cy - innerStripR,
                                             cx + innerStripR, cy + innerStripR);
            c.drawArc(innerStripRect, startAngle + 1f, arcSweep - 2f, false, innerDark);

            // ── Black divider line at start edge ─────────────────────────
            float inset = lineStroke * 0.5f;
            c.drawLine(
                    cx + (holeRad + inset) * (float) Math.cos(startRad),
                    cy + (holeRad + inset) * (float) Math.sin(startRad),
                    cx + (radius  - inset) * (float) Math.cos(startRad),
                    cy + (radius  - inset) * (float) Math.sin(startRad),
                    mEdgePaint);

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
