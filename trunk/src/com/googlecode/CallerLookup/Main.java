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

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.DialogInterface.OnCancelListener;
import android.content.res.XmlResourceParser;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.util.Xml;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;
import android.widget.AdapterView.OnItemSelectedListener;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.HashMap;

public class Main extends Activity implements OnClickListener, OnItemSelectedListener {
    class LookupEntry {
        public String mName = null;
        public String mURL = null;
        public String mRegExp = null;

        public LookupEntry() {
        }
        public LookupEntry(String name, String url, String regExp) {
            mName = name;
            mURL = url;
            mRegExp = regExp;
        }
        public LookupEntry(JSONObject object) throws JSONException {
            mName = object.getString("name");
            mURL = object.getString("url");
            mRegExp = object.getString("regexp");
        }

        public JSONObject toJSONObject() throws JSONException {
            JSONObject object = new JSONObject();
            object.put("name", mName);
            object.put("url", mURL);
            object.put("regexp", mRegExp);
            return object;
        }
    }

    public static final String PREFS = "CallerLookupPrefs";
    public static final String PREFS_LOOKUP = "lookup";
    public static final String PREFS_URL = "url";
    public static final String PREFS_REGEXP = "regexp";
    public static final String PREFS_NOTIFY = "notify";
    public static final String PREFS_UPDATE = "update";
    public static final String STATE_TESTINPUT = "testinput";

    public static final int MENU_CUSTOMIZE = Menu.FIRST + 1;
    public static final int MENU_SAVE = Menu.FIRST + 2;
    public static final int MENU_REMOVE = Menu.FIRST + 3;
    public static final int MENU_SUBMIT = Menu.FIRST + 4;
    public static final int MENU_UPDATE = Menu.FIRST + 5;
    public static final int MENU_HELP = Menu.FIRST + 6;

    public static final int MESSAGE_UPDATE_FINISHED = 1;
    public static final int MESSAGE_UPDATE_UNNECESSARY = 2;

    public static final int DIALOG_PROGRESS = 1;

    public static final String EMAIL_ADDRESS = "fridvin@gmail.com";
    public static final String EMAIL_SUBJECT = "CallerLookup entry";

    public static final String UPDATE_URL = "http://caller-lookup.googlecode.com/svn/trunk/res/xml/lookups.xml";
    public static final String UPDATE_FILE = "CallerLookupEntries.xml";

    public static final String SAVED_FILE = "CallerLookupUserEntries.json";
    public static final String SAVED_PREFIX = "* ";

    private boolean mIgnoreItemSelection = false;
    private boolean mIgnoreLookupResult = false;

    private TestLookupThread mLookupThread = null;

    private ProgressDialog mProgressDialog = null;

    private HashMap<String, LookupEntry> mLookupEntries = null;
    private HashMap<String, LookupEntry> mUserLookupEntries = null;

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

    private final Handler mUpdateHandler = new Handler() {
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MESSAGE_UPDATE_FINISHED:
                    updateLookupEntries();
                    dismissDialog(DIALOG_PROGRESS);
                    Toast.makeText(getApplicationContext(), getApplicationContext().getResources().getText(R.string.UpdateFinished), Toast.LENGTH_SHORT).show();
                    break;
                case MESSAGE_UPDATE_UNNECESSARY:
                    dismissDialog(DIALOG_PROGRESS);
                    Toast.makeText(getApplicationContext(), getApplicationContext().getResources().getText(R.string.UpdateNotNeeded), Toast.LENGTH_SHORT).show();
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

        mPrefs = getSharedPreferences(PREFS, 0);
        mURL.setText(mPrefs.getString(PREFS_URL, ""));
        mRegExp.setText(mPrefs.getString(PREFS_REGEXP, ""));
        mNotify.setChecked(mPrefs.getBoolean(PREFS_NOTIFY, false));

        mLookup.setOnItemSelectedListener(this);
        mTestButton.setOnClickListener(this);

        updateLookupEntries();

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
        if (parent.getId() == mLookup.getId()) {
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

                String name = mLookup.getItemAtPosition(position).toString();
                LookupEntry lookupEntry = mLookupEntries.get(name);
                assert(lookupEntry != null);
                mURL.setText(lookupEntry.mURL);
                mRegExp.setText(lookupEntry.mRegExp);
            }
        }
    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(Menu.NONE, MENU_CUSTOMIZE, Menu.NONE, R.string.Customize);
        menu.add(Menu.NONE, MENU_SAVE, Menu.NONE, R.string.Save);
        menu.add(Menu.NONE, MENU_REMOVE, Menu.NONE, R.string.Remove);
        menu.add(Menu.NONE, MENU_SUBMIT, Menu.NONE, R.string.Submit);
        menu.add(Menu.NONE, MENU_UPDATE, Menu.NONE, R.string.Update);
        menu.add(Menu.NONE, MENU_HELP, Menu.NONE, R.string.Help);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        assert((mLookup.getSelectedItemPosition() == 0) || (mLookupEntries.get(mLookup.getSelectedItem().toString()) != null));
        boolean custom = (mLookup.getSelectedItemPosition() == 0);
        boolean saved = (!custom && mLookupEntries.get(mLookup.getSelectedItem().toString()).mName.startsWith(SAVED_PREFIX));
        boolean empty = (mURL.getText().toString().length() <= 0);

        MenuItem customize = menu.findItem(MENU_CUSTOMIZE);
        assert(customize != null);
        customize.setEnabled(!custom);

        MenuItem save = menu.findItem(MENU_SAVE);
        assert(save != null);
        save.setEnabled(custom && !empty);

        MenuItem remove = menu.findItem(MENU_REMOVE);
        assert(remove != null);
        remove.setEnabled(saved);

        MenuItem submit = menu.findItem(MENU_SUBMIT);
        assert(submit != null);
        submit.setEnabled((custom || saved) && !empty);

        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case MENU_CUSTOMIZE:
                if (item.isEnabled()) {
                    doCustomize();
                }
                return true;
            case MENU_SAVE:
                if (item.isEnabled()) {
                    doSave();
                }
                return true;
            case MENU_REMOVE:
                if (item.isEnabled()) {
                    doRemove();
                }
                return true;
            case MENU_SUBMIT:
                if (item.isEnabled()) {
                    doSubmit();
                }
                return true;
            case MENU_UPDATE:
                if (item.isEnabled()) {
                    doUpdate();
                }
                return true;
            case MENU_HELP:
                if (item.isEnabled()) {
                    doHelp();
                }
                return true;
            default:
                break;
        }

        return super.onOptionsItemSelected(item);
    }

    @SuppressWarnings("unchecked")
    public void updateLookupEntries() {
        parseLookupEntries();

        ArrayAdapter<CharSequence> adapter = (ArrayAdapter<CharSequence>)mLookup.getAdapter();
        if (adapter == null) {
            adapter = new ArrayAdapter<CharSequence>(getApplicationContext(), android.R.layout.simple_spinner_item);
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            mLookup.setAdapter(adapter);
        } else {
            adapter.clear();
        }

        adapter.add("Custom");
        for (String lookupEntryName : mLookupEntries.keySet()) {
            adapter.add(lookupEntryName);
        }

        for (String lookupEntryName : mUserLookupEntries.keySet()) {
            assert(!mLookupEntries.containsKey(lookupEntryName));
            mLookupEntries.put(lookupEntryName, mUserLookupEntries.get(lookupEntryName));
            adapter.add(lookupEntryName);
        }

        String name = mPrefs.getString(PREFS_LOOKUP, "Custom");
        if ((name.length() > 0) && !name.equals("Custom")) {
            int count = mLookup.getCount();
            for (int i = 1; i < count; i++) {
                if (mLookup.getItemAtPosition(i).toString().equals(name)) {
                    mLookup.setSelection(i);
                    break;
                }
            }
        } else {
            if (mLookup.getSelectedItemPosition() != 0) {
                mIgnoreItemSelection = true;
                mLookup.setSelection(0);
            }
        }
    }

    public void parseLookupEntries() {
        mLookupEntries = new HashMap<String, LookupEntry>();
        mUserLookupEntries = new HashMap<String, LookupEntry>();

        boolean updateFound = false;
        for (String fileName : getApplicationContext().fileList()) {
            if (fileName.equals(UPDATE_FILE)) {
                try {
                    FileInputStream file = getApplicationContext().openFileInput(UPDATE_FILE);
                    XmlPullParser xml = Xml.newPullParser();
                    xml.setInput(file, null);
                    parseLookupEntries(xml, mLookupEntries);
                    file.close();
                    updateFound = true;
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                } catch (XmlPullParserException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            } else if (fileName.equals(SAVED_FILE)) {
                try {
                    FileInputStream file = getApplicationContext().openFileInput(SAVED_FILE);
                    InputStreamReader reader = new InputStreamReader(file);
                    char [] content = new char[8000];
                    reader.read(content);
                    JSONArray userLookupEntries = new JSONArray(new String(content));
                    int count = userLookupEntries.length();
                    for (int i = 0; i < count; i++) {
                        JSONObject userLookupEntry = userLookupEntries.getJSONObject(i);
                        mUserLookupEntries.put(userLookupEntry.getString("name"), new LookupEntry(userLookupEntry));
                    }
                    file.close();
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                } catch (JSONException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        if (!updateFound) {
            XmlResourceParser xml = getApplicationContext().getResources().getXml(R.xml.lookups);
            parseLookupEntries(xml, mLookupEntries);
            xml.close();
        }
    }

    public void parseLookupEntries(XmlPullParser xml, HashMap<String, LookupEntry> lookupEntries) {
        try {
            for (int eventType = xml.getEventType(); eventType != XmlPullParser.END_DOCUMENT; eventType = xml.next()) {
                if ((eventType == XmlPullParser.START_TAG) && xml.getName().equals("lookupEntry")) {
                    eventType = xml.next();
                    LookupEntry lookupEntry = new LookupEntry();
                    while ((eventType != XmlPullParser.END_TAG) && (eventType != XmlPullParser.END_DOCUMENT)) {
                        if (eventType == XmlPullParser.START_TAG) {
                            if (xml.getName().equals("name")) {
                                assert(lookupEntry.mName == null);
                                eventType = xml.next(); xml.require(XmlPullParser.TEXT, null, null);
                                lookupEntry.mName = xml.getText();
                            } else if (xml.getName().equals("url")) {
                                assert(lookupEntry.mURL == null);
                                eventType = xml.next(); xml.require(XmlPullParser.TEXT, null, null);
                                lookupEntry.mURL = xml.getText();
                            } else {
                                assert(xml.getName().equals("regexp"));
                                assert(lookupEntry.mRegExp == null);
                                eventType = xml.next(); xml.require(XmlPullParser.TEXT, null, null);
                                lookupEntry.mRegExp = xml.getText();
                            }

                            eventType = xml.next(); xml.require(XmlPullParser.END_TAG, null, null);
                        }
                        eventType = xml.next();
                    }

                    if ((lookupEntry.mName != null) && (lookupEntry.mURL != null) && (lookupEntry.mRegExp != null)) {
                        lookupEntries.put(lookupEntry.mName, lookupEntry);
                    }
                }
            }
        } catch (XmlPullParserException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void addUserLookupEntry(String name) {
        mUserLookupEntries.put(name, new LookupEntry(name, mURL.getText().toString(), mRegExp.getText().toString()));
        mLookupEntries.put(name, new LookupEntry(name, mURL.getText().toString(), mRegExp.getText().toString()));
        saveUserLookupEntries();
    }

    public void removeUserLookupEntry(String name) {
        mLookupEntries.remove(name);
        mUserLookupEntries.remove(name);
        saveUserLookupEntries();
    }

    public void savePreferences() {
        SharedPreferences.Editor prefsEditor = mPrefs.edit();
        prefsEditor.putString(PREFS_LOOKUP, mLookup.getSelectedItem().toString());
        prefsEditor.putString(PREFS_URL, mURL.getText().toString());
        prefsEditor.putString(PREFS_REGEXP, mRegExp.getText().toString());
        prefsEditor.putBoolean(PREFS_NOTIFY, mNotify.isChecked());
        prefsEditor.commit();
    }

    public void saveUserLookupEntries() {
        try {
            FileOutputStream file = getApplicationContext().openFileOutput(SAVED_FILE, MODE_PRIVATE);
            JSONArray userLookupEntries = new JSONArray();
            for (String lookupEntryName : mUserLookupEntries.keySet()) {
                try {
                    userLookupEntries.put(mUserLookupEntries.get(lookupEntryName).toJSONObject());
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }

            OutputStreamWriter content = new OutputStreamWriter(file);
            content.write(userLookupEntries.toString());
            content.flush();
            content.close();
            file.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void doCustomize() {
        mIgnoreItemSelection = true;
        mLookup.setSelection(0);
        mURL.setEnabled(true);
        mURL.setFocusableInTouchMode(true);
        mRegExp.setEnabled(true);
        mRegExp.setFocusableInTouchMode(true);
    }

    public void doSave() {
        final Context context = this;
        final EditText input = new EditText(context);
        input.setHint(R.string.Name);

        AlertDialog.Builder alert = new AlertDialog.Builder(context);
        alert.setTitle(R.string.SaveTitle);
        alert.setMessage(R.string.SaveMessage);
        alert.setView(input);

        alert.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
            @SuppressWarnings("unchecked")
            @Override
            public void onClick(DialogInterface dialog, int which) {
                final String name = SAVED_PREFIX + input.getText().toString().trim();
                if (name.length() <= SAVED_PREFIX.length()) {
                    AlertDialog.Builder error = new AlertDialog.Builder(context);
                    error.setTitle(R.string.NameMissingTitle);
                    error.setMessage(R.string.NameMissingMessage);
                    error.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            doSave();
                        }
                    });
                    error.show();
                    return;
                }
                if (mLookupEntries.containsKey(name)) {
                    AlertDialog.Builder confirm = new AlertDialog.Builder(context);
                    confirm.setTitle(R.string.NameConfirmTitle);
                    confirm.setMessage(R.string.NameConfirmMessage);
                    confirm.setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            mLookupEntries.remove(name);
                            mUserLookupEntries.remove(name);
                            addUserLookupEntry(name);

                            int count = mLookup.getCount();
                            for (int i = 1; i < count; i++) {
                                if (mLookup.getItemAtPosition(i).toString().equals(name)) {
                                    mLookup.setSelection(i);
                                    break;
                                }
                            }
                        }
                    });
                    confirm.setNegativeButton(android.R.string.no, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            doSave();
                        }
                    });
                    confirm.show();
                    return;
                }

                addUserLookupEntry(name);

                ArrayAdapter<CharSequence> adapter = (ArrayAdapter<CharSequence>)mLookup.getAdapter();
                adapter.add(name);
                mLookup.setSelection(mLookup.getCount() - 1);
            }
        });

        alert.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
            }
        });

        alert.show();
    }

    public void doRemove() {
        final Context context = this;
        final String name = mLookup.getSelectedItem().toString();

        AlertDialog.Builder confirm = new AlertDialog.Builder(context);
        confirm.setTitle(R.string.RemoveTitle);
        confirm.setMessage(R.string.RemoveMessage);
        confirm.setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
            @SuppressWarnings("unchecked")
            @Override
            public void onClick(DialogInterface dialog, int which) {
                removeUserLookupEntry(name);
                doCustomize();

                ArrayAdapter<CharSequence> adapter = (ArrayAdapter<CharSequence>)mLookup.getAdapter();
                adapter.remove(name);
            }
        });
        confirm.setNegativeButton(android.R.string.no, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
            }
        });
        confirm.show();
    }

    public void doSubmit() {
        String entry = "";
        if (mLookup.getSelectedItemPosition() != 0) {
            entry = mLookup.getSelectedItem().toString() + "\n";
        }

        entry += mURL.getText().toString() + "\n";
        entry += mRegExp.getText().toString() + "\n";

        try {
            String [] mailto = { EMAIL_ADDRESS };
            Intent sendIntent = new Intent(Intent.ACTION_SEND);
            sendIntent.putExtra(Intent.EXTRA_EMAIL, mailto);
            sendIntent.putExtra(Intent.EXTRA_SUBJECT, EMAIL_SUBJECT);
            sendIntent.putExtra(Intent.EXTRA_TEXT, entry);
            sendIntent.setType("message/rfc822");
            startActivity(sendIntent);
        } catch (ActivityNotFoundException e) {
            e.printStackTrace();

            AlertDialog.Builder alert = new AlertDialog.Builder(this);
            alert.setTitle(R.string.SubmitFailureTitle);
            alert.setMessage(R.string.SubmitFailureMessage);
            alert.setPositiveButton(android.R.string.ok, null);
            alert.show();
        }
    }

    public void doUpdate() {
        showDialog(DIALOG_PROGRESS);
        savePreferences();
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    URL uri = new URL(UPDATE_URL);
                    URLConnection urlc = uri.openConnection();
                    long lastModified = urlc.getLastModified();
                    if ((lastModified == 0) || (lastModified != mPrefs.getLong(PREFS_UPDATE, 0))) {
                        FileOutputStream file = getApplicationContext().openFileOutput(UPDATE_FILE, MODE_PRIVATE);
                        OutputStreamWriter content = new OutputStreamWriter(file);

                        String tmp;
                        InputStream is = urlc.getInputStream();
                        BufferedReader br = new BufferedReader(new InputStreamReader(is));
                        while ((tmp = br.readLine()) != null) {
                            content.write(tmp + "\n");
                        }

                        content.flush();
                        content.close();
                        file.close();

                        SharedPreferences.Editor prefsEditor = mPrefs.edit();
                        prefsEditor.putLong(PREFS_UPDATE, lastModified);
                        prefsEditor.commit();

                        Message message = mUpdateHandler.obtainMessage();
                        message.what = MESSAGE_UPDATE_FINISHED;
                        mUpdateHandler.sendMessage(message);
                    } else {
                        Message message = mUpdateHandler.obtainMessage();
                        message.what = MESSAGE_UPDATE_UNNECESSARY;
                        mUpdateHandler.sendMessage(message);
                    }
                } catch (MalformedURLException e) {
                    System.out.println(e.getMessage());
                } catch (IOException e) {
                    System.out.println(e.getMessage());
                }
            }
        }).start();
    }

    public void doHelp() {
        AlertDialog.Builder alert = new AlertDialog.Builder(this);
        alert.setTitle(R.string.HelpTitle);
        alert.setMessage(R.string.HelpMessage);
        alert.setPositiveButton(android.R.string.ok, null);
        alert.show();
    }
}
