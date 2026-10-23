package com.family.watile;

import android.accessibilityservice.AccessibilityService;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Toast;

import java.util.List;

/**
 * WhatsApp helpers for the TV remote. Android restarts this service by itself after the TV reboots.
 *
 * 1. Incoming calls: when a call rings while WhatsApp is on screen, WhatsApp shows only a banner
 *    at the top. This selects the banner so the full call screen opens, where Play/Pause answers.
 * 2. Calling someone: when a chat is opened from the chat list, this shows a big
 *    Video call / Voice call / Cancel box instead of leaving you in the chat.
 * 3. Calls started from the WhatsApp Beta grid: opens the person's chat (by number, or by name
 *    through WhatsApp's search), presses Video/Voice, and brings the grid back when the call ends.
 */
public class CallHelperService extends AccessibilityService {
    private static final long PENDING_CALL_MS = 20000;
    private static final long NAME_CALL_MS = 30000;

    // Shared with FamilyActivity (same process, main thread).
    private static CallHelperService instance;
    private static String pendingCall;          // "Video call" / "Voice call"
    private static long pendingCallUntil;
    private static String nameTarget;           // WhatsApp name to find, for people without a number
    private static String nameType;
    private static long nameUntil;
    private static boolean betaHandover;        // WhatsApp Beta handed over to WhatsApp; come back after
    private static boolean ringHandover;        // ...because WhatsApp was ringing (not an outgoing call)
    private static long handoverAt;

    /** WhatsApp Beta is opening WhatsApp for a call (or ringing); return to Beta when it's over. */
    static void handedOverFromBeta(boolean forRinging) {
        betaHandover = true;
        ringHandover = forRinging;
        handoverAt = SystemClock.uptimeMillis();
        if (instance != null) instance.startBetaWatch();
    }

    static boolean isRunning() {
        return instance != null;
    }

    /** The next chat WhatsApp opens should start this kind of call (null cancels). */
    static void requestCall(String type) {
        pendingCall = type;
        pendingCallUntil = SystemClock.uptimeMillis() + PENDING_CALL_MS;
    }

    /** Find this person in WhatsApp by name, open their chat and start this kind of call. */
    static void requestCallByName(String whatsappName, String type) {
        nameTarget = whatsappName;
        nameType = type;
        nameUntil = SystemClock.uptimeMillis() + NAME_CALL_MS;
        if (instance != null) instance.startNameCall();
    }

    private static final String ROW_NAME_ID = "com.whatsapp:id/conversations_row_contact_name";
    private static final String ROW_ID = "com.whatsapp:id/contact_row_container";
    private static final String SEARCH_BAR_ID = "com.whatsapp:id/search_bar_inner_layout";
    private static final String SEARCH_INPUT_ID = "com.whatsapp:id/search_input";

    private static final String WHATSAPP = "com.whatsapp";
    private static final String BANNER_ID = "com.whatsapp:id/incall_baner";
    private static final String CHAT_LIST = "com.whatsapp.home.ui.HomeActivity";
    private static final String CHAT = "com.whatsapp.Conversation";
    private static final String[] NAME_IDS = {
            "com.whatsapp:id/conversation_contact_name", "com.whatsapp:id/conversation_title"};

    private static final long WATCH_MS = 15000;   // keep checking for ringing this long after WhatsApp changes
    private static final long TICK_MS = 700;
    private static final long PAUSE_AFTER_OPEN_MS = 4000;
    private static final long CHOOSER_TIMEOUT_MS = 60000;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private long watchUntil;
    private long lastOpened;
    private boolean ticking;
    private String lastScreen;
    private CallChooser chooser;
    private boolean sawCallScreen;     // during a Beta session: WhatsApp's call screen has appeared
    private boolean sawCallAudio;      // during a Beta session: a call actually connected or rang out
    private int quietChecks;           // consecutive checks with no call audio

    // Steps through chat list -> search -> their chat -> call button, a little at a time.
    private final Runnable nameTick = new Runnable() {
        @Override
        public void run() {
            if (nameTarget == null) return;
            if (SystemClock.uptimeMillis() > nameUntil) {
                Toast.makeText(CallHelperService.this, "Couldn't find " + FamilyStore.tidy(nameTarget)
                        + " in WhatsApp.", Toast.LENGTH_LONG).show();
                nameTarget = null;
                endBetaSession();
                return;
            }
            if (!stepTowardsCall()) handler.postDelayed(this, 700);
        }
    };

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            openCallScreenIfRinging();
            if (SystemClock.uptimeMillis() < watchUntil) {
                handler.postDelayed(this, TICK_MS);
            } else {
                ticking = false;
            }
        }
    };

    private final Runnable chooserTimeout = () -> chooser.dismiss();

    private final BroadcastReceiver screenOff = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            closeChooser();
        }
    };

    @Override
    protected void onServiceConnected() {
        instance = this;
        chooser = new CallChooser(this);
        registerReceiver(screenOff, new IntentFilter(Intent.ACTION_SCREEN_OFF));
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            onScreenChanged(event);
        }
        // Ringing and the banner don't always start at the same moment,
        // so keep checking for a short while after any WhatsApp screen change.
        watchUntil = SystemClock.uptimeMillis() + WATCH_MS;
        if (!ticking) {
            ticking = true;
            handler.post(tick);
        }
    }

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        // Remote buttons that leave WhatsApp (Home, Netflix, YouTube, input...) also close the box.
        if (chooser != null && chooser.isShowing() && !isChooserKey(event.getKeyCode())) {
            closeChooser();
        }
        return false;   // never swallow keys
    }

    // ---- Incoming calls ----

    private void openCallScreenIfRinging() {
        AudioManager audio = (AudioManager) getSystemService(AUDIO_SERVICE);
        if (audio == null || audio.getMode() != AudioManager.MODE_RINGTONE) return;
        if (SystemClock.uptimeMillis() - lastOpened < PAUSE_AFTER_OPEN_MS) return;

        AccessibilityNodeInfo root = whatsAppRoot();
        if (root == null) return;
        for (AccessibilityNodeInfo banner : root.findAccessibilityNodeInfosByViewId(BANNER_ID)) {
            if (banner.isVisibleToUser() && banner.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                lastOpened = SystemClock.uptimeMillis();
                closeChooser();
                return;
            }
        }
    }

    // ---- Calling someone from the chat list ----

    private void onScreenChanged(AccessibilityEvent event) {
        String screen = activityName(event);
        if (screen == null) return;   // a dialog or menu inside WhatsApp, not a new screen
        if (screen.contains("Voip")) sawCallScreen = true;
        if (CHAT.equals(screen) && pendingCall != null && SystemClock.uptimeMillis() < pendingCallUntil) {
            String type = pendingCall;
            pendingCall = null;
            closeChooser();
            pressWhenReady(type, 0);
        } else if (CHAT.equals(screen) && betaHandover) {
            // On the way to or back from a Beta call: no chooser here; the Beta watch takes us back.
            closeChooser();
        } else if (CHAT.equals(screen) && CHAT_LIST.equals(lastScreen) && nameTarget == null) {
            handler.postDelayed(this::offerCall, 400);   // let the chat header load first
        } else if (!CHAT.equals(screen)) {
            closeChooser();
        }
        lastScreen = screen;
    }

    private void offerCall() {
        if (!CHAT.equals(lastScreen)) return;
        chooser.show(contactName(), new CallChooser.Listener() {
            @Override
            public void onVideo() {
                closeChooser();
                pressHeaderButton("Video call");
            }

            @Override
            public void onVoice() {
                closeChooser();
                pressHeaderButton("Voice call");
            }

            @Override
            public void onCancel() {
                closeChooser();
                backToChatList();
            }
        });
        handler.postDelayed(chooserTimeout, CHOOSER_TIMEOUT_MS);
    }

    private boolean pressHeaderButton(String description) {
        AccessibilityNodeInfo root = whatsAppRoot();
        if (root == null) return false;
        for (AccessibilityNodeInfo node : root.findAccessibilityNodeInfosByText(description)) {
            CharSequence desc = node.getContentDescription();
            if (desc != null && description.contentEquals(desc)) {
                AccessibilityNodeInfo target = node;
                while (target != null && !target.isClickable()) target = target.getParent();
                if (target != null && target.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true;
            }
        }
        return false;
    }

    /** A chat opened from the grid may take a moment to show its header buttons. */
    private void pressWhenReady(final String type, final int attempt) {
        handler.postDelayed(() -> {
            if (!pressHeaderButton(type) && attempt < 10) pressWhenReady(type, attempt + 1);
        }, 500);
    }

    // ---- Calls by name (grid people without a number) ----

    private void startNameCall() {
        handler.removeCallbacks(nameTick);
        handler.postDelayed(nameTick, 800);
    }

    /** One step towards the call. Returns true once the call button is being pressed. */
    private boolean stepTowardsCall() {
        AccessibilityNodeInfo root = whatsAppRoot();
        if (root == null) return false;   // WhatsApp still opening

        if (CHAT.equals(lastScreen)) {
            String header = contactName();
            if (header != null && header.equalsIgnoreCase(nameTarget)) {
                String type = nameType;
                nameTarget = null;
                pressWhenReady(type, 0);
                return true;
            }
            performGlobalAction(GLOBAL_ACTION_BACK);   // someone else's chat was open
            return false;
        }
        if (!CHAT_LIST.equals(lastScreen)) return false;

        AccessibilityNodeInfo row = findRow(root, nameTarget);
        if (row != null) {
            click(row);
            return false;
        }
        List<AccessibilityNodeInfo> inputs = root.findAccessibilityNodeInfosByViewId(SEARCH_INPUT_ID);
        if (!inputs.isEmpty()) {
            AccessibilityNodeInfo input = inputs.get(0);
            CharSequence current = input.getText();
            if (current == null || !nameTarget.contentEquals(current)) {
                Bundle args = new Bundle();
                args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, nameTarget);
                input.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args);
            }
            return false;
        }
        List<AccessibilityNodeInfo> bars = root.findAccessibilityNodeInfosByViewId(SEARCH_BAR_ID);
        if (!bars.isEmpty()) click(bars.get(0));
        return false;
    }

    /** The chat-list or search-result row whose name is exactly this (ignoring case). */
    private static AccessibilityNodeInfo findRow(AccessibilityNodeInfo root, String name) {
        for (AccessibilityNodeInfo node : root.findAccessibilityNodeInfosByViewId(ROW_NAME_ID)) {
            CharSequence text = node.getText();
            if (text != null && node.isVisibleToUser() && text.toString().trim().equalsIgnoreCase(name.trim())) {
                return node;
            }
        }
        return null;
    }

    private static void click(AccessibilityNodeInfo node) {
        while (node != null && !node.isClickable()) node = node.getParent();
        if (node != null) node.performAction(AccessibilityNodeInfo.ACTION_CLICK);
    }

    // ---- Coming back to WhatsApp Beta ----

    private static final long OUTGOING_GIVE_UP_MS = 60000;

    private final Runnable betaWatch = new Runnable() {
        @Override
        public void run() {
            if (!betaHandover) return;
            if (betaSessionOver()) {
                endBetaSession();
            } else {
                handler.postDelayed(this, 1000);
            }
        }
    };

    private void startBetaWatch() {
        sawCallScreen = false;
        sawCallAudio = false;
        quietChecks = 0;
        handler.removeCallbacks(betaWatch);
        handler.postDelayed(betaWatch, 1000);
    }

    /** Over = a call happened and has ended, or ringing stopped unanswered, or an outgoing call never started. */
    private boolean betaSessionOver() {
        AudioManager audio = (AudioManager) getSystemService(AUDIO_SERVICE);
        int mode = audio == null ? AudioManager.MODE_NORMAL : audio.getMode();
        boolean callAudio = mode == AudioManager.MODE_IN_COMMUNICATION || mode == AudioManager.MODE_IN_CALL;
        if (callAudio) sawCallAudio = true;
        quietChecks = mode == AudioManager.MODE_NORMAL ? quietChecks + 1 : 0;
        long elapsed = SystemClock.uptimeMillis() - handoverAt;

        if ((sawCallAudio || sawCallScreen) && quietChecks >= 2) return true;      // hung up, declined, cut
        if (ringHandover && quietChecks >= 2 && elapsed > 3000) return true;        // stopped ringing (missed)
        return !ringHandover && !sawCallScreen && nameTarget == null && elapsed > OUTGOING_GIVE_UP_MS;
    }

    private void endBetaSession() {
        if (!betaHandover) return;
        betaHandover = false;
        handler.removeCallbacks(betaWatch);
        // Only take over the screen if WhatsApp is still showing (not if they've moved on to YouTube etc.).
        if (whatsAppRoot() != null) returnToGrid();
    }

    private void returnToGrid() {
        Intent grid = new Intent(this, FamilyActivity.class);
        grid.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        startActivity(grid);
    }

    private void backToChatList() {
        performGlobalAction(GLOBAL_ACTION_BACK);
        // If the first Back only closed the keyboard, the chat header is still on screen: press once more.
        handler.postDelayed(() -> {
            if (chatHeaderVisible()) performGlobalAction(GLOBAL_ACTION_BACK);
        }, 1000);
    }

    private boolean chatHeaderVisible() {
        AccessibilityNodeInfo root = whatsAppRoot();
        if (root == null) return false;
        for (AccessibilityNodeInfo node : root.findAccessibilityNodeInfosByViewId(NAME_IDS[0])) {
            if (node.isVisibleToUser()) return true;
        }
        return false;
    }

    private String contactName() {
        AccessibilityNodeInfo root = whatsAppRoot();
        if (root == null) return null;
        for (String id : NAME_IDS) {
            List<AccessibilityNodeInfo> found = root.findAccessibilityNodeInfosByViewId(id);
            if (!found.isEmpty() && found.get(0).getText() != null) {
                String name = found.get(0).getText().toString().trim();
                if (!name.isEmpty()) return name;
            }
        }
        return null;
    }

    private void closeChooser() {
        handler.removeCallbacks(chooserTimeout);
        if (chooser != null) chooser.dismiss();
    }

    // ---- Helpers ----

    private AccessibilityNodeInfo whatsAppRoot() {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null || root.getPackageName() == null
                || !WHATSAPP.contentEquals(root.getPackageName())) return null;
        return root;
    }

    /** The WhatsApp screen (activity) this event came from, or null if it's not a full screen. */
    private String activityName(AccessibilityEvent event) {
        if (event.getPackageName() == null || event.getClassName() == null) return null;
        try {
            ComponentName c = new ComponentName(event.getPackageName().toString(), event.getClassName().toString());
            getPackageManager().getActivityInfo(c, 0);
            return c.getClassName();
        } catch (PackageManager.NameNotFoundException e) {
            return null;
        }
    }

    private static boolean isChooserKey(int code) {
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_DPAD_DOWN:
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_RIGHT:
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_BACK:
                return true;
            default:
                return false;
        }
    }

    @Override
    public void onInterrupt() {
    }

    @Override
    public void onDestroy() {
        instance = null;
        handler.removeCallbacksAndMessages(null);
        closeChooser();
        unregisterReceiver(screenOff);
        super.onDestroy();
    }
}
