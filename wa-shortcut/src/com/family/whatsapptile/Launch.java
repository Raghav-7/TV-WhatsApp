package com.family.whatsapptile;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;

/** Opens WhatsApp's normal start screen, then closes itself. */
public class Launch extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent open = getPackageManager().getLaunchIntentForPackage("com.whatsapp");
        if (open == null) {
            open = new Intent(Intent.ACTION_MAIN);
            open.setComponent(new ComponentName("com.whatsapp", "com.whatsapp.Main"));
        }
        open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(open);
        } catch (Exception e) {
            // WhatsApp not installed: nothing to open.
        }
        finish();
    }
}
