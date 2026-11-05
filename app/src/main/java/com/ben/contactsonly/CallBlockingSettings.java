package com.ben.contactsonly;

import android.content.Context;
import android.content.SharedPreferences;

/** The service reads this setting for each call, including while the activity is closed. */
final class CallBlockingSettings {
    private static final String FILE = "call_blocking";
    private static final String ENABLED = "enabled";

    private CallBlockingSettings() {}

    static boolean isEnabled(Context context) {
        // Version 1.0 blocked calls whenever the screening role was held. Keep that on upgrade.
        return preferences(context).getBoolean(ENABLED, true);
    }

    static void setEnabled(Context context, boolean enabled) {
        preferences(context).edit().putBoolean(ENABLED, enabled).apply();
    }

    private static SharedPreferences preferences(Context context) {
        return context.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }
}
