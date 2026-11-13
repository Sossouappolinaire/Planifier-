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

    TextView status, scanStatus, selCount;
    EditText filter, manual, message, etCount, etInterval, etHours, etDays;
    Button btnDate, btnTime;
    RadioButton rbInterval, rbFixed;
    LinearLayout groupsBox, intervalBox, fixedBox, list;
    final Set<String> selected = new LinkedHashSet<>();
    final Set<String> cand = new LinkedHashSet<>(); // noms cochés dans les résultats de recherche
    final Calendar cal = Calendar.getInstance();
    final SimpleDateFormat fDate = new SimpleDateFormat("EEE dd/MM/yyyy", Locale.FRANCE);
    final SimpleDateFormat fTime = new SimpleDateFormat("HH:mm", Locale.FRANCE);
    final SimpleDateFormat fFull = new SimpleDateFormat("dd/MM HH:mm", Locale.FRANCE);

    int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }

    static final int BG = Color.parseColor("#0E1F17"), CARD = Color.parseColor("#16302A"),
            GREEN = Color.parseColor("#25D366"), GREEN2 = Color.parseColor("#128C7E"),
            TXT = Color.parseColor("#EAF7EF"), MUTED = Color.parseColor("#9DB8AA"), GOLD = Color.parseColor("#F5C451");
    LinearLayout root, cur;
    int sectionNo = 0;
    EditText phone;
    ProgressBar scanBar, planBar;
    TextView planPct, scanPct;
    final Handler ui = new Handler(Looper.getMainLooper());
    String lastScanStatus = "";
    static final int RED = Color.parseColor("#FF6B6B"), RED2 = Color.parseColor("#B03A3A");
    TextView vGroups, vWaiting, vNext, permTitle;
    LinearLayout permBody;
    boolean permChecked, permOk, planning;

    GradientDrawable round(int color, int r) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(r));
        return g;
    }

    void add(View v) { cur.addView(v); }

    /** Ouvre ou ferme un panneau. */
    void setOpen(TextView tv, LinearLayout body, boolean open, boolean anim) {
        String t = (String) tv.getTag();
        tv.setText((open ? "▾  " : "▸  ") + t);
        if (!anim) {
            body.setVisibility(open ? View.VISIBLE : View.GONE);
            return;
        }
        if (open) {
            body.setVisibility(View.VISIBLE);
            body.setAlpha(0f);
            body.setTranslationY(-dp(10));
            body.animate().alpha(1f).translationY(0).setDuration(300).start();
        } else {
            body.animate().alpha(0f).translationY(-dp(10)).setDuration(220)
                    .withEndAction(() -> body.setVisibility(View.GONE)).start();
        }
    }

    TextView title(String t) { return title(t, true); }

    /** Crée un panneau (carte) repliable ; le contenu s'ajoute avec add(). */
    TextView title(String t, boolean open) {
        sectionNo++;
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(round(CARD, 18));
        card.setPadding(dp(14), dp(6), dp(14), dp(12));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(14);
        card.setLayoutParams(lp);
        TextView tv = new TextView(this);
        tv.setTag(t);
        tv.setText((open ? "▾  " : "▸  ") + t);
        tv.setTextSize(17);
        tv.setTextColor(GREEN);
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        tv.setPadding(0, dp(10), 0, dp(10));
        final LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setVisibility(open ? View.VISIBLE : View.GONE);
        tv.setOnClickListener(v -> setOpen(tv, body, body.getVisibility() != View.VISIBLE, true));
        card.addView(tv);
        card.addView(body);
        root.addView(card);
        card.setAlpha(0f);
        card.setTranslationY(dp(30));
        card.animate().alpha(1f).translationY(0).setStartDelay(sectionNo * 90L).setDuration(450).start();
        cur = body;
        return tv;
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
        sb.append("Groupes trouvés (").append(g.size()).append(") : ").append(g.isEmpty() ? "aucun" : join(g)).append("\n\n");
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

    void surveillerRecherche() {
        scanBar.setVisibility(View.VISIBLE);
        scanPct.setVisibility(View.VISIBLE);
        lastScanStatus = Store.scanStatus(this);
        final int[] v = {0};
        final long t0 = System.currentTimeMillis();
        ui.post(new Runnable() {
            public void run() {
                String st = Store.scanStatus(MainActivity.this);
                if (!st.equals(lastScanStatus) || v[0] >= 100 || System.currentTimeMillis() - t0 > 120000) {
                    scanBar.setProgress(100);
                    scanPct.setText("✔ Recherche terminée");
                    refreshGroups();
                    refreshStatus();
                    ui.postDelayed(() -> { scanBar.setVisibility(View.GONE); scanPct.setVisibility(View.GONE); }, 1500);
                    return;
                }
                v[0] = Math.min(95, v[0] + 1 + (95 - v[0]) / 15);
                scanBar.setProgress(v[0]);
                scanPct.setText("Lecture de la liste… " + v[0] + " %");
                ui.postDelayed(this, 400);
            }
        });
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

        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(8), dp(16), dp(32));
        ScrollView sv = new ScrollView(this);
        sv.setOverScrollMode(View.OVER_SCROLL_NEVER);
        sv.addView(root);
        setContentView(sv);
        // Fond fixe : dégradé vert sombre (reste en place quand on fait défiler, aucun flash blanc)
        GradientDrawable fond = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Color.parseColor("#0A1A13"), Color.parseColor("#0F2B21"), Color.parseColor("#08130E")});
        getWindow().setBackgroundDrawable(fond);
        getWindow().setStatusBarColor(Color.parseColor("#0A1A13"));
        getWindow().setNavigationBarColor(Color.parseColor("#08130E"));
        TextView logo = new TextView(this);
        logo.setText("Planning\nSossou Constant");
        logo.setTextSize(30);
        logo.setTypeface(Typeface.create(Typeface.SERIF, Typeface.BOLD));
        logo.setTextColor(TXT);
        logo.setGravity(Gravity.CENTER);
        logo.setPadding(0, dp(24), 0, dp(4));
        root.addView(logo);
        TextView sub = new TextView(this);
        sub.setText("Choisir • Planifier • Recevoir le récap sur WhatsApp");
        sub.setTextColor(GOLD);
        sub.setGravity(Gravity.CENTER);
        root.addView(sub);
        logo.setScaleX(0.7f); logo.setScaleY(0.7f); logo.setAlpha(0f);
        logo.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(600).start();

        // ---------- Tableau de bord ----------
        LinearLayout dash = new LinearLayout(this);
        dash.setOrientation(LinearLayout.HORIZONTAL);
        dash.setBackground(round(CARD, 18));
        dash.setPadding(dp(8), dp(14), dp(8), dp(14));
        LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(-1, -2);
        dl.topMargin = dp(16);
        dash.setLayoutParams(dl);
        vGroups = tile(dash, "Groupes choisis");
        vWaiting = tile(dash, "Envois en attente");
        vNext = tile(dash, "Prochain envoi");
        vNext.setTextSize(15);
        vNext.setTextColor(GOLD);
        root.addView(dash);

        // ---------- 1. Autorisations ----------
        permTitle = title("1. Autorisations (une seule fois)");
        permBody = cur;
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

        // ---------- 2. Groupes ----------
        title(("2. Choisir les groupes"));
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
                cand.clear();
                toast("WhatsApp changé : relance la recherche de tes groupes.");
            });
            add(rgw);
        }
        add(btn("🔍 Rechercher mes groupes WhatsApp", v -> lancerRecherche()));
        scanBar = bar(); add(scanBar); scanPct = pct(); add(scanPct);
        scanStatus = new TextView(this);
        scanStatus.setTextSize(13);
        add(scanStatus);

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

        manual = new EditText(this);
        manual.setHint("Ajouter un groupe à la main (nom exact)");
        manual.setSingleLine(true);
        add(manual);
        add(btn("Ajouter ce groupe", v -> {
            String n = manual.getText().toString().trim();
            if (n.isEmpty()) return;
            Store.addManual(this, n);
            selected.add(n);
            manual.setText("");
            refreshGroups();
        }));

        // ---------- 3. Horaires ----------
        title(("3. Quand envoyer ?"));
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

        // ---------- 4. Message ----------
        title("4. Message et envoi");
        message = new EditText(this);
        message.setHint("Écris ton message ici");
        message.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        message.setMinLines(5);
        message.setGravity(Gravity.TOP);
        add(message);

        add(btn("📅 Planifier", v -> planifier()));
        planBar = bar(); add(planBar); planPct = pct(); add(planPct);
        add(btn("Tester maintenant (envoi immédiat)", v -> tester()));

        // ---------- 5. Envois planifiés ----------
        title("5. Envois planifiés");
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        add(list);

        // ---------- 6. Récapitulatif ----------
        title("6. Récapitulatif sur mon WhatsApp");
        phone = styled(new EditText(this));
        phone.setHint("Ton numéro avec indicatif, ex : 22901234567");
        phone.setInputType(InputType.TYPE_CLASS_PHONE);
        phone.setText(prefs().getString("phone", ""));
        add(phone);
        add(btn("💬 Envoyer le récapitulatif sur mon WhatsApp", v -> envoyerRecap()));

        for (EditText e : new EditText[]{filter, manual, message, etCount, etInterval, etHours, etDays}) styled(e);
        for (TextView t : new TextView[]{status, scanStatus, selCount}) txt(t);
        rbInterval.setTextColor(TXT); rbFixed.setTextColor(TXT);
        rbInterval.setButtonTintList(android.content.res.ColorStateList.valueOf(GREEN));
        rbFixed.setButtonTintList(android.content.res.ColorStateList.valueOf(GREEN));
        updateMode();
        refreshButtons();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshStatus();
        refreshGroups();
        refreshList();
        if (!permChecked) {
            permChecked = true;
            if (permOk) setOpen(permTitle, permBody, false, false); // tout est prêt : on replie
        }
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
        ligne(sb, secure ? 1 : 0, secure
                ? "Code/empreinte à l'écran de verrouillage : l'envoi automatique peut échouer"
                : "Pas de code à l'écran de verrouillage");
        sb.delete(sb.length() - 1, sb.length());
        status.setText(sb);
        permOk = acc && alarm && bat && !secure;
        scanStatus.setText(Store.scanStatus(this));
    }

    TextView sousTitre(String t) {
        TextView v = new TextView(this);
        v.setText(t);
        v.setTextColor(GOLD);
        v.setTypeface(Typeface.DEFAULT_BOLD);
        v.setPadding(0, dp(10), 0, dp(4));
        return v;
    }

    void refreshGroups() {
        groupsBox.removeAllViews();
        String f = filter.getText().toString().trim().toLowerCase(Locale.ROOT);
        List<String> mine = Store.loadConfirmed(this);
        List<String> found = Store.loadScanned(this);
        found.removeAll(mine);
        selected.retainAll(mine);
        cand.retainAll(found);

        // --- Résultats de la recherche : groupes et contacts mélangés ---
        if (!found.isEmpty()) {
            groupsBox.addView(sousTitre("Résultats de la recherche (" + found.size() + ")"));
            TextView aide = new TextView(this);
            aide.setTextColor(MUTED);
            aide.setText("Coche UNIQUEMENT les groupes (pas les contacts), puis valide. "
                    + "Tout ce qui n'est pas coché sera supprimé de la liste.");
            groupsBox.addView(aide);
            for (final String g : found) {
                if (!f.isEmpty() && !g.toLowerCase(Locale.ROOT).contains(f)) continue;
                CheckBox cb = new CheckBox(this);
                cb.setText(g);
                cb.setChecked(cand.contains(g));
                cb.setTextColor(TXT);
                cb.setButtonTintList(android.content.res.ColorStateList.valueOf(GREEN));
                cb.setOnCheckedChangeListener((v, on) -> {
                    if (on) cand.add(g); else cand.remove(g);
                });
                groupsBox.addView(cb);
            }
            groupsBox.addView(btn("✔ Valider les groupes cochés", v -> validerGroupes()));
        }

        // --- Mes groupes : seuls ceux-là servent à envoyer ---
        groupsBox.addView(sousTitre("Mes groupes (" + mine.size() + ")"));
        if (mine.isEmpty()) {
            TextView t = new TextView(this);
            t.setTextColor(MUTED);
            t.setText("Aucun groupe validé : lance la recherche, coche tes groupes puis valide.");
            groupsBox.addView(t);
        }
        for (final String g : mine) {
            if (!f.isEmpty() && !g.toLowerCase(Locale.ROOT).contains(f)) continue;
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            CheckBox cb = new CheckBox(this);
            cb.setText(g);
            cb.setChecked(selected.contains(g));
            cb.setTextColor(TXT);
            cb.setButtonTintList(android.content.res.ColorStateList.valueOf(GREEN));
            cb.setOnCheckedChangeListener((v, on) -> {
                if (on) selected.add(g); else selected.remove(g);
                selCount.setText(selected.size() + " groupe(s) sélectionné(s) pour l'envoi");
                refreshDashboard();
            });
            row.addView(cb, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            TextView x = new TextView(this);
            x.setText("✕");
            x.setTextColor(RED);
            x.setTextSize(18);
            x.setPadding(dp(14), dp(8), dp(14), dp(8));
            x.setOnClickListener(v -> {
                Store.removeConfirmed(this, g);
                selected.remove(g);
                refreshGroups();
            });
            row.addView(x);
            groupsBox.addView(row);
        }
        selCount.setText(selected.size() + " groupe(s) sélectionné(s) pour l'envoi");
        refreshDashboard();
    }

    /** Garde uniquement les noms cochés comme groupes ; le reste de la recherche est effacé. */
    void validerGroupes() {
        if (cand.isEmpty()) {
            toast("Coche d'abord les groupes dans les résultats.");
            return;
        }
        List<String> ok = new ArrayList<>(cand);
        Store.addConfirmed(this, ok);
        Store.clearScanned(this);
        selected.addAll(ok);
        cand.clear();
        toast(ok.size() + " groupe(s) validé(s).");
        refreshGroups();
    }

    void lancerRecherche() {
        WaService s = WaService.instance;
        if (s == null) {
            toast("Autorise d'abord l'accès à WhatsApp (accessibilité).");
            return;
        }
        if (!s.startScan()) {
            toast("Une opération est déjà en cours, réessaie dans un instant.");
            return;
        }
        surveillerRecherche();
        toast("Recherche en cours : ne touche pas à l'écran, tu reviens ici automatiquement.");
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
        for (String g : selected) WaService.instance.enqueue(new Job(0, g, m));
    }

    void refreshList() {
        list.removeAllViews();
        List<Store.Plan> plans = Store.load(this);
        if (plans.isEmpty()) {
            TextView t = new TextView(this);
            t.setTextColor(MUTED);
            t.setText("Aucun envoi planifié pour l'instant.");
            list.addView(t);
        }
        for (final Store.Plan p : plans) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.VERTICAL);
            row.setBackground(round(BG, 14));
            row.setPadding(dp(12), dp(10), dp(12), dp(10));
            LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(-1, -2);
            rl.topMargin = dp(8);
            row.setLayoutParams(rl);
            row.setAlpha(0f);
            row.animate().alpha(1f).setDuration(350).start();

            row.addView(ligneTxt(p.done ? "✔ TERMINÉ" : "⏰ EN ATTENTE", 12, p.done ? MUTED : GOLD, true));
            boolean enCours = !p.done && p.idx < p.times.size();
            row.addView(ligneTxt(enCours
                    ? "Prochain : " + fFull.format(new Date(p.times.get(p.idx)))
                    + "  (" + (p.idx + 1) + "/" + p.times.size() + ")"
                    : p.times.size() + " envoi(s) au total", 16, TXT, true));
            row.addView(ligneTxt("Groupes : " + join(p.groups), 13, MUTED, false));
            String msg = p.message.replace('\n', ' ');
            if (msg.length() > 80) msg = msg.substring(0, 80) + "…";
            row.addView(ligneTxt("« " + msg + " »", 14, TXT, false));
            if (p.last != null && !p.last.isEmpty()) {
                char c = p.last.charAt(0);
                int col = c == '✔' ? GREEN : c == '✘' ? RED : c == '⚠' ? GOLD : MUTED;
                row.addView(ligneTxt(p.last, 13, col, false));
            }
            row.addView(btnC("Supprimer", RED, RED2, Color.WHITE, v -> {
                Scheduler.cancel(this, p.id);
                Store.remove(this, p.id);
                refreshList();
            }));
            list.addView(row);
        }
        refreshDashboard();
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
