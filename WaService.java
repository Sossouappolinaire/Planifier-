package bj.planificateur;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class WaService extends AccessibilityService {

    public static volatile WaService instance;

    static final String[] PKGS = {"com.whatsapp", "com.whatsapp.w4b"};
    static final String[] SEND = {"envoyer", "send", "enviar", "senden", "invia"};
    static final String[] SEARCH = {"rechercher", "search", "buscar", "suchen", "cerca"};
    static final String[] GROUP_HEAD = {"groupes", "groups", "grupos", "gruppen", "gruppi"};
    static final String[] OTHER_HEAD = {"contacts", "contactos", "kontakte", "contatti",
            "contacts sur whatsapp", "contacts on whatsapp", "autres contacts", "other contacts",
            "discussions récentes", "recent chats", "chats récents", "chats récents",
            "contacts fréquents", "frequent contacts", "frequently contacted",
            "discussions", "chats"};
    static final long TIMEOUT = 60000;

    final Handler h = new Handler(Looper.getMainLooper());
    final ArrayDeque<Job> queue = new ArrayDeque<>();
    PowerManager.WakeLock wl;

    // --- envoi ---
    Job cur;
    int step;            // 0 = choisir le groupe, 1 = appuyer sur Envoyer
    int typed;
    int clicks;
    long startedAt, stepAt, lastClick, lastTick;

    // --- recherche des groupes ---
    boolean scanning;
    final LinkedHashMap<String, Boolean> found = new LinkedHashMap<>();
    String section = "";
    boolean sectionFresh, groupHeaderSeen;
    int noNew, scrolls;
    long scanStart;

    final Runnable poll = new Runnable() {
        @Override
        public void run() {
            if (cur == null) return;
            long now = System.currentTimeMillis();
            if (now - startedAt > TIMEOUT) {
                finish(false, step == 0
                        ? "groupe « " + cur.group + " » introuvable (ou écran verrouillé)"
                        : "bouton Envoyer introuvable");
                return;
            }
            tick();
            h.postDelayed(this, 900);
        }
    };

    final Runnable scanPoll = new Runnable() {
        @Override
        public void run() {
            if (!scanning) return;
            scanTick();
        }
    };

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        instance = null;
        return super.onUnbind(intent);
    }

    @Override
    public void onDestroy() {
        instance = null;
        super.onDestroy();
    }

    @Override
    public void onInterrupt() {
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (cur != null) tick();
    }

    @SuppressWarnings("deprecation")
    void holdWake() {
        try {
            if (wl == null) {
                PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
                wl = pm.newWakeLock(PowerManager.FULL_WAKE_LOCK
                        | PowerManager.ACQUIRE_CAUSES_WAKEUP, "planificateur:service");
            }
            wl.acquire(120000);
        } catch (Exception ignored) {
        }
    }

    boolean launch(String text) {
        for (String pkg : PKGS) {
            try {
                getPackageManager().getPackageInfo(pkg, 0);
                Intent i = new Intent(Intent.ACTION_SEND);
                i.setType("text/plain");
                i.putExtra(Intent.EXTRA_TEXT, text);
                i.setPackage(pkg);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(i);
                return true;
            } catch (PackageManager.NameNotFoundException e) {
                // essaie le suivant
            } catch (Exception e) {
                return false;
            }
        }
        return false;
    }

    // =====================================================================
    //  RECHERCHE DES GROUPES
    // =====================================================================

    /** Ouvre le sélecteur de WhatsApp et lit tous les noms en faisant défiler la liste. */
    public boolean startScan() {
        if (cur != null || scanning) return false;
        scanning = true;
        found.clear();
        section = "";
        groupHeaderSeen = false;
        noNew = 0;
        scrolls = 0;
        scanStart = System.currentTimeMillis();
        holdWake();
        Store.setScanStatus(this, "Recherche en cours…");
        if (!launch(".")) {
            endScan(true, "WhatsApp n'est pas installé");
            return false;
        }
        h.removeCallbacks(scanPoll);
        h.postDelayed(scanPoll, 2500);
        return true;
    }

    void scanTick() {
        long now = System.currentTimeMillis();
        AccessibilityNodeInfo root = getRootInActiveWindow();
        boolean inWa = root != null && root.getPackageName() != null
                && isWa(root.getPackageName().toString());

        if (!inWa) {
            if (now - scanStart > 25000) endScan(true, "WhatsApp ne s'est pas ouvert (écran verrouillé ?)");
            else h.postDelayed(scanPoll, 1000);
            return;
        }

        int before = found.size();
        sectionFresh = false;
        walk(root);
        if (found.size() > before) noNew = 0; else noNew++;

        if (found.isEmpty() && now - scanStart < 15000) {
            h.postDelayed(scanPoll, 1000);
            return;
        }

        AccessibilityNodeInfo sc = findScrollable(root);
        boolean moved = sc != null && sc.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD);
        scrolls++;
        if (!moved || noNew >= 3 || scrolls > 80) {
            endScan(false, null);
        } else {
            h.postDelayed(scanPoll, 900);
        }
    }

    void walk(AccessibilityNodeInfo n) {
        if (n == null) return;
        String id = n.getViewIdResourceName();
        CharSequence tx = n.getText();
        if (tx != null && tx.length() > 0) {
            if (id != null && id.endsWith("contactpicker_row_name")) {
                String name = tx.toString().trim();
                if (!name.isEmpty()) {
                    boolean g = section.equals("g");
                    Boolean old = found.get(name);
                    if (old == null) found.put(name, g);
                    else if (!old && g && sectionFresh) found.put(name, true);
                }
            } else if (n.getChildCount() == 0 && !isEdit(n)) {
                if (inList(tx, GROUP_HEAD)) {
                    section = "g";
                    sectionFresh = true;
                    groupHeaderSeen = true;
                } else if (inList(tx, OTHER_HEAD)) {
                    section = "o";
                    sectionFresh = true;
                }
            }
        }
        for (int i = 0; i < n.getChildCount(); i++) walk(n.getChild(i));
    }

    AccessibilityNodeInfo findScrollable(AccessibilityNodeInfo n) {
        if (n == null) return null;
        if (n.isScrollable()) return n;
        for (int i = 0; i < n.getChildCount(); i++) {
            AccessibilityNodeInfo r = findScrollable(n.getChild(i));
            if (r != null) return r;
        }
        return null;
    }

    void endScan(boolean failed, String reason) {
        scanning = false;
        h.removeCallbacks(scanPoll);
        String hour = new SimpleDateFormat("dd/MM HH:mm", Locale.FRANCE).format(new Date());
        String status;
        List<String> groups = new ArrayList<>();
        if (failed || found.isEmpty()) {
            status = "✘ " + hour + " — " + (reason != null ? reason
                    : "impossible de lire la liste (tu peux ajouter les groupes à la main)");
        } else {
            for (Map.Entry<String, Boolean> e : found.entrySet()) {
                if (e.getValue()) groups.add(e.getKey());
            }
            if (groups.isEmpty()) {
                // Rubrique « Groupes » non reconnue : on garde tous les noms lus
                groups.addAll(found.keySet());
                status = "⚠ " + hour + " — " + groups.size()
                        + " noms trouvés (groupes et contacts mélangés)";
            } else {
                status = "✔ " + hour + " — " + groups.size() + " groupes trouvés";
            }
            Collections.sort(groups, String.CASE_INSENSITIVE_ORDER);
            Store.saveScanned(this, groups);
        }
        Store.setScanStatus(this, status);
        Notifier.show(this, "Recherche des groupes", status);

        // Retour dans l'appli
        Intent i = new Intent(this, MainActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP
                | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        try {
            startActivity(i);
        } catch (Exception ignored) {
        }
        h.postDelayed(this::next, 2500);
    }

    // =====================================================================
    //  ENVOI DES MESSAGES
    // =====================================================================

    public void enqueue(final Job j) {
        h.post(() -> {
            queue.add(j);
            if (cur == null && !scanning) next();
        });
    }

    void next() {
        if (scanning) return;
        cur = queue.poll();
        if (cur == null) {
            if (wl != null && wl.isHeld()) wl.release();
            return;
        }
        holdWake();
        step = 0;
        typed = 0;
        clicks = 0;
        startedAt = System.currentTimeMillis();
        stepAt = startedAt;
        if (!launch(cur.message)) {
            finish(false, "WhatsApp n'est pas installé");
            return;
        }
        h.removeCallbacks(poll);
        h.postDelayed(poll, 1500);
    }

    void finish(boolean ok, String msg) {
        Job j = cur;
        cur = null;
        h.removeCallbacks(poll);
        if (j != null) {
            String hour = new SimpleDateFormat("dd/MM HH:mm", Locale.FRANCE).format(new Date());
            String line = (ok ? "✔ " : "✘ ") + hour + " — " + j.group + " : " + msg;
            Store.setLast(this, j.id, line);
            Notifier.show(this, ok ? "Message envoyé" : "Échec d'envoi", j.group + " : " + msg);
        }
        h.postDelayed(() -> performGlobalAction(GLOBAL_ACTION_HOME), 1200);
        h.postDelayed(this::next, 3000);
    }

    void tick() {
        if (cur == null) return;
        long now = System.currentTimeMillis();
        if (now - lastTick < 400) return;
        lastTick = now;

        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null || root.getPackageName() == null) return;
        String pkg = root.getPackageName().toString();
        if (!isWa(pkg)) return;

        if (step == 0) {
            AccessibilityNodeInfo hit = findText(root, cur.group, true);
            if (hit == null && typed >= 4) hit = findText(root, cur.group, false);
            if (hit != null) {
                if (click(hit)) {
                    step = 1;
                    stepAt = now;
                    clicks = 0;
                }
                return;
            }
            AccessibilityNodeInfo ed = findEdit(root);
            if (ed != null) {
                String t = ed.getText() == null ? "" : ed.getText().toString();
                if (!t.equals(cur.group)) {
                    Bundle b = new Bundle();
                    b.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, cur.group);
                    ed.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, b);
                    typed = 0;
                } else {
                    typed++;
                }
                return;
            }
            AccessibilityNodeInfo s = findSearch(root, pkg);
            if (s != null) click(s);
        } else {
            if (now - stepAt < 900) return;
            AccessibilityNodeInfo sd = findSend(root, pkg);
            if (sd != null) {
                if (clicks < 2 && now - lastClick > 1800) {
                    if (click(sd)) {
                        clicks++;
                        lastClick = now;
                    }
                }
            } else if (clicks > 0 && now - lastClick > 2500) {
                finish(true, "message envoyé");
            }
        }
    }

    // =====================================================================
    //  Outils
    // =====================================================================

    static boolean isWa(String pkg) {
        for (String p : PKGS) if (p.equals(pkg)) return true;
        return false;
    }

    static boolean inList(CharSequence cs, String[] words) {
        if (cs == null) return false;
        String s = cs.toString().trim().toLowerCase(Locale.ROOT);
        for (String w : words) if (s.equals(w)) return true;
        return false;
    }

    static boolean isEdit(AccessibilityNodeInfo n) {
        CharSequence c = n.getClassName();
        return c != null && c.toString().equals("android.widget.EditText");
    }

    AccessibilityNodeInfo findText(AccessibilityNodeInfo n, String t, boolean exact) {
        if (n == null) return null;
        CharSequence cs = n.getText();
        if (cs != null && !isEdit(n)) {
            String s = cs.toString().trim();
            boolean ok = exact ? s.equalsIgnoreCase(t.trim())
                    : s.toLowerCase(Locale.ROOT).contains(t.trim().toLowerCase(Locale.ROOT));
            if (ok) return n;
        }
        for (int i = 0; i < n.getChildCount(); i++) {
            AccessibilityNodeInfo r = findText(n.getChild(i), t, exact);
            if (r != null) return r;
        }
        return null;
    }

    AccessibilityNodeInfo findEdit(AccessibilityNodeInfo n) {
        if (n == null) return null;
        if (isEdit(n)) return n;
        for (int i = 0; i < n.getChildCount(); i++) {
            AccessibilityNodeInfo r = findEdit(n.getChild(i));
            if (r != null) return r;
        }
        return null;
    }

    AccessibilityNodeInfo findSearch(AccessibilityNodeInfo root, String pkg) {
        List<AccessibilityNodeInfo> l = root.findAccessibilityNodeInfosByViewId(pkg + ":id/menuitem_search");
        if (l != null && !l.isEmpty()) return l.get(0);
        return scanDesc(root, SEARCH);
    }

    AccessibilityNodeInfo scanDesc(AccessibilityNodeInfo n, String[] words) {
        if (n == null) return null;
        if (inList(n.getContentDescription(), words)) return n;
        for (int i = 0; i < n.getChildCount(); i++) {
            AccessibilityNodeInfo r = scanDesc(n.getChild(i), words);
            if (r != null) return r;
        }
        return null;
    }

    AccessibilityNodeInfo findSend(AccessibilityNodeInfo root, String pkg) {
        List<AccessibilityNodeInfo> l = root.findAccessibilityNodeInfosByViewId(pkg + ":id/send");
        if (l != null) {
            for (AccessibilityNodeInfo n : l) if (n.isVisibleToUser()) return n;
        }
        return scanSend(root);
    }

    AccessibilityNodeInfo scanSend(AccessibilityNodeInfo n) {
        if (n == null) return null;
        if (inList(n.getContentDescription(), SEND)) return n;
        CharSequence cls = n.getClassName();
        if (inList(n.getText(), SEND) && cls != null && cls.toString().contains("Button")) return n;
        for (int i = 0; i < n.getChildCount(); i++) {
            AccessibilityNodeInfo r = scanSend(n.getChild(i));
            if (r != null) return r;
        }
        return null;
    }

    boolean click(AccessibilityNodeInfo n) {
        AccessibilityNodeInfo c = n;
        for (int i = 0; i < 6 && c != null; i++) {
            if (c.isClickable() && c.isEnabled()) {
                return c.performAction(AccessibilityNodeInfo.ACTION_CLICK);
            }
            c = c.getParent();
        }
        return false;
    }
}
