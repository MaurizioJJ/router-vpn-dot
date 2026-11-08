package local.routervpndot;
import android.app.*;
import android.content.*;
import android.os.SystemClock;
import android.provider.Settings;
final class AutoStart {
 static final String RECOVER="local.routervpndot.RECOVER";
 static void schedule(Context c){
  c.getSystemService(AlarmManager.class).setInexactRepeating(AlarmManager.ELAPSED_REALTIME,
   SystemClock.elapsedRealtime()+60000,60000,pending(c));
 }
 static PendingIntent pending(Context c){return PendingIntent.getBroadcast(c,4,new Intent(c,BootReceiver.class).setAction(RECOVER),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);}
 static void cancel(Context c){c.getSystemService(AlarmManager.class).cancel(pending(c));}
 static void resume(Context c,String source){
  boolean enabled=Config.prefs(c).getBoolean("enabled",false),permission=Settings.canDrawOverlays(c);
  if(!enabled){cancel(c);return;}
  if(!permission){Diagnostics.event(c,source+": overlay permission missing");return;}
  if(MonitorService.running)return;
  Diagnostics.event(c,source+": resuming monitoring");
  try{c.startForegroundService(new Intent(c,MonitorService.class).putExtra("source",source));}
  catch(RuntimeException e){Diagnostics.event(c,source+": start failed "+e);}
 }
}
