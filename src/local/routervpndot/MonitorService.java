package local.routervpndot;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.net.*;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.TextView;
import android.graphics.drawable.GradientDrawable;
import java.net.*;
import org.json.JSONObject;
import java.io.*;
import java.util.concurrent.*;

public class MonitorService extends Service {
 static volatile String lastIp="", detail="Stopped", resolvedIp="", resolvedName="";
 static volatile long checkedAt=0;
 static volatile boolean running=false;
 static volatile boolean settingsVisible=false;
 private String detectionDetail="Waiting for public IP", lastIpProblem="", lastDetectionError="VPN lookup unavailable";
 private final Handler main=new Handler(Looper.getMainLooper());
 private final ExecutorService worker=new ThreadPoolExecutor(0,2,30,TimeUnit.SECONDS,new SynchronousQueue<Runnable>());
 private final ExecutorService detector=Executors.newSingleThreadExecutor();
 private final DetectionState detection=new DetectionState();
 private final StartupBanner startupBanner=new StartupBanner();
 private final long startedAt=SystemClock.elapsedRealtime();
 private final VpnDuration duration=new VpnDuration();
 private long detectionId=0;
 private Future<?> detecting;
 private static volatile MonitorService instance;
 private WindowManager windows;
 private Dot dot;
 private TextView banner;
 private int bannerState=-1;
 private boolean useFallback=false;
 private long requestId=0;
 private Future<?> pending;
 private ConnectivityManager connectivity;
 private ConnectivityManager.NetworkCallback callback;
 private boolean busy=false, destroyed=false;
 private long generation=0;
 private int notificationState=-1;
 // The public IP has no change event: Android network changes and router switches check at once, this slow poll catches the rest. No good result yet: retry every 5 s.
 static final long POLL_MS=60000;
 private final Runnable poll=new Runnable(){public void run(){if(!routerVouches())check();main.postDelayed(this,checkedAt==0?5000:POLL_MS);}};
 // With UniFi set up, the router's own tunnel status is asked every 15 s on the local network. While the dot is
 // green and the tunnel status is the same as at the last public-IP check, that counts as a fresh check and
 // ipify isn't asked at all. Any change, or an unreachable gateway, falls back to an immediate IP check.
 static final long ROUTER_MS=15000;
 private String routerSeen="", routerBaseline="", routerProblem="";
 private long routerVouchedAt=0;
 private boolean routerBusy=false;
 private UnifiClient.VpnClient routerClient;
 private final Runnable routerPoll=new Runnable(){public void run(){askRouter();main.postDelayed(this,ROUTER_MS);}};
 private boolean routerVouches(){return routerVouchedAt>0&&SystemClock.elapsedRealtime()-routerVouchedAt<ROUTER_MS*3;}
 private void askRouter(){
  final UnifiClient c=new UnifiClient(this);
  if(routerBusy||destroyed||!c.configured()){routerVouchedAt=0;return;}
  routerBusy=true;final String wanted=Config.prefs(this).getString("unifiVpnId","");final UnifiClient.VpnClient known=routerClient;
  try{worker.submit(()->{
   UnifiClient.VpnClient v=known;String status="",problem="";
   try{
    if(v==null||(!wanted.isEmpty()&&!wanted.equals(v.id)))v=c.selected(c.clients());
    if(v==null)problem="no WireGuard VPN client picked";else status=c.status(v);
   }catch(Exception e){problem=errorText(e);v=null;}
   final UnifiClient.VpnClient client=v;final String st=status,failure=problem;
   main.post(()->{
    routerBusy=false;if(destroyed)return;routerClient=client;
    if(!failure.equals(routerProblem)){routerProblem=failure;Diagnostics.event(this,failure.isEmpty()?"Router status readable: "+st:"Router status unavailable: "+failure);}
    routerSeen=st;
    if(failure.isEmpty()&&RouterVpn.unchanged(routerBaseline,st)&&detection.state(lastIp,0)==StateLogic.VPN&&checkedAt>0){
     routerVouchedAt=checkedAt=SystemClock.elapsedRealtime();return;
    }
    routerVouchedAt=0;
    if(!failure.isEmpty()||!st.equals(routerBaseline)){if(!routerBaseline.isEmpty())Diagnostics.event(this,"Router tunnel changed: "+(st.isEmpty()?failure:st));check();}
   });
  });}catch(RejectedExecutionException e){routerBusy=false;routerVouchedAt=0;}
 }
 private final Runnable redraw=new Runnable(){public void run(){
  if(dot!=null) dot.invalidate();
  int state=currentState(MonitorService.this);
  updateBanner(state);
  if(notificationState!=state){notificationState=state;getSystemService(NotificationManager.class).notify(1,notification(label(state)));}
  main.postDelayed(this,250);
 }};
 static int currentState(Context c){
  if(!running || checkedAt==0){if(instance!=null)instance.duration.update(StateLogic.UNKNOWN,SystemClock.elapsedRealtime());return StateLogic.UNKNOWN;}
  MonitorService service=instance;
  if(service==null)return StateLogic.UNKNOWN;
  long now=SystemClock.elapsedRealtime();
  int state=service.detection.state(lastIp,now-checkedAt);
  service.duration.update(state,now);return state;
 }
 static String label(int state){return state==StateLogic.VPN ? "VPN detected" : state==StateLogic.NORMAL ? "VPN not detected" : "Unknown / checking";}
 static String connectionAge(){
  MonitorService s=instance;if(s==null||currentState(s)!=StateLogic.VPN)return "";
  long elapsed=s.duration.elapsed(SystemClock.elapsedRealtime());
  String start=new java.text.SimpleDateFormat("dd MMM yyyy HH:mm",java.util.Locale.US).format(new java.util.Date(System.currentTimeMillis()-elapsed));
  return "\nVPN detected since "+start+"  •  "+VpnDuration.format(elapsed);
 }
 private Notification notification(String text){
  Intent open=new Intent(this,MainActivity.class);
  PendingIntent pi=PendingIntent.getActivity(this,0,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
  return new Notification.Builder(this,"monitor").setSmallIcon(R.drawable.icon).setContentTitle("Router VPN Dot").setContentText(text).setContentIntent(pi).setOngoing(true).setShowWhen(false).build();
 }
 @Override public void onCreate(){
  super.onCreate();instance=this;Diagnostics.event(this,"Monitoring service created");
  NotificationChannel ch=new NotificationChannel("monitor","VPN IP monitoring",NotificationManager.IMPORTANCE_LOW);
  getSystemService(NotificationManager.class).createNotificationChannel(ch);
  lastIp="";checkedAt=0;detail="Checking public IPv4";running=true;
  SharedPreferences saved=Config.prefs(this);
  detection.restore(saved.getString("classifiedIp",""),saved.getInt("classifiedState",0));
  lastDetectionError=saved.getString("classificationError","VPN lookup unavailable");
  detection.restoreRetry(saved.getInt("classificationFailures",0),saved.getLong("classificationRetryAt",0),System.currentTimeMillis());
  resolvedIp=saved.getString("classifiedIp","");resolvedName=saved.getString("classifiedName","");
  startForeground(1,notification("Checking public IPv4"));
  windows=(WindowManager)getSystemService(WINDOW_SERVICE);
  connectivity=getSystemService(ConnectivityManager.class);
  callback=new ConnectivityManager.NetworkCallback(){
   @Override public void onAvailable(Network n){networkChanged();}
   @Override public void onLost(Network n){networkChanged();}
  };
  try{connectivity.registerDefaultNetworkCallback(callback);}catch(RuntimeException e){Diagnostics.event(this,"Network callback unavailable: "+e);callback=null;}
  main.post(poll); main.post(redraw); main.postDelayed(routerPoll,3000);
 }
 private void networkChanged(){main.post(()->{if(destroyed)return;generation++;requestId++;busy=false;if(pending!=null)pending.cancel(true);checkedAt=0;lastIp="";detail="Network changed; checking";check();});}
 @Override public int onStartCommand(Intent i,int flags,int id){
  if(!Settings.canDrawOverlays(this)){Diagnostics.event(this,"Stopped: overlay permission missing");detail="Overlay permission required";stopSelf();return START_NOT_STICKY;}
  if(!Config.prefs(this).getBoolean("enabled",false)){stopSelf();return START_NOT_STICKY;}
  AutoStart.schedule(this);
  Diagnostics.event(this,"Service start: "+(i==null?"Android restart":i.getStringExtra("source")));
  showDot();
  updateBanner(currentState(this));
  if(i!=null && "CHECK".equals(i.getAction())) check();
  return START_STICKY;
 }
 private void removeOverlay(View view){
  try{windows.removeView(view);}catch(IllegalArgumentException e){Diagnostics.event(this,"Overlay was already detached: "+e.getMessage());}
 }
 private int dp(int x){return Math.round(x*getResources().getDisplayMetrics().density);}
 private void showDot(){
  if(dot!=null){removeOverlay(dot);dot=null;}
  dot=new Dot(); int size=dp(Config.prefs(this).getInt("size",8));
  WindowManager.LayoutParams p=new WindowManager.LayoutParams(size,size,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE|WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,PixelFormat.TRANSLUCENT);
  int corner=Config.prefs(this).getInt("corner",0);
  p.gravity=(corner<2?Gravity.TOP:Gravity.BOTTOM)|((corner%2)==0?Gravity.RIGHT:Gravity.LEFT);
  p.x=dp(28);p.y=dp(28);p.setTitle("Router VPN status");
  try{windows.addView(dot,p);}catch(RuntimeException e){dot=null;detail="Cannot display overlay";Diagnostics.event(this,"Dot overlay failure: "+e);stopSelf();}
 }
 private void updateBanner(int state){
  int mode=startupBanner.mode(state,SystemClock.elapsedRealtime()-startedAt);
  boolean checking=mode==StartupBanner.CHECKING;
  boolean visible=(!settingsVisible||checking) && Config.prefs(this).getBoolean("banner",true) && state!=StateLogic.VPN && Settings.canDrawOverlays(this);
  if(!visible){if(banner!=null){removeOverlay(banner);banner=null;}bannerState=-1;return;}
  if(banner!=null && bannerState==mode)return;
  if(banner==null){
   banner=new TextView(this);banner.setTextSize(18);banner.setTextColor(Color.WHITE);banner.setGravity(Gravity.CENTER);banner.setPadding(dp(24),dp(14),dp(24),dp(14));
   int width=Math.min(dp(560),getResources().getDisplayMetrics().widthPixels-dp(80));
   WindowManager.LayoutParams p=new WindowManager.LayoutParams(width,WindowManager.LayoutParams.WRAP_CONTENT,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,PixelFormat.TRANSLUCENT);
   p.gravity=Gravity.TOP|Gravity.CENTER_HORIZONTAL;p.y=dp(28);p.setTitle("VPN connection warning");
   try{windows.addView(banner,p);}catch(RuntimeException e){banner=null;Diagnostics.event(this,"Banner overlay failure: "+e);return;}
  }
  bannerState=mode;
  banner.setTextSize(checking?14:18);banner.setTextColor(checking?0xCCCBD5E1:Color.WHITE);
  banner.setPadding(dp(checking?16:24),dp(checking?8:14),dp(checking?16:24),dp(checking?8:14));
  WindowManager.LayoutParams layout=(WindowManager.LayoutParams)banner.getLayoutParams();
  layout.width=Math.min(dp(checking?280:560),getResources().getDisplayMetrics().widthPixels-dp(80));
  windows.updateViewLayout(banner,layout);
  GradientDrawable bg=new GradientDrawable();bg.setColor(checking?0x881E293B:state==StateLogic.NORMAL?0xF0B91C1C:0xF092400E);bg.setCornerRadius(dp(10));banner.setBackground(bg);
  banner.setText(checking?"Checking VPN…":state==StateLogic.NORMAL?"VPN NOT DETECTED":"VPN STATUS UNKNOWN");
 }
 private void check(){
  if(busy || destroyed)return;
  busy=true; final long ticket=generation, id=++requestId;
  final String endpoint=useFallback?"https://checkip.amazonaws.com":"https://api.ipify.org";
  final Runnable deadline=()->{
   if(destroyed || id!=requestId)return;
   requestId++;busy=false;if(pending!=null)pending.cancel(true);
   checkedAt=0;lastIp="";detail="IP check timed out (10 seconds)";logIpProblem(detail);useFallback=!useFallback;
  };
  try{
   pending=worker.submit(()->{
    String result="", error="";
    try{result=fetch(endpoint);}catch(Exception e){error="IP check failed: "+errorText(e);}
    final String ip=result, problem=error;
    main.post(()->{
     if(destroyed || id!=requestId)return;
     main.removeCallbacks(deadline);busy=false;
     if(ticket!=generation){check();return;}
     if(problem.isEmpty()){lastIp=ip;checkedAt=SystemClock.elapsedRealtime();routerBaseline=routerSeen;useFallback=false;if(!lastIpProblem.isEmpty()){Diagnostics.event(this,"Public IP checks recovered: "+ip);lastIpProblem="";}classifyIp(ip);detail=detectionDetail;}
     else{lastIp="";checkedAt=0;detail=problem;logIpProblem(problem);useFallback=!useFallback;}
    });
   });
   main.postDelayed(deadline,10000);
  }catch(RejectedExecutionException e){busy=false;checkedAt=0;lastIp="";detail="Network checks stalled; status unknown";}
 }
 private void logIpProblem(String problem){
  if(!problem.equals(lastIpProblem)){lastIpProblem=problem;Diagnostics.event(this,problem);}
 }
 private static String errorText(Exception e){
  String message=e.getMessage();return e.getClass().getSimpleName()+(message==null?"":": "+message.replace('\n',' ').substring(0,Math.min(200,message.length())));
 }
 private void saveRetry(){Config.prefs(this).edit().putInt("classificationFailures",detection.failures()).putLong("classificationRetryAt",detection.retryAt()).apply();}
 private void detectionFailed(String ip,String problem){
  detection.complete(ip,StateLogic.UNKNOWN);saveRetry();lastDetectionError=problem;
  Config.prefs(this).edit().putString("classificationError",problem).apply();
  detectionDetail=problem+"; automatic retry after "+new java.text.SimpleDateFormat("HH:mm:ss",java.util.Locale.US).format(new java.util.Date(detection.retryAt()));
  detail=detectionDetail;Diagnostics.event(this,"VPN lookup for "+ip+": "+detectionDetail);
 }
 private void classifyIp(String ip){
  if(!detection.observe(ip)){
   if(detection.state(ip,0)!=StateLogic.UNKNOWN)detectionDetail=label(detection.state(ip,0))+" (saved classification)";
   else if(detection.retryAt()>System.currentTimeMillis())detectionDetail=lastDetectionError+"; automatic retry after "+new java.text.SimpleDateFormat("HH:mm:ss",java.util.Locale.US).format(new java.util.Date(detection.retryAt()));
   return;
  }
  final long id=++detectionId;
  if(detecting!=null)detecting.cancel(true);
  detail=detectionDetail="Checking VPN classification";resolvedIp="";resolvedName="";
  Diagnostics.event(this,"VPN lookup started: "+ip);Config.countQuery(this);
  final Runnable timeout=()->{if(!destroyed&&id==detectionId){detectionId++;detectionFailed(ip,"VPN lookup timed out (10 seconds)");if(detecting!=null)detecting.cancel(true);}};
  main.postDelayed(timeout,10000);
  try{detecting=detector.submit(()->{
   int result=StateLogic.UNKNOWN,used=-1,limit=0;String name="Location unavailable",problem="";
   try{
    JSONObject json=new JSONObject(request("https://proxycheck.io/v3/"+ip+key("?"),32768));
    if(!"ok".equals(json.optString("status")))throw new IOException("API status="+json.optString("status")+" "+json.optString("message"));
    Object vpn=json.getJSONObject(ip).getJSONObject("detections").get("vpn");
    if(!(vpn instanceof Boolean))throw new IOException("Invalid VPN detection response");
    result=((Boolean)vpn)?StateLogic.VPN:StateLogic.NORMAL;
    JSONObject data=json.getJSONObject(ip),network=data.optJSONObject("network"),location=data.optJSONObject("location");
    name=IpLabel.format(field(network,"hostname"),field(network,"provider"),field(location,"city_name"),field(location,"country_name"));
   }catch(Exception e){problem=errorText(e);}
   if(!Secrets.PROXYCHECK_KEY.isEmpty())try{
    JSONObject usage=new JSONObject(request("https://proxycheck.io/dashboard/export/usage/"+key("?"),4096));
    used=usage.getInt("Queries Today");limit=usage.getInt("Daily Limit");
   }catch(Exception e){Diagnostics.event(this,"proxycheck.io usage unavailable: "+errorText(e));}
   final int queriesToday=used,dailyLimit=limit;
   final int value=result;final String displayName=name, failure=problem;
   main.post(()->{
    if(destroyed||id!=detectionId)return;main.removeCallbacks(timeout);
    if(queriesToday>=0)Config.syncQuota(this,queriesToday,dailyLimit);
    if(value==StateLogic.UNKNOWN){detectionFailed(ip,failure);return;}
    detection.complete(ip,value);saveRetry();resolvedIp=ip;resolvedName=displayName;detail=detectionDetail=label(value);
    Config.prefs(this).edit().putString("classifiedIp",ip).putInt("classifiedState",value).putString("classifiedName",displayName).apply();
    Diagnostics.event(this,"VPN lookup succeeded: "+ip+" "+label(value));
   });
  });}catch(RejectedExecutionException e){main.removeCallbacks(timeout);detectionFailed(ip,"VPN lookup worker unavailable");}
 }
 private static String key(String prefix){return Secrets.PROXYCHECK_KEY.isEmpty()?"":prefix+"key="+Secrets.PROXYCHECK_KEY;}
 private static String field(JSONObject object,String key){return object==null||object.isNull(key)?"":object.optString(key,"");}
 static String ipDescription(){return lastIp.equals(resolvedIp)&&!resolvedName.isEmpty()?resolvedName:"Looking up IP details";}
 private String fetch(String endpoint) throws Exception{
  String ip=request(endpoint,64).trim();
  if(!StateLogic.ipv4(ip))throw new IOException("Not an IPv4 address");return ip;
 }
 private String request(String endpoint,int limit) throws Exception{
  HttpURLConnection c=(HttpURLConnection)new URL(endpoint).openConnection(java.net.Proxy.NO_PROXY);
  boolean classification=endpoint.startsWith("https://proxycheck.io/");
  c.setConnectTimeout(classification?4000:10000);c.setReadTimeout(classification?4000:10000);c.setUseCaches(false);c.setInstanceFollowRedirects(false);
  c.setRequestProperty("Cache-Control","no-cache");c.setRequestProperty("User-Agent","RouterVPNDot/0.16");
  try{
   int response=c.getResponseCode();if(response!=200)throw new IOException("HTTP "+response+" from "+new URL(endpoint).getHost());
   ByteArrayOutputStream b=new ByteArrayOutputStream();
   try(InputStream in=c.getInputStream()){int v;while((v=in.read())!=-1){if(b.size()>=limit)throw new IOException("Oversized response");b.write(v);}}
   return b.toString("UTF-8");
  }finally{c.disconnect();}
 }
 private class Dot extends View{
  private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
  Dot(){super(MonitorService.this);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}
  @Override protected void onDraw(Canvas canvas){
   int state=currentState(MonitorService.this);
   int color=state==StateLogic.VPN?0xFF34D399:state==StateLogic.NORMAL?0xFFF87171:0xFFFBBF24;
   int level=Config.prefs(MonitorService.this).getInt(state==StateLogic.VPN?"greenBrightness":"alertBrightness",state==StateLogic.VPN?15:35);
   boolean pulse=Config.prefs(MonitorService.this).getBoolean("pulse",true);
   double period=state==StateLogic.VPN?4000:state==StateLogic.NORMAL?1000:2000;
   double factor=pulse?0.15+0.85*(1+Math.sin(SystemClock.elapsedRealtime()*2*Math.PI/period))/2:1;
   float radius=Math.min(getWidth(),getHeight())/2f;
   paint.setColor(color);paint.setAlpha((int)(255*level/100.0*factor));canvas.drawCircle(getWidth()/2f,getHeight()/2f,radius,paint);
  }
 }
 @Override public void onDestroy(){
  Diagnostics.event(this,"Service destroyed; monitoring enabled="+Config.prefs(this).getBoolean("enabled",false));
  destroyed=true;instance=null;detectionId++;detector.shutdownNow();running=false;checkedAt=0;lastIp="";main.removeCallbacksAndMessages(null);worker.shutdownNow();
  if(callback!=null)try{connectivity.unregisterNetworkCallback(callback);}catch(IllegalArgumentException e){Diagnostics.event(this,"Network callback already removed");}
  if(dot!=null){removeOverlay(dot);dot=null;}
  if(banner!=null){removeOverlay(banner);banner=null;}
  if(!Config.prefs(this).getBoolean("enabled",false))AutoStart.cancel(this);
  stopForeground(true);super.onDestroy();
 }
 @Override public IBinder onBind(Intent i){return null;}
}
