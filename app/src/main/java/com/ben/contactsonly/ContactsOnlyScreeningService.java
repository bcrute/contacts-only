package com.ben.contactsonly;

import android.telecom.Call;
import android.telecom.CallScreeningService;
import android.util.Log;

/**
 * Android does the contacts lookup before invoking this service. Because this app deliberately
 * does not request READ_CONTACTS, saved contacts are not sent here. Every incoming call that does
 * reach this service is therefore a non-contact call and is rejected when blocking is enabled.
 */
public final class ContactsOnlyScreeningService extends CallScreeningService {
    private static final String TAG = "ContactsOnly";

    @Override
    public void onScreenCall(Call.Details callDetails) {
        if (callDetails.getCallDirection() != Call.Details.DIRECTION_INCOMING
                || !CallBlockingSettings.isEnabled(this)) {
            respondToCall(callDetails, new CallResponse.Builder().build());
            return;
        }

        CallResponse response = new CallResponse.Builder()
                .setDisallowCall(true)
                .setRejectCall(true)
                .setSkipCallLog(false)
                .setSkipNotification(true)
                .build();

        Log.i(TAG, "Rejected an incoming call from a number outside Contacts");
        respondToCall(callDetails, response);
    }
}
