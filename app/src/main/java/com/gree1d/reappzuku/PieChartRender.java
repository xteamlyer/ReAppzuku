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

    // How much to darken the colour at the inner edge
    private static final float DARKEN_AMOUNT = 0.55f;

    private final Path  mPath      = new Path();
    private final Paint mFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mLinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public PieChartRender(PieChart chart, ChartAnimator animator, ViewPortHandler viewPortHandler) {
        super(chart, animator, viewPortHandler);
        mFillPaint.setStyle(Paint.Style.FILL);

        mLinePaint.setStyle(Paint.Style.STROKE);
        mLinePaint.setColor(Color.BLACK);
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

        // Line thickness proportional to segment width
        final float lineStroke = Math.max(2f, (radius - holeRad) * 0.04f);
        mLinePaint.setStrokeWidth(lineStroke);

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

            final int   sliceColor = colors.get(j % colors.size());
            final float startRad   = (float) Math.toRadians(startAngle);

            // Build clean donut segment:
            // moveTo inner-start → line to outer-start → outer arc → line to inner-end → inner arc back
            mPath.reset();

            final float innerStartX = cx + holeRad * (float) Math.cos(startRad);
            final float innerStartY = cy + holeRad * (float) Math.sin(startRad);
            mPath.moveTo(innerStartX, innerStartY);

            final float outerStartX = cx + radius * (float) Math.cos(startRad);
            final float outerStartY = cy + radius * (float) Math.sin(startRad);
            mPath.lineTo(outerStartX, outerStartY);

            // outer arc clockwise
            mPath.arcTo(outerRect, startAngle, arcSweep, false);

            // line inward to inner-end
            final float endRad    = (float) Math.toRadians(startAngle + arcSweep);
            final float innerEndX = cx + holeRad * (float) Math.cos(endRad);
            final float innerEndY = cy + holeRad * (float) Math.sin(endRad);
            mPath.lineTo(innerEndX, innerEndY);

            // inner arc counter-clockwise back to start
            mPath.arcTo(innerRect, startAngle + arcSweep, -arcSweep, false);

            mPath.close();

            // Radial gradient: dark at hole radius → full colour at outer rim
            RadialGradient gradient = new RadialGradient(
                    cx, cy, radius,
                    new int[]{
                        darken(sliceColor, DARKEN_AMOUNT),
                        darken(sliceColor, DARKEN_AMOUNT * 0.4f),
                        sliceColor
                    },
                    new float[]{
                        holeRad / radius,
                        (holeRad / radius + 1f) / 2f,
                        1f
                    },
                    Shader.TileMode.CLAMP);
            mFillPaint.setShader(gradient);
            c.drawPath(mPath, mFillPaint);

            // Black divider line at the start edge of each segment
            final float inset     = lineStroke * 0.5f;
            final float lineInner = holeRad + inset;
            final float lineOuter = radius  - inset;

            c.drawLine(
                    cx + lineInner * (float) Math.cos(startRad),
                    cy + lineInner * (float) Math.sin(startRad),
                    cx + lineOuter * (float) Math.cos(startRad),
                    cy + lineOuter * (float) Math.sin(startRad),
                    mLinePaint);

            angle += sweepAngle;
        }

        MPPointF.recycleInstance(center);
    }

    private static int darken(int color, float fraction) {
        final int r = (int) (Color.red(color)   * (1f - fraction));
        final int g = (int) (Color.green(color) * (1f - fraction));
        final int b = (int) (Color.blue(color)  * (1f - fraction));
        return Color.argb(Color.alpha(color), r, g, b);
    }
}
