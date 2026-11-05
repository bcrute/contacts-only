package com.ben.contactsonly;

import android.app.Activity;
import android.app.Instrumentation;
import android.app.role.RoleManager;
import android.content.Intent;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

/** Runs against installed Android, without Robolectric, Mockito or product test hooks. */
public final class AndroidIntegration extends Instrumentation {
    private String phase;
    private MainActivity activity;

    @Override public void onCreate(Bundle args) {
        super.onCreate(args);
        phase = args.getString("phase", "verify-on");
        start();
    }

    @Override public void onStart() {
        Bundle result = new Bundle();
        try {
            Intent intent = new Intent(getTargetContext(), MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            activity = (MainActivity) startActivitySync(intent);
            waitForIdleSync();
            boolean expected = !phase.contains("off") && !phase.equals("role-revoked");
            if (phase.equals("set-off")) {
                checkStatus(true);
                tapSwitch();
                expected = false;
            } else if (phase.equals("set-on")) {
                checkStatus(false);
                tapSwitch();
                expected = true;
            } else if (phase.equals("role-revoked")) {
                checkStatus(false);
                tapSwitch();
                // The actual system role UI was opened. Cancel without granting a role.
                waitForPackage("com.android.permissioncontroller");
                AccessibilityNodeInfo cancel = findCancel(getUiAutomation().getRootInActiveWindow());
                require(cancel != null && cancel.performAction(AccessibilityNodeInfo.ACTION_CLICK),
                        "System role dialog has no accessible cancel action");
                waitForPackage(getTargetContext().getPackageName());
                waitForIdleSync();
            } else if (phase.equals("large-text-on")) {
                checkStatus(true);
                tapSwitch();
                checkStatus(false);
                tapSwitch();
            }
            checkStatus(expected);
            if (!phase.equals("role-revoked")) {
                require(getTargetContext().getSystemService(RoleManager.class)
                        .isRoleHeld(RoleManager.ROLE_CALL_SCREENING), "Screening role lost");
                require(CallBlockingSettings.isEnabled(getTargetContext()) == expected,
                        "Persisted setting differs from effective UI");
            }
            result.putString("contacts_only", "passed");
            result.putString("phase", phase);
            snapshot();
            runOnMainSync(() -> activity.finish());
            waitForIdleSync();
            finish(Activity.RESULT_OK, result);
        } catch (Throwable error) {
            snapshot();
            result.putString("contacts_only", "failed: " + error);
            result.putString("phase", phase);
            android.util.Log.e("ContactsOnlyIntegration", "Phase failed", error);
            finish(Activity.RESULT_CANCELED, result);
        }
    }

    private void checkStatus(boolean expected) {
        runOnMainSync(() -> {
            Switch toggle = activity.findViewById(R.id.call_blocking_switch);
            require(toggle.isChecked() == expected, "Unexpected switch state for " + phase);
            String status = activity.getString(expected ? R.string.status_on : R.string.status_off);
            require(containsText((ViewGroup) activity.findViewById(android.R.id.content), status),
                    "Effective status text does not match switch");
            AccessibilityNodeInfo node = toggle.createAccessibilityNodeInfo();
            require(node.isCheckable() && node.isChecked() == expected, "Incorrect accessibility state");
            require(node.getText() != null && node.getText().length() > 0, "Missing switch label");
            require((node.getActions() & AccessibilityNodeInfo.ACTION_CLICK) != 0, "No click action");
            float density = activity.getResources().getDisplayMetrics().density;
            require(toggle.getHeight() >= 48 * density, "Switch touch target smaller than 48dp");
            if (toggle.getLayout() != null) {
                for (int i = 0; i < toggle.getLayout().getLineCount(); i++) {
                    require(toggle.getLayout().getEllipsisCount(i) == 0, "Switch label truncated");
                }
            }
        });
    }

    private void tapSwitch() {
        final int[] center = new int[2];
        runOnMainSync(() -> {
            Switch toggle = activity.findViewById(R.id.call_blocking_switch);
            View parent = (View) toggle.getParent();
            while (parent != null && !(parent instanceof ScrollView)) {
                parent = parent.getParent() instanceof View ? (View) parent.getParent() : null;
            }
            if (parent instanceof ScrollView) ((ScrollView) parent).scrollTo(0, toggle.getTop());
        });
        waitForIdleSync();
        runOnMainSync(() -> {
            Rect bounds = new Rect();
            require(activity.findViewById(R.id.call_blocking_switch).getGlobalVisibleRect(bounds),
                    "Switch is not visible");
            center[0] = bounds.centerX(); center[1] = bounds.centerY();
        });
        long now = SystemClock.uptimeMillis();
        MotionEvent down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, center[0], center[1], 0);
        MotionEvent up = MotionEvent.obtain(now, now + 50, MotionEvent.ACTION_UP, center[0], center[1], 0);
        down.setSource(InputDevice.SOURCE_TOUCHSCREEN); up.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        sendPointerSync(down); sendPointerSync(up);
        down.recycle(); up.recycle();
        waitForIdleSync();
    }

    private static boolean containsText(ViewGroup group, String wanted) {
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            if (child instanceof TextView && wanted.contentEquals(((TextView) child).getText())) return true;
            if (child instanceof ViewGroup && containsText((ViewGroup) child, wanted)) return true;
        }
        return false;
    }

    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    private void waitForPackage(String wanted) {
        long deadline = SystemClock.uptimeMillis() + 10000;
        String observed = "no window";
        while (SystemClock.uptimeMillis() < deadline) {
            AccessibilityNodeInfo root = getUiAutomation().getRootInActiveWindow();
            if (root != null && root.getPackageName() != null) {
                observed = root.getPackageName().toString();
                if (wanted.equals(observed)) return;
            }
            SystemClock.sleep(100);
        }
        throw new AssertionError("Expected visible system/app window: " + wanted + "; observed: " + observed);
    }

    private AccessibilityNodeInfo findCancel(AccessibilityNodeInfo node) {
        if (node == null) return null;
        String id = node.getViewIdResourceName();
        CharSequence label = node.getText();
        if ((id != null && id.endsWith(":id/button2"))
                || (label != null && label.toString().equalsIgnoreCase("Cancel"))) return node;
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo found = findCancel(node.getChild(i));
            if (found != null) return found;
        }
        return null;
    }

    private void snapshot() {
        try {
            android.graphics.Bitmap image = getUiAutomation().takeScreenshot();
            if (image != null) {
                try (java.io.OutputStream stream = new java.io.FileOutputStream(
                        new java.io.File(getTargetContext().getFilesDir(), "integration-screen.png"))) {
                    image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, stream);
                }
                image.recycle();
            }
        } catch (Exception error) {
            android.util.Log.w("ContactsOnlyIntegration", "Screenshot unavailable", error);
        }
    }
}
