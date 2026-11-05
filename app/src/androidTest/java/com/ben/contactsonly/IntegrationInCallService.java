package com.ben.contactsonly;

import android.telecom.Call;
import android.telecom.InCallService;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Observes real Telecom delivery after screening, in the fixture's separate process. */
public final class IntegrationInCallService extends InCallService {
    /** Latest state for each handle. */
    static final Map<String, Integer> STATES = new ConcurrentHashMap<>();
    /** Handles that ever reached RINGING, so a later disconnect cannot hide a ring. */
    static final Set<String> RANG = ConcurrentHashMap.newKeySet();

    @Override public void onCallAdded(Call call) {
        record(call);
        call.registerCallback(new Call.Callback() {
            @Override public void onStateChanged(Call value, int state) { record(value); }
        });
    }

    @Override public void onCallRemoved(Call call) { record(call); }

    private static void record(Call call) {
        if (call.getDetails().getHandle() != null) {
            String handle = call.getDetails().getHandle().getSchemeSpecificPart();
            int state = call.getState();
            STATES.put(handle, state);
            if (state == Call.STATE_RINGING) RANG.add(handle);
        }
    }
}
