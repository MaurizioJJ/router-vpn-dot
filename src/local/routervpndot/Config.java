package local.routervpndot;
import android.content.Context;
import android.content.SharedPreferences;
final class Config {
 // Local count of this app's lookups, replaced by proxycheck.io's own numbers when a key is built in.
 static String today(){java.text.SimpleDateFormat f=new java.text.SimpleDateFormat("yyyy-MM-dd",java.util.Locale.US);f.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));return f.format(new java.util.Date());}
 static int queriesUsed(Context c){SharedPreferences p=prefs(c);return today().equals(p.getString("quotaDay",""))?p.getInt("quotaUsed",0):0;}
 static int queryLimit(Context c){return Secrets.PROXYCHECK_KEY.isEmpty()?100:prefs(c).getInt("quotaLimit",1000);}
 static void syncQuota(Context c,int used,int limit){prefs(c).edit().putString("quotaDay",today()).putInt("quotaUsed",used).putInt("quotaLimit",limit).apply();}
 static void countQuery(Context c){prefs(c).edit().putString("quotaDay",today()).putInt("quotaUsed",queriesUsed(c)+1).apply();}
 static SharedPreferences prefs(Context c) {
  SharedPreferences p=c.getSharedPreferences("config",0);
  if(!p.getBoolean("softDotV2",false)) {
   p.edit().putBoolean("softDotV2",true).putInt("size",8).putInt("greenBrightness",15).putInt("alertBrightness",35).apply();
  }
  return p;
 }
}
