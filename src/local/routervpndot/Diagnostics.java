package local.routervpndot;
import android.app.*;
import android.content.*;
import android.os.Build;
public class Diagnostics extends Application {
 public void onCreate(){
  super.onCreate();final Thread.UncaughtExceptionHandler previous=Thread.getDefaultUncaughtExceptionHandler();
  Thread.setDefaultUncaughtExceptionHandler((t,e)->{
   event(this,"CRASH: "+android.util.Log.getStackTraceString(e));
   if(previous!=null)previous.uncaughtException(t,e);
  });
 }
 static synchronized void event(Context c,String message){
  android.content.SharedPreferences p=c.getSharedPreferences("diagnostics",0);
  String log=p.getString("events","")+"@"+System.currentTimeMillis()+" "+message+"\n";
  if(log.length()>14000)log=log.substring(log.length()-14000);
  p.edit().putString("events",log).commit();
 }
 static String report(Context c){
  java.util.List<LogTimeline.Entry> entries=LogTimeline.parse(c.getSharedPreferences("diagnostics",0).getString("events",""));
  if(Build.VERSION.SDK_INT>=30){
   try{for(ApplicationExitInfo info:c.getSystemService(ActivityManager.class).getHistoricalProcessExitReasons(c.getPackageName(),0,5)){
    entries.add(new LogTimeline.Entry(info.getTimestamp(),"Android process exit: reason="+info.getReason()+" signal/exitStatus="+info.getStatus()+" importance="+info.getImportance()+" RSS_kB="+info.getRss()+(info.getDescription()==null?"":" "+info.getDescription())));
   }}catch(RuntimeException e){entries.add(new LogTimeline.Entry(System.currentTimeMillis(),"Exit history unavailable: "+e.getClass().getSimpleName()));}
  }
  return LogTimeline.render(entries);
 }
}
