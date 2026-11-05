package com.ben.contactsonly;

import android.content.ContentProvider;
import android.content.ContentProviderOperation;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.provider.CallLog;
import android.provider.ContactsContract;
import java.util.ArrayList;
import org.json.JSONObject;

/** Cold-startable fixture: never relies on the instrumentation/target class loader. */
public final class IntegrationFixtureProvider extends ContentProvider {
    @Override public boolean onCreate() { return true; }

    @Override public Bundle call(String method, String arg, Bundle extras) {
        int uid = Binder.getCallingUid();
        if (uid != 0 && uid != 2000) throw new SecurityException("Shell-only integration fixture");
        long identity = Binder.clearCallingIdentity();
        try {
            JSONObject result = new JSONObject();
            if ("seed-contact".equals(method)) {
                ArrayList<ContentProviderOperation> ops = new ArrayList<>();
                ops.add(ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI)
                        .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, null)
                        .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, null).build());
                ops.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                        .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                        .withValue(ContactsContract.Data.MIMETYPE,
                                ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
                        .withValue(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME,
                                "Contacts Only synthetic fixture").build());
                ops.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                        .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                        .withValue(ContactsContract.Data.MIMETYPE,
                                ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
                        .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, arg).build());
                getContext().getContentResolver().applyBatch(ContactsContract.AUTHORITY, ops);
                result.put("seeded", arg);
            } else if ("call-state".equals(method)) {
                // Telecom may present the handle in another format than the console number.
                int state = -1;
                for (java.util.Map.Entry<String, Integer> entry
                        : IntegrationInCallService.STATES.entrySet()) {
                    if (sameNumber(entry.getKey(), arg)) state = entry.getValue();
                }
                boolean rang = false;
                for (String handle : IntegrationInCallService.RANG) {
                    rang |= sameNumber(handle, arg);
                }
                result.put("state", state);
                result.put("rang", rang);
                try (Cursor rows = getContext().getContentResolver().query(CallLog.Calls.CONTENT_URI,
                        new String[]{CallLog.Calls.NUMBER, CallLog.Calls.TYPE, CallLog.Calls.BLOCK_REASON},
                        null, null, CallLog.Calls.DATE + " DESC")) {
                    while (rows != null && rows.moveToNext()) {
                        String number = rows.getString(0);
                        if (sameNumber(number, arg)) {
                            result.put("log_type", rows.getInt(1));
                            result.put("block_reason", rows.getInt(2));
                            break;
                        }
                    }
                }
            } else {
                throw new IllegalArgumentException("Unknown fixture operation: " + method);
            }
            Bundle response = new Bundle();
            response.putString("json", result.toString());
            return response;
        } catch (Exception error) {
            throw new IllegalStateException("Fixture operation failed", error);
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    /** Synthetic numbers are unique in their last ten digits; ignore formatting and country code. */
    private static boolean sameNumber(String a, String b) {
        if (a == null || b == null) return false;
        String x = a.replaceAll("[^0-9]", "");
        String y = b.replaceAll("[^0-9]", "");
        if (x.length() >= 10 && y.length() >= 10) {
            return x.substring(x.length() - 10).equals(y.substring(y.length() - 10));
        }
        return x.equals(y);
    }

    @Override public Cursor query(Uri u, String[] p, String s, String[] a, String o) { throw new UnsupportedOperationException(); }
    @Override public String getType(Uri u) { return null; }
    @Override public Uri insert(Uri u, ContentValues v) { throw new UnsupportedOperationException(); }
    @Override public int delete(Uri u, String s, String[] a) { throw new UnsupportedOperationException(); }
    @Override public int update(Uri u, ContentValues v, String s, String[] a) { throw new UnsupportedOperationException(); }
}
