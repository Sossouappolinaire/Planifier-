package bj.planificateur;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;

public class Scheduler {

    static PendingIntent firePi(Context c, long id) {
        Intent i = new Intent(c, AlarmReceiver.class);
        i.setAction("bj.planificateur.FIRE");
        i.setData(Uri.parse("plan://" + id));
        i.putExtra("id", id);
        return PendingIntent.getBroadcast(c, (int) (id % 1000000007L), i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    public static boolean canExact(Context c) {
        if (Build.VERSION.SDK_INT < 31) return true;
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        return am.canScheduleExactAlarms();
    }

    /** Programme l'alarme du prochain envoi de la planification. */
    public static boolean schedule(Context c, Store.Plan p) {
        if (p.done || p.idx >= p.times.size()) return false;
        if (!canExact(c)) return false;
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        Intent show = new Intent(c, MainActivity.class);
        PendingIntent sp = PendingIntent.getActivity(c, 0, show, PendingIntent.FLAG_IMMUTABLE);
        am.setAlarmClock(new AlarmManager.AlarmClockInfo(p.times.get(p.idx), sp), firePi(c, p.id));
        return true;
    }

    public static void cancel(Context c, long id) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        am.cancel(firePi(c, id));
    }
}
