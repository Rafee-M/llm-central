package org.llmcentral.app.ui;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Build;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Switch;

import org.llmcentral.app.R;
import org.llmcentral.app.theme.Scheme;

/**
 * A small Material 3 switch for the app's native menus (the account menu's "Active accounts").
 *
 * <p><b>Why draw our own:</b> Android's built-in {@link Switch} looks like Android 7–11 depending on the
 * phone, and a Material 3 switch would need the Material Components library, which the app doesn't use
 * (decision D1, APK size). This view draws the Material 3 shapes itself with a {@link Canvas}, in the
 * theme's colours ({@link Scheme}), so it follows the palette, Material You and dark mode.</p>
 *
 * <p><b>Shape:</b> the same proportions as the Settings page's switch (settings.html {@code .track}),
 * at 75 % so it fits a menu row: a 39 × 24 dp pill. Off: {@code surfaceContainerHighest} track, a
 * 1.5 dp {@code outline} border and a small 12 dp {@code outline} thumb on the left. On: a
 * {@code primary} track and a bigger 18 dp {@code onPrimary} thumb on the right. Changes slide in
 * 150 ms. Spec: https://m3.material.io/components/switch/specs</p>
 *
 * <p>It is only a picture: the menu row around it is what you tap ({@link #describeRowAsSwitch} tells
 * screen readers that the row is a switch and whether it's on).</p>
 */
public final class M3Switch extends View {

    private static final long SLIDE_MS = 150;

    // Allocated once: onDraw runs every animation frame and must not create objects.
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint border = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF track = new RectF();
    private final ArgbEvaluator colors = new ArgbEvaluator();
    private final float density;

    private Scheme scheme;
    private boolean checked;
    private float progress; // 0 = off, 1 = on; in between while sliding
    private ValueAnimator slide;

    public M3Switch(Context context) {
        super(context);
        density = context.getResources().getDisplayMetrics().density;
        border.setStyle(Paint.Style.STROKE);
        border.setStrokeWidth(1.5f * density);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO); // the row speaks for it
    }

    /** The theme colours to draw with (call before the first draw and after a theme change). */
    public void setScheme(Scheme scheme) {
        this.scheme = scheme;
        invalidate();
    }

    public boolean isChecked() {
        return checked;
    }

    /** @param animate true to slide the thumb (a tap), false to jump (initial state) */
    public void setChecked(boolean checked, boolean animate) {
        if (slide != null) {
            slide.cancel();
        }
        this.checked = checked;
        float target = checked ? 1f : 0f;
        if (!animate || !isAttachedToWindow()) {
            progress = target;
            invalidate();
            return;
        }
        slide = ValueAnimator.ofFloat(progress, target);
        slide.setDuration(SLIDE_MS);
        slide.addUpdateListener(a -> {
            progress = (float) a.getAnimatedValue();
            invalidate();
        });
        slide.start();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension(resolveSize(Math.round(39 * density), widthMeasureSpec),
                resolveSize(Math.round(24 * density), heightMeasureSpec));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (scheme == null) {
            return;
        }
        float w = getWidth();
        float h = getHeight();
        float inset = border.getStrokeWidth() / 2f; // keep the border inside the view
        track.set(inset, inset, w - inset, h - inset);
        float radius = track.height() / 2f;

        int trackColor = (int) colors.evaluate(progress,
                scheme.get("surfaceContainerHighest"), scheme.get(Scheme.PRIMARY));
        fill.setColor(trackColor);
        canvas.drawRoundRect(track, radius, radius, fill);
        border.setColor((int) colors.evaluate(progress, scheme.get(Scheme.OUTLINE), scheme.get(Scheme.PRIMARY)));
        canvas.drawRoundRect(track, radius, radius, border);

        // Thumb: 12 dp at the left (off) → 18 dp at the right (on); its centre moves from 12 to 27 dp
        // of the 39 dp width. Written as fractions of the real size, so it also fits a stretched view.
        float thumbRadius = h * (6f + 3f * progress) / 24f;
        float cx = w * (12f + 15f * progress) / 39f;
        fill.setColor((int) colors.evaluate(progress, scheme.get(Scheme.OUTLINE), scheme.get(Scheme.ON_PRIMARY)));
        canvas.drawCircle(cx, h / 2f, thumbRadius, fill);
    }

    @Override
    protected void onDetachedFromWindow() {
        if (slide != null) {
            slide.cancel(); // the menu closed mid-slide: stop the animation (no leaks, no drawing)
        }
        super.onDetachedFromWindow();
    }

    /**
     * Makes TalkBack announce {@code row} (the tappable menu row holding the switch) as a switch with its
     * on/off state, e.g. "Active accounts, switch, on".
     */
    public static void describeRowAsSwitch(View row, M3Switch toggle) {
        row.setAccessibilityDelegate(new View.AccessibilityDelegate() {
            @Override
            public void onInitializeAccessibilityNodeInfo(View host, AccessibilityNodeInfo info) {
                super.onInitializeAccessibilityNodeInfo(host, info);
                info.setClassName(Switch.class.getName());
                info.setCheckable(true);
                info.setChecked(toggle.isChecked());
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    info.setStateDescription(host.getContext().getString(
                            toggle.isChecked() ? R.string.state_on : R.string.state_off));
                }
            }
        });
    }
}
