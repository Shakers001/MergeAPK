package com.mergesms.app;

import android.Manifest;
import android.app.Activity;
import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.PendingIntent;
import android.app.TimePickerDialog;
import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.provider.Telephony;
import android.telephony.SmsManager;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.*;

public class MainActivity extends AppCompatActivity {
    static final String PREFS = "mergesms";
    static final String GREEN = "#16834A";
    static final String PALE = "#E3F4E8";
    static final String DARK = "#153324";
    LinearLayout root, content, nav;
    EditText composer;
    TextView count;
    SharedPreferences prefs;
    ArrayList<Recipient> recipients = new ArrayList<>();
    ArrayList<MergeField> fields = new ArrayList<>();
    ArrayList<String> csvHeaders = new ArrayList<>();
    ArrayList<Map<String,String>> csvRows = new ArrayList<>();
    String currentTab = "Messages";
    ActivityResultLauncher<String[]> permissionLauncher;
    ActivityResultLauncher<String> csvLauncher;
    ActivityResultLauncher<String> contactLauncher;

    static class Recipient {
        String name, phone, status = "Not sent";
        Map<String,String> data = new LinkedHashMap<>();
        boolean selected = true;
        Recipient(String n, String p) { name=n; phone=p; }
    }
    static class MergeField {
        String token, column;
        MergeField(String t, String c) { token=t; column=c; }
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        permissionLauncher = registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
            Toast.makeText(this, "Review the requested permissions to enable SMS and contacts features.", Toast.LENGTH_LONG).show();
        });
        csvLauncher = registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> { if(uri!=null) readCsv(uri); });
        contactLauncher = registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> { if(uri!=null) readCsv(uri); });
        requestNeededPermissions();
        showHome();
    }

    void requestNeededPermissions() {
        ArrayList<String> p = new ArrayList<>();
        for(String s : new String[]{Manifest.permission.SEND_SMS, Manifest.permission.READ_SMS, Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_CONTACTS}) {
            if(ContextCompat.checkSelfPermission(this,s)!=PackageManager.PERMISSION_GRANTED) p.add(s);
        }
        if(!p.isEmpty()) permissionLauncher.launch(p.toArray(new String[0]));
    }

    TextView text(String value, int size, boolean bold) {
        TextView t = new TextView(this); t.setText(value); t.setTextSize(size); t.setTextColor(android.graphics.Color.parseColor(DARK));
        if(bold)t.setTypeface(null,android.graphics.Typeface.BOLD);
        t.setPadding(dp(4),dp(5),dp(4),dp(5)); return t;
    }
    int dp(float v){return (int)(v*getResources().getDisplayMetrics().density+0.5f);}
    LinearLayout column(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}
    LinearLayout row(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.HORIZONTAL);l.setGravity(Gravity.CENTER_VERTICAL);return l;}
    void styleButton(Button b){b.setAllCaps(false);b.setTextColor(android.graphics.Color.WHITE);b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor(GREEN)));b.setPadding(dp(12),dp(9),dp(12),dp(9));}
    Button button(String label,Runnable action){Button b=new Button(this);b.setText(label);styleButton(b);b.setOnClickListener(v->action.run());return b;}
    void setup(String title,String subtitle){
        root=column();
        root.setBackgroundColor(android.graphics.Color.rgb(242,248,244));
        setContentView(root);

        // Green gradient header with branding and three-dot menu.
        LinearLayout header=row();
        android.graphics.drawable.GradientDrawable gradient =
            new android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{0xFF075E3B,0xFF16A05A});
        header.setBackground(gradient);
        header.setPadding(dp(16),dp(12),dp(6),dp(12));

        LinearLayout titles=column();
        TextView heading=new TextView(this);
        heading.setText("MergeSMS".equals(title)?"MergeSMS":title);
        heading.setTextSize(25);
        heading.setTypeface(null,android.graphics.Typeface.BOLD);
        heading.setTextColor(android.graphics.Color.WHITE);
        titles.addView(heading);
        if(subtitle!=null){
            TextView sub=new TextView(this);sub.setText(subtitle);sub.setTextSize(13);
            sub.setTextColor(0xFFE0F5E8);titles.addView(sub);
        }
        header.addView(titles,new LinearLayout.LayoutParams(0,-2,1));

        Button search=new Button(this);search.setText("⌕");search.setTextSize(25);
        search.setTextColor(android.graphics.Color.WHITE);
        search.setBackgroundColor(android.graphics.Color.TRANSPARENT);
        search.setOnClickListener(v->{
            EditText query=new EditText(this);query.setHint("Search messages or contacts");
            new AlertDialog.Builder(this).setTitle("Search MergeSMS").setView(query)
                .setPositiveButton("Search",(d,w)->{
                    String q=query.getText().toString().trim();
                    if(!q.isEmpty()){
                        currentTab="Messages";
                        showMessages();
                        toast("Search is ready for: "+q);
                    }
                }).setNegativeButton("Cancel",null).show();
        });
        header.addView(search);

        Button menu=new Button(this);menu.setText("⋮");menu.setTextSize(26);
        menu.setTextColor(android.graphics.Color.WHITE);
        menu.setBackgroundColor(android.graphics.Color.TRANSPARENT);
        header.addView(menu);
        menu.setOnClickListener(v->{
            android.widget.PopupMenu popup=new android.widget.PopupMenu(this,menu);
            popup.getMenu().add("Settings").setOnMenuItemClickListener(item->{currentTab="Settings";showSettings();return true;});
            popup.getMenu().add("Permissions").setOnMenuItemClickListener(item->{requestNeededPermissions();return true;});
            popup.getMenu().add("Export Data").setOnMenuItemClickListener(item->{
                new AlertDialog.Builder(this).setTitle("Export Data")
                    .setMessage("Data export is not implemented yet.")
                    .setPositiveButton("OK",null).show();return true;});
            popup.getMenu().add("Help & Support").setOnMenuItemClickListener(item->{
                new AlertDialog.Builder(this).setTitle("Help & Support")
                    .setMessage("Use Messages for normal SMS and Bulk SMS for CSV mail merge.")
                    .setPositiveButton("OK",null).show();return true;});
            popup.getMenu().add("About").setOnMenuItemClickListener(item->{
                new AlertDialog.Builder(this).setTitle("About MergeSMS")
                    .setMessage("MergeSMS 1.0.0\nYour standalone SMS app.")
                    .setPositiveButton("OK",null).show();return true;});
            popup.show();
        });
        root.addView(header);

        ScrollView sc=new ScrollView(this);
        content=column();content.setPadding(dp(16),dp(8),dp(16),dp(8));sc.addView(content);
        root.addView(sc,new LinearLayout.LayoutParams(-1,0,1));

        // Floating action opens Contacts and Bulk SMS as popup choices.
        LinearLayout actionRow=row();actionRow.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);
        actionRow.setPadding(dp(8),dp(4),dp(16),dp(8));
        Button actions=new Button(this);actions.setText("＋");actions.setTextSize(25);styleButton(actions);
        actionRow.addView(actions,new LinearLayout.LayoutParams(dp(58),dp(54)));
        actions.setOnClickListener(v->{
            android.widget.PopupMenu popup=new android.widget.PopupMenu(this,actions);
            popup.getMenu().add("Contacts").setOnMenuItemClickListener(item->{currentTab="Contacts";showContacts();return true;});
            popup.getMenu().add("Bulk SMS").setOnMenuItemClickListener(item->{currentTab="Bulk SMS";showBulk();return true;});
            popup.show();
        });
        root.addView(actionRow);

        // Messages is the only permanent bottom navigation item.
        nav=row();nav.setGravity(Gravity.CENTER);nav.setPadding(dp(8),dp(5),dp(8),dp(8));
        nav.setBackgroundColor(android.graphics.Color.WHITE);
        Button messages=new Button(this);messages.setText("▣  Messages");messages.setAllCaps(false);
        messages.setTextColor(android.graphics.Color.parseColor(GREEN));
        messages.setBackgroundColor(android.graphics.Color.TRANSPARENT);
        messages.setOnClickListener(v->{currentTab="Messages";showMessages();});
        nav.addView(messages);root.addView(nav);
    }
    void showHome(){
        if("Messages".equals(currentTab)) showMessages();
        else if("Contacts".equals(currentTab)) showContacts();
        else if("Bulk SMS".equals(currentTab)) showBulk();
        else showSettings();
    }
    void showMessages(){
        setup("MergeSMS","Your standalone SMS app");
        content.addView(button("＋  New message",()->composeNormal()));
        content.addView(button("◷  Scheduled messages",()->showScheduled()));
        content.addView(text("Recent conversations",18,true));
        if(ContextCompat.checkSelfPermission(this,Manifest.permission.READ_SMS)==PackageManager.PERMISSION_GRANTED){
            Cursor c=null;try{c=getContentResolver().query(Uri.parse("content://sms/conversations"),new String[]{"thread_id","address","body","date","read"},null,null,"date DESC");
                if(c!=null){HashSet<String> seen=new HashSet<>();int n=0;while(c.moveToNext()&&n<50){String address=c.getString(1);if(address==null||seen.contains(address))continue;seen.add(address);String body=c.getString(2);LinearLayout r=row();TextView t=text("●  "+address+"\n"+(body==null?"":body),15,false);r.addView(t,new LinearLayout.LayoutParams(0,-2,1));r.setPadding(0,dp(5),0,dp(5));r.setOnClickListener(v->openConversation(address));content.addView(r);View line=new View(this);line.setBackgroundColor(0xffdce5de);content.addView(line,new LinearLayout.LayoutParams(-1,dp(1)));n++;}}
            }catch(Exception e){content.addView(text("SMS inbox unavailable: "+e.getMessage(),13,false));}finally{if(c!=null)c.close();}
        } else content.addView(text("Grant SMS permission to display messages stored on this phone.",14,false));
    }
    void composeNormal(){final EditText phone=new EditText(this);phone.setHint("Recipient phone number");final EditText body=new EditText(this);body.setHint("Write a message");body.setMinLines(3);LinearLayout box=column();box.addView(phone);box.addView(body);new AlertDialog.Builder(this).setTitle("New message").setView(box).setPositiveButton("Save draft",(d,w)->saveDraft("normal:"+phone.getText(),body.getText().toString())).setNeutralButton("Send",(d,w)->sendOne(phone.getText().toString(),body.getText().toString())).setNegativeButton("Cancel",null).show();}
    void openConversation(String address){Cursor c=null;StringBuilder s=new StringBuilder();try{c=getContentResolver().query(Uri.parse("content://sms"),new String[]{"address","body","date","type"}, "address=?",new String[]{address},"date ASC");if(c!=null)while(c.moveToNext())s.append(c.getString(2)).append("  ").append(c.getInt(3)==2?"You: ":"").append(c.getString(1)).append("\n\n");}catch(Exception ignored){}finally{if(c!=null)c.close();}
        EditText reply=new EditText(this);reply.setHint("Reply");new AlertDialog.Builder(this).setTitle(address).setMessage(s.toString()).setView(reply).setPositiveButton("Send",(d,w)->sendOne(address,reply.getText().toString())).setNeutralButton("Save draft",(d,w)->saveDraft("normal:"+address,reply.getText().toString())).setNegativeButton("Back",null).show();}
    void showContacts(){setup("Contacts","Find and message people");content.addView(button("＋  Add recipient manually",()->{EditText n=new EditText(this),p=new EditText(this);n.setHint("Name");p.setHint("Phone number");LinearLayout l=column();l.addView(n);l.addView(p);new AlertDialog.Builder(this).setTitle("Add contact to campaign").setView(l).setPositiveButton("Add",(d,w)->{recipients.add(new Recipient(n.getText().toString(),p.getText().toString()));showContacts();}).setNegativeButton("Cancel",null).show();}));if(ContextCompat.checkSelfPermission(this,Manifest.permission.READ_CONTACTS)==PackageManager.PERMISSION_GRANTED){Cursor c=getContentResolver().query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI,new String[]{ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,ContactsContract.CommonDataKinds.Phone.NUMBER},null,null,ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME+" ASC");if(c!=null){while(c.moveToNext())content.addView(text(c.getString(0)+"\n"+c.getString(1),15,false));c.close();}}}
    void showBulk(){setup("Bulk SMS","Personalized SMS from your phone");content.addView(button("▤  Import CSV file",()->csvLauncher.launch("*/*")));content.addView(text("Imported "+recipients.size()+" recipients (maximum 1,000)",14,false));
        content.addView(text("1. Recipients",18,true));content.addView(button("＋  Add recipient manually",()->{EditText n=new EditText(this),p=new EditText(this);n.setHint("Name");p.setHint("Phone number");LinearLayout l=column();l.addView(n);l.addView(p);new AlertDialog.Builder(this).setTitle("Add recipient").setView(l).setPositiveButton("Add",(d,w)->{recipients.add(new Recipient(n.getText().toString(),p.getText().toString()));showBulk();}).setNegativeButton("Cancel",null).show();}));
        content.addView(button("▣  Show / hide recipient list ("+recipients.size()+")",()->{prefs.edit().putBoolean("recipientsExpanded",!prefs.getBoolean("recipientsExpanded",true)).apply();showBulk();}));
        if(prefs.getBoolean("recipientsExpanded",true)){for(Recipient r:recipients){CheckBox cb=new CheckBox(this);cb.setText((r.name.isEmpty()?r.phone:r.name+"  "+r.phone)+" — "+r.status);cb.setChecked(r.selected);cb.setOnCheckedChangeListener((v,b)->r.selected=b);content.addView(cb);}}
        content.addView(text("2. Merge fields",18,true));content.addView(text("Import columns are available as merge fields. Remove a field here only to exclude it from merging; the original data is retained.",13,false));
        for(MergeField f:new ArrayList<>(fields)){LinearLayout rr=row();TextView label=text("{"+f.token+"}  ←  "+f.column,14,false);rr.addView(label,new LinearLayout.LayoutParams(0,-2,1));TextView x=text("×",20,true);x.setTextColor(0xff617468);x.setGravity(Gravity.CENTER);x.setOnClickListener(v->{fields.remove(f);showBulk();});rr.addView(x,new LinearLayout.LayoutParams(dp(34),dp(38)));content.addView(rr);}
        content.addView(button("＋  Add merge field",()->addField()));
        content.addView(text("3. Message",18,true));composer=new EditText(this);composer.setHint("Write your message. Use fields like {Name} or {Balance}.");composer.setMinLines(3);composer.setGravity(Gravity.TOP);composer.setText(prefs.getString("bulkDraft",""));composer.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int c,int a){}public void onTextChanged(CharSequence s,int st,int before,int count){prefs.edit().putString("bulkDraft",s.toString()).apply();}public void afterTextChanged(Editable e){}});content.addView(composer);count=text("0 / 500 characters",12,false);content.addView(count);composer.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int c,int a){}public void onTextChanged(CharSequence s,int st,int before,int n){count.setText(s.length()+" / 500 characters");}public void afterTextChanged(Editable e){}});content.addView(button("◷  Schedule bulk SMS",()->scheduleBulk()));content.addView(button("➤  Send bulk SMS",()->sendBulk()));}
    void addField(){if(csvHeaders.isEmpty()){new AlertDialog.Builder(this).setMessage("Import a CSV file first.").setPositiveButton("OK",null).show();return;}String[] cols=csvHeaders.toArray(new String[0]);Spinner sp=new Spinner(this);sp.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,cols));EditText token=new EditText(this);token.setHint("Short field name, e.g. Balance");LinearLayout l=column();l.addView(sp);l.addView(token);new AlertDialog.Builder(this).setTitle("Add merge field").setView(l).setPositiveButton("Add",(d,w)->{String t=token.getText().toString().trim().replaceAll("[^A-Za-z0-9_]","");if(t.isEmpty())t=sp.getSelectedItem().toString().replaceAll("[^A-Za-z0-9_]","");fields.add(new MergeField(t,sp.getSelectedItem().toString()));showBulk();}).setNegativeButton("Cancel",null).show();}
    void readCsv(Uri uri){try{InputStream in=getContentResolver().openInputStream(uri);BufferedReader br=new BufferedReader(new InputStreamReader(in));String line=br.readLine();if(line==null)return;csvHeaders.clear();csvRows.clear();csvHeaders.addAll(parseCsv(line));String[] first=csvHeaders.toArray(new String[0]);int phoneCol=-1,nameCol=-1;for(int i=0;i<first.length;i++){String h=first[i].toLowerCase(Locale.ROOT);if(phoneCol<0&&(h.contains("phone")||h.contains("mobile")||h.contains("number")))phoneCol=i;if(nameCol<0&&h.contains("name"))nameCol=i;}
        String row;HashSet<String> seen=new HashSet<>();int duplicates=0;while((row=br.readLine())!=null&&recipients.size()<1000){List<String> vals=parseCsv(row);Map<String,String> map=new LinkedHashMap<>();for(int i=0;i<csvHeaders.size();i++)map.put(csvHeaders.get(i),i<vals.size()?vals.get(i):"");csvRows.add(map);String phone=phoneCol>=0&&phoneCol<vals.size()?vals.get(phoneCol).trim():"";String name=nameCol>=0&&nameCol<vals.size()?vals.get(nameCol).trim():"";if(!phone.isEmpty()){String norm=phone.replaceAll("[^+0-9]","");if(seen.add(norm)){Recipient r=new Recipient(name,phone);r.data.putAll(map);recipients.add(r);}else duplicates++;}}
        br.close();if(fields.isEmpty()){for(String h:csvHeaders){if(phoneCol>=0&&h.equals(csvHeaders.get(phoneCol)))fields.add(new MergeField("Phonenumber",h));else if(nameCol>=0&&h.equals(csvHeaders.get(nameCol)))fields.add(new MergeField("Name",h));}}
        new AlertDialog.Builder(this).setTitle("CSV imported").setMessage("Imported recipients: "+recipients.size()+"\nDuplicate phone numbers skipped: "+duplicates+"\nColumns detected: "+csvHeaders.size()).setPositiveButton("Continue",null).show();showHome();
    }catch(Exception e){new AlertDialog.Builder(this).setMessage("Could not read CSV: "+e.getMessage()).setPositiveButton("OK",null).show();}}
    List<String> parseCsv(String line){ArrayList<String> out=new ArrayList<>();StringBuilder b=new StringBuilder();boolean q=false;for(int i=0;i<line.length();i++){char c=line.charAt(i);if(c=='"'){if(q&&i+1<line.length()&&line.charAt(i+1)=='"'){b.append('"');i++;}else q=!q;}else if(c==','&&!q){out.add(b.toString().trim());b.setLength(0);}else b.append(c);}out.add(b.toString().trim());return out;}
    String personalize(Recipient r,String template){String s=template;for(MergeField f:fields)s=s.replace("{"+f.token+"}",r.data.getOrDefault(f.column,""));s=s.replace("{Name}",r.name);s=s.replace("{Phonenumber}",r.phone);return s;}
    void sendOne(String phone,String body){if(phone.trim().isEmpty()||body.trim().isEmpty()){toast("Enter a phone number and message.");return;}if(ContextCompat.checkSelfPermission(this,Manifest.permission.SEND_SMS)!=PackageManager.PERMISSION_GRANTED){requestNeededPermissions();return;}try{SmsManager sm=SmsManager.getDefault();ArrayList<String> parts=sm.divideMessage(body);sm.sendMultipartTextMessage(phone,null,parts,null,null);toast("SMS submitted to Android's SMS service.");}catch(Exception e){toast("Send failed: "+e.getMessage());}}
    void sendBulk(){String msg=composer==null?"":composer.getText().toString();if(msg.trim().isEmpty()){toast("Write a message first.");return;}ArrayList<Recipient> chosen=new ArrayList<>();for(Recipient r:recipients)if(r.selected)chosen.add(r);if(chosen.isEmpty()){toast("Select at least one recipient.");return;}new AlertDialog.Builder(this).setTitle("Send "+chosen.size()+" SMS messages?").setMessage("Messages will be sent through your device SIM and may incur carrier charges.").setNegativeButton("Cancel",null).setPositiveButton("Send",(d,w)->{int ok=0;for(Recipient r:chosen){try{String msgFor=personalize(r,msg);SmsManager.getDefault().sendMultipartTextMessage(r.phone,null,SmsManager.getDefault().divideMessage(msgFor),null,null);r.status="Submitted";ok++;}catch(Exception e){r.status="Failed";}}saveCampaign(chosen,msg);if(ok>0){prefs.edit().putString("bulkDraft","").apply();for(Recipient sent:chosen)sent.selected=false;}toast("Submitted "+ok+" of "+chosen.size()+" messages.");showBulk();}).show();}
    void saveCampaign(ArrayList<Recipient> chosen,String template){try{JSONArray arr=new JSONArray();for(Recipient r:chosen){JSONObject o=new JSONObject();o.put("phone",r.phone);o.put("name",r.name);o.put("status",r.status);o.put("message",personalize(r,template));arr.put(o);}prefs.edit().putString("lastCampaign",arr.toString()).apply();}catch(Exception ignored){}}
    void scheduleBulk(){String msg=composer==null?"":composer.getText().toString();if(msg.trim().isEmpty()){toast("Write a message first.");return;}Calendar cal=Calendar.getInstance();new DatePickerDialog(this,(v,y,m,d)->{cal.set(y,m,d);new TimePickerDialog(this,(tv,h,min)->{cal.set(Calendar.HOUR_OF_DAY,h);cal.set(Calendar.MINUTE,min);cal.set(Calendar.SECOND,0);if(cal.getTimeInMillis()<=System.currentTimeMillis()){toast("Choose a future time.");return;}saveScheduledCampaign(msg,cal.getTimeInMillis());},cal.get(Calendar.HOUR_OF_DAY),cal.get(Calendar.MINUTE),true).show();},cal.get(Calendar.YEAR),cal.get(Calendar.MONTH),cal.get(Calendar.DAY_OF_MONTH)).show();}
    void saveScheduledCampaign(String msg,long when){try{JSONArray a=new JSONArray(prefs.getString("scheduled","[]"));JSONObject o=new JSONObject();o.put("id",System.currentTimeMillis());o.put("message",msg);o.put("when",when);JSONArray rs=new JSONArray();for(Recipient r:recipients)if(r.selected){JSONObject x=new JSONObject();x.put("phone",r.phone);x.put("name",r.name);x.put("data",new JSONObject(r.data));rs.put(x);}o.put("recipients",rs);a.put(o);prefs.edit().putString("scheduled",a.toString()).apply();Intent i=new Intent(this,ScheduleReceiver.class);i.putExtra("id",o.getLong("id"));PendingIntent pi=PendingIntent.getBroadcast(this,(int)(o.getLong("id")%Integer.MAX_VALUE),i,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);AlarmManager am=(AlarmManager)getSystemService(ALARM_SERVICE);if(Build.VERSION.SDK_INT<31||am.canScheduleExactAlarms())am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,when,pi);else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,when,pi);toast("Bulk campaign scheduled.");}catch(Exception e){toast("Could not schedule: "+e.getMessage());}}
    void showScheduled(){setup("Scheduled SMS","Individual messages and bulk campaigns");try{JSONArray a=new JSONArray(prefs.getString("scheduled","[]"));for(int i=0;i<a.length();i++){JSONObject o=a.getJSONObject(i);content.addView(text(new SimpleDateFormat("EEE, d MMM yyyy HH:mm",Locale.getDefault()).format(new Date(o.getLong("when")))+"\n"+o.getJSONArray("recipients").length()+" recipients\n"+o.getString("message"),14,false));content.addView(button("Cancel schedule",()->{cancelSchedule(o.optLong("id"));showScheduled();}));}}catch(Exception ignored){}content.addView(button("Back to Messages",()->{currentTab="Messages";showHome();}));}
    void cancelSchedule(long id){try{JSONArray a=new JSONArray(prefs.getString("scheduled","[]"));JSONArray n=new JSONArray();for(int i=0;i<a.length();i++){JSONObject o=a.getJSONObject(i);if(o.optLong("id")==id){Intent in=new Intent(this,ScheduleReceiver.class);PendingIntent pi=PendingIntent.getBroadcast(this,(int)(id%Integer.MAX_VALUE),in,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);((AlarmManager)getSystemService(ALARM_SERVICE)).cancel(pi);}else n.put(o);}prefs.edit().putString("scheduled",n.toString()).apply();}catch(Exception ignored){}}
    void saveDraft(String key,String val){prefs.edit().putString("draft:"+key,val).apply();toast("Draft saved.");}
    void showSettings(){setup("Settings","Personalize your messaging experience");content.addView(text("Appearance",18,true));content.addView(button("Green and white theme: enabled",()->toast("Green-and-white theme is active.")));content.addView(text("Messaging permissions",18,true));content.addView(button("Review SMS permissions",()->requestNeededPermissions()));content.addView(text("Default SMS app",18,true));content.addView(button("Open default SMS app settings",()->{if(Build.VERSION.SDK_INT>=19){Intent i=new Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT);i.putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME,getPackageName());try{startActivity(i);}catch(Exception e){toast("Open Android Settings to choose the default SMS app.");}}}));content.addView(text("About",18,true));content.addView(text("MergeSMS 1.0.0 • Standalone Android app",13,false));}
    void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
}
