package local.routervpndot;
import android.app.PendingIntent;
import android.content.*;
import android.content.pm.PackageInstaller;
import android.provider.Settings;
import android.widget.Toast;
import java.io.*;
import java.net.*;
import org.json.*;
// Public build only: Secrets.UPDATE_REPO comes from update.repo at build time; empty = no update checks.
final class Updater {
 static final String STATUS="local.routervpndot.INSTALL_STATUS";
 static final class Release{String tag,notes,apk;}
 static boolean enabled(){return !Secrets.UPDATE_REPO.isEmpty();}
 static String current(Context c){
  try{return c.getPackageManager().getPackageInfo(c.getPackageName(),0).versionName;}
  catch(Exception e){return "0";}
 }
 static boolean shouldAsk(Context c,Release r){return r.apk!=null&&UpdateCheck.shouldAsk(r.tag,current(c),Config.prefs(c).getString("updateSkipped",""));}
 // Network: call off the main thread. The API can be rate-limited on shared VPN exit IPs
 // (60 calls/hour/IP), so fall back to the release page redirect, which has no quota but no notes.
 static Release latest() throws IOException,JSONException{
  try{return fromApi();}catch(IOException e){return fromRedirect();}
 }
 private static Release fromApi() throws IOException,JSONException{
  HttpURLConnection h=open("https://api.github.com/repos/"+Secrets.UPDATE_REPO+"/releases/latest");
  ByteArrayOutputStream out=new ByteArrayOutputStream();
  try(InputStream in=h.getInputStream()){copy(in,out);}finally{h.disconnect();}
  JSONObject j=new JSONObject(out.toString("UTF-8"));
  Release r=new Release();r.tag=j.getString("tag_name");r.notes=j.optString("body","");
  JSONArray a=j.getJSONArray("assets");
  for(int i=0;i<a.length();i++){JSONObject f=a.getJSONObject(i);if(f.getString("name").endsWith(".apk"))r.apk=f.getString("browser_download_url");}
  return r;
 }
 private static Release fromRedirect() throws IOException{
  String base="https://github.com/"+Secrets.UPDATE_REPO+"/releases/";
  HttpURLConnection h=(HttpURLConnection)new URL(base+"latest").openConnection();
  h.setInstanceFollowRedirects(false);h.setRequestMethod("HEAD");h.setConnectTimeout(15000);h.setReadTimeout(30000);h.setRequestProperty("User-Agent","RouterVPNDot");
  String to;try{h.getResponseCode();to=h.getHeaderField("Location");}finally{h.disconnect();}
  if(to==null||!to.contains("/releases/tag/"))throw new IOException("No release found");
  Release r=new Release();r.tag=to.substring(to.lastIndexOf('/')+1);r.notes="(Release notes: "+base+"tag/"+r.tag+")";
  r.apk=base+"download/"+r.tag+"/RouterVPNDot-"+r.tag.replaceFirst("^[vV]","")+".apk";
  return r;
 }
 // Streams the APK into an install session; Android then asks the viewer to confirm (see status()).
 static void install(Context c,Release r) throws IOException{
  PackageInstaller pi=c.getPackageManager().getPackageInstaller();
  int id=pi.createSession(new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL));
  try(PackageInstaller.Session s=pi.openSession(id)){
   try{
    HttpURLConnection h=open(r.apk);
    try(InputStream in=h.getInputStream();OutputStream out=s.openWrite("update.apk",0,-1)){copy(in,out);s.fsync(out);}finally{h.disconnect();}
    // Mutable on purpose: the installer adds the status extras to this intent.
    s.commit(PendingIntent.getBroadcast(c,id,new Intent(c,BootReceiver.class).setAction(STATUS),PendingIntent.FLAG_UPDATE_CURRENT).getIntentSender());
   }catch(IOException|RuntimeException e){s.abandon();throw e;}
  }
  Diagnostics.event(c,"update "+r.tag+" downloaded, waiting for confirmation");
 }
 @SuppressWarnings("deprecation")
 static void status(Context c,Intent i){
  int s=i.getIntExtra(PackageInstaller.EXTRA_STATUS,PackageInstaller.STATUS_FAILURE);
  if(s==PackageInstaller.STATUS_PENDING_USER_ACTION){
   Intent confirm=i.getParcelableExtra(Intent.EXTRA_INTENT);
   if(confirm!=null)c.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
   return;
  }
  if(s==PackageInstaller.STATUS_SUCCESS)return;
  String m=i.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);
  Diagnostics.event(c,"update install failed: "+s+" "+m);
  Toast.makeText(c,"Update not installed"+(m==null?"":": "+m),Toast.LENGTH_LONG).show();
 }
 // After a restart: if a new version is waiting, open the app so it can ask.
 // ponytail: fixed 6 tries 20 s apart for the network to come up; no backoff needed for one call per boot.
 static void checkAfterBoot(Context c){
  if(!enabled()||!Settings.canDrawOverlays(c))return;
  Context app=c.getApplicationContext();
  new Thread(()->{
   for(int i=0;i<6;i++){
    try{
     if(shouldAsk(app,latest()))app.startActivity(new Intent(app,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
     return;
    }catch(Exception e){try{Thread.sleep(20000);}catch(InterruptedException x){return;}}
   }
   Diagnostics.event(app,"update check after restart failed");
  }).start();
 }
 private static HttpURLConnection open(String url) throws IOException{
  HttpURLConnection h=(HttpURLConnection)new URL(url).openConnection();
  h.setConnectTimeout(15000);h.setReadTimeout(30000);
  h.setRequestProperty("User-Agent","RouterVPNDot");h.setRequestProperty("Accept","application/vnd.github+json");
  if(h.getResponseCode()!=200){int code=h.getResponseCode();h.disconnect();throw new IOException("GitHub answered "+code);}
  return h;
 }
 private static void copy(InputStream in,OutputStream out) throws IOException{byte[] b=new byte[65536];int n;while((n=in.read(b))>0)out.write(b,0,n);}
}
