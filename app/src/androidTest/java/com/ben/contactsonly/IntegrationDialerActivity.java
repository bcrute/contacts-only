package com.ben.contactsonly;

/** Eligibility activity for the disposable emulator's test-only default dialer. */
public final class IntegrationDialerActivity extends android.app.Activity {
    @Override public void onCreate(android.os.Bundle state) {
        super.onCreate(state);
        android.widget.TextView text = new android.widget.TextView(this);
        text.setText("Synthetic-call integration fixture");
        setContentView(text);
    }
}
