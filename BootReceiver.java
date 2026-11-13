package bj.planificateur;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context ctx, Intent intent) {
        long now = System.currentTimeMillis();
        for (Store.Plan p : Store.load(ctx)) {
            if (p.done) continue;
            int before = p.idx;
            while (p.idx < p.times.size() && p.times.get(p.idx) <= now) p.idx++;
            if (p.idx >= p.times.size()) {
                p.done = true;
                p.last = "✘ Manqué (téléphone éteint à l'heure prévue)";
                Store.put(ctx, p);
            } else {
                if (p.idx != before) {
                    p.last = "⚠ " + (p.idx - before) + " envoi(s) manqué(s) pendant l'extinction";
                    Store.put(ctx, p);
                }
                Scheduler.schedule(ctx, p);
            }
        }
    }
}
