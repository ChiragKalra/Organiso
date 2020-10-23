package com.bruhascended.core.constants

import android.Manifest
import com.bruhascended.core.BuildConfig.LIBRARY_PACKAGE_NAME

// message types
const val MESSAGE_TYPE_ALL = 0
const val MESSAGE_TYPE_INBOX = 1
const val MESSAGE_TYPE_SENT = 2
const val MESSAGE_TYPE_DRAFT = 3
const val MESSAGE_TYPE_OUTBOX = 4
const val MESSAGE_TYPE_FAILED = 5 // for failed outgoing messages
const val MESSAGE_TYPE_QUEUED = 6 // for messages to send later

// saved message types
const val SAVED_TYPE_DRAFT = 0
const val SAVED_TYPE_SENT = 1
const val SAVED_TYPE_RECEIVED = 2

// labels
const val LABEL_NONE = -1
const val LABEL_PERSONAL = 0
const val LABEL_IMPORTANT = 1
const val LABEL_TRANSACTIONS = 2
const val LABEL_PROMOTIONS = 3
const val LABEL_SPAM = 4
const val LABEL_BLOCKED = 5

// intent actions
const val ACTION_NEW_MESSAGE = "$LIBRARY_PACKAGE_NAME.NEW_MESSAGE"
const val ACTION_UPDATE_DP = "${LIBRARY_PACKAGE_NAME}.UPDATE_DP"
const val ACTION_OVERWRITE_MESSAGE = "$LIBRARY_PACKAGE_NAME.OVERWRITE_MESSAGE"
const val ACTION_UPDATE_STATUS_MESSAGE = "$LIBRARY_PACKAGE_NAME.UPDATE_MESSAGE"
const val ACTION_CANCEL = "$LIBRARY_PACKAGE_NAME.CANCEL_NOTIFICATION"
const val ACTION_REPLY = "$LIBRARY_PACKAGE_NAME.NOTIFICATION_REPLY"
const val ACTION_COPY = "$LIBRARY_PACKAGE_NAME.OTP_COPY"
const val ACTION_DELETE_OTP = "$LIBRARY_PACKAGE_NAME.OTP_DELETE"
const val ACTION_REPORT_SPAM = "$LIBRARY_PACKAGE_NAME.REPORT_SPAM"
const val ACTION_DELETE_MESSAGE = "$LIBRARY_PACKAGE_NAME.MESSAGE_DELETE"
const val ACTION_MARK_READ = "$LIBRARY_PACKAGE_NAME.MESSAGE_MARK_READ"


// intent extras
const val EXTRA_TAG = "TAG"
const val EXTRA_SAVED_MESSAGE = "SAVED_MESSAGE"
const val EXTRA_MESSAGE_TEXT = "MESSAGE_TEXT"
const val EXTRA_ADDRESS = "ADDRESS"
const val EXTRA_CONVERSATION = "CONVERSATION"
const val EXTRA_CONVERSATION_JSON = "CONVERSATION_JSON"
const val EXTRA_MESSAGE_ID = "MESSAGE_ID"
const val EXTRA_LABEL = "LABEL"
const val EXTRA_MESSAGES = "MESSAGES"
const val EXTRA_MESSAGE = "MESSAGE"
const val EXTRA_MESSAGE_DATE = "MESSAGE_DATE"
const val EXTRA_MESSAGE_TYPE = "MESSAGE_TYPE"
const val EXTRA_TEXT_REPLY = "TEXT_REPLY"
const val EXTRA_OTP = "OTP"
const val EXTRA_NOTIFICATION_ID = "ID_NOTIFICATION"
const val EXTRA_SENDER = "SENDER"
const val EXTRA_FILE_PATH = "FILE_PATH"
const val EXTRA_TIME = "TIME"
const val EXTRA_IS_OTP = "IS_OTP"

// intent data
const val TYPE_MULTI = "multipart/*"

// sp keys
const val KEY_INIT = "InitDataOrganized"
const val KEY_RESUME_INDEX = "last_index"
const val KEY_DONE_COUNT = "done_count"
const val KEY_TIME_TAKEN = "time_taken"
const val KEY_RESUME_DATE = "last_date"
const val KEY_STATE_CHANGED = "state_changed"
const val KEY_LAST_REFRESH = "LAST_REFRESH"

// prefs
const val PREF_VISIBLE_CATEGORIES = "visible_categories"
const val PREF_HIDDEN_CATEGORIES = "hidden_categories"
const val PREF_SEARCH_HIDDEN = "show_hidden_results"
const val PREF_ACTION_NAVIGATE = "action_navigate"
const val PREF_ACTION_CUSTOM = "action_custom"
const val PREF_CUSTOM_LEFT = "action_left_swipe"
const val PREF_CUSTOM_RIGHT = "action_right_swipe"
const val PREF_CUSTOM_STRENGTH = "swipe_strength"
const val PREF_DELETE_OTP = "delete_otp"
const val PREF_COPY_OTP = "copy_otp"
const val PREF_SEND_SPAM = "report_spam"
const val PREF_DARK_THEME = "dark_theme"
const val PREF_ENTER_SEND = "enter_send"

const val ACTION_MOVE = "Move"
const val ACTION_REPORT = "Report Spam"
const val ACTION_BLOCK = "Block"
const val ACTION_DELETE = "Delete"

val ARR_PREF_CUSTOM_LABELS = Array(6) {
    "custom_label_${it}"
}





// permissions
val ARR_PERMS = arrayOf (
    Manifest.permission.READ_SMS,
    Manifest.permission.SEND_SMS,
    Manifest.permission.RECEIVE_SMS,
    Manifest.permission.READ_CONTACTS
)

// firebase db
const val PATH_SPAM_REPORTS = "spam_reports"
const val PATH_BUG_REPORTS = "bug_reports"
const val PATH_TITLE = "title"
const val PATH_DETAIL = "detail"

// firebase analytics
const val EVENT_BUG_REPORTED = "bug_reported"
const val EVENT_CONVERSATION_ORGANISED = "conversation_organised"
const val EVENT_MESSAGE_ORGANISED = "message_organised"


const val PARAM_DEFAULT = "default"
const val PARAM_BACKGROUND = "background"
const val PARAM_INIT = "init"

// search
const val TYPE_HEADER = 4
const val TYPE_CONVERSATION = 0
const val TYPE_CONTACT = 1
const val TYPE_MESSAGE_SENT = 2
const val TYPE_MESSAGE_RECEIVED = 3
const val TYPE_FOOTER = 5

const val HEADER_CONTACTS = 42

// category settings
const val CATEGORY_VISIBLE = 100
const val CATEGORY_HIDDEN = 101