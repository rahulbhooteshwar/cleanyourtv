package com.gathin.tvhome;

import android.app.Notification;
import android.content.Context;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

/**
 * Counts the notifications not looked at yet, for the dot on Home's notifications icon. Only whether a notification
 * is there is used: its text is never read, stored or sent. "Not looked at" means posted by another app, dismissable
 * (not an ongoing one such as media playback) and newer than the last time the icon opened the panel; Android TV
 * keeps no read state of its own. Enable with
 * {@code cmd notification allow_listener com.gathin.tvhome/.NotificationWatcher}.
 */
public class NotificationWatcher extends NotificationListenerService {

    interface Listener {
        void onUnreadChanged(boolean unread);
    }

    private static final String PREFS = "notifications";
    private static final String SEEN_AT = "seen_at";

    private static NotificationWatcher connected;
    private static Listener listener;
    private static boolean unread;

    /** Home's icon: told now and on every change while it is registered; null unregisters. */
    static void setListener(Listener l) {
        listener = l;
        if (l != null) l.onUnreadChanged(unread);
    }

    /** Whether the listener is approved and bound; without it Home shows the icon without a dot. */
    static boolean active() {
        return connected != null;
    }

    /** The icon opened the panel: everything there now counts as seen. */
    static void markSeen(Context context) {
        context.getSharedPreferences(PREFS, MODE_PRIVATE).edit().putLong(SEEN_AT, System.currentTimeMillis()).apply();
        publish(false);
    }

    @Override
    public void onListenerConnected() {
        connected = this;
        recount();
    }

    @Override
    public void onListenerDisconnected() {
        if (connected == this) connected = null;
        publish(false);
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        recount();
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        recount();
    }

    private void recount() {
        long seenAt = getSharedPreferences(PREFS, MODE_PRIVATE).getLong(SEEN_AT, 0);
        boolean any = false;
        try {
            StatusBarNotification[] active = getActiveNotifications();
            if (active != null) {
                for (StatusBarNotification n : active) {
                    if (n.getPackageName().equals(getPackageName()) || n.isOngoing() || !n.isClearable()) continue;
                    if ((n.getNotification().flags & Notification.FLAG_GROUP_SUMMARY) != 0) continue;
                    if (n.getPostTime() > seenAt) {
                        any = true;
                        break;
                    }
                }
            }
        } catch (RuntimeException notBound) {   // SecurityException included: not approved or not bound
            any = false;
        }
        publish(any);
    }

    private static void publish(boolean now) {
        unread = now;
        Listener l = listener;
        if (l != null) l.onUnreadChanged(now);
    }
}
