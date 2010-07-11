/*
 * From project ReMeMore-Moi - http://code.google.com/p/rememore-moi
 * Code license: Apache License 2.0
 */
package com.googlecode.CallerLookup;

import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.telephony.TelephonyManager;
import android.util.Log;

public class PhoneIntentReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        try {
            Bundle bundle = intent.getExtras();
            if (bundle != null) {
                String state = bundle.getString("state");
                if (TelephonyManager.EXTRA_STATE_RINGING.equals(state)) {
                    String phoneNumber = intent.getExtras().getString("incoming_number");
                    ContactAccessor contactAccessor = ContactAccessor.getInstance();
                    ContentResolver resolver = context.getContentResolver();
                    String name = contactAccessor.findPhoneNumber(resolver, phoneNumber);
                    if (name == null) {
                        intent.setClass(context, CallerLookupService.class);
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        Log.d("DEBUG", intent.toString());
                        context.startService(intent);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
