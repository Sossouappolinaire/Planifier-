package bj.planificateur;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.PowerManager;

import java.util.ArrayList;
import java.util.List;

public class AlarmReceiver extends BroadcastReceiver {

    @Override
    @SuppressWarnings("deprecation")
    public void onReceive(Context ctx, Intent intent) {
        long id = intent.getLongExtra("id", 0);
        Store.Plan p = Store.find(ctx, id);
        if (p == null || p.done) return;

        // Réveille l'écran
        PowerManager pm = (PowerManager) ctx.getSystemService(Context.POWER_SERVICE);
        PowerManager.WakeLock wl = pm.newWakeLock(
                PowerManager.FULL_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP
                        | PowerManager.ON_AFTER_RELEASE, "planificateur:reveil");
        wl.acquire(120000);

        List<String> groups = new ArrayList<>(p.groups);
        String message = p.message;

        // Passe à l'envoi suivant (en sautant ceux déjà passés)
        long now = System.currentTimeMillis();
        p.idx++;
        while (p.idx < p.times.size() && p.times.get(p.idx) <= now) p.idx++;
        if (p.idx >= p.times.size()) {
            p.done = true;
        } else {
            Scheduler.schedule(ctx, p);
        }
        p.last = "Envoi en cours…";
        Store.put(ctx, p);

        WaService s = WaService.instance;
        if (s == null) {
            Store.setLast(ctx, id, "✘ Service d'accessibilité inactif");
            Notifier.show(ctx, "Échec d'envoi", "Active le service d'accessibilité de l'appli.");
            return;
        }
        for (String g : groups) {
            s.enqueue(new Job(p.id, g, message));
        }
    }
}
