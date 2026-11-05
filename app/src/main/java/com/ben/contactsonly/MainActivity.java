package com.ben.contactsonly;

import android.app.Activity;
import android.app.role.RoleManager;
import android.content.Intent;
import android.content.ActivityNotFoundException;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Switch;
import android.widget.Toast;

public final class MainActivity extends Activity {
    private static final int REQUEST_CALL_SCREENING_ROLE = 1001;

    private RoleManager roleManager;
    private TextView statusView;
    private TextView statusDetailView;
    private Switch blockingSwitch;
    private boolean refreshing;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        roleManager = getSystemService(RoleManager.class);
        setContentView(createContentView());
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshStatus();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CALL_SCREENING_ROLE) {
            if (roleManager != null && roleManager.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)) {
                CallBlockingSettings.setEnabled(this, true);
            }
            refreshStatus();
        }
    }

    private View createContentView() {
        int pagePadding = dp(24);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(pagePadding, dp(36), pagePadding, dp(36));
        content.setBackgroundColor(getColor(R.color.background));

        TextView eyebrow = text(getString(R.string.eyebrow), 13, R.color.primary);
        eyebrow.setAllCaps(true);
        eyebrow.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        eyebrow.setLetterSpacing(0.12f);
        content.addView(eyebrow);

        TextView title = text(getString(R.string.app_name), 34, R.color.text_primary);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        content.addView(title, margins(dp(0), dp(5), dp(0), dp(8)));

        TextView intro = text(
                getString(R.string.intro),
                17,
                R.color.text_secondary
        );
        intro.setLineSpacing(dp(3), 1.0f);
        content.addView(intro, margins(0, 0, 0, dp(28)));

        LinearLayout statusCard = new LinearLayout(this);
        statusCard.setOrientation(LinearLayout.VERTICAL);
        statusCard.setPadding(dp(20), dp(18), dp(20), dp(18));
        statusCard.setBackgroundColor(getColor(R.color.surface));

        statusView = text(getString(R.string.status_checking), 21, R.color.text_primary);
        statusView.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        statusCard.addView(statusView);

        statusDetailView = text("", 15, R.color.text_secondary);
        statusDetailView.setLineSpacing(dp(2), 1.0f);
        statusCard.addView(statusDetailView, margins(0, dp(6), 0, 0));
        content.addView(statusCard, margins(0, 0, 0, dp(20)));

        blockingSwitch = new Switch(this);
        blockingSwitch.setId(R.id.call_blocking_switch);
        blockingSwitch.setText(R.string.call_blocking_switch);
        blockingSwitch.setTextSize(17);
        blockingSwitch.setMinHeight(dp(56));
        blockingSwitch.setPadding(0, dp(12), 0, dp(12));
        blockingSwitch.setOnCheckedChangeListener((button, checked) -> {
            if (refreshing) {
                return;
            }
            if (!checked) {
                CallBlockingSettings.setEnabled(this, false);
            } else if (roleManager != null
                    && roleManager.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)) {
                CallBlockingSettings.setEnabled(this, true);
            } else {
                requestCallScreeningRole();
            }
            refreshStatus();
        });
        content.addView(blockingSwitch, margins(0, 0, 0, dp(8)));

        TextView toggleHelp = text(getString(R.string.toggle_help), 15, R.color.text_secondary);
        content.addView(toggleHelp, margins(0, 0, 0, dp(26)));

        TextView howTitle = text(getString(R.string.blocked_title), 19, R.color.text_primary);
        howTitle.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        content.addView(howTitle, margins(0, 0, 0, dp(8)));

        TextView how = text(getString(R.string.blocked_detail), 16, R.color.text_secondary);
        how.setLineSpacing(dp(4), 1.0f);
        content.addView(how, margins(0, 0, 0, dp(24)));

        TextView privateTitle = text(getString(R.string.hidden_callers_title), 19, R.color.text_primary);
        privateTitle.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        content.addView(privateTitle, margins(0, 0, 0, dp(8)));

        TextView privateExplanation = text(
                getString(R.string.hidden_callers_detail), 16, R.color.text_secondary);
        privateExplanation.setLineSpacing(dp(4), 1.0f);
        content.addView(privateExplanation, margins(0, 0, 0, dp(14)));

        Button openPhoneButton = new Button(this);
        openPhoneButton.setId(R.id.open_phone_button);
        openPhoneButton.setText(R.string.open_phone_app);
        openPhoneButton.setTextSize(15);
        openPhoneButton.setAllCaps(false);
        openPhoneButton.setOnClickListener(v -> openPhoneApp());
        content.addView(openPhoneButton, margins(0, 0, 0, dp(18)));

        Button appSettingsButton = new Button(this);
        appSettingsButton.setId(R.id.open_app_settings_button);
        appSettingsButton.setText(R.string.open_app_settings);
        appSettingsButton.setTextSize(15);
        appSettingsButton.setAllCaps(false);
        appSettingsButton.setOnClickListener(v -> openAppSettings());
        content.addView(appSettingsButton);

        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        scrollView.addView(content);
        return scrollView;
    }

    private void requestCallScreeningRole() {
        if (roleManager == null || !roleManager.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING)) {
            refreshStatus();
            return;
        }

        try {
            Intent requestIntent = roleManager.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING);
            startActivityForResult(requestIntent, REQUEST_CALL_SCREENING_ROLE);
        } catch (ActivityNotFoundException | SecurityException error) {
            Toast.makeText(this, R.string.role_request_failed, Toast.LENGTH_LONG).show();
        }
    }

    private void refreshStatus() {
        boolean available = roleManager != null
                && roleManager.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING);
        boolean roleHeld = available && roleManager.isRoleHeld(RoleManager.ROLE_CALL_SCREENING);
        boolean enabled = roleHeld && CallBlockingSettings.isEnabled(this);

        refreshing = true;
        blockingSwitch.setEnabled(available);
        blockingSwitch.setChecked(enabled);
        refreshing = false;

        if (!available) {
            statusView.setText(R.string.status_not_supported);
            statusView.setTextColor(getColor(R.color.disabled));
            statusDetailView.setText(R.string.status_not_supported_detail);
            return;
        }

        if (enabled) {
            statusView.setText(R.string.status_on);
            statusView.setTextColor(getColor(R.color.enabled));
            statusDetailView.setText(R.string.status_on_detail);
        } else {
            statusView.setText(R.string.status_off);
            statusView.setTextColor(getColor(R.color.disabled));
            statusDetailView.setText(roleHeld ? R.string.status_paused_detail : R.string.status_off_detail);
        }
    }

    private void openPhoneApp() {
        startIfAvailable(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:")));
    }

    private void openAppSettings() {
        startIfAvailable(new Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", getPackageName(), null)
        ));
    }

    /** Tablets and some OEM builds have no dialer, or hide a settings screen. */
    private void startIfAvailable(Intent intent) {
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException error) {
            Toast.makeText(this, R.string.screen_unavailable, Toast.LENGTH_LONG).show();
        }
    }

    private TextView text(String value, int sizeSp, int colorResource) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sizeSp);
        view.setTextColor(getColor(colorResource));
        view.setGravity(Gravity.START);
        return view;
    }

    private LinearLayout.LayoutParams margins(int left, int top, int right, int bottom) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(left, top, right, bottom);
        return params;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
