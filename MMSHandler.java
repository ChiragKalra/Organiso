package com.bruhascended.sms;

import android.content.Context;
import android.webkit.MimeTypeMap;

import androidx.room.Room;

import com.bruhascended.db.Message;
import com.bruhascended.db.MessageDao;
import com.bruhascended.db.MessageDatabase;
import com.google.android.mms.ContentType;
import com.google.android.mms.pdu_alt.MultimediaMessagePdu;
import com.google.android.mms.pdu_alt.PduBody;
import com.google.android.mms.pdu_alt.PduPart;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;

import io.michaelrocks.libphonenumber.android.NumberParseException;
import io.michaelrocks.libphonenumber.android.PhoneNumberUtil;
import io.michaelrocks.libphonenumber.android.Phonenumber;

public class MMSHandler {
    private static String saveMedia(String content, Context mContext, Long date, String typeString) throws IOException {
        String name = date.toString() + "." + MimeTypeMap.getSingleton().getExtensionFromMimeType(typeString);
        File destination = new File(mContext.getFilesDir(), name);
        OutputStreamWriter output = new OutputStreamWriter( new FileOutputStream(destination));
        output.write(content);
        output.flush();
        return destination.getAbsolutePath();
    }

    private static String getRaw(Context context, String number) {
        if (number.startsWith("+")) {
            try {
                PhoneNumberUtil phoneNumberUtil = PhoneNumberUtil.createInstance(context);
                Phonenumber.PhoneNumber numberProto = phoneNumberUtil.parse(number, "");
                return Long.toString(numberProto.getNationalNumber()).replaceAll("\\s", "");
            } catch (NumberParseException e) {
                return number.replaceAll("\\s", "");
            }
        } else return number.replaceAll("\\s", "");
    }

    public static void processPduAttachments(Context context, MultimediaMessagePdu pdu) {
        PduBody body = pdu.getBody();
        if (body != null) {
            int partsNum = body.getPartsNum();
            for (int i = 0; i < partsNum; i++) {
                try {
                    PduPart part = body.getPart(i);
                    if (part == null || part.getData() == null || part.getContentType() == null || part.getName() == null)
                        continue;
                    String partType = new String(part.getContentType());
                    String address = pdu.getFrom().toString();
                    String name = getRaw(context, address);
                    long date = System.currentTimeMillis();

                    MessageDao db = Room.databaseBuilder(
                            context.getApplicationContext(),
                            MessageDatabase.class,
                            name
                    ).build().manager();
                    Message m = null;
                    if (ContentType.isTextType(partType)) {
                        m = new Message(
                                null,
                                name,
                                body.toString(),
                                1,
                                date,
                                -1,
                                false,
                                null
                        );
                    } else if (ContentType.isImageType(partType) ||
                            ContentType.isVideoType(partType) ||
                            ContentType.isAudioType(partType)) {
                        m = new Message(
                                null,
                                name,
                                "",
                                1,
                                date,
                                -1,
                                false,
                                saveMedia(body.toString(), context, date, partType)
                        );
                    }
                    if (m != null) db.insert(m);
                } catch (Exception e) {
                    e.printStackTrace();
                    // Bad part shouldn't ruin the party for the other parts
                }
            }
        }
    }
}