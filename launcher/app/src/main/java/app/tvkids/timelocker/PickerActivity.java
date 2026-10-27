package app.tvkids.timelocker;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/** Account row in the Google TV arrangement: parent first, then each child. */
public class PickerActivity extends TvActivity {
    private static final String HOURS =
            "平日 8:00–21:00，週六日 8:00–22:00。過了這個時間只準家長登入。切換帳號會留下剩餘時間。";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private TextView note;
    private Runnable tick;
    private String shown = "";
    private String noteHome = HOURS;
    private String preferFocus = "";
    private boolean preferTime;
    private boolean editing;
    private String editingId = "";
    private int editIndex;

    @Override
    protected void onResume() {
        super.onResume();
        if (isFinishing()) return;
        show();
    }

    private void show() {
        editing = false;
        editingId = "";
        Store store = Store.get(this);
        if (!store.hasAdultPin()) {
            Intent intent = new Intent(this, PinActivity.class);
            intent.putExtra("id", "adult");
            intent.putExtra("create", true);
            startActivity(intent);
            return;
        }
        long now = System.currentTimeMillis();
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setGravity(Gravity.CENTER_HORIZONTAL);
        page.setBackgroundColor(Color.TRANSPARENT);
        page.setClipChildren(false);
        page.setClipToPadding(false);
        int pad = Ui.dp(this, 36);
        page.setPadding(pad, pad, pad, pad);

        TextView brand = new TextView(this);
        brand.setText("收看時間");
        brand.setTextSize(16);
        brand.setTextColor(0xFFC5D0D6);
        brand.setLetterSpacing(0.14f);
        brand.setGravity(Gravity.CENTER);
        page.addView(brand);

        TextView title = Ui.title(this, "選擇帳號");
        title.setTextSize(42);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, Ui.dp(this, 18), 0, 0);
        page.addView(title);

        note = Ui.note(this, HOURS);
        note.setGravity(Gravity.CENTER);
        String ended = store.takeEnded();
        if (!ended.isEmpty()) note.setText(ended);
        noteHome = note.getText().toString();
        page.addView(note);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        row.setClipChildren(false);
        row.setClipToPadding(false);
        row.setPadding(0, Ui.dp(this, 16), 0, Ui.dp(this, 8));
        List<Store.Person> people = new ArrayList<>();
        Store.Person adult = null;
        for (Store.Person person : store.people()) {
            if (person.adult) adult = person;
            else people.add(person);
        }
        if (adult != null) people.add(0, adult);

        int count = people.size();
        int[] avatarIds = new int[count];
        int[] timeIds = new int[count];
        View[] avatars = new View[count];
        TextView[] times = new TextView[count];
        View first = null;
        String focusId = preferFocus;
        boolean focusTime = preferTime;
        preferFocus = "";
        preferTime = false;
        View preferred = null;
        boolean open = Gate.kidAllowedNow(now);
        int manageId = View.generateViewId();

        for (int i = 0; i < count; i++) {
            Store.Person person = people.get(i);
            boolean free = !person.adult && store.freeToday(person.id, now);
            boolean spent = !person.adult && store.spent(person.id);
            boolean recorded = spent || free || (!person.adult && store.bankToday(person.id, now) > 0);
            String status = statusText(person, store, spent, open, free, now);
            LinearLayout column = new LinearLayout(this);
            column.setOrientation(LinearLayout.VERTICAL);
            column.setGravity(Gravity.CENTER_HORIZONTAL);
            column.setClipChildren(false);
            column.setClipToPadding(false);
            LinearLayout.LayoutParams columnLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            int gap = Ui.dp(this, 22);
            columnLp.leftMargin = gap;
            columnLp.rightMargin = gap;
            column.setLayoutParams(columnLp);

            LinearLayout orb = Ui.orb(this, person.name, "", Ui.profileColor(person.id));
            avatarIds[i] = View.generateViewId();
            orb.setId(avatarIds[i]);
            if (!person.adult && !free && (!open || spent)) orb.setAlpha(0.45f);
            TextView time = null;
            if (!person.adult) {
                time = timeChip(person, status, recorded);
                timeIds[i] = View.generateViewId();
                time.setId(timeIds[i]);
                times[i] = time;
            }
            Store.Person picked = person;
            TextView chip = time;
            orb.setOnClickListener(v -> choose(picked, chip));
            column.addView(orb);
            if (time != null) column.addView(time);
            row.addView(column);
            avatars[i] = orb;
            if (first == null) first = orb;
            if (person.id.equals(focusId)) preferred = focusTime && time != null ? time : orb;
        }
        page.addView(row);

        for (int i = 0; i < count; i++) {
            int left = i > 0 ? avatarIds[i - 1] : avatarIds[i];
            int right = i + 1 < count ? avatarIds[i + 1] : avatarIds[i];
            avatars[i].setNextFocusLeftId(left);
            avatars[i].setNextFocusRightId(right);
            if (times[i] != null) {
                avatars[i].setNextFocusDownId(timeIds[i]);
                times[i].setNextFocusUpId(avatarIds[i]);
                times[i].setNextFocusDownId(manageId);
                int timeLeft = previousTime(timeIds, i);
                int timeRight = nextTime(timeIds, i);
                times[i].setNextFocusLeftId(timeLeft);
                times[i].setNextFocusRightId(timeRight);
            } else {
                avatars[i].setNextFocusDownId(manageId);
            }
        }

        android.view.View spacer = new android.view.View(this);
        spacer.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        page.addView(spacer);

        TextView manage = new TextView(this);
        manage.setId(manageId);
        manage.setText("管理帳號");
        manage.setTextSize(18);
        manage.setTextColor(Color.WHITE);
        manage.setGravity(Gravity.CENTER);
        int hp = Ui.dp(this, 36);
        int vp = Ui.dp(this, 14);
        manage.setPadding(hp, vp, hp, vp);
        manage.setFocusable(true);
        manage.setClickable(true);
        manage.setDefaultFocusHighlightEnabled(false);
        paintManage(manage, false);
        manage.setOnFocusChangeListener((v, focused) -> paintManage(manage, focused));
        manage.setOnClickListener(v -> {
            Intent intent = new Intent(this, PinActivity.class);
            intent.putExtra("id", "adult");
            intent.putExtra("create", false);
            intent.putExtra("manage", true);
            startActivity(intent);
        });
        LinearLayout.LayoutParams manageLp = new LinearLayout.LayoutParams(
                Ui.dp(this, 320), LinearLayout.LayoutParams.WRAP_CONTENT);
        manageLp.bottomMargin = Ui.dp(this, 12);
        manage.setLayoutParams(manageLp);
        page.addView(manage);

        setContentView(Ui.screen(this, page));
        View focus = preferred != null ? preferred : first;
        if (focus != null) {
            View target = focus;
            target.post(target::requestFocus);
        }
        shown = banks(store, now);
        tick = () -> {
            if (isFinishing()) return;
            if (editing) {
                handler.postDelayed(tick, 2000);
                return;
            }
            Store current = Store.get(this);
            long at = System.currentTimeMillis();
            String next = banks(current, at);
            if (!next.equals(shown)) show();
            else handler.postDelayed(tick, 2000);
        };
        handler.removeCallbacksAndMessages(null);
        handler.postDelayed(tick, 2000);
    }

    private static String statusText(Store.Person person, Store store, boolean spent, boolean open, boolean free, long now) {
        if (free && open) return "今天不限";
        if (free) return "現在不能看";
        if (!person.hasPin) return "尚未設定";
        long bank = store.bankToday(person.id, now);
        if (bank > 0) {
            return "還剩 " + Gate.clock((int) ((bank + 999) / 1000));
        }
        if (spent) return "今天用完";
        if (!open) return "現在不能看";
        return "這次 " + person.minutes + " 分鐘";
    }

    private TextView timeChip(Store.Person person, String status, boolean recorded) {
        TextView time = new TextView(this);
        time.setText(status);
        time.setTextSize(16);
        time.setGravity(Gravity.CENTER);
        time.setMinWidth(Ui.dp(this, 156));
        int pad = Ui.dp(this, 10);
        time.setPadding(pad, Ui.dp(this, 8), pad, Ui.dp(this, 8));
        time.setFocusable(true);
        time.setClickable(true);
        time.setDefaultFocusHighlightEnabled(false);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Ui.dp(this, 8);
        time.setLayoutParams(lp);
        paintTime(time, false);
        time.setOnFocusChangeListener((v, focused) -> {
            TextView view = (TextView) v;
            paintTime(view, focused);
            if (!focused && editing && person.id.equals(editingId)) {
                editing = false;
                editingId = "";
                view.setText(status);
                if (note != null) note.setText(noteHome);
                return;
            }
            if (focused && !editing && note != null) {
                note.setText(recorded
                        ? person.name + "：按中間鍵，上下選擇要加的時間，最後一格是今天不限。"
                        : person.name + "：選頭像登入。要今天不限時，按中間鍵後選無限制。");
            }
        });
        time.setOnKeyListener((v, key, event) -> {
            if (!editing || !person.id.equals(editingId)) return false;
            if (key == KeyEvent.KEYCODE_DPAD_UP || key == KeyEvent.KEYCODE_DPAD_DOWN) {
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    int next = editIndex + (key == KeyEvent.KEYCODE_DPAD_UP ? -1 : 1);
                    if (next >= 0 && next < Gate.pickerSteps()) {
                        editIndex = next;
                        ((TextView) v).setText(roller(editIndex));
                        showGrant(person);
                    }
                }
                return true;
            }
            if (key == KeyEvent.KEYCODE_DPAD_LEFT || key == KeyEvent.KEYCODE_DPAD_RIGHT) return true;
            return false;
        });
        time.setOnClickListener(v -> onTime(person, time, status, recorded));
        return time;
    }

    private void onTime(Store.Person person, TextView time, String status, boolean recorded) {
        if (!editing || !person.id.equals(editingId)) {
            editing = true;
            editingId = person.id;
            editIndex = recorded ? 0 : Gate.pickerSteps() - 1;
            time.setText(roller(editIndex));
            showGrant(person);
            return;
        }
        int choice = Gate.pickerChoice(editIndex);
        if (choice != Gate.PICKER_FREE && !recorded) {
            if (note != null) {
                note.setText(person.name + " 這一輪還沒用過。選頭像登入就會開始。要今天不限時，請選最後一格。");
            }
            return;
        }
        preferFocus = person.id;
        Intent intent = new Intent(this, PinActivity.class);
        intent.putExtra("id", "adult");
        intent.putExtra("create", false);
        intent.putExtra("topup", true);
        intent.putExtra("kid", person.id);
        intent.putExtra("free", choice == Gate.PICKER_FREE);
        intent.putExtra("minutes", choice == Gate.PICKER_FREE ? 0 : choice);
        startActivity(intent);
    }

    private void showGrant(Store.Person person) {
        if (note == null) return;
        if (Gate.pickerChoice(editIndex) == Gate.PICKER_FREE) {
            note.setText("給 " + person.name + " 今天不限時。用量不限，但仍要在"
                    + Gate.kidWindowNow(System.currentTimeMillis())
                    + "。只有今天，過了午夜恢復原來的時間。中間鍵完成，然後輸入家長密碼。");
            return;
        }
        int minutes = Gate.pickerChoice(editIndex);
        long next = Gate.shift(Store.get(this).bankToday(person.id, System.currentTimeMillis()), "add", minutes);
        int sec = (int) ((next + 999) / 1000);
        note.setText("給 " + person.name + " " + Gate.pickerAddLabel(minutes)
                + "，之後還剩 " + Gate.clock(sec) + "。中間鍵完成，然後輸入家長密碼。");
    }

    private CharSequence roller(int index) {
        String prev = index > 0 ? Gate.pickerChoiceLabel(index - 1) : " ";
        String cur = Gate.pickerChoiceLabel(index);
        String next = index + 1 < Gate.pickerSteps() ? Gate.pickerChoiceLabel(index + 1) : " ";
        String all = prev + "\n" + cur + "\n" + next;
        SpannableString span = new SpannableString(all);
        span.setSpan(new ForegroundColorSpan(0x66000000), 0, prev.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        int at = prev.length() + 1;
        span.setSpan(new RelativeSizeSpan(1.25f), at, at + cur.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        span.setSpan(new StyleSpan(Typeface.BOLD), at, at + cur.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        int tail = at + cur.length() + 1;
        if (tail < all.length()) {
            span.setSpan(new ForegroundColorSpan(0x66000000), tail, all.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return span;
    }

    private static int previousTime(int[] timeIds, int index) {
        for (int i = index - 1; i >= 0; i--) if (timeIds[i] != 0) return timeIds[i];
        return timeIds[index];
    }

    private static int nextTime(int[] timeIds, int index) {
        for (int i = index + 1; i < timeIds.length; i++) if (timeIds[i] != 0) return timeIds[i];
        return timeIds[index];
    }

    private static String banks(Store store, long now) {
        StringBuilder text = new StringBuilder();
        text.append(Gate.dayKey(now)).append(';').append(Gate.kidAllowedNow(now)).append(';');
        for (Store.Person person : store.people()) {
            text.append(person.id).append('=').append(store.bankToday(person.id, now));
            text.append(store.freeToday(person.id, now) ? 'F' : '-').append(';');
        }
        return text.toString();
    }

    private void choose(Store.Person person, TextView time) {
        long now = System.currentTimeMillis();
        if (!person.adult && Store.get(this).spent(person.id)) {
            if (note != null) {
                note.setText(person.name + " 今天的時間已用完。剩餘不會留到隔天，隔天重新給一次。要現在加，在下面的時間按中間鍵，再輸入家長密碼。");
            }
            if (time != null) time.requestFocus();
            return;
        }
        if (!person.adult && !Gate.kidAllowedNow(now)) {
            if (note != null) {
                String extra = Store.get(this).freeToday(person.id, now)
                        ? "今天不限也要在這個時段。"
                        : "剩下的時間會留著，只準家長登入。";
                note.setText(person.name + " 現在不能看。" + Gate.kidWindowNow(now) + "。" + extra);
            }
            return;
        }
        Intent intent = new Intent(this, PinActivity.class);
        intent.putExtra("id", person.id);
        intent.putExtra("create", !person.hasPin);
        startActivity(intent);
    }

    private void paintTime(TextView view, boolean focused) {
        GradientDrawable shape = new GradientDrawable();
        shape.setCornerRadius(Ui.dp(this, 16));
        shape.setColor(focused ? Color.WHITE : 0xCC1A1D27);
        view.setBackground(shape);
        view.setTextColor(focused ? Color.BLACK : Color.WHITE);
    }

    private void paintManage(TextView view, boolean focused) {
        GradientDrawable shape = new GradientDrawable();
        shape.setCornerRadius(Ui.dp(this, 28));
        shape.setColor(focused ? Color.WHITE : 0xFF3A3D44);
        view.setBackground(shape);
        view.setTextColor(focused ? Color.BLACK : Color.WHITE);
    }

    @Override
    public void onBackPressed() {
        if (editing) {
            preferFocus = editingId;
            preferTime = true;
            show();
        }
    }

    @Override
    protected void onPause() {
        handler.removeCallbacksAndMessages(null);
        super.onPause();
    }
}
