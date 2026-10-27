package app.tvkids.timelocker;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/** Aurora home: clock, frosted pills, a large title, a dock and one peeking row. */
public final class HomeBoard {
    public final FrameLayout root;
    public final TextView greeting;
    public final TextView clock;
    public final TextView ampm;
    public final TextView date;
    public final TextView hero;
    public final TextView warn;
    public final LinearLayout pills;
    private final TextView weatherLine;
    private final TextView aqiPill;
    private final LinearLayout week;
    private final LinearLayout content;
    private final LinearLayout dockRow;
    private final LinearLayout grid;
    private final ScrollView gridScroll;
    private final FrameLayout scrim;
    private final LinearLayout panelList;
    private final TextView panelTitle;
    private final int tileW;
    private final int tileH;
    private final float radius;

    public static final class Choice {
        public final String label;
        public final boolean dim;
        public final Runnable action;

        public Choice(String label, boolean dim, Runnable action) {
            this.label = label;
            this.dim = dim;
            this.action = action;
        }
    }

    private HomeBoard(Activity activity) {
        int width = activity.getResources().getDisplayMetrics().widthPixels;
        int gap = Ui.dp(activity, 14);
        int side = Ui.dp(activity, 56);
        tileW = Math.max(Ui.dp(activity, 140), (width - side * 2 - Ui.dp(activity, 28) * 2 - gap * 3) / 4);
        tileH = Math.round(tileW * 0.56f);
        radius = Ui.dp(activity, 16);

        root = new FrameLayout(activity);
        root.addView(new Aurora(activity), match());
        content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(side, Ui.dp(activity, 28), side, Ui.dp(activity, 18));
        content.setClipChildren(false);
        content.setClipToPadding(false);
        root.addView(content, match());

        LinearLayout header = new LinearLayout(activity);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setClipChildren(false);
        LinearLayout titles = new LinearLayout(activity);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        greeting = light(activity, 13, 0xFFD5E7EA);
        greeting.setLetterSpacing(0.16f);
        titles.addView(greeting);
        LinearLayout clockRow = new LinearLayout(activity);
        clockRow.setOrientation(LinearLayout.HORIZONTAL);
        clockRow.setGravity(Gravity.BOTTOM);
        clock = light(activity, 48, Color.WHITE);
        clock.setPadding(0, 0, Ui.dp(activity, 8), 0);
        clockRow.addView(clock);
        ampm = light(activity, 16, Color.WHITE);
        ampm.setPadding(0, 0, Ui.dp(activity, 14), Ui.dp(activity, 8));
        clockRow.addView(ampm);
        View rule = new View(activity);
        rule.setBackgroundColor(0x66FFFFFF);
        LinearLayout.LayoutParams ruleLp = new LinearLayout.LayoutParams(Ui.dp(activity, 1), Ui.dp(activity, 22));
        ruleLp.bottomMargin = Ui.dp(activity, 8);
        ruleLp.rightMargin = Ui.dp(activity, 14);
        rule.setLayoutParams(ruleLp);
        clockRow.addView(rule);
        date = light(activity, 16, 0xFFD0D8DE);
        date.setPadding(0, 0, 0, Ui.dp(activity, 8));
        clockRow.addView(date);
        titles.addView(clockRow);
        warn = light(activity, 13, 0xFFFFD60A);
        warn.setVisibility(View.GONE);
        titles.addView(warn);
        header.addView(titles);
        pills = new LinearLayout(activity);
        pills.setOrientation(LinearLayout.HORIZONTAL);
        pills.setGravity(Gravity.CENTER_VERTICAL);
        pills.setClipChildren(false);
        header.addView(pills);
        content.addView(header);

        LinearLayout weatherBand = new LinearLayout(activity);
        weatherBand.setOrientation(LinearLayout.VERTICAL);
        weatherBand.setPadding(0, Ui.dp(activity, 8), 0, Ui.dp(activity, 4));
        LinearLayout weatherTop = new LinearLayout(activity);
        weatherTop.setOrientation(LinearLayout.HORIZONTAL);
        weatherTop.setGravity(Gravity.CENTER_VERTICAL);
        weatherLine = light(activity, 18, Color.WHITE);
        weatherLine.setText("");
        weatherTop.addView(weatherLine, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        aqiPill = light(activity, 14, Color.WHITE);
        aqiPill.setFocusable(false);
        aqiPill.setVisibility(View.GONE);
        int aqiPadH = Ui.dp(activity, 12);
        int aqiPadV = Ui.dp(activity, 4);
        aqiPill.setPadding(aqiPadH, aqiPadV, aqiPadH, aqiPadV);
        weatherTop.addView(aqiPill);
        weatherBand.addView(weatherTop);
        week = new LinearLayout(activity);
        week.setOrientation(LinearLayout.HORIZONTAL);
        week.setPadding(0, Ui.dp(activity, 6), 0, 0);
        weatherBand.addView(week);
        content.addView(weatherBand);

        LinearLayout stage = new LinearLayout(activity);
        stage.setOrientation(LinearLayout.VERTICAL);
        stage.setGravity(Gravity.CENTER_VERTICAL);
        stage.setClipChildren(false);
        stage.setClipToPadding(false);
        hero = light(activity, 42, Color.WHITE);
        hero.setPadding(0, 0, 0, Ui.dp(activity, 16));
        stage.addView(hero);

        LinearLayout band = new LinearLayout(activity);
        band.setOrientation(LinearLayout.HORIZONTAL);
        band.setGravity(Gravity.CENTER_VERTICAL);
        band.setClipChildren(false);
        band.setClipToPadding(false);
        int bandPad = Ui.dp(activity, 14);
        band.setPadding(bandPad, bandPad, bandPad, bandPad);
        GradientDrawable plate = new GradientDrawable();
        plate.setCornerRadius(Ui.dp(activity, 28));
        plate.setColor(0xF010141C);
        plate.setStroke(Ui.dp(activity, 1), 0x28FFFFFF);
        band.setBackground(plate);
        dockRow = new LinearLayout(activity);
        dockRow.setOrientation(LinearLayout.HORIZONTAL);
        dockRow.setClipChildren(false);
        band.addView(dockRow);
        stage.addView(band);
        content.addView(stage, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        gridScroll = new ScrollView(activity);
        gridScroll.setFillViewport(true);
        gridScroll.setVerticalScrollBarEnabled(false);
        gridScroll.setClipChildren(false);
        gridScroll.setClipToPadding(false);
        grid = new LinearLayout(activity);
        grid.setOrientation(LinearLayout.VERTICAL);
        grid.setClipChildren(false);
        grid.setPadding(0, Ui.dp(activity, 12), 0, 0);
        gridScroll.addView(grid, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        content.addView(gridScroll);

        scrim = new FrameLayout(activity);
        scrim.setVisibility(View.GONE);
        scrim.setBackgroundColor(0xC0070A14);
        scrim.setClickable(true);
        LinearLayout card = new LinearLayout(activity);
        card.setOrientation(LinearLayout.VERTICAL);
        int cardPad = Ui.dp(activity, 22);
        card.setPadding(cardPad, cardPad, cardPad, cardPad);
        GradientDrawable glass = new GradientDrawable();
        glass.setCornerRadius(Ui.dp(activity, 24));
        glass.setColor(0xF01A1E28);
        glass.setStroke(Ui.dp(activity, 1), 0x33FFFFFF);
        card.setBackground(glass);
        panelTitle = light(activity, 24, Color.WHITE);
        panelTitle.setPadding(0, 0, 0, Ui.dp(activity, 8));
        card.addView(panelTitle);
        ScrollView panelScroll = new ScrollView(activity);
        panelList = new LinearLayout(activity);
        panelList.setOrientation(LinearLayout.VERTICAL);
        panelScroll.addView(panelList, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        card.addView(panelScroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        FrameLayout.LayoutParams cardLp = new FrameLayout.LayoutParams(Ui.dp(activity, 520), ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
        scrim.addView(card, cardLp);
        root.addView(scrim, match());
    }

    public static HomeBoard create(Activity activity) {
        return new HomeBoard(activity);
    }

    public TextView pill(String text, Runnable action) {
        TextView pill = new TextView(root.getContext());
        pill.setText(text);
        pill.setTextSize(16);
        pill.setTextColor(Color.WHITE);
        pill.setGravity(Gravity.CENTER);
        int h = Ui.dp(root.getContext(), 18);
        int v = Ui.dp(root.getContext(), 10);
        pill.setPadding(h, v, h, v);
        pill.setFocusable(true);
        pill.setClickable(true);
        pill.setDefaultFocusHighlightEnabled(false);
        paintPill(pill, false);
        pill.setOnFocusChangeListener((view, focused) -> paintPill((TextView) view, focused));
        pill.setOnClickListener(clicked -> {
            if (action != null) action.run();
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = Ui.dp(root.getContext(), 10);
        pill.setLayoutParams(lp);
        pills.addView(pill);
        return pill;
    }

    public void setTiles(List<Devices.Item> dock, List<Devices.Item> rest) {
        dockRow.removeAllViews();
        grid.removeAllViews();
        View first = null;
        for (int i = 0; i < dock.size(); i++) {
            View tile = tile(dock.get(i), i < dock.size() - 1);
            dockRow.addView(tile);
            if (first == null) first = tile;
        }
        LinearLayout row = null;
        for (int i = 0; i < rest.size(); i++) {
            if (i % 4 == 0) {
                row = new LinearLayout(root.getContext());
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setClipChildren(false);
                grid.addView(row);
            }
            row.addView(tile(rest.get(i), i % 4 != 3));
        }
        int rows = (rest.size() + 3) / 4;
        ViewGroup.LayoutParams lp = gridScroll.getLayoutParams();
        if (lp == null) lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        if (rest.isEmpty()) {
            gridScroll.setVisibility(View.GONE);
        } else if (rows <= 1) {
            gridScroll.setVisibility(View.VISIBLE);
            lp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
        } else {
            gridScroll.setVisibility(View.VISIBLE);
            lp.height = tileH + Ui.dp(root.getContext(), 36);
        }
        gridScroll.setLayoutParams(lp);
        if (dock.isEmpty() && rest.isEmpty()) hero.setText("還沒安裝 TizenTube");
        else if (!dock.isEmpty()) hero.setText(dock.get(0).label);
        if (first != null) {
            View focus = first;
            focus.post(focus::requestFocus);
        }
    }

    public void tickClock(boolean greet) {
        Calendar calendar = Calendar.getInstance();
        int hour = calendar.get(Calendar.HOUR);
        if (hour == 0) hour = 12;
        clock.setText(hour + ":" + String.format(Locale.US, "%02d", calendar.get(Calendar.MINUTE)));
        ampm.setText(calendar.get(Calendar.AM_PM) == Calendar.PM ? "下午" : "上午");
        if (greet) {
            int day = calendar.get(Calendar.HOUR_OF_DAY);
            greeting.setText(day < 5 ? "晚安" : day < 12 ? "早安" : day < 18 ? "午安" : "晚安");
            String[] weeks = {"", "星期日", "星期一", "星期二", "星期三", "星期四", "星期五", "星期六"};
            date.setText((calendar.get(Calendar.MONTH) + 1) + "月" + calendar.get(Calendar.DAY_OF_MONTH) + "日  " + weeks[calendar.get(Calendar.DAY_OF_WEEK)]);
        }
    }

    public void setStatus(String text) {
        date.setText(text);
    }

    public void setGreeting(String text) {
        greeting.setText(text);
    }

    /** Current reading plus the seven-day row. The row is not focusable. */
    public void showWeather(Weather.Report report) {
        weatherLine.setVisibility(View.VISIBLE);
        if (report == null) {
            weatherLine.setText(weatherPrefix() + "天氣讀取中");
            aqiPill.setVisibility(View.GONE);
            week.removeAllViews();
            return;
        }
        weatherLine.setText(weatherPrefix() + report.temp + "°  " + report.condition + "    體感 " + report.feels + "°");
        if (report.aqi < 0) {
            aqiPill.setVisibility(View.GONE);
        } else {
            aqiPill.setVisibility(View.VISIBLE);
            aqiPill.setText("空氣 " + report.aqi + " " + report.aqiLabel);
            int color = Weather.aqiColor(report.aqi);
            aqiPill.setTextColor(color);
            GradientDrawable shape = new GradientDrawable();
            shape.setCornerRadius(Ui.dp(aqiPill.getContext(), 14));
            shape.setColor(0x332A3140);
            shape.setStroke(Ui.dp(aqiPill.getContext(), 1), color);
            aqiPill.setBackground(shape);
        }
        week.removeAllViews();
        for (Weather.Day day : report.days) week.addView(dayChip(day));
    }

    public void showWeatherUnconfigured() {
        weatherLine.setText("");
        weatherLine.setVisibility(View.GONE);
        aqiPill.setVisibility(View.GONE);
        week.removeAllViews();
    }

    public void showWeatherMissing() {
        weatherLine.setVisibility(View.VISIBLE);
        weatherLine.setText(weatherPrefix() + "天氣暫時讀不到");
        aqiPill.setVisibility(View.GONE);
        week.removeAllViews();
    }

    private static String weatherPrefix() {
        if (Weather.PLACE == null || Weather.PLACE.isEmpty()) return "";
        return Weather.PLACE + "  ";
    }

    private LinearLayout dayChip(Weather.Day day) {
        LinearLayout col = new LinearLayout(root.getContext());
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        col.setFocusable(false);
        TextView name = light(root.getContext(), 13, 0xFFD0D8DE);
        name.setText(day.label);
        col.addView(name);
        TextView condition = light(root.getContext(), 13, 0xFF7EE0D6);
        condition.setText(day.condition);
        col.addView(condition);
        TextView high = light(root.getContext(), 16, Color.WHITE);
        high.setText(day.high + "°");
        col.addView(high);
        TextView low = light(root.getContext(), 13, 0xFF9AA4AE);
        low.setText(day.low + "°");
        col.addView(low);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        col.setLayoutParams(lp);
        return col;
    }

    public void warn(String text) {
        if (text == null || text.isEmpty()) {
            warn.setVisibility(View.GONE);
            return;
        }
        warn.setText(text);
        warn.setVisibility(View.VISIBLE);
    }

    public void showPanel(String title, List<Choice> choices) {
        panelTitle.setText(title);
        panelList.removeAllViews();
        View first = null;
        if (choices.isEmpty()) {
            TextView empty = Ui.note(root.getContext(), "這裡沒有項目");
            panelList.addView(empty);
        }
        for (Choice choice : choices) {
            TextView row = Ui.row(root.getContext(), choice.label);
            row.setAlpha(choice.dim ? 0.45f : 1f);
            row.setOnClickListener(v -> {
                hidePanel();
                if (choice.action != null) choice.action.run();
            });
            panelList.addView(row);
            if (first == null) first = row;
        }
        scrim.setVisibility(View.VISIBLE);
        content.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        if (first != null) {
            View focus = first;
            focus.post(focus::requestFocus);
        }
    }

    public void hidePanel() {
        scrim.setVisibility(View.GONE);
        content.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
    }

    public boolean panelOpen() {
        return scrim.getVisibility() == View.VISIBLE;
    }

    private View tile(Devices.Item item, boolean gap) {
        FrameLayout tile = new FrameLayout(root.getContext());
        GradientDrawable plate = new GradientDrawable();
        plate.setCornerRadius(radius);
        plate.setColor(0xFF16181E);
        tile.setBackground(plate);
        tile.setClipToOutline(true);
        tile.setOutlineProvider(ViewOutlineProvider.BACKGROUND);
        tile.setFocusable(true);
        tile.setClickable(true);
        tile.setDefaultFocusHighlightEnabled(false);
        Drawable art = item.art;
        if (art != null) {
            ImageView image = new ImageView(root.getContext());
            image.setImageDrawable(art);
            boolean wide = art.getIntrinsicWidth() > art.getIntrinsicHeight() * 1.15f && art.getIntrinsicWidth() > 0;
            image.setScaleType(wide ? ImageView.ScaleType.CENTER_CROP : ImageView.ScaleType.CENTER_INSIDE);
            if (!wide) {
                int inset = Ui.dp(root.getContext(), 16);
                image.setPadding(inset, inset, inset, inset);
            }
            tile.addView(image, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        } else {
            TextView letter = new TextView(root.getContext());
            letter.setText(item.label == null || item.label.isEmpty() ? "?" : item.label.substring(0, 1));
            letter.setTextColor(Color.WHITE);
            letter.setTextSize(28);
            letter.setGravity(Gravity.CENTER);
            tile.addView(letter, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(tileW, tileH);
        if (gap) lp.rightMargin = Ui.dp(root.getContext(), 14);
        lp.bottomMargin = Ui.dp(root.getContext(), 12);
        tile.setLayoutParams(lp);
        tile.setOnFocusChangeListener((view, focused) -> {
            hero.setText(item.label);
            GradientDrawable ring = new GradientDrawable();
            ring.setCornerRadius(radius);
            ring.setColor(Color.TRANSPARENT);
            if (focused) ring.setStroke(Ui.dp(root.getContext(), 3), Color.WHITE);
            view.setForeground(ring);
            view.animate().scaleX(focused ? 1.05f : 1f).scaleY(focused ? 1.05f : 1f).setDuration(120).start();
        });
        tile.setOnClickListener(v -> {
            if (item.pkg != null) {
                // Adult and child callers pass the right launcher through the tag.
            }
        });
        tile.setTag(item);
        return tile;
    }

    public void bind(boolean adult) {
        bindClicks(dockRow, adult);
        bindClicks(grid, adult);
    }

    private void bindClicks(ViewGroup group, boolean adult) {
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            if (child instanceof ViewGroup && !(child.getTag() instanceof Devices.Item)) {
                bindClicks((ViewGroup) child, adult);
                continue;
            }
            Object tag = child.getTag();
            if (!(tag instanceof Devices.Item)) continue;
            Devices.Item item = (Devices.Item) tag;
            child.setOnClickListener(v -> {
                if (adult) Devices.launchAny(root.getContext(), item.pkg);
                else Devices.launch(root.getContext(), item.pkg);
            });
        }
    }

    private static void paintPill(TextView pill, boolean focused) {
        GradientDrawable shape = new GradientDrawable();
        shape.setCornerRadius(Ui.dp(pill.getContext(), 24));
        shape.setColor(focused ? Color.WHITE : 0x332A3140);
        shape.setStroke(Ui.dp(pill.getContext(), 1), focused ? Color.WHITE : 0x66FFFFFF);
        pill.setBackground(shape);
        pill.setTextColor(focused ? Color.BLACK : Color.WHITE);
    }

    private static TextView light(android.content.Context context, int sp, int color) {
        TextView view = new TextView(context);
        view.setTextSize(sp);
        view.setTextColor(color);
        view.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        return view;
    }

    private static FrameLayout.LayoutParams match() {
        return new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
    }
}
