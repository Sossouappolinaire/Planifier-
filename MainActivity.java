package bj.planificateur;

import android.app.Activity;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ProgressBar;
import android.animation.ValueAnimator;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.app.KeyguardManager;
import android.content.pm.PackageManager;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

public class MainActivity extends Activity {

    // ---- Champs de saisie et zones à remplir ----
    TextView status, selCount, lienStatus, pinStatus;
    CheckBox cbUnlock, cbRelock;
    EditText etPin;
    EditText lien, filter, manual, message, etCount, etInterval, etHours, etDays, phone;
    View btnLien;
    Button btnDate, btnTime;
    RadioButton rbInterval, rbFixed;
    LinearLayout groupsBox, manageBox, intervalBox, fixedBox;
    LinearLayout histBox, failBox, okBox, alertBox;
    ProgressBar planBar;
    TextView planPct;

    final Set<String> selected = new LinkedHashSet<>();
    final Calendar cal = Calendar.getInstance();
    final SimpleDateFormat fDate = new SimpleDateFormat("EEE dd/MM/yyyy", Locale.FRANCE);
    final SimpleDateFormat fTime = new SimpleDateFormat("HH:mm", Locale.FRANCE);
    final SimpleDateFormat fFull = new SimpleDateFormat("dd/MM HH:mm", Locale.FRANCE);

    int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }

    static final int BG = Color.parseColor("#0E1F17"), CARD = Color.parseColor("#16302A"),
            GREEN = Color.parseColor("#25D366"), GREEN2 = Color.parseColor("#128C7E"),
            TXT = Color.parseColor("#EAF7EF"), MUTED = Color.parseColor("#9DB8AA"), GOLD = Color.parseColor("#F5C451"),
            RED = Color.parseColor("#FF6B6B"), RED2 = Color.parseColor("#B03A3A"),
            BACK = Color.parseColor("#1F4036");

    // ---- Navigation : un accueil + des panneaux ----
    ScrollView scroll;
    LinearLayout holder;      // contient l'écran affiché (accueil ou un panneau)
    LinearLayout home;        // accueil
    LinearLayout pPlan, pHist, pFail, pOk, pGroups, pAlert, pSet;
    View currentView;
    LinearLayout cur;         // zone où add() ajoute les vues pendant la construction d'un panneau
    final Handler ui = new Handler(Looper.getMainLooper());
    TextView vGroups, vWaiting, vNext;
    TextView bdHist, bdFail, bdOk, bdGroups, bdAlert, bdSet;
    boolean permOk, planning;

    GradientDrawable round(int color, int r) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(r));
        return g;
    }

    void add(View v) { cur.addView(v); }

    /** Affiche l'accueil ou un panneau (un seul à la fois). */
    void show(View v) {
        holder.removeAllViews();
        holder.addView(v);
        currentView = v;
        scroll.scrollTo(0, 0);
        v.setAlpha(0f);
        v.animate().alpha(1f).setDuration(220).start();
    }

    void openPanel(View p) {
        refreshAll();
        show(p);
    }

    void goHome() {
        refreshAll();
        show(home);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        if (currentView != null && currentView != home) goHome();
        else super.onBackPressed();
    }

    Button backBtn() {
        return btnC("←  Retour à l'accueil", BACK, BACK, GREEN, v -> goHome());
    }

    /** Crée un panneau : bouton retour, titre, puis une carte où ajouter le contenu avec add(). */
    LinearLayout newPanel(String titre) {
        LinearLayout p = new LinearLayout(this);
        p.setOrientation(LinearLayout.VERTICAL);
        p.addView(backBtn());
        TextView t = new TextView(this);
        t.setText(titre);
        t.setTextSize(24);
        t.setTypeface(Typeface.create(Typeface.SERIF, Typeface.BOLD));
        t.setTextColor(TXT);
        t.setPadding(dp(4), dp(18), 0, dp(6));
        p.addView(t);
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(round(CARD, 18));
        card.setPadding(dp(14), dp(10), dp(14), dp(14));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(8);
        p.addView(card, lp);
        cur = card;
        return p;
    }

    void endPanel(LinearLayout p) {
        p.addView(backBtn());
    }

    /** Gros bouton de l'accueil : ouvre un panneau. Renvoie le petit compteur à droite. */
    TextView menu(String emoji, String titre, String sous, final View target) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackground(round(CARD, 18));
        row.setPadding(dp(14), dp(14), dp(14), dp(14));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(10);
        row.setLayoutParams(lp);
        TextView em = new TextView(this);
        em.setText(emoji);
        em.setTextSize(26);
        em.setGravity(Gravity.CENTER);
        row.addView(em, new LinearLayout.LayoutParams(dp(46), -2));
        LinearLayout mid = new LinearLayout(this);
        mid.setOrientation(LinearLayout.VERTICAL);
        mid.addView(ligneTxt(titre, 17, TXT, true));
        mid.addView(ligneTxt(sous, 12, MUTED, false));
        row.addView(mid, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView badge = new TextView(this);
        badge.setTextSize(17);
        badge.setTypeface(Typeface.DEFAULT_BOLD);
        badge.setTextColor(GREEN);
        badge.setPadding(dp(8), 0, dp(4), 0);
        row.addView(badge);
        TextView arrow = new TextView(this);
        arrow.setText("›");
        arrow.setTextSize(26);
        arrow.setTextColor(MUTED);
        row.addView(arrow);
        row.setOnClickListener(v -> v.animate().scaleX(0.97f).scaleY(0.97f).setDuration(80)
                .withEndAction(() -> {
                    v.animate().scaleX(1f).scaleY(1f).setDuration(120).start();
                    openPanel(target);
                }).start());
        home.addView(row);
        return badge;
    }

    /** Petit bloc chiffre + libellé du tableau de bord. */
    TextView tile(LinearLayout parent, String label) {
        LinearLayout t = new LinearLayout(this);
        t.setOrientation(LinearLayout.VERTICAL);
        t.setGravity(Gravity.CENTER_HORIZONTAL);
        t.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
        TextView v = new TextView(this);
        v.setText("–");
        v.setTextSize(20);
        v.setTypeface(Typeface.DEFAULT_BOLD);
        v.setTextColor(GREEN);
        v.setGravity(Gravity.CENTER);
        TextView l = new TextView(this);
        l.setText(label);
        l.setTextSize(11);
        l.setTextColor(MUTED);
        l.setGravity(Gravity.CENTER);
        t.addView(v);
        t.addView(l);
        parent.addView(t);
        return v;
    }

    /** Met à jour le tableau de bord et les compteurs de l'accueil. */
    void refreshDashboard() {
        if (vGroups == null) return;
        vGroups.setText(String.valueOf(selected.size()));
        int waiting = 0;
        long next = Long.MAX_VALUE;
        for (Store.Plan p : Store.load(this)) {
            if (p.done || p.idx >= p.times.size()) continue;
            waiting++;
            next = Math.min(next, p.times.get(p.idx));
        }
        vWaiting.setText(String.valueOf(waiting));
        vNext.setText(next == Long.MAX_VALUE ? "—" : fFull.format(new Date(next)));
        bdAlert.setText(waiting > 0 ? String.valueOf(waiting) : "");
        bdGroups.setText(String.valueOf(Store.loadConfirmed(this).size()));
        int nOk = Store.historyCount(this, 1), nFail = Store.historyCount(this, 2);
        bdOk.setText(nOk > 0 ? String.valueOf(nOk) : "");
        bdFail.setText(nFail > 0 ? String.valueOf(nFail) : "");
        bdFail.setTextColor(RED);
        bdHist.setText(nOk + nFail > 0 ? String.valueOf(nOk + nFail) : "");
        bdSet.setText(permOk ? "✔" : "⚠");
        bdSet.setTextColor(permOk ? GREEN : GOLD);
    }

    void refreshAll() {
        refreshStatus();
        refreshGroups();
        refreshList();
        refreshHistory();
        refreshDashboard();
    }

    Button btn(String t, View.OnClickListener l) {
        return btnC(t, GREEN, GREEN2, Color.parseColor("#06291A"), l);
    }

    Button btnC(String t, int c1, int c2, int txtColor, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(t);
        b.setAllCaps(false);
        b.setTextColor(txtColor);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{c1, c2});
        g.setCornerRadius(dp(14));
        b.setBackground(g);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(50));
        lp.topMargin = dp(8);
        b.setLayoutParams(lp);
        b.setOnClickListener(v -> v.animate().scaleX(0.94f).scaleY(0.94f).setDuration(90)
                .withEndAction(() -> {
                    v.animate().scaleX(1f).scaleY(1f).setDuration(140).start();
                    l.onClick(v);
                }).start());
        return b;
    }

    EditText styled(EditText e) {
        e.setTextColor(TXT);
        e.setHintTextColor(MUTED);
        e.setBackground(round(BG, 12));
        e.setPadding(dp(12), dp(10), dp(12), dp(10));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(8);
        e.setLayoutParams(lp);
        return e;
    }

    TextView txt(TextView t) { t.setTextColor(MUTED); return t; }

    ProgressBar bar() {
        ProgressBar pb = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        pb.setMax(100);
        pb.setProgressTintList(android.content.res.ColorStateList.valueOf(GREEN));
        pb.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(BG));
        pb.setLayoutParams(new LinearLayout.LayoutParams(-1, dp(10)));
        pb.setVisibility(View.GONE);
        return pb;
    }

    TextView pct() {
        TextView t = new TextView(this);
        t.setTextColor(GOLD);
        t.setTextSize(13);
        t.setVisibility(View.GONE);
        return t;
    }

    /** Anime une barre de 0 à 100 %, puis lance l'action. */
    void progress(ProgressBar pb, TextView label, String what, Runnable end) {
        pb.setVisibility(View.VISIBLE);
        label.setVisibility(View.VISIBLE);
        ValueAnimator a = ValueAnimator.ofInt(0, 100);
        a.setDuration(1400);
        a.addUpdateListener(an -> {
            int v = (int) an.getAnimatedValue();
            pb.setProgress(v);
            label.setText(what + " " + v + " %");
        });
        a.addListener(new android.animation.AnimatorListenerAdapter() {
            public void onAnimationEnd(android.animation.Animator x) {
                label.setText("✔ Terminé");
                ui.postDelayed(() -> { pb.setVisibility(View.GONE); label.setVisibility(View.GONE); }, 1200);
                end.run();
            }
        });
        a.start();
    }

    SharedPreferences prefs() { return getSharedPreferences("reglages", MODE_PRIVATE); }

    /** Ouvre WhatsApp vers le numéro de l'utilisateur avec la liste trouvée et planifiée. */
    void envoyerRecap() {
        String num = phone.getText().toString().replaceAll("\\D", "");
        if (num.isEmpty()) { toast("Saisis ton numéro WhatsApp (avec indicatif, ex : 22901234567)."); return; }
        prefs().edit().putString("phone", num).apply();
        StringBuilder sb = new StringBuilder("*Planning Sossou Constant* — récapitulatif\n\n");
        List<String> g = Store.loadConfirmed(this);
        sb.append("Groupes (").append(g.size()).append(") : ").append(g.isEmpty() ? "aucun" : join(g)).append("\n\n");
        int i = 1;
        for (Store.Plan p : Store.load(this)) {
            sb.append("*Planification ").append(i++).append("*").append(p.done ? " (terminée)" : "").append("\n");
            sb.append("Groupes : ").append(join(p.groups)).append("\n");
            sb.append("Message : ").append(p.message).append("\n");
            int n = p.times.size();
            if (n <= 6) {
                sb.append("Horaires : ");
                for (int k = 0; k < n; k++) {
                    if (k > 0) sb.append(" | ");
                    sb.append(fFull.format(new Date(p.times.get(k))));
                }
            } else {
                sb.append("Horaires : ").append(n).append(" envois, du ")
                        .append(fFull.format(new Date(p.times.get(0)))).append(" au ")
                        .append(fFull.format(new Date(p.times.get(n - 1))));
            }
            sb.append("\n\n");
        }
        if (i == 1) sb.append("Aucune planification pour l'instant.");
        if (sb.length() > 1800) { sb.setLength(1800); sb.append("…"); }
        String lien = "https://wa.me/" + num + "?text=" + Uri.encode(sb.toString());
        WaService svc = WaService.instance;
        if (svc != null) {
            // Envoi automatique : le service ouvre la discussion et appuie sur Envoyer tout seul
            svc.enqueue(new Job(0, "Mon numéro", sb.toString(), lien));
            toast("Récapitulatif en cours d'envoi : ne touche pas à l'écran.");
            return;
        }
        // Sans accessibilité, on ne peut qu'ouvrir WhatsApp (il faudra appuyer sur Envoyer)
        toast("Autorise l'accès à WhatsApp (accessibilité) pour un envoi automatique.");
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(lien)));
        } catch (Exception e) {
            toast("WhatsApp introuvable sur ce téléphone.");
        }
    }

    EditText num(String hint, String def) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setText(def);
        e.setInputType(InputType.TYPE_CLASS_NUMBER);
        return e;
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setTitle("Planning Sossou Constant");
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission("android.permission.POST_NOTIFICATIONS") != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 1);
        }
        cal.add(Calendar.MINUTE, 2);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);

        holder = new LinearLayout(this);
        holder.setOrientation(LinearLayout.VERTICAL);
        holder.setPadding(dp(16), dp(8), dp(16), dp(32));
        scroll = new ScrollView(this);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scroll.setFillViewport(false);
        scroll.addView(holder);
        setContentView(scroll);
        // Fond fixe : dégradé vert sombre
        GradientDrawable fond = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Color.parseColor("#0A1A13"), Color.parseColor("#0F2B21"), Color.parseColor("#08130E")});
        getWindow().setBackgroundDrawable(fond);
        getWindow().setStatusBarColor(Color.parseColor("#0A1A13"));
        getWindow().setNavigationBarColor(Color.parseColor("#08130E"));

        buildPlanPanel();
        buildHistoryPanels();
        buildGroupsPanel();
        buildAlertPanel();
        buildSettingsPanel();
        buildHome();

        for (EditText e : new EditText[]{filter, lien, manual, message, etCount, etInterval, etHours, etDays, etPin}) styled(e);
        for (TextView t : new TextView[]{status, selCount}) txt(t);
        rbInterval.setTextColor(TXT);
        rbFixed.setTextColor(TXT);
        rbInterval.setButtonTintList(android.content.res.ColorStateList.valueOf(GREEN));
        rbFixed.setButtonTintList(android.content.res.ColorStateList.valueOf(GREEN));
        updateMode();
        refreshButtons();
        show(home);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshAll();
    }

    // ================= ACCUEIL =================
    void buildHome() {
        home = new LinearLayout(this);
        home.setOrientation(LinearLayout.VERTICAL);
        TextView logo = new TextView(this);
        logo.setText("Planning\nSossou Constant");
        logo.setTextSize(30);
        logo.setTypeface(Typeface.create(Typeface.SERIF, Typeface.BOLD));
        logo.setTextColor(TXT);
        logo.setGravity(Gravity.CENTER);
        logo.setPadding(0, dp(24), 0, dp(4));
        home.addView(logo);
        TextView sub = new TextView(this);
        sub.setText("Choisir • Planifier • Suivre les envois");
        sub.setTextColor(GOLD);
        sub.setGravity(Gravity.CENTER);
        home.addView(sub);

        LinearLayout dash = new LinearLayout(this);
        dash.setOrientation(LinearLayout.HORIZONTAL);
        dash.setBackground(round(CARD, 18));
        dash.setPadding(dp(8), dp(14), dp(8), dp(14));
        LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(-1, -2);
        dl.topMargin = dp(16);
        dash.setLayoutParams(dl);
        vGroups = tile(dash, "Groupes cochés");
        vWaiting = tile(dash, "Envois en attente");
        vNext = tile(dash, "Prochain envoi");
        vNext.setTextSize(15);
        vNext.setTextColor(GOLD);
        home.addView(dash);

        menu("📅", "Planifier un envoi", "Quand, groupes cochés et message", pPlan);
        bdAlert = menu("⏰", "Alertes en attente", "Les envois prévus, à lire ou supprimer", pAlert);
        bdHist = menu("📖", "Historique", "Tous les envois, du plus récent au plus ancien", pHist);
        bdOk = menu("✔", "Envois validés", "Les envois réussis", pOk);
        bdFail = menu("✘", "Échecs", "Les envois qui n'ont pas marché", pFail);
        bdGroups = menu("🔗", "Groupes et liens", "Ajouter un lien, voir le nom du groupe", pGroups);
        bdSet = menu("⚙", "Réglages", "Autorisations et récapitulatif WhatsApp", pSet);
    }

    // ================= PANNEAU : PLANIFIER =================
    void buildPlanPanel() {
        pPlan = newPanel("📅 Planifier un envoi");

        // --- Quand envoyer ---
        add(sousTitre("1. Quand envoyer ?"));
        RadioGroup rg = new RadioGroup(this);
        rbInterval = new RadioButton(this);
        rbInterval.setId(View.generateViewId());
        rbInterval.setText("À intervalle régulier (ex : 5 fois, toutes les 30 min)");
        rbFixed = new RadioButton(this);
        rbFixed.setId(View.generateViewId());
        rbFixed.setText("À des heures fixes (ex : 08:00, 12:30, 18:00)");
        rg.addView(rbInterval);
        rg.addView(rbFixed);
        rbInterval.setChecked(true);
        rg.setOnCheckedChangeListener((g, id) -> updateMode());
        add(rg);

        btnDate = btn("", v -> new DatePickerDialog(this, (dpk, y, m, d) -> {
            cal.set(y, m, d);
            refreshButtons();
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show());
        add(btnDate);

        intervalBox = new LinearLayout(this);
        intervalBox.setOrientation(LinearLayout.VERTICAL);
        btnTime = btn("", v -> new TimePickerDialog(this, (tp, h, m) -> {
            cal.set(Calendar.HOUR_OF_DAY, h);
            cal.set(Calendar.MINUTE, m);
            cal.set(Calendar.SECOND, 0);
            refreshButtons();
        }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), true).show());
        intervalBox.addView(btnTime);
        etCount = num("Nombre d'envois", "1");
        etCount.setHint("Nombre d'envois (1 = un seul)");
        intervalBox.addView(etCount);
        etInterval = num("Intervalle en minutes", "60");
        etInterval.setHint("Intervalle entre deux envois (minutes)");
        intervalBox.addView(etInterval);
        add(intervalBox);

        fixedBox = new LinearLayout(this);
        fixedBox.setOrientation(LinearLayout.VERTICAL);
        etHours = new EditText(this);
        etHours.setHint("Heures fixes, ex : 08:00, 12:30, 18:00");
        etHours.setInputType(InputType.TYPE_CLASS_TEXT);
        fixedBox.addView(etHours);
        etDays = num("Nombre de jours", "1");
        etDays.setHint("Pendant combien de jours (à partir de la date)");
        fixedBox.addView(etDays);
        add(fixedBox);

        // --- Groupes cochés ---
        add(sousTitre("2. Groupes à cocher"));
        filter = new EditText(this);
        filter.setHint("Filtrer la liste…");
        filter.setSingleLine(true);
        filter.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b2, int c) {
            }

            public void onTextChanged(CharSequence s, int a, int b2, int c) {
            }

            public void afterTextChanged(Editable s) {
                refreshGroups();
            }
        });
        add(filter);
        selCount = new TextView(this);
        selCount.setPadding(0, dp(4), 0, dp(4));
        add(selCount);
        groupsBox = new LinearLayout(this);
        groupsBox.setOrientation(LinearLayout.VERTICAL);
        add(groupsBox);

        // --- Message ---
        add(sousTitre("3. Message à envoyer"));
        message = new EditText(this);
        message.setHint("Écris ton message ici");
        message.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        message.setMinLines(5);
        message.setGravity(Gravity.TOP);
        add(message);

        add(btn("📅 Planifier", v -> planifier()));
        planBar = bar();
        add(planBar);
        planPct = pct();
        add(planPct);
        add(btn("Tester maintenant (envoi immédiat)", v -> tester()));
        endPanel(pPlan);
    }

    // ================= PANNEAUX : HISTORIQUE / VALIDÉS / ÉCHECS =================
    void buildHistoryPanels() {
        pHist = newPanel("📖 Historique");
        histBox = new LinearLayout(this);
        histBox.setOrientation(LinearLayout.VERTICAL);
        add(histBox);
        add(btnC("Effacer l'historique", RED, RED2, Color.WHITE, v -> {
            Store.clearHistory(this);
            refreshHistory();
            refreshDashboard();
        }));
        endPanel(pHist);

        pOk = newPanel("✔ Envois validés");
        okBox = new LinearLayout(this);
        okBox.setOrientation(LinearLayout.VERTICAL);
        add(okBox);
        endPanel(pOk);

        pFail = newPanel("✘ Échecs");
        failBox = new LinearLayout(this);
        failBox.setOrientation(LinearLayout.VERTICAL);
        add(failBox);
        endPanel(pFail);
    }

    // ================= PANNEAU : GROUPES ET LIENS =================
    void buildGroupsPanel() {
        pGroups = newPanel("🔗 Groupes et liens");
        if (Store.installed(this).size() > 1) {
            TextView lab = new TextView(this);
            lab.setText("Deux WhatsApp détectés : lequel utiliser ?");
            lab.setTextColor(GOLD);
            lab.setTypeface(Typeface.DEFAULT_BOLD);
            add(lab);
            RadioGroup rgw = new RadioGroup(this);
            final RadioButton rw1 = new RadioButton(this), rw2 = new RadioButton(this);
            rw1.setId(View.generateViewId());
            rw2.setId(View.generateViewId());
            rw1.setText("WhatsApp");
            rw2.setText("WhatsApp Business");
            for (RadioButton r : new RadioButton[]{rw1, rw2}) {
                r.setTextColor(TXT);
                r.setButtonTintList(android.content.res.ColorStateList.valueOf(GREEN));
                rgw.addView(r);
            }
            (Store.waPkg(this).equals("com.whatsapp.w4b") ? rw2 : rw1).setChecked(true);
            rgw.setOnCheckedChangeListener((g, id) -> {
                Store.setWaPkg(this, id == rw2.getId() ? "com.whatsapp.w4b" : "com.whatsapp");
                selected.clear();
                refreshGroups();
                toast("WhatsApp changé : ajoute tes groupes avec leur lien.");
            });
            add(rgw);
        }

        TextView aideLien = new TextView(this);
        aideLien.setText("Ajouter un groupe : colle son lien d'invitation (WhatsApp > infos du groupe > "
                + "Inviter via un lien). L'appli retrouve le nom exact toute seule.");
        aideLien.setTextColor(MUTED);
        aideLien.setPadding(0, dp(4), 0, dp(4));
        add(aideLien);
        lien = new EditText(this);
        lien.setHint("https://chat.whatsapp.com/…");
        lien.setSingleLine(true);
        add(lien);
        manual = new EditText(this);
        manual.setHint("Nom du groupe (seulement si le nom n'est pas trouvé tout seul)");
        manual.setSingleLine(true);
        add(manual);
        lienStatus = new TextView(this);
        lienStatus.setTextColor(MUTED);
        add(lienStatus);
        btnLien = btn("🔗 Ajouter le groupe avec son lien", v -> ajouterParLien());
        add(btnLien);

        manageBox = new LinearLayout(this);
        manageBox.setOrientation(LinearLayout.VERTICAL);
        add(manageBox);
        endPanel(pGroups);
    }

    // ================= PANNEAU : ALERTES EN ATTENTE =================
    void buildAlertPanel() {
        pAlert = newPanel("⏰ Alertes en attente");
        alertBox = new LinearLayout(this);
        alertBox.setOrientation(LinearLayout.VERTICAL);
        add(alertBox);
        endPanel(pAlert);
    }

    // ================= PANNEAU : RÉGLAGES =================
    void buildSettingsPanel() {
        pSet = newPanel("⚙ Réglages");
        add(sousTitre("Autorisations (une seule fois)"));
        status = new TextView(this);
        status.setTextSize(15);
        add(status);
        add(btn("Autoriser l'accès à WhatsApp (accessibilité)", v ->
                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))));
        add(btn("Autoriser les alarmes exactes", v -> openExactSettings()));
        add(btn("Désactiver l'optimisation batterie", v -> {
            try {
                startActivity(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                        Uri.parse("package:" + getPackageName())));
            } catch (Exception e) {
                startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
            }
        }));

        add(sousTitre("Déverrouillage automatique"));
        TextView aideCode = new TextView(this);
        aideCode.setText("Si le téléphone est verrouillé à l'heure de l'envoi, l'appli saisit ton code PIN, envoie "
                + "le message, puis reverrouille. L'empreinte digitale ne peut pas être utilisée par une appli : "
                + "seul le code PIN (chiffres) est possible. Le code reste chiffré sur ce téléphone.");
        aideCode.setTextColor(MUTED);
        aideCode.setPadding(0, 0, 0, dp(6));
        add(aideCode);
        cbUnlock = new CheckBox(this);
        cbUnlock.setText("Déverrouiller le téléphone tout seul");
        cbUnlock.setTextColor(TXT);
        cbUnlock.setButtonTintList(android.content.res.ColorStateList.valueOf(GREEN));
        cbUnlock.setChecked(Vault.enabled(this));
        cbUnlock.setOnCheckedChangeListener((v, on) -> {
            if (on && !Vault.hasPin(this)) {
                toast("Enregistre d'abord ton code PIN ci-dessous.");
                v.setChecked(false);
                return;
            }
            Vault.setEnabled(this, on);
            refreshStatus();
            refreshDashboard();
        });
        add(cbUnlock);
        cbRelock = new CheckBox(this);
        cbRelock.setText("Reverrouiller le téléphone après l'envoi");
        cbRelock.setTextColor(TXT);
        cbRelock.setButtonTintList(android.content.res.ColorStateList.valueOf(GREEN));
        cbRelock.setChecked(Vault.relock(this));
        cbRelock.setOnCheckedChangeListener((v, on) -> Vault.setRelock(this, on));
        add(cbRelock);
        etPin = new EditText(this);
        etPin.setHint("Code PIN du téléphone (chiffres seulement)");
        etPin.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        add(etPin);
        pinStatus = new TextView(this);
        pinStatus.setTextColor(MUTED);
        add(pinStatus);
        add(btn("Enregistrer le code", v -> enregistrerCode()));
        add(btnC("Effacer le code", RED, RED2, Color.WHITE, v -> {
            Vault.clearPin(this);
            cbUnlock.setChecked(false);
            refreshStatus();
            refreshDashboard();
        }));

        add(sousTitre("Récapitulatif sur mon WhatsApp"));
        phone = styled(new EditText(this));
        phone.setHint("Ton numéro avec indicatif, ex : 22901234567");
        phone.setInputType(InputType.TYPE_CLASS_PHONE);
        phone.setText(prefs().getString("phone", ""));
        add(phone);
        add(btn("💬 Envoyer le récapitulatif sur mon WhatsApp", v -> envoyerRecap()));
        endPanel(pSet);
    }

    void updateMode() {
        boolean iv = rbInterval.isChecked();
        intervalBox.setVisibility(iv ? View.VISIBLE : View.GONE);
        fixedBox.setVisibility(iv ? View.GONE : View.VISIBLE);
        refreshButtons();
    }

    void refreshButtons() {
        boolean iv = rbInterval.isChecked();
        btnDate.setText((iv ? "Date du premier envoi : " : "À partir du : ") + fDate.format(cal.getTime()));
        btnTime.setText("Heure du premier envoi : " + fTime.format(cal.getTime()));
    }

    void ligne(SpannableStringBuilder sb, int etat, String t) {
        int s = sb.length();
        sb.append(etat == 0 ? "✔ " : etat == 1 ? "⚠ " : "✘ ").append(t).append("\n");
        sb.setSpan(new ForegroundColorSpan(etat == 0 ? GREEN : etat == 1 ? GOLD : RED), s, s + 1, 0);
    }

    void refreshStatus() {
        boolean acc = WaService.instance != null;
        boolean alarm = Scheduler.canExact(this);
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        boolean bat = pm.isIgnoringBatteryOptimizations(getPackageName());
        KeyguardManager km = (KeyguardManager) getSystemService(KEYGUARD_SERVICE);
        boolean secure = km != null && km.isDeviceSecure();
        SpannableStringBuilder sb = new SpannableStringBuilder();
        ligne(sb, acc ? 0 : 2, "Accès à WhatsApp (accessibilité)");
        ligne(sb, alarm ? 0 : 2, "Alarmes exactes");
        ligne(sb, bat ? 0 : 2, "Batterie non restreinte");
        boolean autoUnlock = Vault.enabled(this) && Vault.hasPin(this);
        ligne(sb, secure && !autoUnlock ? 1 : 0, !secure ? "Pas de code à l'écran de verrouillage"
                : autoUnlock ? "Déverrouillage automatique prêt (code enregistré)"
                : "Code/empreinte à l'écran de verrouillage : enregistre ton code ci-dessous pour l'envoi automatique");
        sb.delete(sb.length() - 1, sb.length());
        status.setText(sb);
        if (pinStatus != null) {
            pinStatus.setText(Vault.hasPin(this) ? "✔ Code enregistré (chiffré)" : "Aucun code enregistré");
            if (cbUnlock != null && cbUnlock.isChecked() != Vault.enabled(this)) cbUnlock.setChecked(Vault.enabled(this));
        }
        permOk = acc && alarm && bat && (!secure || autoUnlock);
    }

    TextView sousTitre(String t) {
        TextView v = new TextView(this);
        v.setText(t);
        v.setTextColor(GOLD);
        v.setTypeface(Typeface.DEFAULT_BOLD);
        v.setPadding(0, dp(10), 0, dp(4));
        return v;
    }

    // ================= RAFRAÎCHISSEMENT DES PANNEAUX =================
    void refreshGroups() {
        if (groupsBox == null || manageBox == null) return;
        groupsBox.removeAllViews();
        manageBox.removeAllViews();
        String f = filter.getText().toString().trim().toLowerCase(Locale.ROOT);
        List<String> mine = Store.loadConfirmed(this);
        selected.retainAll(mine);

        // --- Panneau Planifier : seulement les cases à cocher ---
        if (mine.isEmpty()) {
            TextView t = new TextView(this);
            t.setTextColor(MUTED);
            t.setText("Aucun groupe pour l'instant. Ajoute-en dans « Groupes et liens ».");
            groupsBox.addView(t);
            groupsBox.addView(btnC("🔗 Ajouter un groupe", BACK, BACK, GREEN, v -> openPanel(pGroups)));
        }
        for (final String g : mine) {
            if (!f.isEmpty() && !g.toLowerCase(Locale.ROOT).contains(f)) continue;
            CheckBox cb = new CheckBox(this);
            cb.setText(g);
            cb.setChecked(selected.contains(g));
            cb.setTextColor(TXT);
            cb.setButtonTintList(android.content.res.ColorStateList.valueOf(GREEN));
            cb.setOnCheckedChangeListener((v, on) -> {
                if (on) selected.add(g); else selected.remove(g);
                selCount.setText(selected.size() + " groupe(s) coché(s) pour l'envoi");
                refreshDashboard();
            });
            groupsBox.addView(cb);
        }
        selCount.setText(selected.size() + " groupe(s) coché(s) pour l'envoi");

        // --- Panneau Groupes et liens : liste avec suppression ---
        manageBox.addView(sousTitre("Mes groupes (" + mine.size() + ")"));
        if (mine.isEmpty()) {
            TextView t = new TextView(this);
            t.setTextColor(MUTED);
            t.setText("Aucun groupe : colle le lien d'un groupe ci-dessus pour l'ajouter.");
            manageBox.addView(t);
        }
        for (final String g : mine) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setBackground(round(BG, 12));
            row.setPadding(dp(12), dp(6), dp(4), dp(6));
            LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(-1, -2);
            rl.topMargin = dp(6);
            row.setLayoutParams(rl);
            LinearLayout info = new LinearLayout(this);
            info.setOrientation(LinearLayout.VERTICAL);
            info.addView(ligneTxt(g, 15, TXT, true));
            boolean aLien = Store.linkOf(this, g) != null;
            info.addView(ligneTxt(aLien ? "🔗 lien enregistré" : "sans lien (envoi par recherche du nom)", 12,
                    aLien ? MUTED : GOLD, false));
            row.addView(info, new LinearLayout.LayoutParams(0, -2, 1f));
            TextView x = new TextView(this);
            x.setText("✕");
            x.setTextColor(RED);
            x.setTextSize(20);
            x.setPadding(dp(14), dp(8), dp(14), dp(8));
            x.setOnClickListener(v -> {
                Store.removeConfirmed(this, g);
                selected.remove(g);
                refreshGroups();
            });
            row.addView(x);
            manageBox.addView(row);
        }
        refreshDashboard();
    }

    /** Alertes en attente : les envois planifiés qui n'ont pas encore fini. */
    void refreshList() {
        if (alertBox == null) return;
        alertBox.removeAllViews();
        int waiting = 0, finished = 0;
        for (final Store.Plan p : Store.load(this)) {
            boolean enCours = !p.done && p.idx < p.times.size();
            if (!enCours) {
                finished++;
                continue;
            }
            waiting++;
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.VERTICAL);
            row.setBackground(round(BG, 14));
            row.setPadding(dp(12), dp(10), dp(12), dp(10));
            LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(-1, -2);
            rl.topMargin = dp(8);
            row.setLayoutParams(rl);

            row.addView(ligneTxt("⏰ EN ATTENTE", 12, GOLD, true));
            row.addView(ligneTxt("Prochain : " + fFull.format(new Date(p.times.get(p.idx)))
                    + "  (" + (p.idx + 1) + "/" + p.times.size() + ")", 16, TXT, true));
            if (p.times.size() - p.idx > 1) {
                StringBuilder up = new StringBuilder("Ensuite : ");
                int shown = 0;
                for (int k = p.idx + 1; k < p.times.size() && shown < 5; k++, shown++) {
                    if (shown > 0) up.append(" • ");
                    up.append(fFull.format(new Date(p.times.get(k))));
                }
                int rest = p.times.size() - p.idx - 1 - shown;
                if (rest > 0) up.append(" … (+").append(rest).append(")");
                row.addView(ligneTxt(up.toString(), 12, MUTED, false));
            }
            row.addView(ligneTxt("Groupes : " + join(p.groups), 13, MUTED, false));
            String msg = p.message.replace('\n', ' ');
            if (msg.length() > 80) msg = msg.substring(0, 80) + "…";
            row.addView(ligneTxt("« " + msg + " »", 14, TXT, false));
            if (p.last != null && !p.last.isEmpty()) {
                char c = p.last.charAt(0);
                int col = c == '✔' ? GREEN : c == '✘' ? RED : c == '⚠' ? GOLD : MUTED;
                row.addView(ligneTxt(p.last, 13, col, false));
            }
            row.addView(btnC("Supprimer cette alerte", RED, RED2, Color.WHITE, v -> {
                Scheduler.cancel(this, p.id);
                Store.remove(this, p.id);
                refreshList();
            }));
            alertBox.addView(row);
        }
        if (waiting == 0) {
            alertBox.addView(ligneTxt("Aucune alerte en attente.", 14, MUTED, false));
        }
        if (finished > 0) {
            final int n = finished;
            alertBox.addView(btnC("Effacer les " + n + " planification(s) terminée(s)", BACK, BACK, GREEN, v -> {
                for (Store.Plan q : Store.load(this)) {
                    if (q.done || q.idx >= q.times.size()) {
                        Scheduler.cancel(this, q.id);
                        Store.remove(this, q.id);
                    }
                }
                refreshList();
            }));
        }
        refreshDashboard();
    }

    void refreshHistory() {
        fillHistory(histBox, 0, "Aucun envoi pour l'instant.");
        fillHistory(okBox, 1, "Aucun envoi validé pour l'instant.");
        fillHistory(failBox, 2, "Aucun échec. 👍");
    }

    /** mode : 0 = tout, 1 = validés, 2 = échecs. */
    void fillHistory(LinearLayout box, int mode, String vide) {
        if (box == null) return;
        box.removeAllViews();
        List<String> h = Store.historyLines(this, 100, mode);
        if (h.isEmpty()) {
            box.addView(ligneTxt(vide, 14, MUTED, false));
            return;
        }
        box.addView(ligneTxt(h.size() + " envoi(s) — du plus récent au plus ancien", 12, GOLD, false));
        for (String l : h) {
            TextView t = ligneTxt(l, 13, l.startsWith("✔") ? TXT : RED, false);
            t.setBackground(round(BG, 12));
            t.setPadding(dp(12), dp(8), dp(12), dp(8));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.topMargin = dp(6);
            t.setLayoutParams(lp);
            box.addView(t);
        }
    }

    /** Ajoute un groupe à partir de son lien d'invitation : retrouve son nom exact sur la page du lien. */
    void ajouterParLien() {
        final String code = Store.inviteCode(lien.getText().toString());
        if (code == null) {
            toast("Lien invalide : il doit ressembler à https://chat.whatsapp.com/…");
            return;
        }
        final String link = Store.inviteUrl(code);
        final String saisi = manual.getText().toString().trim();
        btnLien.setEnabled(false);
        lienStatus.setText("Recherche du nom exact du groupe…");
        new Thread(() -> {
            String trouve = null;
            try {
                trouve = nomDuGroupe(code);
            } catch (Exception ignored) {
            }
            final String nom = trouve;
            ui.post(() -> {
                btnLien.setEnabled(true);
                String finalName = nom != null ? nom : saisi;
                if (finalName.isEmpty()) {
                    lienStatus.setText("✘ Nom introuvable automatiquement (pas de réseau, ou lien révoqué). "
                            + "Écris le nom du groupe dans le champ ci-dessus puis ré-appuie.");
                    return;
                }
                // Deux groupes différents ne doivent pas porter le même nom
                String other = Store.linkOf(this, finalName);
                if (other != null && !other.equals(link)) finalName = finalName + " (2)";
                Store.addGroup(this, finalName, link);
                selected.add(finalName);
                lien.setText("");
                manual.setText("");
                lienStatus.setText("✔ Ajouté : " + finalName + (nom == null ? " (nom saisi à la main)" : " (nom exact trouvé)"));
                refreshGroups();
            });
        }).start();
    }

    /** Lit le nom du groupe dans la page publique du lien d'invitation. */
    String nomDuGroupe(String code) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(Store.inviteUrl(code)).openConnection();
        c.setConnectTimeout(8000);
        c.setReadTimeout(8000);
        c.setInstanceFollowRedirects(true);
        c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36");
        c.setRequestProperty("Accept-Language", "fr");
        try {
            BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream(), "UTF-8"));
            StringBuilder sb = new StringBuilder();
            char[] buf = new char[4096];
            int n;
            while ((n = r.read(buf)) > 0 && sb.length() < 300000) sb.append(buf, 0, n);
            java.util.regex.Matcher m = java.util.regex.Pattern
                    .compile("<meta[^>]+property=[\"']og:title[\"'][^>]+content=[\"']([^\"']*)[\"']",
                            java.util.regex.Pattern.CASE_INSENSITIVE).matcher(sb);
            if (!m.find()) {
                m = java.util.regex.Pattern
                        .compile("<meta[^>]+content=[\"']([^\"']*)[\"'][^>]+property=[\"']og:title[\"']",
                                java.util.regex.Pattern.CASE_INSENSITIVE).matcher(sb);
                if (!m.find()) return null;
            }
            String t = android.text.Html.fromHtml(m.group(1), android.text.Html.FROM_HTML_MODE_LEGACY)
                    .toString().trim();
            // Titre générique renvoyé pour un lien invalide ou révoqué
            if (t.isEmpty() || t.equalsIgnoreCase("WhatsApp Group Invite")
                    || t.equalsIgnoreCase("WhatsApp")) return null;
            return t;
        } finally {
            c.disconnect();
        }
    }

    void enregistrerCode() {
        String code = etPin.getText().toString().trim();
        if (!code.matches("\\d{4,16}")) {
            toast("Le code doit contenir de 4 à 16 chiffres.");
            return;
        }
        if (!Vault.savePin(this, code)) {
            toast("Impossible d'enregistrer le code sur ce téléphone.");
            return;
        }
        etPin.setText("");
        Vault.setEnabled(this, true);
        cbUnlock.setChecked(true);
        toast("Code enregistré. Teste avec « Tester maintenant » en verrouillant l'écran.");
        refreshStatus();
        refreshDashboard();
    }

    void openExactSettings() {
        if (Build.VERSION.SDK_INT >= 31) {
            try {
                startActivity(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                        Uri.parse("package:" + getPackageName())));
                return;
            } catch (Exception ignored) {
            }
        }
        toast("Les alarmes exactes sont déjà autorisées.");
    }

    void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_LONG).show();
    }

    int parse(EditText e, int def) {
        try {
            return Integer.parseInt(e.getText().toString().trim());
        } catch (Exception ex) {
            return def;
        }
    }

    /** Calcule la liste des heures d'envoi, ou null (avec un message) si invalide. */
    List<Long> calculerHeures() {
        List<Long> times = new ArrayList<>();
        long now = System.currentTimeMillis();
        if (rbInterval.isChecked()) {
            int count = parse(etCount, 1);
            int mins = parse(etInterval, 60);
            if (count < 1 || count > 300) {
                toast("Nombre d'envois : entre 1 et 300.");
                return null;
            }
            if (count > 1 && mins < 1) {
                toast("Intervalle : au moins 1 minute.");
                return null;
            }
            if (cal.getTimeInMillis() <= now + 5000) {
                toast("Choisis une date et une heure dans le futur.");
                return null;
            }
            for (int i = 0; i < count; i++) {
                times.add(cal.getTimeInMillis() + i * mins * 60000L);
            }
        } else {
            int days = parse(etDays, 1);
            if (days < 1 || days > 60) {
                toast("Nombre de jours : entre 1 et 60.");
                return null;
            }
            String raw = etHours.getText().toString().trim();
            if (raw.isEmpty()) {
                toast("Saisis au moins une heure (ex : 08:00, 18:30).");
                return null;
            }
            List<int[]> hm = new ArrayList<>();
            try {
                for (String part : raw.split("[,;\\s]+")) {
                    if (part.isEmpty()) continue;
                    part = part.toLowerCase(Locale.ROOT).replace('h', ':');
                    if (part.endsWith(":")) part += "00";
                    String[] x = part.split(":");
                    int h = Integer.parseInt(x[0]);
                    int m = x.length > 1 && !x[1].isEmpty() ? Integer.parseInt(x[1]) : 0;
                    if (h < 0 || h > 23 || m < 0 || m > 59) throw new NumberFormatException();
                    hm.add(new int[]{h, m});
                }
            } catch (Exception e) {
                toast("Heures invalides. Exemple : 08:00, 12:30, 18:00");
                return null;
            }
            for (int d = 0; d < days; d++) {
                for (int[] t : hm) {
                    Calendar c = (Calendar) cal.clone();
                    c.add(Calendar.DAY_OF_MONTH, d);
                    c.set(Calendar.HOUR_OF_DAY, t[0]);
                    c.set(Calendar.MINUTE, t[1]);
                    c.set(Calendar.SECOND, 0);
                    c.set(Calendar.MILLISECOND, 0);
                    if (c.getTimeInMillis() > now + 5000) times.add(c.getTimeInMillis());
                }
            }
            if (times.isEmpty()) {
                toast("Toutes ces heures sont déjà passées.");
                return null;
            }
            if (times.size() > 300) {
                toast("Trop d'envois (maximum 300).");
                return null;
            }
        }
        return new ArrayList<>(new TreeSet<>(times)); // trié, sans doublon
    }

    void planifier() {
        String m = message.getText().toString();
        if (selected.isEmpty()) {
            toast("Coche au moins un groupe.");
            return;
        }
        if (m.trim().isEmpty()) {
            toast("Écris le message.");
            return;
        }
        if (!Scheduler.canExact(this)) {
            toast("Autorise d'abord les alarmes exactes.");
            openExactSettings();
            return;
        }
        if (planning) return;
        List<Long> times = calculerHeures();
        if (times == null) return;
        planning = true;

        Store.Plan p = new Store.Plan();
        p.id = System.currentTimeMillis();
        p.groups = new ArrayList<>(selected);
        p.message = m;
        p.times = times;
        p.idx = 0;
        progress(planBar, planPct, "Planification…", () -> {
            Store.add(this, p);
            Scheduler.schedule(this, p);
            toast(times.size() + " envoi(s) planifié(s), le premier le "
                    + fFull.format(new Date(times.get(0))));
            planning = false;
            refreshList();
            if (!phone.getText().toString().replaceAll("\\D", "").isEmpty()) envoyerRecap();
        });
    }

    void tester() {
        String m = message.getText().toString();
        if (selected.isEmpty() || m.trim().isEmpty()) {
            toast("Coche au moins un groupe et écris le message.");
            return;
        }
        if (WaService.instance == null) {
            toast("Autorise d'abord l'accès à WhatsApp (accessibilité).");
            return;
        }
        toast("Envoi en cours : ne touche pas à l'écran.");
        for (String g : selected) WaService.instance.enqueue(new Job(0, g, m, null, Store.linkOf(this, g)));
    }

    TextView ligneTxt(String s, int size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(size);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setPadding(0, dp(2), 0, dp(2));
        return t;
    }

    static String join(List<String> l) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < l.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(l.get(i));
        }
        return sb.toString();
    }
}

