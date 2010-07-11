/*
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

import android.os.Bundle;
import android.os.Handler;
import android.os.Message;

public class TestLookupThread extends Thread {
    public static final int MESSAGE_COMPLETE = 1;
    public static final int MESSAGE_ERROR = 2;

    public Handler mActivityHandler;

    public String mURL = "";
    public String mRegExp = "";
    public String mNumber = "";
    public String mCaller = "";

    public TestLookupThread(Handler activity) {
        mActivityHandler = activity;
    }

    @Override
    public void run() {
        try {
            WebParser wp = new WebParser(mURL, mRegExp, mNumber);
            mCaller = wp.getMatches();

            Message message = mActivityHandler.obtainMessage();
            message.what = MESSAGE_COMPLETE;
            mActivityHandler.sendMessage(message);
        } catch (Exception ex) {
            Bundle bundle = new Bundle();
            bundle.putString("Message", ex.getMessage());
            Message message = mActivityHandler.obtainMessage();
            message.setData(bundle);
            message.what = MESSAGE_ERROR;
            mActivityHandler.sendMessage(message);
        }
    }

    public void stopThread() {
        // TODO: implement
    }
}
