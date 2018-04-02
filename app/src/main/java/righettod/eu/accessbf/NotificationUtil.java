package righettod.eu.accessbf;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;



/**
 * Handle sending notification to notification bar
 */

class NotificationUtil {

    /**
     * Send message in notification bar.
     * Use a fixed ID to reused the same notification slot.
     *
     * @param context Context
     * @param content Notification content
     */
    static void sendNotification(Context context, String content) {
        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);
        NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(context.getApplicationContext()).setContentText(content).setSmallIcon(R.drawable.ic_stat_info_outline);
        notificationManager.notify(1, mBuilder.build());
    }

    /**
     * Show an message dialog with a single OK button
     *
     * @param activity Parent UII
     * @param content  Message content
     * @param title    Message dialog
     */
    static void showMessageDialog(Activity activity, String content, String title) {
        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setMessage(content).setTitle(title).setPositiveButton("OK", (DialogInterface dialog, int id) -> {
                }
        );
        AlertDialog dialog = builder.create();
        dialog.show();
    }
}
