package bj.planificateur;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;

public class Notifier {
    static final String CH = "planificateur";

    public static void show(Context c, String title, String text) {
        try {
            NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
            Notification.Builder b;
            if (Build.VERSION.SDK_INT >= 26) {
                nm.createNotificationChannel(new NotificationChannel(CH, "Planificateur",
                        NotificationManager.IMPORTANCE_DEFAULT));
                b = new Notification.Builder(c, CH);
            } else {
                b = new Notification.Builder(c);
            }
            b.setSmallIcon(android.R.drawable.ic_dialog_info)
                    .setContentTitle(title)
                    .setContentText(text)
                    .setStyle(new Notification.BigTextStyle().bigText(text))
                    .setAutoCancel(true);
            nm.notify((int) (System.currentTimeMillis() % 100000), b.build());
        } catch (Exception ignored) {
        }
    }
}
