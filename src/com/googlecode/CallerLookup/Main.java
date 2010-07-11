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

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import android.app.Activity;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.DialogInterface.OnCancelListener;
import android.content.res.XmlResourceParser;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;
import android.widget.AdapterView.OnItemSelectedListener;

import java.io.IOException;

public class Main extends Activity implements OnClickListener, OnItemSelectedListener {
    public static final String PREFS = "CallerLookupPrefs";
    public static final String PREFS_LOOKUP = "lookup";
    public static final String PREFS_URL = "url";
    public static final String PREFS_REGEXP = "regexp";
    public static final String PREFS_NOTIFY = "notify";
    public static final String STATE_TESTINPUT = "testinput";

    public static final int MENU_CUSTOMIZE = Menu.FIRST + 1;

    public static final int DIALOG_PROGRESS = 1;

    private boolean mIgnoreItemSelection = false;
    private boolean mIgnoreLookupResult = false;

    private TestLookupThread mLookupThread = null;

    private ProgressDialog mProgressDialog = null;

    private SharedPreferences mPrefs;

    private Spinner mLookup;
    private EditText mURL;
    private EditText mRegExp;
    private EditText mTestInput;
    private Button mTestButton;
    private CheckBox mNotify;

    private final Handler mHandler = new Handler() {
        public void handleMessage(Message msg) {
            dismissDialog(DIALOG_PROGRESS);
            switch (msg.what) {
                case TestLookupThread.MESSAGE_COMPLETE:
                    if (!mIgnoreLookupResult && (mLookupThread != null) && (mLookupThread.mCaller != null)) {
                        Context context = getApplicationContext();
                        Toast.makeText(context, mLookupThread.mCaller, Toast.LENGTH_LONG).show();

                        SharedPreferences prefs = context.getSharedPreferences(PREFS, 0);
                        if (prefs.getBoolean(PREFS_NOTIFY, false)) {
                            CallerLookupService.addNotification(context, mLookupThread.mNumber, mLookupThread.mCaller);
                        }
                    }

                    mIgnoreLookupResult = false;
                    mLookupThread = null;
                    break;
                case TestLookupThread.MESSAGE_ERROR:
                    Log.d("DEBUG", "Error: " + msg.getData().getString("Message"));
                    mIgnoreLookupResult = false;
                    mLookupThread.stopThread();
                    mLookupThread = null;
                    break;
                default:
                    break;
            }
        }
    };

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        mLookup = (Spinner)findViewById(R.id.Lookup);
        mURL = (EditText)findViewById(R.id.URL);
        mRegExp = (EditText)findViewById(R.id.RegExp);
        mTestInput = (EditText)findViewById(R.id.TestInput);
        mTestButton = (Button)findViewById(R.id.TestButton);
        mNotify = (CheckBox)findViewById(R.id.Notify);

        mTestButton.setOnClickListener(this);
        mLookup.setOnItemSelectedListener(this);

        mIgnoreItemSelection = true;
        mLookup.setSelection(0);
        mPrefs = getSharedPreferences(PREFS, 0);
        mURL.setText(mPrefs.getString(PREFS_URL, ""));
        mRegExp.setText(mPrefs.getString(PREFS_REGEXP, ""));
        mNotify.setChecked(mPrefs.getBoolean(PREFS_NOTIFY, false));

        String name = mPrefs.getString(PREFS_LOOKUP, "Custom");
        if ((name.length() > 0) && !name.equals("Custom")) {
            int count = mLookup.getCount();
            for (int i = 1; i < count; i++) {
                if (mLookup.getItemAtPosition(i).toString().equals(name)) {
                    mIgnoreItemSelection = false; // might not have been reset by above call to mLookup.setSelection(0)
                    mLookup.setSelection(i);
                    break;
                }
            }
        }

        if (mPrefs.getAll().isEmpty()) {
            savePreferences();
        }

        if (getLastNonConfigurationInstance() != null) {
            mLookupThread = (TestLookupThread)getLastNonConfigurationInstance();
            mLookupThread.mActivityHandler = mHandler;

            if (mLookupThread.isAlive()) {
                showDialog(DIALOG_PROGRESS);
            } else {
                dismissDialog(DIALOG_PROGRESS);
                mLookupThread = null;
            }
        }
    }

    @Override
    protected Dialog onCreateDialog(int id) {
        if (id != DIALOG_PROGRESS) {
            return super.onCreateDialog(id);
        }

        mProgressDialog = new ProgressDialog(Main.this);
        mProgressDialog.setMessage(getString(R.string.ProgressMessage));
        mProgressDialog.setOnCancelListener(new OnCancelListener() {
            @Override
            public void onCancel(DialogInterface dialog) {
                // TODO: stop mLookupThread instead of doing this ignore-lookup-result hack
                mIgnoreLookupResult = true;
            }
        });
        return mProgressDialog;
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);

        mIgnoreItemSelection = true;
        mLookup.setSelection(savedInstanceState.getInt(PREFS_LOOKUP));
        mURL.setText(savedInstanceState.getString(PREFS_URL));
        mRegExp.setText(savedInstanceState.getString(PREFS_REGEXP));
        mTestInput.setText(savedInstanceState.getString(STATE_TESTINPUT));
        mNotify.setChecked(savedInstanceState.getBoolean(PREFS_NOTIFY));
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        outState.putInt(PREFS_LOOKUP, mLookup.getSelectedItemPosition());
        outState.putString(PREFS_URL, mURL.getText().toString());
        outState.putString(PREFS_REGEXP, mRegExp.getText().toString());
        outState.putString(STATE_TESTINPUT, mTestInput.getText().toString());
        outState.putBoolean(PREFS_NOTIFY, mNotify.isChecked());
    }

    @Override
    public Object onRetainNonConfigurationInstance() {
        removeDialog(DIALOG_PROGRESS);
        mProgressDialog = null;
        if (mLookupThread != null) {
            mLookupThread.mActivityHandler = null; // avoid memory leak
            return mLookupThread;
        }

        return super.onRetainNonConfigurationInstance();
    }

    @Override
    protected void onPause() {
        super.onPause();
        savePreferences();
    }

    @Override
    public void onClick(View v) {
        if (v.getId() == mTestButton.getId()) {
            String number = mTestInput.getText().toString();

            if (number.length() > 0) {
                String lookup = mURL.getText().toString();
                String regExp = mRegExp.getText().toString();

                savePreferences();

                showDialog(DIALOG_PROGRESS);
                mLookupThread = new TestLookupThread(mHandler);
                mLookupThread.mURL = lookup;
                mLookupThread.mRegExp = regExp;
                mLookupThread.mNumber = number;
                mLookupThread.start();
            }
        }
    }

    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        if (mIgnoreItemSelection) {
            mIgnoreItemSelection = false;
            return;
        }

        if (position == 0) {
            mURL.setText("");
            mURL.setEnabled(true);
            mURL.setFocusableInTouchMode(true);
            mRegExp.setText("");
            mRegExp.setEnabled(true);
            mRegExp.setFocusableInTouchMode(true);
        } else {
            mURL.setEnabled(false);
            mURL.setFocusable(false);
            mURL.clearFocus();
            mRegExp.setEnabled(false);
            mRegExp.setFocusable(false);
            mRegExp.clearFocus();

            try {
                String name = mLookup.getItemAtPosition(position).toString();
                XmlResourceParser xml = getApplicationContext().getResources().getXml(R.xml.lookups);
                for (int eventType = xml.getEventType(); eventType != XmlPullParser.END_DOCUMENT; eventType = xml.next()) {
                    if ((eventType == XmlPullParser.START_TAG) && xml.getName().equals("name")) {
                        eventType = xml.next(); xml.require(XmlPullParser.TEXT, null, null);
                        if (xml.getText().equals(name)) {
                            eventType = xml.next(); xml.require(XmlPullParser.END_TAG, null, null);
                            eventType = xml.next(); xml.require(XmlPullParser.START_TAG, null, "url");
                            eventType = xml.next(); xml.require(XmlPullParser.TEXT, null, null);
                            mURL.setText(xml.getText());

                            eventType = xml.next(); xml.require(XmlPullParser.END_TAG, null, null);
                            eventType = xml.next(); xml.require(XmlPullParser.START_TAG, null, "regexp");
                            eventType = xml.next(); xml.require(XmlPullParser.TEXT, null, null);
                            mRegExp.setText(xml.getText());

                            break;
                        }
                    }
                }

                xml.close();
            } catch (XmlPullParserException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(Menu.NONE, MENU_CUSTOMIZE, Menu.NONE, R.string.Customize);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        MenuItem customize = menu.findItem(MENU_CUSTOMIZE);
        if (customize != null) {
            customize.setEnabled(mLookup.getSelectedItemPosition() != 0);
        }

        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == MENU_CUSTOMIZE) {
            if (item.isEnabled()) {
                mIgnoreItemSelection = true;
                mLookup.setSelection(0);
                mURL.setEnabled(true);
                mURL.setFocusableInTouchMode(true);
                mRegExp.setEnabled(true);
                mRegExp.setFocusableInTouchMode(true);
            }

            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    public void savePreferences() {
        SharedPreferences.Editor prefsEditor = mPrefs.edit();
        prefsEditor.putString(PREFS_LOOKUP, mLookup.getSelectedItem().toString());
        prefsEditor.putString(PREFS_URL, mURL.getText().toString());
        prefsEditor.putString(PREFS_REGEXP, mRegExp.getText().toString());
        prefsEditor.putBoolean(PREFS_NOTIFY, mNotify.isChecked());
        prefsEditor.commit();
    }
}
