package com.mergesms.app;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.app.PendingIntent;
import android.telephony.SmsManager;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.Map;
public class ScheduleReceiver extends BroadcastReceiver {
 @Override public void onReceive(Context context, Intent intent) {
   long id=intent.getLongExtra("id",-1);
   SharedPreferences p=context.getSharedPreferences("mergesms",Context.MODE_PRIVATE);
   try {
     JSONArray a=new JSONArray(p.getString("scheduled","[]"));
     for(int i=0;i<a.length();i++){
       JSONObject campaign=a.getJSONObject(i);
       if(campaign.optLong("id")!=id)continue;
       JSONArray rs=campaign.getJSONArray("recipients");
       String template=campaign.getString("message");
       for(int j=0;j<rs.length();j++){
         JSONObject r=rs.getJSONObject(j);
         String msg=template.replace("{Name}",r.optString("name")).replace("{Phonenumber}",r.optString("phone"));
         JSONObject data=r.optJSONObject("data");
         if(data!=null){java.util.Iterator<String> keys=data.keys();while(keys.hasNext()){String k=keys.next();msg=msg.replace("{"+k+"}",data.optString(k));}}
         SmsManager.getDefault().sendMultipartTextMessage(r.optString("phone"),null,SmsManager.getDefault().divideMessage(msg),null,null);
       }
       JSONArray keep=new JSONArray();for(int k=0;k<a.length();k++)if(a.getJSONObject(k).optLong("id")!=id)keep.put(a.getJSONObject(k));
       p.edit().putString("scheduled",keep.toString()).apply();break;
     }
   } catch(Exception ignored) {}
 }
}
