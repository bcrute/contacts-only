package com.ben.contactsonly;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import android.app.Activity;
import android.app.Service;
import android.app.role.RoleManager;
import android.content.Context;
import android.content.Intent;
import android.os.Process;
import android.telecom.Call;
import android.telecom.CallScreeningService.CallResponse;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Switch;
import android.widget.TextView;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.android.controller.ServiceController;
import org.robolectric.annotation.Config;
import org.robolectric.shadow.api.Shadow;
import org.robolectric.shadows.ShadowActivity;
import org.robolectric.shadows.ShadowCallScreeningService;
import org.robolectric.shadows.ShadowRoleManager;
import org.robolectric.shadows.ShadowToast;

@RunWith(RobolectricTestRunner.class)
// Android 15 (API 35) is the oldest supported version; older ones are not tested.
@Config(sdk = {35, 36})
public final class CallBlockingTest {
    private Context context;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
        context.getSharedPreferences("call_blocking", Context.MODE_PRIVATE).edit().clear().commit();
    }

    @Test
    public void upgradeKeepsBlockingAndPreservesBlockedHistory() {
        assertTrue(CallBlockingSettings.isEnabled(context));
        try (ScreeningSession controller = service()) {
            CallResponse response = screen(controller.get(), Call.Details.DIRECTION_INCOMING);
            assertTrue(response.getDisallowCall());
            assertTrue(response.getRejectCall());
            assertFalse(response.getSkipCallLog());
            assertTrue(response.getSkipNotification());
        }
    }

    @Test
    public void switchingOffAndOnChangesTheNextCallInTheSameService() {
        holdRole();
        try (ActivityController<MainActivity> ui = activity();
                ScreeningSession controller = service()) {
            Switch toggle = ui.get().findViewById(R.id.call_blocking_switch);
            assertTrue(toggle.isChecked());
            toggle.performClick();
            assertFalse(toggle.isChecked());
            assertTrue(context.getSystemService(RoleManager.class)
                    .isRoleHeld(RoleManager.ROLE_CALL_SCREENING));
            CallResponse allowed = screen(controller.get(), Call.Details.DIRECTION_INCOMING);
            assertFalse(allowed.getDisallowCall());
            assertFalse(allowed.getRejectCall());
            assertFalse(allowed.getSkipCallLog());
            assertFalse(allowed.getSkipNotification());

            toggle.performClick();
            assertTrue(toggle.isChecked());
            assertTrue(screen(controller.get(), Call.Details.DIRECTION_INCOMING).getRejectCall());
            assertNull(Shadows.shadowOf(ui.get()).getNextStartedActivityForResult());
        }
    }

    @Test
    public void pausedChoiceSurvivesActivityAndServiceRecreation() {
        holdRole();
        try (ActivityController<MainActivity> ui = activity()) {
            ui.get().<Switch>findViewById(R.id.call_blocking_switch).performClick();
        }
        try (ActivityController<MainActivity> reopened = activity();
                ScreeningSession controller = service()) {
            assertFalse(reopened.get().<Switch>findViewById(R.id.call_blocking_switch).isChecked());
            assertFalse(screen(controller.get(), Call.Details.DIRECTION_INCOMING).getRejectCall());
        }
    }

    @Test
    public void outgoingAndUnknownDirectionAreNeverRejected() {
        try (ScreeningSession controller = service()) {
            for (boolean enabled : new boolean[]{true, false}) {
                CallBlockingSettings.setEnabled(context, enabled);
                assertFalse(screen(controller.get(), Call.Details.DIRECTION_OUTGOING).getRejectCall());
                assertFalse(screen(controller.get(), Call.Details.DIRECTION_UNKNOWN).getRejectCall());
            }
        }
    }

    @Test
    public void missingRoleShowsOffAndGrantEnablesBlocking() {
        makeRoleAvailable();
        CallBlockingSettings.setEnabled(context, false);
        try (ActivityController<MainActivity> ui = activity()) {
            Switch toggle = ui.get().findViewById(R.id.call_blocking_switch);
            assertFalse(toggle.isChecked());
            assertTrue(toggle.isEnabled());
            toggle.performClick();
            ShadowActivity.IntentForResult request = Shadows.shadowOf(ui.get())
                    .getNextStartedActivityForResult();
            assertNotNull(request);
            assertEquals("android.app.role.action.REQUEST_ROLE", request.intent.getAction());
            assertFalse(toggle.isChecked());
            assertFalse(CallBlockingSettings.isEnabled(context));

            holdRole();
            ui.get().onActivityResult(request.requestCode, Activity.RESULT_OK, new Intent());
            assertTrue(toggle.isChecked());
            assertTrue(CallBlockingSettings.isEnabled(context));
        }
    }

    @Test
    public void cancellingRoleRequestKeepsBlockingOff() {
        makeRoleAvailable();
        CallBlockingSettings.setEnabled(context, false);
        try (ActivityController<MainActivity> ui = activity()) {
            Switch toggle = ui.get().findViewById(R.id.call_blocking_switch);
            toggle.performClick();
            ShadowActivity.IntentForResult request = Shadows.shadowOf(ui.get())
                    .getNextStartedActivityForResult();
            ui.get().onActivityResult(request.requestCode, Activity.RESULT_CANCELED, null);
            assertFalse(toggle.isChecked());
            assertFalse(CallBlockingSettings.isEnabled(context));
        }
    }

    @Test
    public void unsupportedDeviceShowsOffAndDisablesTheSwitch() {
        try (ActivityController<MainActivity> ui = activity()) {
            Switch toggle = ui.get().findViewById(R.id.call_blocking_switch);
            assertFalse(toggle.isChecked());
            assertFalse(toggle.isEnabled());
        }
    }

    @Test
    public void returningFromSettingsReflectsRoleRevocation() {
        holdRole();
        try (ActivityController<MainActivity> ui = activity()) {
            assertTrue(ui.get().<Switch>findViewById(R.id.call_blocking_switch).isChecked());
            ui.pause();
            ShadowRoleManager.removeRoleHolder(RoleManager.ROLE_CALL_SCREENING,
                    context.getPackageName(), Process.myUserHandle());
            ui.resume();
            assertFalse(ui.get().<Switch>findViewById(R.id.call_blocking_switch).isChecked());
        }
    }

    @Test
    public void rotationKeepsPausedChoiceAndDoesNotRequestTheRoleAgain() {
        holdRole();
        try (ActivityController<MainActivity> ui = activity()) {
            ui.get().<Switch>findViewById(R.id.call_blocking_switch).performClick();
            ui.recreate();
            assertFalse(ui.get().<Switch>findViewById(R.id.call_blocking_switch).isChecked());
            assertFalse(CallBlockingSettings.isEnabled(context));
            assertNull(Shadows.shadowOf(ui.get()).getNextStartedActivityForResult());
        }
    }

    @Test
    public void openingWithoutTheRoleNeverSilentlyRequestsAccess() {
        makeRoleAvailable();
        try (ActivityController<MainActivity> ui = activity()) {
            assertFalse(ui.get().<Switch>findViewById(R.id.call_blocking_switch).isChecked());
            assertNull(Shadows.shadowOf(ui.get()).getNextStartedActivityForResult());
        }
    }

    @Test
    public void grantingRoleElsewhereDoesNotUndoAPausedChoice() {
        makeRoleAvailable();
        CallBlockingSettings.setEnabled(context, false);
        try (ActivityController<MainActivity> ui = activity()) {
            ui.pause();
            holdRole();
            ui.resume();
            assertFalse(ui.get().<Switch>findViewById(R.id.call_blocking_switch).isChecked());
            assertFalse(CallBlockingSettings.isEnabled(context));
        }
    }

    @Test
    public void unrelatedActivityResultDoesNotResumeBlocking() {
        holdRole();
        CallBlockingSettings.setEnabled(context, false);
        try (ActivityController<MainActivity> ui = activity()) {
            ui.get().onActivityResult(9999, Activity.RESULT_OK, null);
            assertFalse(ui.get().<Switch>findViewById(R.id.call_blocking_switch).isChecked());
            assertFalse(CallBlockingSettings.isEnabled(context));
        }
    }

    @Test
    public void phoneAppButtonOpensTheDialer() {
        try (ActivityController<MainActivity> ui = activity()) {
            ui.get().findViewById(R.id.open_phone_button).performClick();
            Intent started = Shadows.shadowOf(ui.get()).getNextStartedActivity();
            assertNotNull(started);
            assertEquals(Intent.ACTION_DIAL, started.getAction());
        }
    }

    @Test
    public void buttonsExplainInsteadOfCrashingWhenNoAppCanOpenTheScreen() {
        Shadows.shadowOf(RuntimeEnvironment.getApplication()).checkActivities(true);
        try (ActivityController<MainActivity> ui = activity()) {
            for (int button : new int[]{R.id.open_phone_button, R.id.open_app_settings_button}) {
                ShadowToast.reset();
                ui.get().findViewById(button).performClick();
                assertEquals(context.getString(R.string.screen_unavailable),
                        ShadowToast.getTextOfLatestToast());
                assertFalse(ui.get().isFinishing());
            }
        }
    }

    @Test
    public void roleRequestThatCannotOpenExplainsAndStaysOff() {
        makeRoleAvailable();
        CallBlockingSettings.setEnabled(context, false);
        Shadows.shadowOf(RuntimeEnvironment.getApplication()).checkActivities(true);
        try (ActivityController<MainActivity> ui = activity()) {
            Switch toggle = ui.get().findViewById(R.id.call_blocking_switch);
            toggle.performClick();
            assertEquals(context.getString(R.string.role_request_failed),
                    ShadowToast.getTextOfLatestToast());
            assertFalse(toggle.isChecked());
            assertFalse(CallBlockingSettings.isEnabled(context));
        }
    }

    @Test
    public void statusDetailDistinguishesOnPausedAndNotSetUp() {
        makeRoleAvailable();
        try (ActivityController<MainActivity> ui = activity()) {
            assertTrue(showsText(ui.get(), R.string.status_off_detail));
        }
        holdRole();
        try (ActivityController<MainActivity> ui = activity()) {
            assertTrue(showsText(ui.get(), R.string.status_on_detail));
        }
        CallBlockingSettings.setEnabled(context, false);
        try (ActivityController<MainActivity> ui = activity()) {
            assertTrue(showsText(ui.get(), R.string.status_paused_detail));
            assertFalse(showsText(ui.get(), R.string.status_on_detail));
        }
    }

    private boolean showsText(Activity activity, int text) {
        return containsText(activity.findViewById(android.R.id.content), context.getString(text));
    }

    private static boolean containsText(View view, String wanted) {
        if (view instanceof TextView && wanted.contentEquals(((TextView) view).getText())) return true;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                if (containsText(group.getChildAt(i), wanted)) return true;
            }
        }
        return false;
    }

    private void holdRole() {
        ShadowRoleManager.addRoleHolder(RoleManager.ROLE_CALL_SCREENING,
                context.getPackageName(), Process.myUserHandle());
    }

    private void makeRoleAvailable() {
        ShadowRoleManager.addRoleHolder(RoleManager.ROLE_CALL_SCREENING, "", Process.myUserHandle());
    }

    private ActivityController<MainActivity> activity() {
        return Robolectric.buildActivity(MainActivity.class).setup();
    }

    private ScreeningSession service() {
        return new ScreeningSession();
    }

    private static final class ScreeningSession implements AutoCloseable {
        private final ServiceController<ContactsOnlyScreeningService> controller =
                Robolectric.buildService(ContactsOnlyScreeningService.class).create();

        ContactsOnlyScreeningService get() {
            return controller.get();
        }

        @Override
        public void close() {
            // Robolectric's CallScreeningService shadow does not extend ShadowService.
            // Its inherited onDestroy shadow therefore throws ClassCastException after
            // our assertions. Run the real, empty framework teardown instead.
            Shadow.directlyOn(controller.get(), Service.class, "onDestroy");
        }
    }

    private CallResponse screen(ContactsOnlyScreeningService service, int direction) {
        Call.Details details = mock(Call.Details.class);
        when(details.getCallDirection()).thenReturn(direction);
        service.onScreenCall(details);
        ShadowCallScreeningService shadow = Shadow.extract(service);
        ShadowCallScreeningService.RespondToCallInput input = shadow.getLastRespondToCallInput().get();
        assertSame(details, input.getCallDetails());
        return input.getCallResponse();
    }
}
