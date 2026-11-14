package bj.planificateur;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.app.KeyguardManager;
import android.graphics.Path;
import android.os.Build;
import android.util.DisplayMetrics;
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
    static final String[] VIEW_GROUP = {"voir le groupe", "view group", "ouvrir le groupe", "open group"};
    static final String[] JOIN_GROUP = {"rejoindre le groupe", "join group", "join chat",
            "rejoindre la discussion", "unirse al grupo"};
    static final long TIMEOUT = 60000;

    final Handler h = new Handler(Looper.getMainLooper());
    final ArrayDeque<Job> queue = new ArrayDeque<>();
    PowerManager.WakeLock wl;

    // --- envoi ---
    Job cur;
    int step;            // 0 = choisir le groupe, 1 = appuyer sur Envoyer
    int typed;
    int clicks;
    long startedAt, stepAt, lastClick, lastTick, lastType;

    // --- recherche des groupes ---
    boolean scanning;
    final LinkedHashMap<String, Boolean> found = new LinkedHashMap<>();
    String section = "";
    boolean sectionFresh, groupHeaderSeen;
    int noNew, scrolls;
    long scanStart;
    int budget; // nombre maximum de nœuds lus par analyse
    static final int MAX_NODES = 700;

    final Runnable poll = new Runnable() {
        @Override
        public void run() {
            if (cur == null) return;
            long now = System.currentTimeMillis();
            if (now - startedAt > TIMEOUT) {
                finish(false, step == 0
                        ? "groupe « " + cur.group + " » introuvable (ou écran verrouillé)"
                        : step == 2
                        ? "discussion du groupe introuvable (lien invalide, tu n'es plus membre, ou écran verrouillé)"
                        : "bouton Envoyer introuvable");
                return;
            }
            tick();
            h.postDelayed(this, 700);
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
        // Volontairement vide : le minuteur (poll / scanPoll) pilote tout.
        // Analyser l'écran de WhatsApp à chaque événement figeait le téléphone.
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

    /** Le WhatsApp choisi par l'utilisateur passe en premier. */
    String[] order() {
        return Store.waPkg(this).equals(PKGS[0]) ? PKGS : new String[]{PKGS[1], PKGS[0]};
    }

    boolean launch(String text) {
        for (String pkg : order()) {
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

    /** Ouvre un lien WhatsApp (wa.me) directement dans l'appli installée. */
    boolean launchUri(String uri) {
        for (String pkg : order()) {
            try {
                getPackageManager().getPackageInfo(pkg, 0);
                Intent i = new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(uri));
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
        budget = MAX_NODES;
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
        if (n == null || budget-- <= 0) return;
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
        if (n == null || budget-- <= 0) return null;
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
            groups.addAll(found.keySet());
            status = "✔ " + hour + " — " + groups.size()
                    + " noms trouvés (groupes et contacts) : coche uniquement les groupes";
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

    // =====================================================================
    //  DÉVERROUILLAGE AUTOMATIQUE (code PIN enregistré dans l'appli)
    // =====================================================================

    static final String[] ENTER = {"enter", "entrée", "entrer", "ok", "valider", "done", "confirm", "confirmer"};

    boolean unlocking, unlockedByUs, enterClicked;
    String pin;
    int pinIdx;
    long unlockStart, lastSwipe, pinDoneAt, noUnlockUntil;

    final Runnable unlockPoll = new Runnable() {
        @Override
        public void run() {
            if (unlocking) unlockTick();
        }
    };

    boolean isLocked() {
        KeyguardManager km = (KeyguardManager) getSystemService(KEYGUARD_SERVICE);
        return km != null && km.isKeyguardLocked();
    }

    void startUnlock() {
        pin = Vault.getPin(this);
        if (pin == null || pin.isEmpty()) {
            Vault.setEnabled(this, false);
            h.postDelayed(this::next, 100);
            return;
        }
        unlocking = true;
        enterClicked = false;
        pinIdx = 0;
        pinDoneAt = 0;
        lastSwipe = 0;
        unlockStart = System.currentTimeMillis();
        holdWake();
        h.removeCallbacks(unlockPoll);
        h.postDelayed(unlockPoll, 600);
    }

    /** Fait échouer (avec réessai automatique) le premier envoi en attente. */
    void failFirst(String msg) {
        cur = queue.poll();
        if (cur == null) {
            relockIfNeeded();
            return;
        }
        finish(false, msg);
    }

    void unlockTick() {
        long now = System.currentTimeMillis();
        if (!isLocked()) {
            unlocking = false;
            unlockedByUs = true;
            pin = null;
            h.postDelayed(this::next, 1500);
            return;
        }
        if (pinDoneAt > 0 && now - pinDoneAt > 7000) {
            // Code saisi en entier mais le téléphone reste verrouillé : on s'arrête pour ne pas bloquer le téléphone
            unlocking = false;
            pin = null;
            noUnlockUntil = now + 600000;
            Vault.setEnabled(this, false);
            Notifier.show(this, "Déverrouillage automatique désactivé",
                    "Le code enregistré semble refusé. Vérifie-le dans Réglages avant de réactiver.");
            failFirst("déverrouillage refusé (code incorrect ?)");
            return;
        }
        if (now - unlockStart > 30000) {
            unlocking = false;
            pin = null;
            noUnlockUntil = now + 300000;
            failFirst("déverrouillage automatique impossible (clavier du code introuvable)");
            return;
        }
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root != null) {
            budget = MAX_NODES * 2;
            if (pinIdx < pin.length()) {
                AccessibilityNodeInfo key = findKey(root, pin.charAt(pinIdx));
                if (key != null) {
                    if (click(key)) {
                        pinIdx++;
                        if (pinIdx >= pin.length()) pinDoneAt = now;
                    }
                    h.postDelayed(unlockPoll, 350);
                    return;
                }
            } else {
                if (!enterClicked) {
                    AccessibilityNodeInfo en = findEnter(root);
                    if (en != null && click(en)) enterClicked = true;
                }
                h.postDelayed(unlockPoll, 800);
                return;
            }
        }
        // Clavier du code pas encore visible : on glisse vers le haut pour le faire apparaître
        if (pinIdx == 0 && now - lastSwipe > 2500) {
            lastSwipe = now;
            swipeUp();
        }
        h.postDelayed(unlockPoll, 700);
    }

    AccessibilityNodeInfo findKey(AccessibilityNodeInfo n, char d) {
        if (n == null || budget-- <= 0) return null;
        String want = String.valueOf(d);
        CharSequence tx = n.getText(), cd = n.getContentDescription();
        if (n.getChildCount() == 0
                && ((tx != null && want.equals(tx.toString().trim()))
                || (cd != null && want.equals(cd.toString().trim())))) return n;
        for (int i = 0; i < n.getChildCount(); i++) {
            AccessibilityNodeInfo r = findKey(n.getChild(i), d);
            if (r != null) return r;
        }
        return null;
    }

    AccessibilityNodeInfo findEnter(AccessibilityNodeInfo n) {
        if (n == null || budget-- <= 0) return null;
        String id = n.getViewIdResourceName();
        CharSequence cd = n.getContentDescription(), tx = n.getText();
        if (id != null && id.endsWith("key_enter")) return n;
        for (String w : ENTER) {
            if ((cd != null && w.equalsIgnoreCase(cd.toString().trim()))
                    || (tx != null && n.getChildCount() == 0 && w.equalsIgnoreCase(tx.toString().trim()))) return n;
        }
        for (int i = 0; i < n.getChildCount(); i++) {
            AccessibilityNodeInfo r = findEnter(n.getChild(i));
            if (r != null) return r;
        }
        return null;
    }

    void swipeUp() {
        try {
            DisplayMetrics dm = getResources().getDisplayMetrics();
            Path p = new Path();
            p.moveTo(dm.widthPixels / 2f, dm.heightPixels * 0.85f);
            p.lineTo(dm.widthPixels / 2f, dm.heightPixels * 0.25f);
            GestureDescription.Builder b = new GestureDescription.Builder();
            b.addStroke(new GestureDescription.StrokeDescription(p, 0, 300));
            dispatchGesture(b.build(), null, null);
        } catch (Exception ignored) {
        }
    }

    /** Si c'est l'appli qui a déverrouillé le téléphone, on le reverrouille quand tout est envoyé. */
    void relockIfNeeded() {
        if (!unlockedByUs) return;
        unlockedByUs = false;
        if (!Vault.relock(this) || Build.VERSION.SDK_INT < 28) return;
        h.postDelayed(() -> {
            if (queue.isEmpty() && cur == null && !unlocking) performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN);
        }, 2500);
    }

    public void enqueue(final Job j) {
        h.post(() -> {
            queue.add(j);
            if (cur == null && !scanning) next();
        });
    }

    void next() {
        if (scanning || cur != null || unlocking) return;
        if (queue.isEmpty()) {
            if (wl != null && wl.isHeld()) wl.release();
            relockIfNeeded();
            return;
        }
        if (isLocked()) {
            if (System.currentTimeMillis() < noUnlockUntil) {
                cur = queue.poll();
                finish(false, "écran verrouillé : déverrouillage automatique impossible");
                return;
            }
            if (Vault.enabled(this) && Vault.hasPin(this)) {
                startUnlock();
                return;
            }
        }
        cur = queue.poll();
        holdWake();
        step = 0;
        typed = 0;
        clicks = 0;
        startedAt = System.currentTimeMillis();
        stepAt = startedAt;
        boolean ok;
        typed = 0;
        lastType = 0;
        if (cur.uri != null) {
            ok = launchUri(cur.uri);
            step = 1; // la discussion est déjà ouverte : on appuie directement sur Envoyer
        } else if (cur.link != null) {
            ok = launchUri(cur.link);
            step = 2; // lien du groupe : la discussion s'ouvre, il reste à écrire puis envoyer
        } else {
            ok = launch(cur.message);
        }
        if (!ok) {
            finish(false, "WhatsApp n'est pas installé");
            return;
        }
        h.removeCallbacks(poll);
        h.postDelayed(poll, 1500);
    }

    static final int MAX_RETRY = 2;          // 2 réessais, soit 3 essais au total
    static final long RETRY_DELAY = 120000;  // 2 minutes entre deux essais

    void finish(boolean ok, String msg) {
        Job j = cur;
        cur = null;
        h.removeCallbacks(poll);
        if (j != null) {
            String hour = new SimpleDateFormat("dd/MM HH:mm", Locale.FRANCE).format(new Date());
            // Inutile de réessayer si WhatsApp est absent ou si le compte n'est pas dans le groupe
            boolean retry = !ok && j.attempt < MAX_RETRY
                    && !msg.contains("pas installé") && !msg.contains("pas membre");
            String suffix = retry ? " — nouvel essai dans 2 min (essai " + (j.attempt + 2) + " sur "
                    + (MAX_RETRY + 1) + ")" : "";
            String line = (ok ? "✔ " : "✘ ") + hour + " — " + j.group + " : " + msg + suffix;
            Store.setLast(this, j.id, line);
            Store.addHistory(this, j.group, ok, msg + suffix, j.attempt);
            Notifier.show(this, ok ? "Message envoyé" : "Échec d'envoi",
                    hour + " — " + j.group + " : " + msg + suffix);
            if (retry) {
                final Job r = new Job(j.id, j.group, j.message, j.uri, j.link, j.attempt + 1);
                h.postDelayed(() -> enqueue(r), RETRY_DELAY);
            }
        }
        h.postDelayed(() -> performGlobalAction(GLOBAL_ACTION_HOME), 1200);
        h.postDelayed(this::next, 3000);
    }

    void tick() {
        if (cur == null) return;
        long now = System.currentTimeMillis();
        if (now - lastTick < 400) return;
        lastTick = now;

        budget = MAX_NODES;
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null || root.getPackageName() == null) return;
        String pkg = root.getPackageName().toString();
        if (!isWa(pkg)) return;

        if (step == 2) {
            tickLink(root, pkg, now);
            return;
        }

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

    /** Discussion ouverte par le lien du groupe : écrit le message puis passe à l'étape « Envoyer ». */
    void tickLink(AccessibilityNodeInfo root, String pkg, long now) {
        AccessibilityNodeInfo ed = findEdit(root);
        if (ed == null) {
            // Pas de champ de saisie : écran d'invitation, ou WhatsApp pas encore prêt
            for (String w : VIEW_GROUP) {
                budget = MAX_NODES;
                AccessibilityNodeInfo v = findText(root, w, true);
                if (v != null) {
                    click(v);
                    return;
                }
            }
            for (String w : JOIN_GROUP) {
                budget = MAX_NODES;
                if (findText(root, w, true) != null) {
                    finish(false, "tu n'es pas membre de ce groupe (le lien propose de le rejoindre)");
                    return;
                }
            }
            return;
        }
        String want = norm(cur.message);
        String have = ed.getText() == null ? "" : norm(ed.getText().toString());
        if (have.equals(want)) {
            step = 1;
            stepAt = now;
            clicks = 0;
            return;
        }
        // Le bouton Envoyer n'apparaît que lorsque le champ contient du texte
        if (typed > 0 && now - lastType > 900) {
            budget = MAX_NODES;
            if (findSend(root, pkg) != null) {
                step = 1;
                stepAt = now;
                clicks = 0;
                return;
            }
        }
        if (now - lastType > 1500 && typed < 6) {
            ed.performAction(AccessibilityNodeInfo.ACTION_FOCUS);
            Bundle b = new Bundle();
            b.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, cur.message);
            ed.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, b);
            typed++;
            lastType = now;
        }
    }

    static String norm(String s) {
        return s == null ? "" : s.replace("\r", "").trim();
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
        if (n == null || budget-- <= 0) return null;
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
        if (n == null || budget-- <= 0) return null;
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
        if (n == null || budget-- <= 0) return null;
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
        if (n == null || budget-- <= 0) return null;
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
