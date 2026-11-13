package bj.planificateur;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Store {
    static final String PREFS = "planifications";
    static final String KEY = "liste";
    static final String K_SCANNED = "groupes_trouves";
    static final String K_MANUAL = "groupes_manuels";
    static final String K_STATUS = "scan_status";
    static final String K_PKG = "wa_pkg";

    /** WhatsApp installés sur le téléphone (normal et/ou Business). */
    public static List<String> installed(Context c) {
        List<String> l = new ArrayList<>();
        for (String p : new String[]{"com.whatsapp", "com.whatsapp.w4b"}) {
            try {
                c.getPackageManager().getPackageInfo(p, 0);
                l.add(p);
            } catch (Exception ignored) {
            }
        }
        return l;
    }

    /** WhatsApp à utiliser : celui choisi s'il est installé, sinon le premier trouvé. */
    public static String waPkg(Context c) {
        List<String> in = installed(c);
        String pref = sp(c).getString(K_PKG, "");
        if (in.contains(pref)) return pref;
        return in.isEmpty() ? "com.whatsapp" : in.get(0);
    }

    public static void setWaPkg(Context c, String p) {
        sp(c).edit().putString(K_PKG, p).apply();
    }

    /** Les groupes diffèrent d'un WhatsApp à l'autre : une liste par application. */
    static String sfx(Context c) {
        String p = waPkg(c);
        return p.equals("com.whatsapp") ? "" : "_" + p;
    }

    public static class Plan {
        public long id;
        public List<String> groups = new ArrayList<>();
        public String message;
        public List<Long> times = new ArrayList<>();
        public int idx;          // index du prochain envoi
        public boolean done;
        public String last = "";
    }

    static SharedPreferences sp(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    // ---------- Planifications ----------

    public static synchronized List<Plan> load(Context c) {
        List<Plan> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(sp(c).getString(KEY, "[]"));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                Plan p = new Plan();
                p.id = o.getLong("id");
                p.message = o.getString("message");
                p.idx = o.optInt("idx");
                p.done = o.optBoolean("done");
                p.last = o.optString("last", "");
                JSONArray g = o.getJSONArray("groups");
                for (int k = 0; k < g.length(); k++) p.groups.add(g.getString(k));
                JSONArray t = o.getJSONArray("times");
                for (int k = 0; k < t.length(); k++) p.times.add(t.getLong(k));
                out.add(p);
            }
        } catch (Exception ignored) {
        }
        Collections.sort(out, (x, y) -> Long.compare(x.id, y.id));
        return out;
    }

    static void saveAll(Context c, List<Plan> list) {
        JSONArray a = new JSONArray();
        for (Plan p : list) {
            try {
                JSONObject o = new JSONObject();
                o.put("id", p.id);
                o.put("message", p.message);
                o.put("idx", p.idx);
                o.put("done", p.done);
                o.put("last", p.last == null ? "" : p.last);
                JSONArray g = new JSONArray();
                for (String s : p.groups) g.put(s);
                o.put("groups", g);
                JSONArray t = new JSONArray();
                for (Long l : p.times) t.put(l.longValue());
                o.put("times", t);
                a.put(o);
            } catch (Exception ignored) {
            }
        }
        sp(c).edit().putString(KEY, a.toString()).apply();
    }

    public static synchronized void add(Context c, Plan p) {
        List<Plan> l = load(c);
        l.add(p);
        saveAll(c, l);
    }

    public static synchronized Plan find(Context c, long id) {
        for (Plan p : load(c)) {
            if (p.id == id) return p;
        }
        return null;
    }

    public static synchronized void put(Context c, Plan plan) {
        List<Plan> l = load(c);
        for (int i = 0; i < l.size(); i++) {
            if (l.get(i).id == plan.id) {
                l.set(i, plan);
                break;
            }
        }
        saveAll(c, l);
    }

    public static synchronized void remove(Context c, long id) {
        List<Plan> l = load(c);
        for (int i = 0; i < l.size(); i++) {
            if (l.get(i).id == id) {
                l.remove(i);
                break;
            }
        }
        saveAll(c, l);
    }

    public static synchronized void setLast(Context c, long id, String last) {
        Plan p = find(c, id);
        if (p != null) {
            p.last = last;
            put(c, p);
        }
    }

    // ---------- Groupes ----------

    static List<String> readList(Context c, String key) {
        List<String> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(sp(c).getString(key, "[]"));
            for (int i = 0; i < a.length(); i++) out.add(a.getString(i));
        } catch (Exception ignored) {
        }
        return out;
    }

    static void writeList(Context c, String key, List<String> l) {
        JSONArray a = new JSONArray();
        for (String s : l) a.put(s);
        sp(c).edit().putString(key, a.toString()).apply();
    }

    /** Groupes trouvés dans WhatsApp + groupes ajoutés à la main, triés, sans doublon. */
    public static synchronized List<String> loadGroups(Context c) {
        List<String> all = new ArrayList<>();
        for (String s : readList(c, K_SCANNED + sfx(c))) addUnique(all, s);
        for (String s : readList(c, K_MANUAL + sfx(c))) addUnique(all, s);
        Collections.sort(all, String.CASE_INSENSITIVE_ORDER);
        return all;
    }

    static void addUnique(List<String> l, String s) {
        for (String x : l) if (x.equalsIgnoreCase(s)) return;
        l.add(s);
    }

    public static synchronized void saveScanned(Context c, List<String> l) {
        writeList(c, K_SCANNED + sfx(c), l);
    }

    public static synchronized void addManual(Context c, String name) {
        List<String> l = readList(c, K_MANUAL + sfx(c));
        addUnique(l, name);
        writeList(c, K_MANUAL + sfx(c), l);
    }

    public static String scanStatus(Context c) {
        return sp(c).getString(K_STATUS, "Aucune recherche faite pour l'instant.");
    }

    public static void setScanStatus(Context c, String s) {
        sp(c).edit().putString(K_STATUS, s).apply();
    }
}
