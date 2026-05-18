package com.gree1d.reappzuku;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.Bundle;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;

public class ColorPickerDialog {

    public interface OnColorPickedListener {
        void onColorPicked(int color);
    }

    public static void show(Context context, int initialColor, OnColorPickedListener listener) {
        float[] hsv = new float[3];
        Color.colorToHSV(initialColor, hsv);

        final float[] currentHsv = { hsv[0], hsv[1], hsv[2] };

        LayoutInflater inflater = LayoutInflater.from(context);
        View view = inflater.inflate(R.layout.dialog_color_picker, null);

        HueSaturationView hsvView = view.findViewById(R.id.color_picker_hsv);
        HueBarView hueBar         = view.findViewById(R.id.color_picker_hue);
        View previewView          = view.findViewById(R.id.color_picker_preview);

        hsvView.setHsv(currentHsv[0], currentHsv[1], currentHsv[2]);
        hueBar.setHue(currentHsv[0]);
        previewView.setBackgroundColor(Color.HSVToColor(currentHsv));

        hueBar.setOnHueChangedListener(hue -> {
            currentHsv[0] = hue;
            hsvView.setHue(hue);
            previewView.setBackgroundColor(Color.HSVToColor(currentHsv));
        });

        hsvView.setOnSatValChangedListener((sat, val) -> {
            currentHsv[1] = sat;
            currentHsv[2] = val;
            previewView.setBackgroundColor(Color.HSVToColor(currentHsv));
        });

        AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle(R.string.settings_accent_custom_picker_title)
                .setView(view)
                .setPositiveButton(R.string.dialog_save, (d, w) -> {
                    if (listener != null) listener.onColorPicked(Color.HSVToColor(currentHsv));
                })
                .setNegativeButton(R.string.dialog_cancel, null)
                .create();
        dialog.show();
    }

    public static class HueSaturationView extends View {

        public interface OnSatValChangedListener {
            void onChanged(float sat, float val);
        }

        private float hue = 0f;
        private float sat = 1f;
        private float val = 1f;
        private OnSatValChangedListener listener;

        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();

        public HueSaturationView(Context ctx) { super(ctx); }
        public HueSaturationView(Context ctx, AttributeSet attrs) { super(ctx, attrs); }

        public void setHsv(float h, float s, float v) {
            hue = h; sat = s; val = v;
            invalidate();
        }

        public void setHue(float h) {
            hue = h;
            invalidate();
        }

        public void setOnSatValChangedListener(OnSatValChangedListener l) { listener = l; }

        @Override
        protected void onDraw(Canvas canvas) {
            int w = getWidth(), h = getHeight();
            rect.set(0, 0, w, h);

            Shader satShader = new LinearGradient(0, 0, w, 0,
                    Color.WHITE, Color.HSVToColor(new float[]{hue, 1f, 1f}),
                    Shader.TileMode.CLAMP);
            paint.setShader(satShader);
            canvas.drawRect(rect, paint);

            Shader valShader = new LinearGradient(0, 0, 0, h,
                    Color.TRANSPARENT, Color.BLACK,
                    Shader.TileMode.CLAMP);
            paint.setShader(valShader);
            canvas.drawRect(rect, paint);

            float cx = sat * w;
            float cy = (1f - val) * h;
            paint.setShader(null);
            paint.setColor(Color.WHITE);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(3f);
            canvas.drawCircle(cx, cy, 12f, paint);
            paint.setColor(Color.BLACK);
            paint.setStrokeWidth(1.5f);
            canvas.drawCircle(cx, cy, 12f, paint);
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            if (event.getAction() == MotionEvent.ACTION_DOWN
                    || event.getAction() == MotionEvent.ACTION_MOVE) {
                sat = Math.max(0f, Math.min(1f, event.getX() / getWidth()));
                val = Math.max(0f, Math.min(1f, 1f - event.getY() / getHeight()));
                invalidate();
                if (listener != null) listener.onChanged(sat, val);
            }
            return true;
        }
    }

    public static class HueBarView extends View {

        public interface OnHueChangedListener {
            void onHueChanged(float hue);
        }

        private float hue = 0f;
        private OnHueChangedListener listener;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        public HueBarView(Context ctx) { super(ctx); }
        public HueBarView(Context ctx, AttributeSet attrs) { super(ctx, attrs); }

        public void setHue(float h) { hue = h; invalidate(); }
        public void setOnHueChangedListener(OnHueChangedListener l) { listener = l; }

        @Override
        protected void onDraw(Canvas canvas) {
            int w = getWidth(), h = getHeight();

            int[] colors = new int[361];
            for (int i = 0; i <= 360; i++) {
                colors[i] = Color.HSVToColor(new float[]{i, 1f, 1f});
            }
            Shader hueShader = new LinearGradient(0, 0, w, 0, colors, null, Shader.TileMode.CLAMP);
            paint.setShader(hueShader);
            canvas.drawRoundRect(0, 0, w, h, h / 2f, h / 2f, paint);

            float cx = hue / 360f * w;
            paint.setShader(null);
            paint.setColor(Color.WHITE);
            paint.setStyle(Paint.Style.FILL);
            canvas.drawCircle(cx, h / 2f, h / 2f - 2, paint);
            paint.setColor(Color.DKGRAY);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(2f);
            canvas.drawCircle(cx, h / 2f, h / 2f - 2, paint);
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            if (event.getAction() == MotionEvent.ACTION_DOWN
                    || event.getAction() == MotionEvent.ACTION_MOVE) {
                hue = Math.max(0f, Math.min(360f, event.getX() / getWidth() * 360f));
                invalidate();
                if (listener != null) listener.onHueChanged(hue);
            }
            return true;
        }
    }
}
