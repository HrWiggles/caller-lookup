/*
 * Quite a bit from project ReMeMore-Moi - http://code.google.com/p/rememore-moi
 * Code license: Apache License 2.0
 *
 * The rest:
 * Copyright (C) 2010 Fridvin Logi
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.googlecode.CallerLookup;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.IBinder;
import android.provider.ContactsContract;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.widget.Toast;

public class CallerLookupService extends Service {
    private final Handler mHandler = new Handler();
    private Context mContext;
    private WebParser mWebParser;

    final Runnable mUpdateResults = new Runnable() {
        @Override
        public void run() {
            onWebParserDone();
        }
    };

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Deprecated
    @Override
    public void onStart(Intent intent, int startId) {
        super.onStart(intent, startId);
        if (intent != null) {
            if (TelephonyManager.ACTION_PHONE_STATE_CHANGED.equals(intent.getAction())) {
                final String number = intent.getExtras().getString("incoming_number");
                if (number != null) {
                    mContext = getApplicationContext();

                    SharedPreferences prefs = mContext.getSharedPreferences(Main.PREFS, 0);
                    final String lookup = prefs.getString(Main.PREFS_URL, "");
                    final String regExp = prefs.getString(Main.PREFS_REGEXP, "");
                    if (lookup.length() > 0) {
                        new Thread(new Runnable() {
                            @Override
                            public void run() {
                                mWebParser = new WebParser(lookup, regExp, number);
                                mHandler.post(mUpdateResults);
                            }
                        }).start();
                    }
                } else {
                    stopSelf();
                }
            } else {
                stopSelf();
            }
        } else {
            Log.d("DEBUG", "Error: couldn't start CallerLookupService without valid Intent!");
            stopSelf();
        }
    }

    private void onWebParserDone() {
        if (mWebParser != null) {
            String caller = mWebParser.getMatches();
            if (caller != null) {
                Toast.makeText(mContext, caller, Toast.LENGTH_LONG).show();

                SharedPreferences prefs = mContext.getSharedPreferences(Main.PREFS, 0);
                if (prefs.getBoolean(Main.PREFS_NOTIFY, false)) {
                    addNotification(mContext, mWebParser.getParam(), caller);
                }
            }
        }
        stopSelf();
    }

    public static void addNotification(Context context, String number, String caller) {
        CharSequence tickerText = context.getResources().getText(R.string.NotificationTicker);
        CharSequence contentTitle = context.getResources().getString(R.string.NotificationTitle);
        NotificationManager notificationManager = (NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE);

        String [] names = caller.split("\\n");
        for (String name : names) {
            if (name.length() > 0) {
                int nameHash = name.hashCode();
                CharSequence contentText = String.format(context.getResources().getString(R.string.NotificationContent), name, number);

                Intent notificationIntent = new Intent(Intent.ACTION_INSERT_OR_EDIT);
                notificationIntent.setType(ContactAccessor.getInstance().getContentItemType());
                notificationIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                notificationIntent.putExtra(ContactsContract.Intents.Insert.PHONE, number);
                notificationIntent.putExtra(ContactsContract.Intents.Insert.NAME, name);

                Notification notification = new Notification(R.drawable.icon_notification, tickerText, System.currentTimeMillis());
                notification.setLatestEventInfo(context, contentTitle, contentText, PendingIntent.getActivity(context, nameHash, notificationIntent, 0));
                notification.flags |= Notification.FLAG_AUTO_CANCEL;
                notificationManager.notify(nameHash, notification);
            }
        }
    }
}
