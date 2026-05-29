package com.gree1d.reappzuku;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
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

    private static final float CORNER_FRACTION = 0.38f;
    private static final float DARKEN_AMOUNT   = 0.52f;

    private static final float DIVIDER_STROKE_DP = 1.5f;

    private final Path  mPath      = new Path();
    private final Paint mFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mLinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public PieChartRender(PieChart chart, ChartAnimator animator, ViewPortHandler viewPortHandler) {
        super(chart, animator, viewPortHandler);
        mFillPaint.setStyle(Paint.Style.FILL);

        mLinePaint.setStyle(Paint.Style.STROKE);
        mLinePaint.setColor(Color.BLACK);
        mLinePaint.setStrokeWidth(3f);
        mLinePaint.setStrokeCap(Paint.Cap.ROUND);
    }

    @Override
    protected void drawDataSet(Canvas c, IPieDataSet dataSet) {
        float rotationAngle = mChart.getRotationAngle();
        float phaseX        = mAnimator.getPhaseX();
        float phaseY        = mAnimator.getPhaseY();

        final MPPointF center   = mChart.getCenterCircleBox();
        final float    radius   = mChart.getRadius();
        final float    holeRad  = radius * (mChart.getHoleRadius() / 100f);
        final float    sliceW   = radius - holeRad;
        final float    cornerR  = sliceW * CORNER_FRACTION;
        final float    cx       = center.x;
        final float    cy       = center.y;

        float lineStroke = Math.max(2f, radius * 0.018f);
        mLinePaint.setStrokeWidth(lineStroke);

        final RectF outerBox = mChart.getCircleBox();
        final RectF innerBox = new RectF(cx - holeRad, cy - holeRad, cx + holeRad, cy + holeRad);

        final float[]       drawAngles = mChart.getDrawAngles();
        final float         sliceSpace = dataSet.getSliceSpace();
        final List<Integer> colors     = dataSet.getColors();

        float angle = 0f;

        for (int j = 0; j < dataSet.getEntryCount(); j++) {
            float sweepAngle   = drawAngles[j] * phaseX;
            PieEntry entry     = (PieEntry) dataSet.getEntryForIndex(j);
            if (entry == null || entry.getValue() == 0f) { angle += sweepAngle; continue; }

            float effectiveSweep = sweepAngle * phaseY;
            float startAngle     = rotationAngle + angle + sliceSpace / 2f;
            float arcSweep       = Math.max(0f, effectiveSweep - sliceSpace);
            if (arcSweep <= 0f) { angle += sweepAngle; continue; }

            int   sliceColor = colors.get(j % colors.size());
            float endAngle   = startAngle + arcSweep;

            mPath.reset();

            float cr = Math.min(cornerR, sliceW * 0.48f);

            float outerR     = radius - cr;
            float innerR     = holeRad + cr;
            float outerDelta = (float) Math.toDegrees(cr / radius);
            float innerDelta = (float) Math.toDegrees(cr / holeRad);

            float startRad = (float) Math.toRadians(startAngle);
            float endRad   = (float) Math.toRadians(endAngle);

            float osX = cx + radius * (float) Math.cos(startRad);
            float osY = cy + radius * (float) Math.sin(startRad);
            float oeX = cx + radius * (float) Math.cos(endRad);
            float oeY = cy + radius * (float) Math.sin(endRad);

            mPath.moveTo(
                    cx + innerR * (float) Math.cos(startRad),
                    cy + innerR * (float) Math.sin(startRad));

            mPath.lineTo(
                    cx + outerR * (float) Math.cos(startRad),
                    cy + outerR * (float) Math.sin(startRad));
            RectF cornerBox = new RectF(osX - cr, osY - cr, osX + cr, osY + cr);
            mPath.arcTo(cornerBox, startAngle + 180f, 90f, false);

            float outerBoxR = radius - cr;
            RectF outerArcBox = new RectF(cx - outerBoxR, cy - outerBoxR,
                                          cx + outerBoxR, cy + outerBoxR);
            mPath.arcTo(outerArcBox, startAngle + outerDelta, arcSweep - outerDelta * 2f);

            cornerBox = new RectF(oeX - cr, oeY - cr, oeX + cr, oeY + cr);
            mPath.arcTo(cornerBox, endAngle, 90f, false);

            mPath.lineTo(
                    cx + innerR * (float) Math.cos(endRad),
                    cy + innerR * (float) Math.sin(endRad));

            float ieCornerX = cx + holeRad * (float) Math.cos(endRad);
            float ieCornerY = cy + holeRad * (float) Math.sin(endRad);
            cornerBox = new RectF(ieCornerX - cr, ieCornerY - cr,
                                  ieCornerX + cr, ieCornerY + cr);
            mPath.arcTo(cornerBox, endAngle, 90f, false);

            float innerArcR = holeRad + cr;
            RectF innerArcBox = new RectF(cx - innerArcR, cy - innerArcR,
                                          cx + innerArcR, cy + innerArcR);
            mPath.arcTo(innerArcBox, endAngle - innerDelta, -(arcSweep - innerDelta * 2f));

            float isCornerX = cx + holeRad * (float) Math.cos(startRad);
            float isCornerY = cy + holeRad * (float) Math.sin(startRad);
            cornerBox = new RectF(isCornerX - cr, isCornerY - cr,
                                  isCornerX + cr, isCornerY + cr);
            mPath.arcTo(cornerBox, startAngle + 180f, 90f, false);

            mPath.close();

            float midAngle = startAngle + arcSweep / 2f;
            float midRad   = (float) Math.toRadians(midAngle);

            RadialGradient gradient = new RadialGradient(
                    cx, cy, radius,
                    new int[]{
                        darken(sliceColor, DARKEN_AMOUNT),  
                        darken(sliceColor, DARKEN_AMOUNT * 0.5f), 
                        sliceColor 
                    },
                    new float[]{
                        holeRad / radius,
                        (holeRad / radius + 1f) * 0.5f,
                        1f
                    },
                    Shader.TileMode.CLAMP);
            mFillPaint.setShader(gradient);

            c.drawPath(mPath, mFillPaint);

            float innerLineX = cx + (holeRad + lineStroke) * (float) Math.cos(startRad);
            float innerLineY = cy + (holeRad + lineStroke) * (float) Math.sin(startRad);
            float outerLineX = cx + (radius  - lineStroke) * (float) Math.cos(startRad);
            float outerLineY = cy + (radius  - lineStroke) * (float) Math.sin(startRad);
            c.drawLine(innerLineX, innerLineY, outerLineX, outerLineY, mLinePaint);

            angle += sweepAngle;
        }

        MPPointF.recycleInstance(center);
    }

    private static int darken(int color, float fraction) {
        int r = (int) (Color.red(color)   * (1f - fraction));
        int g = (int) (Color.green(color) * (1f - fraction));
        int b = (int) (Color.blue(color)  * (1f - fraction));
        return Color.argb(Color.alpha(color), r, g, b);
    }
}
