package com.mergesms.app;

import android.Manifest;
import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.Dialog;
import android.app.PendingIntent;
import android.app.TimePickerDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.InsetDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.Telephony;
import android.telephony.SmsManager;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.*;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.*;

public class MainActivity extends AppCompatActivity {
    static final String PREFS = "mergesms";

    // ---- Futuristic dark palette ----
    static final int BG = 0xFF060D0A, SURFACE = 0xFF0E1A15, SURFACE2 = 0xFF152A21, STROKE = 0xFF1F3D2F;
    static final int ACCENT = 0xFF2BFF88, ACCENT2 = 0xFF00D1C1, TEXT = 0xFFE9FFF4, MUTED = 0xFF7E9A8C;
    static final int DANGER = 0xFFFF5470, WARN = 0xFFFFC857;

    LinearLayout root, content, listBox;
    SharedPreferences prefs;
    EditText composer;
    String currentTab = "Messages";

    ArrayList<Recipient> recipients = new ArrayList<>();
    ArrayList<MergeField> fields = new ArrayList<>();
    ArrayList<String> csvHeaders = new ArrayList<>();
    ArrayList<Map<String, String>> csvRows = new ArrayList<>();
    ArrayList<Convo> convos = new ArrayList<>();
    ArrayList<String[]> contactCache = null;
    HashMap<String, String> nameMap = new HashMap<>();

    ActivityResultLauncher<String[]> permissionLauncher;
    ActivityResultLauncher<String> csvLauncher;

    static class Recipient {
        String name, phone, status = "Not sent";
        Map<String, String> data = new LinkedHashMap<>();
        boolean selected = true;
        Recipient(String n, String p) { name = n; phone = p; }
    }
    static class MergeField {
        String token, column;
        MergeField(String t, String c) { token = t; column = c; }
    }
    static class Convo {
        String address, preview; long date; int count, unread;
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        permissionLauncher = registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), r -> {
            contactCache = null;
            showHome();
        });
        csvLauncher = registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> { if (uri != null) readCsv(uri); });
        requestNeededPermissions();
        showHome();
    }

    // =====================================================================
    //  Permissions
    // =====================================================================
    boolean has(String p) { return ContextCompat.checkSelfPermission(this, p) == PackageManager.PERMISSION_GRANTED; }

    void requestNeededPermissions() {
        ArrayList<String> p = new ArrayList<>();
        for (String s : new String[]{Manifest.permission.SEND_SMS, Manifest.permission.READ_SMS,
                Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_CONTACTS})
            if (!has(s)) p.add(s);
        if (!p.isEmpty()) permissionLauncher.launch(p.toArray(new String[0]));
    }

    SmsManager sms() {
        return Build.VERSION.SDK_INT >= 31 ? getSystemService(SmsManager.class) : SmsManager.getDefault();
    }

    // =====================================================================
    //  UI toolkit
    // =====================================================================
    int dp(float v) { return (int) (v * getResources().getDisplayMetrics().density + 0.5f); }

    GradientDrawable shape(int fill, int stroke, float radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill); g.setCornerRadius(dp(radiusDp));
        if (stroke != 0) g.setStroke(dp(1), stroke);
        return g;
    }
    LinearLayout column() { LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); return l; }
    LinearLayout row() { LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.HORIZONTAL); l.setGravity(Gravity.CENTER_VERTICAL); return l; }
    LinearLayout.LayoutParams lp(int w, int h, float l, float t, float r, float b) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w, h);
        p.setMargins(dp(l), dp(t), dp(r), dp(b)); return p;
    }
    LinearLayout.LayoutParams half() { LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, -2, 1); p.setMargins(dp(3), 0, dp(3), 0); return p; }
    void add(ViewGroup parent, View v) { parent.addView(v, lp(-1, -2, 0, 0, 0, 10)); }

    LinearLayout card() {
        LinearLayout l = column();
        l.setBackground(shape(SURFACE, STROKE, 18));
        l.setPadding(dp(14), dp(12), dp(14), dp(12));
        return l;
    }
    TextView tv(String s, int sp, int color, boolean bold) {
        TextView t = new TextView(this); t.setText(s); t.setTextSize(sp); t.setTextColor(color);
        if (bold) t.setTypeface(null, Typeface.BOLD);
        t.setPadding(dp(2), dp(3), dp(2), dp(3)); return t;
    }
    TextView section(String s) {
        TextView t = tv(s.toUpperCase(Locale.ROOT), 11, ACCENT, true);
        t.setLetterSpacing(0.15f); t.setPadding(dp(4), dp(10), dp(4), dp(6)); return t;
    }
    Button primary(String label, Runnable a) {
        Button b = new Button(this); b.setText(label); b.setAllCaps(false);
        b.setTextColor(0xFF03120A); b.setTypeface(null, Typeface.BOLD);
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{ACCENT, ACCENT2});
        g.setCornerRadius(dp(14)); b.setBackground(g); b.setStateListAnimator(null);
        b.setMinHeight(0); b.setMinimumHeight(0); b.setPadding(dp(16), dp(11), dp(16), dp(11));
        b.setOnClickListener(v -> a.run()); return b;
    }
    Button ghost(String label, Runnable a) {
        Button b = new Button(this); b.setText(label); b.setAllCaps(false);
        b.setTextColor(ACCENT); b.setBackground(shape(0x1A2BFF88, 0x552BFF88, 14)); b.setStateListAnimator(null);
        b.setMinHeight(0); b.setMinimumHeight(0); b.setPadding(dp(14), dp(10), dp(14), dp(10));
        b.setOnClickListener(v -> a.run()); return b;
    }
    EditText input(String hint) {
        EditText e = new EditText(this); e.setHint(hint); e.setHintTextColor(MUTED); e.setTextColor(TEXT);
        e.setTextSize(15); e.setBackground(shape(SURFACE2, STROKE, 14));
        e.setPadding(dp(14), dp(11), dp(14), dp(11));
        e.setLayoutParams(lp(-1, -2, 0, 4, 0, 4)); return e;
    }
    TextView chip(String s, int color) {
        TextView t = tv(s, 12, color, true);
        t.setBackground(shape((color & 0x00FFFFFF) | 0x22000000, (color & 0x00FFFFFF) | 0x66000000, 20));
        t.setPadding(dp(11), dp(5), dp(11), dp(5)); return t;
    }
    TextView avatar(String name) {
        String n = name == null ? "" : name.trim();
        String ch = "#";
        for (int i = 0; i < n.length(); i++) if (Character.isLetterOrDigit(n.charAt(i))) { ch = ("" + n.charAt(i)).toUpperCase(Locale.ROOT); break; }
        TextView t = new TextView(this); t.setText(ch); t.setTextColor(ACCENT); t.setTextSize(17);
        t.setTypeface(null, Typeface.BOLD); t.setGravity(Gravity.CENTER);
        GradientDrawable g = new GradientDrawable(); g.setShape(GradientDrawable.OVAL);
        g.setColor(SURFACE2); g.setStroke(dp(1), 0x772BFF88); t.setBackground(g);
        t.setLayoutParams(lp(dp(44), dp(44), 0, 0, 12, 0)); return t;
    }
    View divider() { View v = new View(this); v.setBackgroundColor(STROKE); v.setLayoutParams(lp(-1, 1, 58, 0, 0, 0)); return v; }
    View scrollList(LinearLayout inner) {
        ScrollView sv = new ScrollView(this); sv.addView(inner);
        sv.setLayoutParams(lp(-1, dp(260), 0, 8, 0, 4)); return sv;
    }
    LinearLayout stepCard(String num, String title) {
        LinearLayout c = card();
        LinearLayout h = row();
        TextView n = tv(num, 13, 0xFF03120A, true); n.setGravity(Gravity.CENTER);
        n.setBackground(shape(ACCENT, 0, 20)); n.setLayoutParams(lp(dp(26), dp(26), 0, 0, 10, 0));
        h.addView(n); h.addView(tv(title, 17, TEXT, true));
        c.addView(h, lp(-1, -2, 0, 0, 0, 6));
        add(content, c); return c;
    }
    void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_LONG).show(); }

    Dialog dialog(String title, View body, String ok, Runnable onOk) { return dialog(title, body, ok, onOk, "Close"); }
    Dialog dialog(String title, View body, String ok, Runnable onOk, String closeLabel) {
        final Dialog d = new Dialog(this);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout box = card(); box.setPadding(dp(18), dp(16), dp(18), dp(12));
        box.addView(tv(title, 18, TEXT, true));
        if (body != null) box.addView(body);
        LinearLayout actions = row(); actions.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        if (closeLabel != null) actions.addView(ghost(closeLabel, d::dismiss), lp(-2, -2, 0, 0, 8, 0));
        if (ok != null) actions.addView(primary(ok, () -> { d.dismiss(); if (onOk != null) onOk.run(); }));
        box.addView(actions, lp(-1, -2, 0, 12, 0, 0));
        d.setContentView(box);
        Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new InsetDrawable(new ColorDrawable(Color.TRANSPARENT), dp(14)));
            w.setLayout(-1, -2);
            w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
        d.show(); return d;
    }
    void info(String title, String msg) {
        TextView t = tv(msg, 14, MUTED, false); t.setPadding(dp(2), dp(10), dp(2), dp(4));
        dialog(title, t, null, null, "OK");
    }

    // =====================================================================
    //  Screen scaffold: header + body + FAB + bottom nav
    // =====================================================================
    void setup(String title, String subtitle) {
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        root = column(); root.setBackgroundColor(BG);
        root.setOnApplyWindowInsetsListener((v, i) -> {
            v.setPadding(i.getSystemWindowInsetLeft(), i.getSystemWindowInsetTop(), i.getSystemWindowInsetRight(), i.getSystemWindowInsetBottom());
            return i;
        });
        setContentView(root);

        LinearLayout header = row();
        header.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{0xFF06110C, 0xFF0B2A1D}));
        header.setPadding(dp(18), dp(14), dp(8), dp(14));
        LinearLayout titles = column();
        TextView heading = tv(title, 24, TEXT, true); heading.setLetterSpacing(0.04f);
        titles.addView(heading);
        if (subtitle != null) titles.addView(tv(subtitle, 12, MUTED, false));
        header.addView(titles, new LinearLayout.LayoutParams(0, -2, 1));
        TextView menu = tv("⋮", 26, ACCENT, true); menu.setPadding(dp(14), dp(4), dp(14), dp(4));
        menu.setOnClickListener(v -> showMenu(menu));
        header.addView(menu);
        root.addView(header);
        View glow = new View(this);
        glow.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{ACCENT, ACCENT2, 0x00000000}));
        root.addView(glow, new LinearLayout.LayoutParams(-1, dp(2)));

        FrameLayout body = new FrameLayout(this);
        ScrollView sc = new ScrollView(this); sc.setFillViewport(false);
        content = column(); content.setPadding(dp(14), dp(12), dp(14), dp(90));
        sc.addView(content);
        body.addView(sc, new FrameLayout.LayoutParams(-1, -1));
        if ("Messages".equals(currentTab)) {
            TextView fab = new TextView(this); fab.setText("✎"); fab.setTextSize(24); fab.setGravity(Gravity.CENTER);
            fab.setTextColor(0xFF03120A);
            GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{ACCENT, ACCENT2});
            g.setShape(GradientDrawable.OVAL); fab.setBackground(g); fab.setElevation(dp(8));
            fab.setOnClickListener(v -> composeNormal(""));
            FrameLayout.LayoutParams fp = new FrameLayout.LayoutParams(dp(58), dp(58), Gravity.END | Gravity.BOTTOM);
            fp.setMargins(0, 0, dp(18), dp(18));
            body.addView(fab, fp);
        }
        root.addView(body, new LinearLayout.LayoutParams(-1, 0, 1));

        View line = new View(this); line.setBackgroundColor(STROKE);
        root.addView(line, new LinearLayout.LayoutParams(-1, dp(1)));
        LinearLayout nav = row(); nav.setBackgroundColor(SURFACE);
        nav.addView(navItem("✉", "Messages", "Messages", () -> showMessages()), new LinearLayout.LayoutParams(0, -2, 1));
        nav.addView(navItem("⚡", "Bulk", "Bulk SMS", () -> showBulk()), new LinearLayout.LayoutParams(0, -2, 1));
        nav.addView(navItem("☺", "Contacts", "Contacts", () -> showContacts()), new LinearLayout.LayoutParams(0, -2, 1));
        nav.addView(navItem("⚙", "Settings", "Settings", () -> showSettings()), new LinearLayout.LayoutParams(0, -2, 1));
        root.addView(nav);
    }

    View navItem(String icon, String label, String tab, Runnable action) {
        boolean on = tab.equals(currentTab);
        LinearLayout l = column(); l.setGravity(Gravity.CENTER_HORIZONTAL); l.setPadding(0, dp(6), 0, dp(8));
        View bar = new View(this); bar.setBackground(shape(on ? ACCENT : Color.TRANSPARENT, 0, 2));
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(dp(26), dp(3)); bp.gravity = Gravity.CENTER_HORIZONTAL; bp.bottomMargin = dp(4);
        l.addView(bar, bp);
        TextView i = tv(icon, 20, on ? ACCENT : MUTED, false); i.setGravity(Gravity.CENTER); l.addView(i);
        TextView t = tv(label, 11, on ? ACCENT : MUTED, on); t.setGravity(Gravity.CENTER); l.addView(t);
        l.setOnClickListener(v -> { currentTab = tab; action.run(); });
        return l;
    }

    void showMenu(View anchor) {
        PopupMenu p = new PopupMenu(this, anchor);
        p.getMenu().add("Campaign history").setOnMenuItemClickListener(i -> { showHistory(); return true; });
        p.getMenu().add("Export last campaign").setOnMenuItemClickListener(i -> { exportLast(); return true; });
        p.getMenu().add("Permissions").setOnMenuItemClickListener(i -> { requestNeededPermissions(); return true; });
        p.getMenu().add("Help & Support").setOnMenuItemClickListener(i -> {
            info("Help & Support", "Messages: normal SMS.\nBulk: import a CSV, tap a field chip to insert it, preview, then send or schedule.\nLong-press a field chip to remove it."); return true; });
        p.getMenu().add("About").setOnMenuItemClickListener(i -> { info("About MergeSMS", "MergeSMS 2.0\nYour standalone SMS app."); return true; });
        p.show();
    }

    void showHome() {
        if ("Messages".equals(currentTab)) showMessages();
        else if ("Contacts".equals(currentTab)) showContacts();
        else if ("Bulk SMS".equals(currentTab)) showBulk();
        else showSettings();
    }

    // =====================================================================
    //  Contacts helpers
    // =====================================================================
    String key(String num) {
        String d = num == null ? "" : num.replaceAll("[^0-9]", "");
        return d.length() > 9 ? d.substring(d.length() - 9) : d;
    }
    void loadContacts() {
        if (contactCache != null) return;
        contactCache = new ArrayList<>(); nameMap.clear();
        if (!has(Manifest.permission.READ_CONTACTS)) return;
        Cursor c = null;
        try {
            c = getContentResolver().query(Phone.CONTENT_URI, new String[]{Phone.DISPLAY_NAME, Phone.NUMBER}, null, null, Phone.DISPLAY_NAME + " ASC");
            while (c != null && c.moveToNext()) {
                String n = c.getString(0), p = c.getString(1);
                if (p == null) continue;
                contactCache.add(new String[]{n == null ? p : n, p});
                if (n != null) nameMap.put(key(p), n);
            }
        } catch (Exception ignored) { } finally { if (c != null) c.close(); }
    }
    String nameFor(String address) {
        loadContacts();
        String n = nameMap.get(key(address));
        return n == null ? address : n;
    }
    String fmtTime(long t) {
        Calendar a = Calendar.getInstance(), b = Calendar.getInstance(); b.setTimeInMillis(t);
        boolean today = a.get(Calendar.YEAR) == b.get(Calendar.YEAR) && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR);
        return new SimpleDateFormat(today ? "h:mm a" : "d MMM", Locale.getDefault()).format(new Date(t));
    }
    boolean addRecipient(String name, String phone) {
        String k = key(phone);
        for (Recipient r : recipients) if (key(r.phone).equals(k)) return false;
        recipients.add(new Recipient(name == null ? "" : name, phone)); return true;
    }

    // =====================================================================
    //  MESSAGES
    // =====================================================================
    void showMessages() {
        setup("MergeSMS", "Messaging, upgraded");
        LinearLayout qa = row();
        qa.addView(primary("＋  New message", () -> composeNormal("")), half());
        qa.addView(ghost("◷  Scheduled", () -> showScheduled()), half());
        add(content, qa);

        if (!has(Manifest.permission.READ_SMS)) {
            LinearLayout c = card();
            c.addView(tv("SMS access needed", 16, TEXT, true));
            c.addView(tv("Allow SMS permission so MergeSMS can show messages stored on this phone.", 13, MUTED, false));
            c.addView(primary("Grant SMS permission", () -> requestNeededPermissions()), lp(-2, -2, 0, 10, 0, 0));
            add(content, c); return;
        }
        loadConvos();
        int sched = 0;
        try { sched = new JSONArray(prefs.getString("scheduled", "[]")).length(); } catch (Exception ignored) { }
        int totalUnread = 0; for (Convo cv : convos) totalUnread += cv.unread;
        LinearLayout stats = row();
        stats.addView(statCard(String.valueOf(convos.size()), "Chats"), half());
        stats.addView(statCard(String.valueOf(totalUnread), "Unread"), half());
        stats.addView(statCard(String.valueOf(sched), "Scheduled"), half());
        add(content, stats);

        EditText search = input("⌕  Search messages or contacts");
        content.addView(search);
        listBox = column(); content.addView(listBox);
        renderConvos("");
        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            public void onTextChanged(CharSequence s, int a, int b, int c) { renderConvos(s.toString()); }
            public void afterTextChanged(Editable e) { }
        });
    }

    View statCard(String value, String label) {
        LinearLayout c = column(); c.setGravity(Gravity.CENTER);
        c.setBackground(shape(SURFACE, STROKE, 16)); c.setPadding(dp(8), dp(10), dp(8), dp(10));
        TextView v = tv(value, 22, ACCENT, true); v.setGravity(Gravity.CENTER);
        TextView l = tv(label, 11, MUTED, false); l.setGravity(Gravity.CENTER);
        c.addView(v); c.addView(l); return c;
    }

    void loadConvos() {
        convos.clear();
        LinkedHashMap<String, Convo> map = new LinkedHashMap<>();
        Cursor c = null;
        try {
            c = getContentResolver().query(Uri.parse("content://sms"), new String[]{"address", "body", "date", "type", "read"}, null, null, "date DESC");
            int rows = 0;
            while (c != null && c.moveToNext() && rows++ < 3000) {
                String a = c.getString(0);
                if (a == null || a.trim().isEmpty()) a = "Unknown sender";
                Convo cv = map.get(a);
                if (cv == null) {
                    cv = new Convo(); cv.address = a; cv.date = c.getLong(2);
                    String body = c.getString(1);
                    cv.preview = (c.getInt(3) == 2 ? "You: " : "") + (body == null ? "" : body.replace('\n', ' '));
                    map.put(a, cv);
                }
                cv.count++;
                if (c.getInt(3) == 1 && c.getInt(4) == 0) cv.unread++;
            }
        } catch (Exception e) {
            toast("Could not load messages: " + e.getMessage());
        } finally { if (c != null) c.close(); }
        convos.addAll(map.values());
    }

    void renderConvos(String q) {
        listBox.removeAllViews();
        String ql = q.trim().toLowerCase(Locale.ROOT);
        int shown = 0;
        for (Convo cv : convos) {
            String name = nameFor(cv.address);
            if (!ql.isEmpty() && !name.toLowerCase(Locale.ROOT).contains(ql) && !cv.address.contains(ql)
                    && !cv.preview.toLowerCase(Locale.ROOT).contains(ql)) continue;
            if (shown++ >= 100) break;
            LinearLayout item = row(); item.setPadding(dp(4), dp(10), dp(4), dp(10));
            item.addView(avatar(name));
            LinearLayout mid = column();
            mid.addView(tv(name, 15, TEXT, cv.unread > 0));
            TextView pv = tv(cv.preview, 13, cv.unread > 0 ? TEXT : MUTED, false);
            pv.setMaxLines(1); pv.setEllipsize(TextUtils.TruncateAt.END); mid.addView(pv);
            item.addView(mid, new LinearLayout.LayoutParams(0, -2, 1));
            LinearLayout right = column(); right.setGravity(Gravity.END);
            right.addView(tv(fmtTime(cv.date), 11, MUTED, false));
            if (cv.unread > 0) right.addView(chip(String.valueOf(cv.unread), ACCENT));
            item.addView(right);
            final String addr = cv.address;
            item.setOnClickListener(v -> openConversation(addr));
            listBox.addView(item); listBox.addView(divider());
        }
        if (shown == 0) {
            TextView t = tv(convos.isEmpty() ? "No SMS messages found. Set MergeSMS as default SMS app in Settings if your messages appear elsewhere." : "No results.", 13, MUTED, false);
            t.setPadding(dp(4), dp(16), dp(4), dp(16)); listBox.addView(t);
        }
    }

    void composeNormal(String prefill) {
        final EditText phone = input("Recipient phone number");
        phone.setInputType(InputType.TYPE_CLASS_PHONE); phone.setText(prefill);
        final EditText body = input("Write a message"); body.setMinLines(3); body.setGravity(Gravity.TOP);
        LinearLayout box = column(); box.setPadding(0, dp(8), 0, 0);
        box.addView(phone); box.addView(body);
        box.addView(ghost("Save draft", () -> saveDraft("normal:" + phone.getText(), body.getText().toString())), lp(-2, -2, 0, 4, 0, 0));
        dialog("New message", box, "Send", () -> sendOne(phone.getText().toString(), body.getText().toString()), "Cancel");
    }

    void openConversation(String address) {
        LinearLayout thread = column(); thread.setPadding(dp(2), dp(2), dp(2), dp(2));
        final ScrollView history = new ScrollView(this); history.addView(thread);
        Cursor c = null;
        try {
            c = getContentResolver().query(Uri.parse("content://sms"), new String[]{"body", "date", "type"},
                    "address=?", new String[]{address}, "date ASC");
            while (c != null && c.moveToNext()) {
                String body = c.getString(0); long date = c.getLong(1); boolean mine = c.getInt(2) != 1;
                LinearLayout wrap = column(); wrap.setGravity(mine ? Gravity.END : Gravity.START);
                TextView b = tv(body == null ? "" : body, 14, mine ? 0xFF03120A : TEXT, false);
                b.setMaxWidth(dp(250)); b.setPadding(dp(12), dp(8), dp(12), dp(8));
                b.setBackground(mine ? new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{ACCENT, ACCENT2}) : shape(SURFACE2, STROKE, 16));
                if (mine) ((GradientDrawable) b.getBackground()).setCornerRadius(dp(16));
                wrap.addView(b);
                wrap.addView(tv(fmtTime(date), 10, MUTED, false));
                thread.addView(wrap, lp(-1, -2, 0, 3, 0, 3));
            }
        } catch (Exception e) {
            thread.addView(tv("Could not load this conversation: " + e.getMessage(), 13, DANGER, false));
        } finally { if (c != null) c.close(); }
        history.post(() -> history.fullScroll(View.FOCUS_DOWN));

        final EditText reply = input("Reply to " + nameFor(address)); reply.setMinLines(2);
        reply.setText(prefs.getString("draft:normal:" + address, ""));
        LinearLayout box = column(); box.setPadding(0, dp(8), 0, 0);
        box.addView(history, new LinearLayout.LayoutParams(-1, dp(300)));
        box.addView(reply);
        box.addView(ghost("Save draft", () -> saveDraft("normal:" + address, reply.getText().toString())), lp(-2, -2, 0, 4, 0, 0));
        dialog(nameFor(address), box, "Send", () -> sendOne(address, reply.getText().toString()));
    }

    void saveDraft(String key, String val) { prefs.edit().putString("draft:" + key, val).apply(); toast("Draft saved."); }

    void sendOne(String phone, String body) {
        if (phone.trim().isEmpty() || body.trim().isEmpty()) { toast("Enter a phone number and message."); return; }
        if (!has(Manifest.permission.SEND_SMS)) { requestNeededPermissions(); return; }
        try {
            SmsManager sm = sms();
            sm.sendMultipartTextMessage(phone.trim(), null, sm.divideMessage(body), null, null);
            prefs.edit().remove("draft:normal:" + phone.trim()).apply();
            toast("SMS submitted to Android's SMS service.");
        } catch (Exception e) { toast("Send failed: " + e.getMessage()); }
    }

    // =====================================================================
    //  CONTACTS
    // =====================================================================
    void showContacts() {
        setup("Contacts", "Tap to message  •  ＋ adds to campaign");
        add(content, primary("＋  Add recipient manually", () -> addRecipientDialog(() -> showContacts())));
        if (!has(Manifest.permission.READ_CONTACTS)) {
            LinearLayout c = card();
            c.addView(tv("Contacts access needed", 16, TEXT, true));
            c.addView(primary("Grant permission", () -> requestNeededPermissions()), lp(-2, -2, 0, 10, 0, 0));
            add(content, c); return;
        }
        loadContacts();
        EditText search = input("⌕  Search contacts");
        content.addView(search);
        listBox = column(); content.addView(listBox);
        renderContacts("");
        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            public void onTextChanged(CharSequence s, int a, int b, int c) { renderContacts(s.toString()); }
            public void afterTextChanged(Editable e) { }
        });
    }

    void renderContacts(String q) {
        listBox.removeAllViews();
        String ql = q.trim().toLowerCase(Locale.ROOT); int shown = 0;
        for (String[] ct : contactCache) {
            if (!ql.isEmpty() && !ct[0].toLowerCase(Locale.ROOT).contains(ql) && !ct[1].contains(ql)) continue;
            if (shown++ >= 300) break;
            LinearLayout item = row(); item.setPadding(dp(4), dp(9), dp(4), dp(9));
            item.addView(avatar(ct[0]));
            LinearLayout mid = column();
            mid.addView(tv(ct[0], 15, TEXT, true)); mid.addView(tv(ct[1], 13, MUTED, false));
            item.addView(mid, new LinearLayout.LayoutParams(0, -2, 1));
            TextView plus = chip("＋ Campaign", ACCENT);
            plus.setOnClickListener(v -> toast(addRecipient(ct[0], ct[1]) ? ct[0] + " added to campaign." : "Already in campaign."));
            item.addView(plus);
            item.setOnClickListener(v -> composeNormal(ct[1]));
            listBox.addView(item); listBox.addView(divider());
        }
        if (shown == 0) { TextView t = tv("No contacts found.", 13, MUTED, false); t.setPadding(dp(4), dp(16), dp(4), dp(16)); listBox.addView(t); }
    }

    void addRecipientDialog(Runnable after) {
        EditText n = input("Name"), p = input("Phone number"); p.setInputType(InputType.TYPE_CLASS_PHONE);
        LinearLayout l = column(); l.setPadding(0, dp(8), 0, 0); l.addView(n); l.addView(p);
        dialog("Add recipient", l, "Add", () -> {
            if (p.getText().toString().trim().isEmpty()) { toast("Phone number required."); return; }
            if (!addRecipient(n.getText().toString().trim(), p.getText().toString().trim())) toast("Already in list.");
            after.run();
        }, "Cancel");
    }

    // =====================================================================
    //  BULK SMS
    // =====================================================================
    void showBulk() {
        setup("Bulk SMS", "Personalized campaigns from your SIM");
        int sel = 0; for (Recipient r : recipients) if (r.selected) sel++;
        LinearLayout stats = row();
        stats.addView(statCard(String.valueOf(recipients.size()), "Recipients"), half());
        stats.addView(statCard(String.valueOf(sel), "Selected"), half());
        stats.addView(statCard(String.valueOf(fields.size()), "Fields"), half());
        add(content, stats);

        // ---- 1. Recipients ----
        LinearLayout c1 = stepCard("1", "Recipients");
        LinearLayout r1 = row();
        r1.addView(primary("▤  Import CSV", () -> csvLauncher.launch("*/*")), half());
        r1.addView(ghost("＋  Add", () -> addRecipientDialog(() -> showBulk())), half());
        c1.addView(r1, lp(-1, -2, 0, 4, 0, 6));
        LinearLayout r2 = row();
        r2.addView(ghost("Select all", () -> { for (Recipient r : recipients) r.selected = true; showBulk(); }), half());
        r2.addView(ghost("None", () -> { for (Recipient r : recipients) r.selected = false; showBulk(); }), half());
        r2.addView(ghost("Clear", () -> {
            recipients.clear(); showBulk(); }), half());
        c1.addView(r2, lp(-1, -2, 0, 0, 0, 6));
        boolean open = prefs.getBoolean("recipientsExpanded", true);
        c1.addView(ghost((open ? "▾ Hide" : "▸ Show") + " recipient list (" + recipients.size() + ")",
                () -> { prefs.edit().putBoolean("recipientsExpanded", !open).apply(); showBulk(); }));
        if (open) {
            int n = 0;
            for (Recipient r : recipients) {
                if (n++ >= 300) { c1.addView(tv("+" + (recipients.size() - 300) + " more (included in send)", 12, MUTED, false)); break; }
                LinearLayout line = row();
                CheckBox cb = new CheckBox(this);
                cb.setText((r.name.isEmpty() ? r.phone : r.name + "  •  " + r.phone));
                cb.setTextColor(TEXT); cb.setTextSize(13); cb.setButtonTintList(ColorStateList.valueOf(ACCENT));
                cb.setChecked(r.selected); cb.setOnCheckedChangeListener((v, b) -> r.selected = b);
                line.addView(cb, new LinearLayout.LayoutParams(0, -2, 1));
                int sc = "Submitted".equals(r.status) ? ACCENT : "Failed".equals(r.status) ? DANGER : MUTED;
                line.addView(chip(r.status, sc));
                c1.addView(line);
            }
        }

        // ---- 2. Merge fields ----
        LinearLayout c2 = stepCard("2", "Merge fields");
        c2.addView(tv("Tap a field to insert it into your message. Long-press to remove (data is kept).", 12, MUTED, false));
        HorizontalScrollView hs = new HorizontalScrollView(this); hs.setHorizontalScrollBarEnabled(false);
        LinearLayout chips = row();
        for (MergeField f : new ArrayList<>(fields)) {
            TextView ch = chip("{" + f.token + "}", ACCENT2);
            ch.setOnClickListener(v -> insertToken("{" + f.token + "}"));
            ch.setOnLongClickListener(v -> { fields.remove(f); showBulk(); return true; });
            chips.addView(ch, lp(-2, -2, 0, 0, 8, 0));
        }
        if (fields.isEmpty()) chips.addView(tv("No fields yet — import a CSV.", 13, MUTED, false));
        hs.addView(chips); c2.addView(hs, lp(-1, -2, 0, 8, 0, 8));
        c2.addView(ghost("＋  Add merge field", () -> addField()));

        // ---- 3. Message ----
        LinearLayout c3 = stepCard("3", "Message");
        LinearLayout tr = row();
        tr.addView(ghost("💾 Save template", () -> saveTemplate()), half());
        tr.addView(ghost("📂 Templates", () -> loadTemplate()), half());
        c3.addView(tr, lp(-1, -2, 0, 4, 0, 4));
        composer = input("Write your message. Use fields like {Name} or {Balance}.");
        composer.setMinLines(4); composer.setGravity(Gravity.TOP);
        composer.setText(prefs.getString("bulkDraft", ""));
        c3.addView(composer);
        final TextView counter = tv("", 12, MUTED, false);
        final TextView preview = tv("", 13, TEXT, false);
        preview.setBackground(shape(SURFACE2, 0x552BFF88, 14)); preview.setPadding(dp(12), dp(10), dp(12), dp(10));
        c3.addView(counter); c3.addView(preview, lp(-1, -2, 0, 4, 0, 0));
        Runnable refresh = () -> {
            String t = composer.getText().toString();
            Recipient first = null; int selCount = 0;
            for (Recipient r : recipients) if (r.selected) { if (first == null) first = r; selCount++; }
            String sample = first == null ? t : personalize(first, t);
            int seg = t.isEmpty() ? 0 : sms().divideMessage(sample).size();
            counter.setText(t.length() + " / 500 chars  •  " + seg + " segment" + (seg == 1 ? "" : "s")
                    + (selCount > 0 ? "  •  ≈" + (seg * selCount) + " SMS total" : ""));
            counter.setTextColor(t.length() > 500 ? DANGER : MUTED);
            preview.setText(first == null ? "Live preview appears once you add recipients." :
                    "PREVIEW · " + (first.name.isEmpty() ? first.phone : first.name) + "\n" + (t.isEmpty() ? "…" : sample));
        };
        composer.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            public void onTextChanged(CharSequence s, int a, int b, int c) {
                prefs.edit().putString("bulkDraft", s.toString()).apply(); refresh.run();
            }
            public void afterTextChanged(Editable e) { }
        });
        refresh.run();

        LinearLayout act = row();
        act.addView(ghost("◷  Schedule", () -> scheduleBulk()), half());
        act.addView(primary("➤  Send now", () -> sendBulk()), half());
        content.addView(act, lp(-1, -2, 0, 4, 0, 0));
    }

    void insertToken(String tok) {
        if (composer == null) return;
        int s = Math.max(composer.getSelectionStart(), 0), e = Math.max(composer.getSelectionEnd(), 0);
        composer.getText().replace(Math.min(s, e), Math.max(s, e), tok);
    }

    void addField() {
        if (csvHeaders.isEmpty()) { info("Merge fields", "Import a CSV file first."); return; }
        final Dialog[] ref = new Dialog[1];
        LinearLayout list = column();
        for (String h : csvHeaders) {
            TextView item = tv(h, 15, TEXT, false); item.setPadding(dp(8), dp(12), dp(8), dp(12));
            item.setOnClickListener(v -> {
                ref[0].dismiss();
                EditText token = input("Short field name, e.g. Balance");
                token.setText(h.replaceAll("[^A-Za-z0-9_]", ""));
                LinearLayout l = column(); l.setPadding(0, dp(8), 0, 0); l.addView(token);
                dialog("Name for “" + h + "”", l, "Add", () -> {
                    String t = token.getText().toString().trim().replaceAll("[^A-Za-z0-9_]", "");
                    if (t.isEmpty()) t = h.replaceAll("[^A-Za-z0-9_]", "");
                    fields.add(new MergeField(t, h)); showBulk();
                }, "Cancel");
            });
            list.addView(item); list.addView(divider());
        }
        ref[0] = dialog("Choose a column", scrollList(list), null, null, "Cancel");
    }

    void saveTemplate() {
        String t = composer == null ? "" : composer.getText().toString();
        if (t.trim().isEmpty()) { toast("Write a message first."); return; }
        EditText name = input("Template name");
        LinearLayout l = column(); l.setPadding(0, dp(8), 0, 0); l.addView(name);
        dialog("Save template", l, "Save", () -> {
            try {
                JSONObject o = new JSONObject(prefs.getString("templates", "{}"));
                o.put(name.getText().toString().trim().isEmpty() ? "Template " + (o.length() + 1) : name.getText().toString().trim(), t);
                prefs.edit().putString("templates", o.toString()).apply(); toast("Template saved.");
            } catch (Exception e) { toast("Could not save: " + e.getMessage()); }
        }, "Cancel");
    }

    void loadTemplate() {
        try {
            final JSONObject o = new JSONObject(prefs.getString("templates", "{}"));
            if (o.length() == 0) { info("Templates", "No templates yet. Write a message and tap “Save template”."); return; }
            final Dialog[] ref = new Dialog[1];
            LinearLayout list = column();
            Iterator<String> it = o.keys();
            while (it.hasNext()) {
                final String k = it.next();
                LinearLayout item = column(); item.setPadding(dp(8), dp(10), dp(8), dp(10));
                item.addView(tv(k, 15, ACCENT, true));
                TextView pv = tv(o.optString(k), 12, MUTED, false); pv.setMaxLines(2); pv.setEllipsize(TextUtils.TruncateAt.END);
                item.addView(pv);
                item.setOnClickListener(v -> { composer.setText(o.optString(k)); ref[0].dismiss(); });
                item.setOnLongClickListener(v -> {
                    o.remove(k); prefs.edit().putString("templates", o.toString()).apply();
                    ref[0].dismiss(); toast("Template deleted."); return true; });
                list.addView(item); list.addView(divider());
            }
            ref[0] = dialog("Templates  (long-press to delete)", scrollList(list), null, null, "Close");
        } catch (Exception e) { toast("Could not load templates."); }
    }

    // ---- CSV ----
    void readCsv(Uri uri) {
        try (BufferedReader br = new BufferedReader(new InputStreamReader(getContentResolver().openInputStream(uri)))) {
            String line = br.readLine();
            if (line == null) return;
            line = line.replace("\uFEFF", "");
            csvHeaders.clear(); csvRows.clear(); csvHeaders.addAll(parseCsv(line));
            int phoneCol = -1, nameCol = -1;
            for (int i = 0; i < csvHeaders.size(); i++) {
                String h = csvHeaders.get(i).toLowerCase(Locale.ROOT);
                if (phoneCol < 0 && (h.contains("phone") || h.contains("mobile") || h.contains("number"))) phoneCol = i;
                if (nameCol < 0 && h.contains("name")) nameCol = i;
            }
            boolean dedupe = prefs.getBoolean("dedupe", true);
            HashSet<String> seen = new HashSet<>();
            for (Recipient r : recipients) seen.add(key(r.phone));
            int duplicates = 0, added = 0; String row;
            while ((row = br.readLine()) != null && recipients.size() < 1000) {
                if (row.trim().isEmpty()) continue;
                List<String> vals = parseCsv(row);
                Map<String, String> map = new LinkedHashMap<>();
                for (int i = 0; i < csvHeaders.size(); i++) map.put(csvHeaders.get(i), i < vals.size() ? vals.get(i) : "");
                csvRows.add(map);
                String phone = phoneCol >= 0 && phoneCol < vals.size() ? vals.get(phoneCol).trim() : "";
                String name = nameCol >= 0 && nameCol < vals.size() ? vals.get(nameCol).trim() : "";
                if (phone.isEmpty()) continue;
                if (dedupe && !seen.add(key(phone))) { duplicates++; continue; }
                Recipient r = new Recipient(name, phone); r.data.putAll(map); recipients.add(r); added++;
            }
            // every column becomes a merge field
            fields.clear(); HashSet<String> used = new HashSet<>();
            for (int i = 0; i < csvHeaders.size(); i++) {
                String h = csvHeaders.get(i);
                String tok = i == phoneCol ? "Phonenumber" : i == nameCol ? "Name" : h.replaceAll("[^A-Za-z0-9_]", "");
                if (tok.isEmpty()) tok = "Col" + (i + 1);
                String base = tok; int n = 2; while (!used.add(tok)) tok = base + n++;
                fields.add(new MergeField(tok, h));
            }
            currentTab = "Bulk SMS"; showBulk();
            info("CSV imported", "Added: " + added + "\nTotal recipients: " + recipients.size()
                    + "\nDuplicates skipped: " + duplicates + "\nColumns detected: " + csvHeaders.size());
        } catch (Exception e) { info("Could not read CSV", String.valueOf(e.getMessage())); }
    }

    List<String> parseCsv(String line) {
        ArrayList<String> out = new ArrayList<>(); StringBuilder b = new StringBuilder(); boolean q = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') { if (q && i + 1 < line.length() && line.charAt(i + 1) == '"') { b.append('"'); i++; } else q = !q; }
            else if (c == ',' && !q) { out.add(b.toString().trim()); b.setLength(0); }
            else b.append(c);
        }
        out.add(b.toString().trim()); return out;
    }

    String personalize(Recipient r, String template) {
        String s = template;
        for (MergeField f : fields) s = s.replace("{" + f.token + "}", r.data.getOrDefault(f.column, ""));
        s = s.replace("{Name}", r.name); s = s.replace("{Phonenumber}", r.phone);
        return s;
    }

    // ---- Send ----
    void sendBulk() {
        final String msg = composer == null ? "" : composer.getText().toString();
        if (msg.trim().isEmpty()) { toast("Write a message first."); return; }
        if (!has(Manifest.permission.SEND_SMS)) { requestNeededPermissions(); return; }
        final ArrayList<Recipient> chosen = new ArrayList<>();
        for (Recipient r : recipients) if (r.selected) chosen.add(r);
        if (chosen.isEmpty()) { toast("Select at least one recipient."); return; }
        if (!prefs.getBoolean("confirmBulk", true)) { runSend(chosen, msg); return; }
        String note = "Messages go through your SIM and may incur carrier charges."
                + (chosen.size() > 30 ? "\n\nAndroid may ask you to approve sending more than 30 messages in a short time." : "");
        TextView t = tv(note, 14, MUTED, false); t.setPadding(dp(2), dp(10), dp(2), dp(4));
        dialog("Send " + chosen.size() + " messages?", t, "Send", () -> runSend(chosen, msg), "Cancel");
    }

    void runSend(final ArrayList<Recipient> chosen, final String msg) {
        final ProgressBar pb = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        pb.setMax(chosen.size()); pb.setProgressTintList(ColorStateList.valueOf(ACCENT));
        final TextView status = tv("Starting…", 13, MUTED, false);
        final boolean[] stop = {false};
        LinearLayout b = column(); b.setPadding(0, dp(10), 0, 0);
        b.addView(pb); b.addView(status);
        b.addView(ghost("Stop", () -> stop[0] = true), lp(-2, -2, 0, 8, 0, 0));
        final Dialog d = dialog("Sending campaign", b, null, null, null);
        d.setCancelable(false);
        final int delay = prefs.getInt("delay", 400);
        new Thread(() -> {
            final int[] ok = {0}, done = {0};
            SmsManager sm = sms();
            for (Recipient r : chosen) {
                if (stop[0]) break;
                try {
                    sm.sendMultipartTextMessage(r.phone, null, sm.divideMessage(personalize(r, msg)), null, null);
                    r.status = "Submitted"; ok[0]++;
                } catch (Exception e) { r.status = "Failed"; }
                done[0]++;
                runOnUiThread(() -> { pb.setProgress(done[0]); status.setText(done[0] + " / " + chosen.size() + " processed • " + ok[0] + " submitted"); });
                try { Thread.sleep(delay); } catch (InterruptedException ignored) { }
            }
            runOnUiThread(() -> {
                d.dismiss();
                saveCampaign(chosen, msg); addHistory(chosen.size(), ok[0], msg);
                if (ok[0] > 0) { prefs.edit().putString("bulkDraft", "").apply(); for (Recipient s : chosen) s.selected = false; }
                toast("Submitted " + ok[0] + " of " + chosen.size() + " messages.");
                showBulk();
            });
        }).start();
    }

    void saveCampaign(ArrayList<Recipient> chosen, String template) {
        try {
            JSONArray arr = new JSONArray();
            for (Recipient r : chosen) {
                JSONObject o = new JSONObject();
                o.put("phone", r.phone); o.put("name", r.name); o.put("status", r.status); o.put("message", personalize(r, template));
                arr.put(o);
            }
            prefs.edit().putString("lastCampaign", arr.toString()).apply();
        } catch (Exception ignored) { }
    }

    void addHistory(int total, int ok, String msg) {
        try {
            JSONArray old = new JSONArray(prefs.getString("history", "[]"));
            JSONArray n = new JSONArray();
            JSONObject o = new JSONObject();
            o.put("when", System.currentTimeMillis()); o.put("total", total); o.put("ok", ok); o.put("message", msg);
            n.put(o);
            for (int i = 0; i < old.length() && i < 19; i++) n.put(old.get(i));
            prefs.edit().putString("history", n.toString()).apply();
        } catch (Exception ignored) { }
    }

    void showHistory() {
        setup("Campaign history", "Your last 20 campaigns");
        try {
            JSONArray a = new JSONArray(prefs.getString("history", "[]"));
            if (a.length() == 0) add(content, tv("No campaigns sent yet.", 14, MUTED, false));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                LinearLayout c = card();
                c.addView(tv(new SimpleDateFormat("EEE, d MMM yyyy • HH:mm", Locale.getDefault()).format(new Date(o.getLong("when"))), 12, MUTED, false));
                c.addView(chip(o.getInt("ok") + " / " + o.getInt("total") + " submitted", o.getInt("ok") == o.getInt("total") ? ACCENT : WARN));
                TextView m = tv(o.getString("message"), 13, TEXT, false); m.setMaxLines(3); m.setEllipsize(TextUtils.TruncateAt.END);
                c.addView(m, lp(-1, -2, 0, 6, 0, 0));
                add(content, c);
            }
        } catch (Exception ignored) { }
        add(content, ghost("←  Back", () -> showHome()));
    }

    void exportLast() {
        try {
            JSONArray a = new JSONArray(prefs.getString("lastCampaign", "[]"));
            if (a.length() == 0) { toast("No campaign to export yet."); return; }
            StringBuilder sb = new StringBuilder("name,phone,status,message\n");
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                sb.append(csvq(o.optString("name"))).append(',').append(csvq(o.optString("phone"))).append(',')
                        .append(csvq(o.optString("status"))).append(',').append(csvq(o.optString("message"))).append('\n');
            }
            ((ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("campaign", sb.toString()));
            toast("Last campaign copied to clipboard as CSV.");
        } catch (Exception e) { toast("Export failed: " + e.getMessage()); }
    }
    String csvq(String s) { return "\"" + s.replace("\"", "\"\"") + "\""; }

    // ---- Scheduling (storage format unchanged so ScheduleReceiver keeps working) ----
    void scheduleBulk() {
        final String msg = composer == null ? "" : composer.getText().toString();
        if (msg.trim().isEmpty()) { toast("Write a message first."); return; }
        boolean any = false; for (Recipient r : recipients) if (r.selected) any = true;
        if (!any) { toast("Select at least one recipient."); return; }
        final Calendar cal = Calendar.getInstance();
        new DatePickerDialog(this, (v, y, m, d) -> {
            cal.set(y, m, d);
            new TimePickerDialog(this, (tv, h, min) -> {
                cal.set(Calendar.HOUR_OF_DAY, h); cal.set(Calendar.MINUTE, min); cal.set(Calendar.SECOND, 0);
                if (cal.getTimeInMillis() <= System.currentTimeMillis()) { toast("Choose a future time."); return; }
                saveScheduledCampaign(msg, cal.getTimeInMillis());
            }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), true).show();
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show();
    }

    void saveScheduledCampaign(String msg, long when) {
        try {
            JSONArray a = new JSONArray(prefs.getString("scheduled", "[]"));
            JSONObject o = new JSONObject();
            o.put("id", System.currentTimeMillis()); o.put("message", msg); o.put("when", when);
            JSONArray rs = new JSONArray();
            for (Recipient r : recipients) if (r.selected) {
                JSONObject x = new JSONObject(); x.put("phone", r.phone); x.put("name", r.name); x.put("data", new JSONObject(r.data)); rs.put(x);
            }
            o.put("recipients", rs); a.put(o);
            prefs.edit().putString("scheduled", a.toString()).apply();
            Intent i = new Intent(this, ScheduleReceiver.class); i.putExtra("id", o.getLong("id"));
            PendingIntent pi = PendingIntent.getBroadcast(this, (int) (o.getLong("id") % Integer.MAX_VALUE), i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            AlarmManager am = (AlarmManager) getSystemService(ALARM_SERVICE);
            if (Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, when, pi);
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, when, pi);
            toast("Bulk campaign scheduled.");
        } catch (Exception e) { toast("Could not schedule: " + e.getMessage()); }
    }

    void showScheduled() {
        setup("Scheduled SMS", "Upcoming campaigns");
        try {
            JSONArray a = new JSONArray(prefs.getString("scheduled", "[]"));
            if (a.length() == 0) add(content, tv("Nothing scheduled.", 14, MUTED, false));
            for (int i = 0; i < a.length(); i++) {
                final JSONObject o = a.getJSONObject(i);
                LinearLayout c = card();
                c.addView(tv(new SimpleDateFormat("EEE, d MMM yyyy • HH:mm", Locale.getDefault()).format(new Date(o.getLong("when"))), 15, ACCENT, true));
                c.addView(chip(o.getJSONArray("recipients").length() + " recipients", ACCENT2));
                TextView m = tv(o.getString("message"), 13, TEXT, false); m.setMaxLines(3); m.setEllipsize(TextUtils.TruncateAt.END);
                c.addView(m, lp(-1, -2, 0, 6, 0, 6));
                c.addView(ghost("Cancel schedule", () -> { cancelSchedule(o.optLong("id")); showScheduled(); }));
                add(content, c);
            }
        } catch (Exception ignored) { }
        add(content, ghost("←  Back to Messages", () -> { currentTab = "Messages"; showHome(); }));
    }

    void cancelSchedule(long id) {
        try {
            JSONArray a = new JSONArray(prefs.getString("scheduled", "[]")), n = new JSONArray();
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                if (o.optLong("id") == id) {
                    Intent in = new Intent(this, ScheduleReceiver.class);
                    PendingIntent pi = PendingIntent.getBroadcast(this, (int) (id % Integer.MAX_VALUE), in, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                    ((AlarmManager) getSystemService(ALARM_SERVICE)).cancel(pi);
                } else n.put(o);
            }
            prefs.edit().putString("scheduled", n.toString()).apply();
        } catch (Exception ignored) { }
    }

    // =====================================================================
    //  SETTINGS
    // =====================================================================
    Switch toggle(String label, String key, boolean def) {
        Switch s = new Switch(this); s.setText(label); s.setTextColor(TEXT); s.setTextSize(14);
        s.setChecked(prefs.getBoolean(key, def)); s.setPadding(dp(2), dp(8), dp(2), dp(8));
        s.setOnCheckedChangeListener((b, on) -> prefs.edit().putBoolean(key, on).apply()); return s;
    }
    View permRow(String label, String perm) {
        LinearLayout r = row(); r.setPadding(0, dp(4), 0, dp(4));
        r.addView(tv(label, 14, TEXT, false), new LinearLayout.LayoutParams(0, -2, 1));
        r.addView(chip(has(perm) ? "Granted" : "Missing", has(perm) ? ACCENT : DANGER)); return r;
    }

    void showSettings() {
        setup("Settings", "Personalize your messaging experience");
        content.addView(section("Bulk sending"));
        LinearLayout c1 = card();
        c1.addView(toggle("Confirm before sending campaigns", "confirmBulk", true));
        c1.addView(toggle("Skip duplicate numbers on CSV import", "dedupe", true));
        int d = prefs.getInt("delay", 400);
        final Button speed = ghost("Send speed: " + (d <= 250 ? "Fast" : d <= 400 ? "Normal" : "Gentle") + "  (tap to change)", () -> { });
        speed.setOnClickListener(v -> {
            int cur = prefs.getInt("delay", 400), nx = cur <= 250 ? 400 : cur <= 400 ? 1000 : 250;
            prefs.edit().putInt("delay", nx).apply(); showSettings();
        });
        c1.addView(speed, lp(-1, -2, 0, 6, 0, 0));
        add(content, c1);

        content.addView(section("Permissions"));
        LinearLayout c2 = card();
        c2.addView(permRow("Send SMS", Manifest.permission.SEND_SMS));
        c2.addView(permRow("Read SMS", Manifest.permission.READ_SMS));
        c2.addView(permRow("Receive SMS", Manifest.permission.RECEIVE_SMS));
        c2.addView(permRow("Read contacts", Manifest.permission.READ_CONTACTS));
        c2.addView(ghost("Review permissions", () -> requestNeededPermissions()), lp(-1, -2, 0, 8, 0, 0));
        add(content, c2);

        content.addView(section("Default SMS app"));
        add(content, ghost("Make MergeSMS the default SMS app", () -> {
            Intent i = new Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT);
            i.putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, getPackageName());
            try { startActivity(i); } catch (Exception e) { toast("Open Android Settings to choose the default SMS app."); }
        }));

        content.addView(section("Data"));
        LinearLayout c3 = card();
        LinearLayout r = row();
        r.addView(ghost("Export last campaign", () -> exportLast()), half());
        r.addView(ghost("History", () -> showHistory()), half());
        c3.addView(r, lp(-1, -2, 0, 0, 0, 6));
        LinearLayout r2 = row();
        r2.addView(ghost("Clear draft", () -> { prefs.edit().putString("bulkDraft", "").apply(); toast("Draft cleared."); }), half());
        r2.addView(ghost("Clear recipients", () -> { recipients.clear(); toast("Recipients cleared."); }), half());
        c3.addView(r2);
        add(content, c3);

        content.addView(tv("MergeSMS 2.0 • Standalone Android app", 12, MUTED, false));
    }
}
