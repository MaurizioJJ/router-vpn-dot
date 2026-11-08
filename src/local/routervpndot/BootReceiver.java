package local.routervpndot;
import android.content.*;
import android.provider.Settings;
public class BootReceiver extends BroadcastReceiver {
 @Override public void onReceive(Context c, Intent i) {
  String action=i.getAction();
  if(AutoStart.RECOVER.equals(action)){AutoStart.resume(c,"Recovery check");return;}
  if(Intent.ACTION_BOOT_COMPLETED.equals(action)||Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)){
   boolean boot=Config.prefs(c).getBoolean("boot",false),enabled=Config.prefs(c).getBoolean("enabled",false);
   Diagnostics.event(c,"Startup broadcast "+action+" boot="+boot+" enabled="+enabled+" overlay="+Settings.canDrawOverlays(c));
   if(enabled&&(boot||Intent.ACTION_MY_PACKAGE_REPLACED.equals(action))){AutoStart.schedule(c);AutoStart.resume(c,"Boot/update");}
  }
 }
}
