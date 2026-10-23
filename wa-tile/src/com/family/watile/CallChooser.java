package com.family.watile;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Large "Video call / Voice call / Cancel" box shown over a WhatsApp chat, made for the TV remote. */
final class CallChooser {
    interface Listener {
        void onVideo();
        void onVoice();
        void onCancel();
    }

    private static final int GREEN = 0xFF25D366;
    private static final int GREY = 0xFF2A3942;
    private static final int CARD = 0xFF1F2C34;

    private final Context context;
    private final WindowManager windows;
    private final ViewGroup host;   // null: show as an overlay above WhatsApp (helper service)
    // WhatsApp colours over WhatsApp; Meet-style colours on our own family screen.
    private final int accent, accentText, cardColor, neutral, neutralText;
    private View shown;

    /** Shown above other apps; only usable from the accessibility service. */
    CallChooser(Context context) {
        this(context, null);
    }

    /** Shown inside one of our own screens. */
    CallChooser(Context context, ViewGroup host) {
        this.context = context;
        this.host = host;
        this.windows = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        boolean meet = host != null;
        accent = meet ? 0xFF8AB4F8 : GREEN;
        accentText = meet ? 0xFF202124 : 0xFFFFFFFF;
        cardColor = meet ? 0xFF2D2E31 : CARD;
        neutral = meet ? 0xFF3C4043 : GREY;
        neutralText = meet ? 0xFFE8EAED : 0xFFFFFFFF;
    }

    boolean isShowing() {
        return shown != null;
    }

    void show(String name, final Listener listener) {
        show(name, null, null, listener);
    }

    /** With the person's photo and number shown, so the right person can be checked before calling. */
    void show(String name, String detail, Bitmap photo, final Listener listener) {
        if (shown != null) return;

        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        card.setPadding(dp(40), dp(32), dp(40), dp(32));
        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setColor(cardColor);
        cardBg.setCornerRadius(dp(24));
        card.setBackground(cardBg);

        if (photo != null) {
            ImageView picture = new ImageView(context);
            picture.setImageBitmap(photo);
            LinearLayout.LayoutParams pictureLp = new LinearLayout.LayoutParams(dp(128), dp(128));
            pictureLp.bottomMargin = dp(18);
            card.addView(picture, pictureLp);
        }

        TextView title = new TextView(context);
        title.setText(name != null ? "Call " + name + "?" : "Call this person?");
        title.setTextColor(0xFFFFFFFF);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 30);
        title.setGravity(Gravity.CENTER);
        card.addView(title, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        if (detail != null) {
            TextView sub = new TextView(context);
            sub.setText(detail);
            sub.setTextColor(0xFFBDC1C6);
            sub.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24);
            sub.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            subLp.topMargin = dp(8);
            card.addView(sub, subLp);
        }

        Button video = button("🎥  Video call", accent, accentText);
        Button voice = button("📞  Voice call", accent, accentText);
        Button cancel = button("Cancel", neutral, neutralText);
        video.setOnClickListener(v -> listener.onVideo());
        voice.setOnClickListener(v -> listener.onVoice());
        cancel.setOnClickListener(v -> listener.onCancel());
        card.addView(video, buttonParams(dp(28)));
        card.addView(voice, buttonParams(dp(16)));
        card.addView(cancel, buttonParams(dp(32)));

        FrameLayout root = new FrameLayout(context) {
            @Override
            public boolean dispatchKeyEvent(KeyEvent event) {
                if (event.getKeyCode() == KeyEvent.KEYCODE_BACK) {
                    if (event.getAction() == KeyEvent.ACTION_UP) listener.onCancel();
                    return true;
                }
                return super.dispatchKeyEvent(event);
            }
        };
        root.setBackgroundColor(0xCC000000);
        root.addView(card, new FrameLayout.LayoutParams(dp(560), ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER));

        if (host != null) {
            root.setClickable(true);   // keep clicks from reaching the screen underneath
            host.addView(root, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));
        } else {
            WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY, 0, PixelFormat.TRANSLUCENT);
            windows.addView(root, lp);
        }
        shown = root;
        video.requestFocus();
    }

    void dismiss() {
        if (shown == null) return;
        if (host != null) {
            host.removeView(shown);
        } else {
            windows.removeView(shown);
        }
        shown = null;
    }

    private Button button(String text, int color, int textColor) {
        Button b = new Button(context);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextColor(textColor);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 26);
        b.setFocusable(true);
        // The highlighted button gets a thick white border so it's easy to see from the sofa.
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[] {android.R.attr.state_focused}, pill(color, true));
        states.addState(new int[] {}, pill(dim(color), false));
        b.setBackground(states);
        return b;
    }

    private GradientDrawable pill(int color, boolean focused) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(40));
        if (focused) g.setStroke(dp(5), 0xFFFFFFFF);
        return g;
    }

    private static int dim(int color) {
        int r = (color >> 16 & 0xFF) * 6 / 10, g = (color >> 8 & 0xFF) * 6 / 10, b = (color & 0xFF) * 6 / 10;
        return 0xFF000000 | r << 16 | g << 8 | b;
    }

    private LinearLayout.LayoutParams buttonParams(int topMargin) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(80));
        p.topMargin = topMargin;
        return p;
    }

    private int dp(int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
