/*
 * Copyright (C) 2015 Jacob Klinker
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.mms.service_alt;

import android.app.PendingIntent;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteException;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.provider.Telephony;
import android.text.TextUtils;
import android.webkit.MimeTypeMap;

import androidx.room.Room;

import io.michaelrocks.libphonenumber.android.NumberParseException;
import io.michaelrocks.libphonenumber.android.PhoneNumberUtil;
import io.michaelrocks.libphonenumber.android.Phonenumber;
import com.android.mms.service_alt.exception.MmsHttpException;
import com.bruhascended.sms.db.Message;
import com.bruhascended.sms.db.MessageDao;
import com.bruhascended.sms.db.MessageDatabase;
import com.google.android.mms.ContentType;
import com.google.android.mms.MmsException;
import com.google.android.mms.pdu_alt.GenericPdu;
import com.google.android.mms.pdu_alt.MultimediaMessagePdu;
import com.google.android.mms.pdu_alt.PduBody;
import com.google.android.mms.pdu_alt.PduHeaders;
import com.google.android.mms.pdu_alt.PduParser;
import com.google.android.mms.pdu_alt.PduPart;
import com.google.android.mms.pdu_alt.PduPersister;
import com.google.android.mms.pdu_alt.RetrieveConf;
import com.google.android.mms.util_alt.SqliteWrapper;
import com.klinker.android.send_message.BroadcastUtils;
import com.klinker.android.send_message.Transaction;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;

import timber.log.Timber;

/**
 * Request to download an MMS
 */
public class DownloadRequest extends MmsRequest {

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

    private static void processPduAttachments(Context context, MultimediaMessagePdu pdu) {
        PduBody body = pdu.getBody();
        if (body != null) {
            int partsNum = body.getPartsNum();
            for (int i = 0; i < partsNum; i++) {
                try {
                    PduPart part = body.getPart(i);
                    if (part == null || part.getData() == null || part.getContentType() == null || part.getName() == null)
                        continue;
                    String partType = new String(part.getContentType());
                    String partName = new String(part.getName());
                    String address = pdu.getFrom().toString();
                    String name = getRaw(context, address);
                    Long date = System.currentTimeMillis();

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

    private static final String LOCATION_SELECTION =
            Telephony.Mms.MESSAGE_TYPE + "=? AND " + Telephony.Mms.CONTENT_LOCATION + " =?";

    static final String[] PROJECTION = new String[]{
            Telephony.Mms.CONTENT_LOCATION
    };

    // The indexes of the columns which must be consistent with above PROJECTION.
    static final int COLUMN_CONTENT_LOCATION = 0;

    private final String mLocationUrl;
    private final PendingIntent mDownloadedIntent;
    private final Uri mContentUri;

    public DownloadRequest(RequestManager manager, int subId, String locationUrl,
                           Uri contentUri, PendingIntent downloadedIntent, String creator,
                           Bundle configOverrides, Context context) throws MmsException {
        super(manager, subId, creator, configOverrides);

        if (locationUrl == null) {
            mLocationUrl = getContentLocation(context, contentUri);
        } else {
            mLocationUrl = locationUrl;
        }

        mDownloadedIntent = downloadedIntent;
        mContentUri = contentUri;
    }

    @Override
    protected byte[] doHttp(Context context, MmsNetworkManager netMgr, ApnSettings apn)
            throws MmsHttpException {
        final MmsHttpClient mmsHttpClient = netMgr.getOrCreateHttpClient();
        if (mmsHttpClient == null) {
            Timber.e("MMS network is not ready!");
            throw new MmsHttpException(0/*statusCode*/, "MMS network is not ready");
        }
        return mmsHttpClient.execute(
                mLocationUrl,
                null/*pud*/,
                MmsHttpClient.METHOD_GET,
                apn.isProxySet(),
                apn.getProxyAddress(),
                apn.getProxyPort(),
                mMmsConfig);
    }

    @Override
    protected PendingIntent getPendingIntent() {
        return mDownloadedIntent;
    }

    @Override
    protected int getQueueType() {
        return 1;
    }

    @Override
    protected Uri persistIfRequired(Context context, int result, byte[] response) {
        if (!mRequestManager.getAutoPersistingPref()) {
            notifyOfDownload(context);
            return null;
        }

        return persist(context, response, mMmsConfig, mLocationUrl, mSubId, mCreator);
    }

    public static Uri persist(Context context, byte[] response, MmsConfig.Overridden mmsConfig,
                              String locationUrl, int subId, String creator) {
        // Let any mms apps running as secondary user know that a new mms has been downloaded.
        notifyOfDownload(context);

        Timber.d("DownloadRequest.persistIfRequired");
        if (response == null || response.length < 1) {
            Timber.e("DownloadRequest.persistIfRequired: empty response");
            // Update the retrieve status of the NotificationInd
            final ContentValues values = new ContentValues(1);
            values.put(Telephony.Mms.RETRIEVE_STATUS, PduHeaders.RETRIEVE_STATUS_ERROR_END);
            SqliteWrapper.update(
                    context,
                    context.getContentResolver(),
                    Telephony.Mms.CONTENT_URI,
                    values,
                    LOCATION_SELECTION,
                    new String[]{
                            Integer.toString(PduHeaders.MESSAGE_TYPE_NOTIFICATION_IND),
                            locationUrl
                    });
            return null;
        }
        final long identity = Binder.clearCallingIdentity();
        try {
            final GenericPdu pdu = (new PduParser(response, mmsConfig.getSupportMmsContentDisposition())).parse();
            processPduAttachments(context, (MultimediaMessagePdu) pdu);
            if (!(pdu instanceof RetrieveConf)) {
                Timber.e("DownloadRequest.persistIfRequired: invalid parsed PDU");

                // Update the error type of the NotificationInd
                setErrorType(context, locationUrl, Telephony.MmsSms.ERR_TYPE_MMS_PROTO_PERMANENT);
                return null;
            }
            final RetrieveConf retrieveConf = (RetrieveConf) pdu;
            final int status = retrieveConf.getRetrieveStatus();
//            if (status != PduHeaders.RETRIEVE_STATUS_OK) {
//                Timber.e("DownloadRequest.persistIfRequired: retrieve failed "
//                        + status);
//                // Update the retrieve status of the NotificationInd
//                final ContentValues values = new ContentValues(1);
//                values.put(Telephony.Mms.RETRIEVE_STATUS, status);
//                SqliteWrapper.update(
//                        context,
//                        context.getContentResolver(),
//                        Telephony.Mms.CONTENT_URI,
//                        values,
//                        LOCATION_SELECTION,
//                        new String[]{
//                                Integer.toString(PduHeaders.MESSAGE_TYPE_NOTIFICATION_IND),
//                                mLocationUrl
//                        });
//                return null;
//            }
            // Store the downloaded message
            final PduPersister persister = PduPersister.getPduPersister(context);
            final Uri messageUri = persister.persist(pdu, Telephony.Mms.Inbox.CONTENT_URI, PduPersister.DUMMY_THREAD_ID, true, true, null);
            if (messageUri == null) {
                Timber.e("DownloadRequest.persistIfRequired: can not persist message");
                return null;
            }

            // Update some of the properties of the message
            final ContentValues values = new ContentValues();
            values.put(Telephony.Mms.DATE, System.currentTimeMillis() / 1000L);
            try {
                values.put(Telephony.Mms.DATE_SENT, retrieveConf.getDate());
            } catch (Exception ignored) {
            }
            values.put(Telephony.Mms.READ, 0);
            values.put(Telephony.Mms.SEEN, 0);
            if (!TextUtils.isEmpty(creator)) {
                values.put(Telephony.Mms.CREATOR, creator);
            }

            if (SubscriptionIdChecker.getInstance(context).canUseSubscriptionId()) {
                values.put(Telephony.Mms.SUBSCRIPTION_ID, subId);
            }

            try {
                context.getContentResolver().update(messageUri, values, null, null);
            } catch (SQLiteException e) {
                // On MIUI and a couple other devices, the above call will fail and say `no such column: sub_id`
                // If before making that call, we check to see if the sub_id column is available for messageUri, we will
                // find that it is available, and yet the update call will still fail. So - there's no way we can know
                // in advance that it will fail, and we have to just try again
                if (values.containsKey(Telephony.Mms.SUBSCRIPTION_ID)) {
                    values.remove(Telephony.Mms.SUBSCRIPTION_ID);
                    context.getContentResolver().update(messageUri, values, null, null);
                } else {
                    throw e;
                }
            }
            // Delete the corresponding NotificationInd
            SqliteWrapper.delete(context, context.getContentResolver(), Telephony.Mms.CONTENT_URI, LOCATION_SELECTION,
                    new String[]{Integer.toString(PduHeaders.MESSAGE_TYPE_NOTIFICATION_IND), locationUrl});

            return messageUri;
        } catch (MmsException e) {
            Timber.e(e, "DownloadRequest.persistIfRequired: can not persist message");
        } catch (SQLiteException e) {
            Timber.e(e, "DownloadRequest.persistIfRequired: can not update message");
        } catch (RuntimeException e) {
            Timber.e(e, "DownloadRequest.persistIfRequired: can not parse response");
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
        return null;
    }

    private static void notifyOfDownload(Context context) {
        BroadcastUtils.sendExplicitBroadcast(context, new Intent(), Transaction.NOTIFY_OF_MMS);

        // TODO, not sure what this is doing... sending a broadcast that
        // the download has finished from a specific user account I believe.
//        final Intent intent = new Intent("android.provider.Telephony.MMS_DOWNLOADED");
//        intent.addFlags(Intent.FLAG_RECEIVER_NO_ABORT);
//
//        // Get a list of currently started users.
//        int[] users = null;
//        try {
//            users = ActivityManagerNative.getDefault().getRunningUserIds();
//        } catch (RemoteException re) {
//        }
//        if (users == null) {
//            users = new int[] {UserHandle.ALL.getIdentifier()};
//        }
//        final UserManager userManager =
//                (UserManager) context.getSystemService(Context.USER_SERVICE);
//
//        // Deliver the broadcast only to those running users that are permitted
//        // by user policy.
//        for (int i = users.length - 1; i >= 0; i--) {
//            UserHandle targetUser = new UserHandle(users[i]);
//            if (users[i] != UserHandle.USER_OWNER) {
//                // Is the user not allowed to use SMS?
//                if (userManager.hasUserRestriction(UserManager.DISALLOW_SMS, targetUser)) {
//                    continue;
//                }
//                // Skip unknown users and managed profiles as well
//                UserInfo info = userManager.getUserInfo(users[i]);
//                if (info == null || info.isManagedProfile()) {
//                    continue;
//                }
//            }
//            context.sendOrderedBroadcastAsUser(intent, targetUser,
//                    android.Manifest.permission.RECEIVE_MMS,
//                    18,
//                    null,
//                    null, Activity.RESULT_OK, null, null);
//        }
    }

    /**
     * Transfer the received response to the caller (for download requests write to content uri)
     *
     * @param fillIn   the intent that will be returned to the caller
     * @param response the pdu to transfer
     */
    @Override
    protected boolean transferResponse(Intent fillIn, final byte[] response) {
        return mRequestManager.writePduToContentUri(mContentUri, response);
    }

    @Override
    protected boolean prepareForHttpRequest() {
        return true;
    }

    /**
     * Try downloading via the carrier app.
     *
     * @param context                        The context
     * @param carrierMessagingServicePackage The carrier messaging service handling the download
     */
    public void tryDownloadingByCarrierApp(Context context, String carrierMessagingServicePackage) {
//        final CarrierDownloadManager carrierDownloadManger = new CarrierDownloadManager();
//        final CarrierDownloadCompleteCallback downloadCallback =
//                new CarrierDownloadCompleteCallback(context, carrierDownloadManger);
//        carrierDownloadManger.downloadMms(context, carrierMessagingServicePackage,
//                downloadCallback);
    }

    @Override
    protected void revokeUriPermission(Context context) {
        context.revokeUriPermission(mContentUri, Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
    }

    private String getContentLocation(Context context, Uri uri)
            throws MmsException {
        Cursor cursor = android.database.sqlite.SqliteWrapper.query(context, context.getContentResolver(),
                uri, PROJECTION, null, null, null);

        if (cursor != null) {
            try {
                if ((cursor.getCount() == 1) && cursor.moveToFirst()) {
                    String location = cursor.getString(COLUMN_CONTENT_LOCATION);
                    cursor.close();
                    return location;
                }
            } finally {
                cursor.close();
            }
        }

        throw new MmsException("Cannot get X-Mms-Content-Location from: " + uri);
    }

    private static Long getId(Context context, String location) {
        String selection = Telephony.Mms.CONTENT_LOCATION + " = ?";
        String[] selectionArgs = new String[]{location};
        Cursor c = android.database.sqlite.SqliteWrapper.query(
                context, context.getContentResolver(),
                Telephony.Mms.CONTENT_URI, new String[]{Telephony.Mms._ID},
                selection, selectionArgs, null);
        if (c != null) {
            try {
                if (c.moveToFirst()) {
                    return c.getLong(c.getColumnIndex(Telephony.Mms._ID));
                }
            } finally {
                c.close();
            }
        }
        return null;
    }

    private static void setErrorType(Context context, String locationUrl, int errorType) {
        Long msgId = getId(context, locationUrl);
        if (msgId == null) {
            return;
        }

        Uri.Builder uriBuilder = Telephony.MmsSms.PendingMessages.CONTENT_URI.buildUpon();
        uriBuilder.appendQueryParameter("protocol", "mms");
        uriBuilder.appendQueryParameter("message", String.valueOf(msgId));

        Cursor cursor = android.database.sqlite.SqliteWrapper.query(context, context.getContentResolver(),
                uriBuilder.build(), null, null, null, null);
        if (cursor == null) {
            return;
        }

        try {
            if ((cursor.getCount() == 1) && cursor.moveToFirst()) {
                ContentValues values = new ContentValues();
                values.put(Telephony.MmsSms.PendingMessages.ERROR_TYPE, errorType);

                int columnIndex = cursor.getColumnIndexOrThrow(
                        Telephony.MmsSms.PendingMessages._ID);
                long id = cursor.getLong(columnIndex);

                android.database.sqlite.SqliteWrapper.update(context, context.getContentResolver(),
                        Telephony.MmsSms.PendingMessages.CONTENT_URI,
                        values, Telephony.MmsSms.PendingMessages._ID + "=" + id, null);
            }
        } finally {
            cursor.close();
        }
    }
}
