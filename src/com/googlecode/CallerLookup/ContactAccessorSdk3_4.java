/*
 * Copyright (C) 2009 The Android Open Source Project
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

import android.content.ContentResolver;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.provider.Contacts;
import android.provider.Contacts.People;

/**
 * An implementation of {@link ContactAccessor} that uses legacy Contacts API.
 * These APIs are deprecated and should not be used unless we are running on a
 * pre-Eclair SDK.
 * <p>
 * There are several reasons why we wouldn't want to use this class on an Eclair device:
 * <ul>
 * <li>It would see at most one account, namely the first Google account created on the device.
 * <li>It would work through a compatibility layer, which would make it inherently less efficient.
 * <li>Not relevant to this particular example, but it would not have access to new kinds
 * of data available through current APIs.
 * </ul>
 */
@SuppressWarnings("deprecation")
public class ContactAccessorSdk3_4 extends ContactAccessor {

    /**
     * Returns a Pick Contact intent using the pre-Eclair "people" URI.
     */
    @Override
    public Intent getPickContactIntent() {
        return new Intent(Intent.ACTION_PICK, People.CONTENT_URI);
    }

    /**
     * Retrieves the contact information.
     */
    @Override
    public String findPhoneNumber(ContentResolver contentResolver, String phoneNumber) {
        String contactInfo = null;
        Uri contactUri = Uri.withAppendedPath(Contacts.Phones.CONTENT_FILTER_URL, Uri.encode(phoneNumber));
        Cursor cursor = contentResolver.query(contactUri, new String[]{People.DISPLAY_NAME}, null, null, null);
        try {
            if (cursor.moveToFirst()) {
                contactInfo = (cursor.getString(0));
            }
        } finally {
            cursor.close();
        }

        return contactInfo;
    }

    @Override
    public String getContentItemType() {
        return Contacts.People.CONTENT_ITEM_TYPE;
    }
}
