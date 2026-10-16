package com.mergesms.app;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.IBinder;
import android.provider.Telephony;

public class SmsDeliveredReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) { }

    public static class Incoming extends BroadcastReceiver {
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

    public static class Mms extends BroadcastReceiver {
        @Override public void onReceive(Context c, Intent i) { }
    }

    public static class Respond extends Service {
        @Override public IBinder onBind(Intent i) { return null; }
    }
}
