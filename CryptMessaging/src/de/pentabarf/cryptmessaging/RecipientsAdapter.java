/*
 * Copyright (C) 2008 Esmertec AG.
 * Copyright (C) 2008 The Android Open Source Project
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

package de.pentabarf.cryptmessaging;

import android.content.ContentResolver;
import android.content.Context;
import android.database.CharArrayBuffer;
import android.database.Cursor;
import android.database.CursorWrapper;
import android.net.Uri;
import android.provider.ContactsContract.CommonDataKinds.Note;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.Data;
import android.provider.ContactsContract.DataUsageFeedback;
import android.telephony.PhoneNumberUtils;
import android.text.Annotation;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.TextUtils;
import android.util.SparseArray;
import android.view.View;
import android.widget.ResourceCursorAdapter;
import android.widget.TextView;

/**
 * This adapter is used to filter contacts on both name and number. Originally
 * taken from com.android.mms.ui.RecipientsAdapter, but adjusted to our needs
 */
@SuppressWarnings({
        "unqualified-field-access", "static-access", "unused"
})
public class RecipientsAdapter extends ResourceCursorAdapter {

    private class NoteJoinedCursor extends CursorWrapper {
        private final int columnCount;
        private int count = 0;
        private String note = null;
        private int position = 0;
        private final String TAG = "NoteJoinedCursor";

        public NoteJoinedCursor(Cursor cursor) {
            super(cursor);
            columnCount = cursor.getColumnCount() + 1;

            for (boolean status = moveToFirst(); status; status = moveToNext())
                count++;
            moveToFirst();

        }

        @Override
        public void copyStringToBuffer(int columnIndex, CharArrayBuffer buffer) {
            if (columnIndex == getColumnCount() - 1) {
                if (note != null) {
                    if (buffer.data == null
                            || buffer.data.length < note.length())
                        buffer.data = note.toCharArray();
                    else
                        note.getChars(0, note.length(), buffer.data, 0);
                } else
                    buffer.sizeCopied = 0;
            } else
                super.copyStringToBuffer(columnIndex, buffer);
        }

        private String fetchNote() {
            int rawContactId = getInt(CONTACT_ID_INDEX);
            return noteContacts.get(rawContactId, null);
        }

        @Override
        public int getColumnCount() {
            return columnCount;
        }

        @Override
        public int getColumnIndex(String columnName) {
            if ("note".equals(columnName))
                return getColumnCount() - 1;
            return super.getColumnIndex(columnName);
        }

        @Override
        public int getColumnIndexOrThrow(String columnName)
                throws IllegalArgumentException {
            if ("note".equals(columnName))
                return getColumnCount() - 1;
            return super.getColumnIndexOrThrow(columnName);
        }

        @Override
        public String getColumnName(int columnIndex) {
            if (columnIndex == getColumnCount() - 1)
                return "note";
            return super.getColumnName(columnIndex);
        }

        @Override
        public String[] getColumnNames() {
            int count = getColumnCount();
            String[] names = new String[count];
            System.arraycopy(super.getColumnNames(), 0, names, 0, count - 1);
            names[count - 1] = "note";
            return names;
        }

        @Override
        public int getCount() {
            return count;
        }

        @Override
        public int getPosition() {
            return position;
        }

        @Override
        public String getString(int columnIndex) {
            if (columnIndex == getColumnCount() - 1)
                return note;
            return super.getString(columnIndex);
        }

        @Override
        public int getType(int columnIndex) {
            if (columnIndex == getColumnCount() - 1)
                return FIELD_TYPE_STRING;
            return super.getType(columnIndex);
        }

        @Override
        public boolean move(int offset) {
            boolean status = true;
            for (int i = offset; status && i < 0; i++)
                status = moveToPrevious();

            for (int i = offset; status && i > 0; i--)
                status = moveToNext();

            if (status)
                position += offset;
            else
                position = -1;

            return status;
        }

        @Override
        public boolean moveToFirst() {
            note = null;

            boolean status;
            for (status = super.moveToFirst(); status == true && note == null; status = super
                    .moveToNext()) {
                note = fetchNote();
                if (note != null)
                    break;
            }

            if (status)
                position = 0;
            else
                position = -1;

            return status;
        }

        @Override
        public boolean moveToLast() {
            note = null;

            boolean status;
            for (status = super.moveToLast(); status == true && note == null; status = super
                    .moveToPrevious()) {
                note = fetchNote();
                if (note != null)
                    break;
            }

            if (status)
                position = count;
            else
                position = -1;

            return status;
        }

        @Override
        public boolean moveToNext() {
            note = null;

            boolean status;
            for (status = super.moveToNext(); status == true && note == null; status = super
                    .moveToNext()) {
                note = fetchNote();
                if (note != null)
                    break;
            }

            if (status)
                position++;
            else
                position = -1;

            return status;
        }

        @Override
        public boolean moveToPosition(int position) {
            if (position == -1 || !moveToFirst()) {
                this.position = -1;
                return false;
            }

            return move(position);
        }

        @Override
        public boolean moveToPrevious() {
            note = null;

            boolean status;
            for (status = super.moveToPrevious(); status == true
                    && note == null; status = super.moveToPrevious()) {
                note = fetchNote();
                if (note != null)
                    break;
            }

            if (status)
                position--;
            else
                position = -1;

            return status;
        }

    }

    public static final int CONTACT_ID_INDEX = 1;
    public static final int LABEL_INDEX = 4;
    public static final int NAME_INDEX = 5;
    public static final int NORMALIZED_NUMBER = 6;
    public static final int NUMBER_INDEX = 3;

    private static final String[] PROJECTION_PHONE = {
            Phone._ID, // 0
            Phone.CONTACT_ID, // 1
            Phone.TYPE, // 2
            Phone.NUMBER, // 3
            Phone.LABEL, // 4
            Phone.DISPLAY_NAME, // 5
            Phone.NORMALIZED_NUMBER, // 6
    };

    private static final String SORT_ORDER = Contacts.TIMES_CONTACTED
            + " DESC," + Contacts.DISPLAY_NAME + "," + Phone.TYPE;

    public static final int TYPE_INDEX = 2;

    private static String formatNameAndNumber(String name, String number) {
        String formattedNumber = PhoneNumberUtils.formatNumber(number);

        if (!TextUtils.isEmpty(name) && !name.equals(number)) {
            return name + " <" + formattedNumber + ">";
        } else {
            return formattedNumber;
        }
    }

    protected final ContentResolver mContentResolver;

    private final Context mContext;

    protected final SparseArray<String> noteContacts = new SparseArray<String>();

    public RecipientsAdapter(Context context) {
        // Note that the RecipientsAdapter doesn't support auto-requeries. If we
        // want to respond to changes in the contacts we're displaying in the
        // drop-down,
        // code using this adapter would have to add a line such as:
        // mRecipientsAdapter.setOnDataSetChangedListener(mDataSetChangedListener);
        // See ComposeMessageActivity for an example.
        super(context, R.layout.recipient_filter_item, null, false /*
                                                                    * no
                                                                    * auto-requery
                                                                    */);
        mContext = context;
        mContentResolver = context.getContentResolver();

        Cursor noteCursor = mContentResolver.query(Data.CONTENT_URI,
                new String[] {
                        Data.CONTACT_ID, Note.NOTE
                }, Note.MIMETYPE + "='"
                        + Note.CONTENT_ITEM_TYPE + "'",
                null, null);
        if (noteCursor != null)
            while (noteCursor.moveToNext()) {
                String note = noteCursor.getString(1);
                if (!TextUtils.isEmpty(note))
                    noteContacts.put(noteCursor.getInt(0), note);
            }
    }

    @Override
    public final void bindView(View view, Context context, Cursor cursor) {
        TextView name = (TextView) view.findViewById(R.id.name);
        name.setText(cursor.getString(NAME_INDEX));

        TextView label = (TextView) view.findViewById(R.id.label);
        int type = cursor.getInt(TYPE_INDEX);
        CharSequence labelText = Phone.getTypeLabel(mContext.getResources(),
                type, cursor.getString(LABEL_INDEX));
        // When there's no label, getDisplayLabel() returns a CharSequence of
        // length==1 containing
        // a unicode non-breaking space. Need to check for that and consider
        // that as "no label".
        if (labelText.length() == 0
                || (labelText.length() == 1 && labelText.charAt(0) == '\u00A0')) {
            label.setVisibility(View.GONE);
        } else {
            label.setText(labelText);
            label.setVisibility(View.VISIBLE);
        }

        TextView number = (TextView) view.findViewById(R.id.number);
        number.setText(PhoneNumberUtils.formatNumber(cursor
                .getString(NUMBER_INDEX)));
    }

    @Override
    public final CharSequence convertToString(Cursor cursor) {
        String number = cursor.getString(RecipientsAdapter.NUMBER_INDEX);
        if (number == null) {
            return "";
        }
        number = number.trim();

        String name = cursor.getString(RecipientsAdapter.NAME_INDEX);
        int type = cursor.getInt(RecipientsAdapter.TYPE_INDEX);

        String label = cursor.getString(RecipientsAdapter.LABEL_INDEX);
        CharSequence displayLabel = Phone.getTypeLabel(mContext.getResources(),
                type, label);

        if (name == null) {
            name = "";
        } else {
            // Names with commas are the bane of the recipient editor's
            // existence.
            // We've worked around them by using spans, but there are edge cases
            // where the spans get deleted. Furthermore, having commas in names
            // can be confusing to the user since commas are used as separators
            // between recipients. The best solution is to simply remove commas
            // from names.
            name = name.replace(", ", " ").replace(",", " "); // Make sure we
                                                              // leave a space
                                                              // between parts
                                                              // of names.
        }

        String nameAndNumber = formatNameAndNumber(name, number);

        SpannableString out = new SpannableString(nameAndNumber);
        int len = out.length();

        if (!TextUtils.isEmpty(name)) {
            out.setSpan(new Annotation("name", name), 0, len,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        } else {
            out.setSpan(new Annotation("name", number), 0, len,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }

        String person_id = cursor.getString(RecipientsAdapter.CONTACT_ID_INDEX);
        out.setSpan(new Annotation("person_id", person_id), 0, len,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        out.setSpan(new Annotation("label", displayLabel.toString()), 0, len,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        out.setSpan(new Annotation("number", number), 0, len,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

        return out;
    }

    public String getContactId(int position) {
        Cursor c = (Cursor) getItem(position);
        return c.getString(CONTACT_ID_INDEX);
    }

    public String getContactNumber(int position) {
        Cursor c = (Cursor) getItem(position);
        return c.getString(NUMBER_INDEX);
    }

    @Override
    public Cursor runQueryOnBackgroundThread(CharSequence constraint) {
        String phone = "";
        String cons = null;

        if (constraint != null) {
            cons = constraint.toString();
        }

        Uri uri = Phone.CONTENT_FILTER_URI
                .buildUpon()
                .appendPath(cons)
                .appendQueryParameter(DataUsageFeedback.USAGE_TYPE,
                        DataUsageFeedback.USAGE_TYPE_SHORT_TEXT).build();
        /*
         * if we decide to filter based on phone types use a selection like
         * this. String selection = String.format("%s=%s OR %s=%s OR %s=%s",
         * Phone.TYPE, Phone.TYPE_MOBILE, Phone.TYPE, Phone.TYPE_WORK_MOBILE,
         * Phone.TYPE, Phone.TYPE_MMS);
         */
        Cursor phoneCursor = mContentResolver.query(uri, PROJECTION_PHONE,
                null, // selection,
                null, null);

        // return new CursorWrapper(phoneCursor) {
        //
        // };
        return new NoteJoinedCursor(phoneCursor);
    }
}
