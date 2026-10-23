package com.family.watile;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * "WhatsApp Beta": a contact grid with photos, names and numbers, laid out like a TV video-calling
 * app (title, search bar, six-across grid, blue focus outline). Pick a person, then Video or Voice;
 * the call itself goes through WhatsApp, and you come back here when it ends.
 */
public class FamilyActivity extends Activity {
    private static final int BG = 0xFF101820;
    private static final int SEARCH_BG = 0xFF1A2430;
    private static final int SEARCH_LINE = 0xFF3A4757;
    private static final int ACCENT = 0xFF4C8DF6;
    private static final int TILE_GREY = 0xFF3C4043;
    private static final int TEXT = 0xFFE8EAED;
    private static final int TEXT_DIM = 0xFF9AA0A6;
    private static final int[] AVATAR_COLORS = {
            0xFF1A73E8, 0xFF8E44EC, 0xFF188038, 0xFFE37400, 0xFFD93025, 0xFF12B5CB, 0xFFE52592, 0xFF5F6368};
    private static final int COLUMNS = 6;
    private static final long RING_CHECK_MS = 1000;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private FrameLayout root;
    private GridLayout grid;
    private EditText search;
    private TextView noMatches;
    private TextView helperWarning;
    private CallChooser chooser;
    private long lastRingJump;
    private int tileWidthDp;
    private int photoDp;
    private List<FamilyStore.Person> people = new ArrayList<>();

    // If WhatsApp starts ringing while this screen is open, hand over to WhatsApp so its call screen
    // opens; the helper brings us back here once the call is over (or missed).
    private final Runnable ringWatch = new Runnable() {
        @Override
        public void run() {
            AudioManager audio = (AudioManager) getSystemService(AUDIO_SERVICE);
            if (audio != null && audio.getMode() == AudioManager.MODE_RINGTONE
                    && SystemClock.uptimeMillis() - lastRingJump > 15000) {
                lastRingJump = SystemClock.uptimeMillis();
                chooser.dismiss();
                CallHelperService.handedOverFromBeta(true);
                openWhatsApp();
            }
            handler.postDelayed(this, RING_CHECK_MS);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        int screenDp = getResources().getConfiguration().screenWidthDp;
        tileWidthDp = (screenDp - 2 * 24) / COLUMNS - 2 * 6;
        photoDp = Math.min(88, tileWidthDp - 44);
        FamilyStore.deleteOldCopiedPhotos(this);

        root = new FrameLayout(this);
        root.setBackgroundColor(BG);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setGravity(Gravity.CENTER_HORIZONTAL);
        page.addView(title());
        page.addView(searchBar());

        helperWarning = text("Turn on “WhatsApp call helper” in Settings › System › Accessibility "
                + "so calls start by themselves.", 15, 0xFFFFD279, false);
        helperWarning.setPadding(dp(48), dp(8), dp(48), 0);
        page.addView(helperWarning);

        noMatches = text("No contacts match your search", 18, TEXT_DIM, false);
        noMatches.setPadding(0, dp(40), 0, 0);
        noMatches.setVisibility(View.GONE);
        page.addView(noMatches);

        grid = new GridLayout(this);
        grid.setColumnCount(COLUMNS);
        grid.setPadding(dp(24), dp(10), dp(24), dp(24));
        ScrollView scroll = new ScrollView(this);
        scroll.addView(grid, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        page.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        root.addView(page);
        setContentView(root);
        chooser = new CallChooser(this, root);
    }

    @Override
    protected void onResume() {
        super.onResume();
        helperWarning.setVisibility(CallHelperService.isRunning() ? View.GONE : View.VISIBLE);
        people = FamilyStore.load(this);
        showPeople(search.getText().toString());
        if (grid.getChildCount() > 0) grid.getChildAt(0).requestFocus();
        handler.post(ringWatch);
    }

    @Override
    protected void onPause() {
        handler.removeCallbacks(ringWatch);
        chooser.dismiss();
        super.onPause();
    }

    // ---- Layout ----

    private View title() {
        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER);
        bar.setPadding(0, dp(18), 0, dp(14));
        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.wa_logo);
        bar.addView(logo, new LinearLayout.LayoutParams(dp(34), dp(34)));
        TextView name = text(getString(R.string.app_name), 26, TEXT, false);
        name.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        name.setPadding(dp(12), 0, 0, 0);
        bar.addView(name);
        return bar;
    }

    private View searchBar() {
        final LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(20), 0, dp(20), 0);
        bar.setBackground(pill(false));

        ImageView icon = new ImageView(this);
        icon.setImageResource(android.R.drawable.ic_menu_search);
        icon.setImageTintList(ColorStateList.valueOf(TEXT_DIM));
        bar.addView(icon, new LinearLayout.LayoutParams(dp(24), dp(24)));

        search = new EditText(this);
        search.setHint("Search contacts by name or number");
        search.setHintTextColor(TEXT_DIM);
        search.setTextColor(TEXT);
        search.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        search.setSingleLine(true);
        search.setBackground(null);
        search.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        search.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        search.setOnFocusChangeListener((v, focused) -> bar.setBackground(pill(focused)));
        search.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                showPeople(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
        LinearLayout.LayoutParams searchLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        searchLp.leftMargin = dp(12);
        bar.addView(search, searchLp);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(640), dp(50));
        lp.bottomMargin = dp(6);
        bar.setLayoutParams(lp);
        return bar;
    }

    private void showPeople(String query) {
        grid.removeAllViews();
        String q = query.trim().toLowerCase(Locale.ROOT);
        String qDigits = q.replaceAll("[^0-9]", "");
        int shown = 0;
        for (final FamilyStore.Person person : people) {
            boolean match = q.isEmpty()
                    || person.displayName.toLowerCase(Locale.ROOT).contains(q)
                    || person.whatsappName.toLowerCase(Locale.ROOT).contains(q)
                    || (!qDigits.isEmpty() && person.number != null && person.number.contains(qDigits));
            if (!match) continue;
            View tile = tile(person.displayName, person.number != null ? formatNumber(person.number) : null,
                    avatar(person));
            tile.setOnClickListener(v -> offerCall(person, v));
            tile.setOnLongClickListener(v -> {
                confirmRemove(person);
                return true;
            });
            grid.addView(tile);
            shown++;
        }
        noMatches.setVisibility(shown == 0 && !q.isEmpty() ? View.VISIBLE : View.GONE);
        if (!q.isEmpty()) return;   // keep extra tiles out of search results

        View add = tile("Add contact", null, iconCircle("+", TILE_GREY));
        add.setOnClickListener(v -> showAddPerson());
        grid.addView(add);
        View open = tile("Open WhatsApp", null, logoCircle());
        open.setOnClickListener(v -> openWhatsApp());
        grid.addView(open);
    }

    private View tile(String name, String number, Bitmap picture) {
        LinearLayout tile = new LinearLayout(this);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setGravity(Gravity.CENTER_HORIZONTAL);
        tile.setFocusable(true);
        tile.setClickable(true);
        tile.setPadding(dp(4), dp(12), dp(4), dp(10));
        int textWidth = dp(tileWidthDp - 8);

        ImageView photo = new ImageView(this);
        photo.setImageBitmap(picture);
        tile.addView(photo, new LinearLayout.LayoutParams(dp(photoDp), dp(photoDp)));

        TextView label = text(name, 17, TEXT, false);
        label.setGravity(Gravity.CENTER);
        label.setSingleLine(true);
        label.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams labelLp = new LinearLayout.LayoutParams(textWidth, ViewGroup.LayoutParams.WRAP_CONTENT);
        labelLp.topMargin = dp(10);
        tile.addView(label, labelLp);

        // Always reserve the number line so every row lines up, with or without a number.
        TextView sub = text(number != null ? number : " ", 14, TEXT_DIM, false);
        sub.setGravity(Gravity.CENTER);
        sub.setSingleLine(true);
        tile.addView(sub, new LinearLayout.LayoutParams(textWidth, ViewGroup.LayoutParams.WRAP_CONTENT));

        tile.setOnFocusChangeListener((v, focused) -> v.setBackground(focused ? outline() : null));

        GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
        lp.width = dp(tileWidthDp);
        lp.setMargins(dp(6), dp(4), dp(6), dp(4));
        tile.setLayoutParams(lp);
        return tile;
    }

    // ---- Actions ----

    private void offerCall(final FamilyStore.Person person, final View tile) {
        String detail = person.number != null ? formatNumber(person.number) : null;
        chooser.show(person.displayName, detail, avatar(person), new CallChooser.Listener() {
            @Override
            public void onVideo() {
                startCall(person, "Video call");
            }

            @Override
            public void onVoice() {
                startCall(person, "Voice call");
            }

            @Override
            public void onCancel() {
                chooser.dismiss();
                tile.requestFocus();
            }
        });
    }

    private void startCall(FamilyStore.Person person, String type) {
        chooser.dismiss();
        if (!CallHelperService.isRunning()) {
            Toast.makeText(this, "Turn on WhatsApp call helper in Accessibility, then try again.",
                    Toast.LENGTH_LONG).show();
            return;
        }
        CallHelperService.handedOverFromBeta(false);
        if (person.number == null) {
            // No number: the helper finds them by name using WhatsApp's search.
            CallHelperService.requestCallByName(person.whatsappName, type);
            openWhatsApp();
            return;
        }
        CallHelperService.requestCall(type);
        Intent chat = new Intent(Intent.ACTION_VIEW, Uri.parse("whatsapp://send?phone=" + person.number));
        chat.setPackage("com.whatsapp");
        chat.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(chat);
        } catch (Exception e) {
            CallHelperService.requestCall(null);
            Toast.makeText(this, "WhatsApp couldn't open " + person.displayName + ".", Toast.LENGTH_LONG).show();
        }
    }

    private void openWhatsApp() {
        Intent open = getPackageManager().getLaunchIntentForPackage("com.whatsapp");
        if (open == null) return;
        open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(open);
    }

    private void showAddPerson() {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(32), dp(16), dp(32), 0);
        final EditText name = new EditText(this);
        name.setHint("Name exactly as saved in WhatsApp");
        name.setInputType(InputType.TYPE_CLASS_TEXT);
        final EditText number = new EditText(this);
        number.setHint("WhatsApp number");
        number.setInputType(InputType.TYPE_CLASS_PHONE);
        form.addView(name);
        form.addView(number);

        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Add contact")
                .setView(form)
                .setPositiveButton("Save", (d, w) -> {
                    String n = name.getText().toString().trim();
                    String typed = number.getText().toString().trim();
                    String num = typed.isEmpty() ? null : FamilyStore.normalizeNumber(typed);
                    if (n.isEmpty() || (!typed.isEmpty() && num == null)) {
                        Toast.makeText(this, "Please enter the name, and a 10-digit number if you add one.",
                                Toast.LENGTH_LONG).show();
                        return;
                    }
                    try {
                        FamilyStore.add(this, n, num);
                    } catch (IOException e) {
                        Toast.makeText(this, "Couldn't save.", Toast.LENGTH_LONG).show();
                    }
                    people = FamilyStore.load(this);
                    showPeople(search.getText().toString());
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void confirmRemove(final FamilyStore.Person person) {
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Remove " + person.displayName + "?")
                .setPositiveButton("Remove", (d, w) -> {
                    try {
                        FamilyStore.remove(this, person);
                    } catch (IOException ignored) {
                        // Leave the grid as it was.
                    }
                    people = FamilyStore.load(this);
                    showPeople(search.getText().toString());
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ---- Pictures ----

    /** A photo given in the PC list if there is one; otherwise a coloured circle with their initial. */
    private Bitmap avatar(FamilyStore.Person person) {
        if (person.photo != null) {
            Bitmap raw = BitmapFactory.decodeFile(person.photo.getPath());
            if (raw != null) return circle(raw);
        }
        int color = AVATAR_COLORS[Math.abs(person.key().hashCode()) % AVATAR_COLORS.length];
        return iconCircle(initials(person.displayName), color);
    }

    private Bitmap circle(Bitmap source) {
        int size = Math.min(source.getWidth(), source.getHeight());
        Bitmap square = Bitmap.createBitmap(source, (source.getWidth() - size) / 2,
                (source.getHeight() - size) / 2, size, size);
        Bitmap out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        paint.setShader(new BitmapShader(square, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP));
        new Canvas(out).drawCircle(size / 2f, size / 2f, size / 2f, paint);
        return out;
    }

    private Bitmap iconCircle(String label, int color) {
        int size = dp(photoDp);
        Bitmap out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(out);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        // Soft top-left light to bottom-right shade, so the default picture doesn't look flat.
        paint.setShader(new LinearGradient(0, 0, size, size, lighten(color), color, Shader.TileMode.CLAMP));
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint);
        paint.setShader(null);
        paint.setColor(0xFFFFFFFF);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        paint.setTextSize(label.length() > 1 ? size * 0.38f : size * 0.48f);
        Paint.FontMetrics fm = paint.getFontMetrics();
        canvas.drawText(label, size / 2f, size / 2f - (fm.ascent + fm.descent) / 2f, paint);
        return out;
    }

    private Bitmap logoCircle() {
        Bitmap logo = BitmapFactory.decodeResource(getResources(), R.drawable.wa_logo);
        int size = dp(photoDp);
        Bitmap out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(out);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(TILE_GREY);
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint);
        int inset = size / 5;
        canvas.drawBitmap(Bitmap.createScaledBitmap(logo, size - 2 * inset, size - 2 * inset, true),
                inset, inset, null);
        return out;
    }

    private static int lighten(int color) {
        int r = (color >> 16 & 0xFF), g = (color >> 8 & 0xFF), b = (color & 0xFF);
        r += (255 - r) * 3 / 10;
        g += (255 - g) * 3 / 10;
        b += (255 - b) * 3 / 10;
        return 0xFF000000 | r << 16 | g << 8 | b;
    }

    /** 919876543210 -> "+91 98765 43210". */
    private static String formatNumber(String digits) {
        if (digits.length() == 12 && digits.startsWith("91")) {
            return "+91 " + digits.substring(2, 7) + " " + digits.substring(7);
        }
        return "+" + digits;
    }

    private static String initials(String name) {
        String[] words = name.trim().split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (String w : words) {
            if (!w.isEmpty() && Character.isLetterOrDigit(w.charAt(0)) && sb.length() < 1) {
                sb.append(Character.toUpperCase(w.charAt(0)));
            }
        }
        return sb.length() == 0 ? "?" : sb.toString();
    }

    // ---- Small helpers ----

    private GradientDrawable outline() {
        GradientDrawable g = new GradientDrawable();
        g.setColor(0x00000000);
        g.setCornerRadius(dp(14));
        g.setStroke(dp(3), ACCENT);
        return g;
    }

    private GradientDrawable pill(boolean focused) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(SEARCH_BG);
        g.setCornerRadius(dp(25));
        g.setStroke(dp(focused ? 2 : 1), focused ? ACCENT : SEARCH_LINE);
        return g;
    }

    private TextView text(String value, int sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextColor(color);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
