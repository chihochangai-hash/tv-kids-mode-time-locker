package app.cleanyourtv.home;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public final class Ui {
    private Ui() {
    }

    public static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    public static LinearLayout column(Context context) {
        LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setBackgroundColor(Color.BLACK);
        int pad = dp(context, 48);
        column.setPadding(pad, dp(context, 36), pad, dp(context, 48));
        return column;
    }

    public static ScrollView scroll(Context context, LinearLayout column) {
        ScrollView scroll = new ScrollView(context);
        scroll.setBackgroundColor(Color.TRANSPARENT);
        scroll.setClipChildren(false);
        scroll.setClipToPadding(false);
        scroll.setFillViewport(true);
        scroll.addView(column, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return scroll;
    }

    public static TextView title(Context context, String text) {
        TextView view = new TextView(context);
        view.setText(text);
        view.setTextSize(34);
        view.setTextColor(Color.WHITE);
        view.setPadding(0, 0, 0, dp(context, 8));
        return view;
    }

    public static TextView note(Context context, String text) {
        TextView view = new TextView(context);
        view.setText(text);
        view.setTextSize(16);
        view.setTextColor(0xFF94A3B8);
        view.setPadding(0, dp(context, 4), 0, dp(context, 8));
        return view;
    }

    public static TextView section(Context context, String text) {
        TextView view = note(context, text);
        view.setTextSize(18);
        view.setPadding(0, dp(context, 18), 0, dp(context, 4));
        return view;
    }

    public static TextView countdown(Context context) {
        TextView view = new TextView(context);
        view.setTextSize(14);
        view.setTypeface(Typeface.MONOSPACE);
        view.setTextColor(Color.WHITE);
        view.setFocusable(false);
        view.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        return view;
    }

    public static Button button(Context context, String text) {
        Button button = new Button(context);
        button.setText(text);
        button.setAllCaps(false);
        button.setTransformationMethod(null);
        button.setTextColor(Color.WHITE);
        button.setTextSize(18);
        button.setBackgroundResource(R.drawable.tile);
        button.setBackgroundTintList(null);
        button.setFocusable(true);
        button.setDefaultFocusHighlightEnabled(false);
        int pad = dp(context, 12);
        button.setPadding(pad, pad, pad, pad);
        button.setOnFocusChangeListener((v, focused) -> button.setTextColor(focused ? Color.BLACK : Color.WHITE));
        return button;
    }

    public static LinearLayout page(Context context) {
        LinearLayout column = column(context);
        column.setBackgroundColor(Color.TRANSPARENT);
        column.setClipChildren(false);
        column.setClipToPadding(false);
        return column;
    }

    public static FrameLayout screen(Context context, View content) {
        FrameLayout frame = new FrameLayout(context);
        frame.setClipChildren(false);
        frame.setClipToPadding(false);
        frame.setBackgroundColor(0xFF070A14);
        frame.addView(new Aurora(context), new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        frame.addView(content, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        return frame;
    }

    public static TextView row(Context context, String text) {
        TextView view = new TextView(context);
        view.setText(text);
        view.setTextSize(20);
        view.setTextColor(Color.WHITE);
        view.setGravity(Gravity.CENTER_VERTICAL);
        int pad = dp(context, 18);
        view.setPadding(pad, pad, pad, pad);
        view.setFocusable(true);
        view.setClickable(true);
        view.setDefaultFocusHighlightEnabled(false);
        paintRow(view, false);
        view.setOnFocusChangeListener((v, focused) -> {
            paintRow((TextView) v, focused);
            v.animate().scaleX(focused ? 1.03f : 1f).scaleY(focused ? 1.03f : 1f).setDuration(120).start();
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(context, 8);
        view.setLayoutParams(lp);
        return view;
    }

    public static LinearLayout orb(Context context, String name, String caption, int color) {
        LinearLayout box = new LinearLayout(context);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER_HORIZONTAL);
        box.setClipChildren(false);
        box.setClipToPadding(false);
        int side = dp(context, 14);
        box.setPadding(side, dp(context, 6), side, dp(context, 6));
        TextView avatar = new TextView(context);
        String letter = name == null || name.isEmpty() ? "?" : new String(Character.toChars(name.codePointAt(0)));
        avatar.setText(letter);
        avatar.setTextSize(30);
        avatar.setTextColor(Color.WHITE);
        avatar.setGravity(Gravity.CENTER);
        avatar.setTypeface(Typeface.DEFAULT_BOLD);
        int size = dp(context, 96);
        avatar.setLayoutParams(new LinearLayout.LayoutParams(size, size));
        avatar.setBackground(circle(color, 0));
        box.addView(avatar);
        TextView label = new TextView(context);
        label.setText(name);
        label.setTextSize(20);
        label.setTextColor(Color.WHITE);
        label.setGravity(Gravity.CENTER);
        label.setPadding(0, dp(context, 14), 0, 0);
        box.addView(label);
        if (caption != null && !caption.isEmpty()) {
            TextView sub = new TextView(context);
            sub.setText(caption);
            sub.setTextSize(13);
            sub.setTextColor(0xFFB7C0C8);
            sub.setGravity(Gravity.CENTER);
            sub.setPadding(0, dp(context, 4), 0, 0);
            box.addView(sub);
        }
        box.setFocusable(true);
        box.setClickable(true);
        box.setDefaultFocusHighlightEnabled(false);
        box.setOnFocusChangeListener((v, focused) -> {
            avatar.setBackground(circle(color, focused ? dp(context, 4) : 0));
            v.animate().scaleX(focused ? 1.04f : 1f).scaleY(focused ? 1.04f : 1f).setDuration(140).start();
        });
        return box;
    }

    public static int profileColor(String id) {
        if ("eason".equals(id)) return 0xFF3B82F6;
        if ("yian".equals(id)) return 0xFFFF9F0A;
        if ("yvonne".equals(id)) return 0xFFBF5AF2;
        if ("adult".equals(id)) return 0xFF5AC8FA;
        int mix = id == null ? 0 : Math.abs(id.hashCode());
        int[] palette = {0xFF30D158, 0xFFFF375F, 0xFF64D2FF, 0xFFFFD60A};
        return palette[mix % palette.length];
    }

    private static void paintRow(TextView view, boolean focused) {
        GradientDrawable shape = new GradientDrawable();
        shape.setCornerRadius(dp(view.getContext(), 18));
        shape.setColor(focused ? Color.WHITE : 0xCC1A1D27);
        shape.setStroke(dp(view.getContext(), 1), focused ? Color.WHITE : 0x33FFFFFF);
        view.setBackground(shape);
        view.setTextColor(focused ? Color.BLACK : Color.WHITE);
    }

    private static GradientDrawable circle(int color, int stroke) {
        GradientDrawable shape = new GradientDrawable();
        shape.setShape(GradientDrawable.OVAL);
        shape.setColor(color);
        if (stroke > 0) shape.setStroke(stroke, Color.WHITE);
        return shape;
    }

    public static void stack(View view, int topMargin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = topMargin;
        view.setLayoutParams(params);
    }
}
