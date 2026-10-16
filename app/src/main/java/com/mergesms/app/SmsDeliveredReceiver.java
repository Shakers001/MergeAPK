package com.mergesms.app;

import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.provider.Telephony;

public class SmsDeliverReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c, Intent i) {
        for (android.telephony.SmsMessage m : Telephony.Sms.Intents.getMessagesFromIntent(i)) {
            ContentValues v = new ContentValues();
            v.put("address", m.getOriginatingAddress());
            v.put("body", m.getMessageBody());
            v.put("date", System.currentTimeMillis());
            v.put("read", 0);
            v.put("type", 1);
            c.getContentResolver().insert(Uri.parse("content://sms/inbox"), v);
        }
    }
}
