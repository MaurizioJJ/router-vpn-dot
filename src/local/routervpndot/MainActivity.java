package local.routervpndot;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;

public class MainActivity extends Activity {
 private LinearLayout body;
 private TextView status;
 
 private Button toggle;
 private QuotaRing quota;
 private final Handler handler=new Handler(Looper.getMainLooper());
 private final Runnable refresh=new Runnable(){public void run(){
  if(status!=null){
   int state=MonitorService.currentState(MainActivity.this);
   String age=MonitorService.checkedAt==0?"":("  •  "+((SystemClock.elapsedRealtime()-MonitorService.checkedAt)/1000)+"s ago");
   status.setText((MonitorService.running?MonitorService.label(state):"Monitoring stopped")+"\n"+(MonitorService.lastIp.isEmpty()?MonitorService.detail:MonitorService.lastIp+age+"  •  "+MonitorService.ipDescription())+"\n"+MonitorService.detail+MonitorService.connectionAge());
   status.setTextColor(!MonitorService.running?0xFFCBD5E1:state==StateLogic.VPN?0xFF34D399:state==StateLogic.NORMAL?0xFFF87171:0xFFFBBF24);
   toggle.setText(MonitorService.running?"Stop monitoring":"Start monitoring");
   quota.used=Math.min(1f,Config.queriesUsed(MainActivity.this)/(float)Config.queryLimit(MainActivity.this));
   quota.setBounds(0,0,toggle.getWidth(),toggle.getHeight());quota.invalidateSelf();
  }
  handler.postDelayed(this,1000);
 }};
 private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
 @Override public void onCreate(Bundle state){
  super.onCreate(state);
  getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
  ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setBackgroundColor(0xFF0B1020);
  body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(dp(48),dp(28),dp(48),dp(32));scroll.addView(body);setContentView(scroll);
  TextView title=text("ROUTER VPN DOT",28,0xFFF8FAFC);title.setTypeface(null,Typeface.BOLD);
  text("Version 0.13",14,0xFF94A3B8);
  status=text("Checking status",21,0xFFCBD5E1);status.setPadding(0,dp(14),0,dp(14));
  toggle=button("Start monitoring",()->{
   if(MonitorService.running){Config.prefs(this).edit().putBoolean("enabled",false).apply();stopService(new Intent(this,MonitorService.class));}
   else {if(!Settings.canDrawOverlays(this)){permission();return;}Config.prefs(this).edit().putBoolean("enabled",true).apply();startForegroundService(new Intent(this,MonitorService.class));}
  });
  quota=new QuotaRing();toggle.getOverlay().add(quota);
  button("Allow display over other apps",this::permission);
  text("GREEN = VPN detected   •   RED = VPN not detected   •   AMBER = unknown",16,0xFFCBD5E1);
  text("Public IP checked every 5 seconds. Successful classification is saved across restarts and reused only after a fresh matching IP check. No fixed IP list.",16,0xFF94A3B8);
  text("Failed classification stays amber and retries automatically with increasing delays. Detection is a public database classification, not proof of your router tunnel.",14,0xFF94A3B8);
  text("Appearance",22,0xFFF8FAFC);
  final String[] corners={"Top right","Top left","Bottom right","Bottom left"};
  Button corner=button("Corner: "+corners[Config.prefs(this).getInt("corner",0)],()->{});
  corner.setOnClickListener(v->{int n=(Config.prefs(this).getInt("corner",0)+1)%4;Config.prefs(this).edit().putInt("corner",n).apply();corner.setText("Corner: "+corners[n]);updateDot();});
  final int[] sizes={4,6,8,10,14,20};
  Button size=button("Dot size: "+Config.prefs(this).getInt("size",8)+" dp",()->{});
  size.setOnClickListener(v->{int now=Config.prefs(this).getInt("size",8),i=0;while(i<sizes.length && sizes[i]!=now)i++;int next=sizes[(i+1)%sizes.length];Config.prefs(this).edit().putInt("size",next).apply();size.setText("Dot size: "+next+" dp");updateDot();});
  brightness("greenBrightness","Green brightness",15);
  brightness("alertBrightness","Red / amber brightness",35);
  Switch pulse=new Switch(this);pulse.setText("Pulse the dot (slow green / faster red)");pulse.setTextSize(18);pulse.setPadding(dp(12),dp(16),dp(12),dp(16));pulse.setChecked(Config.prefs(this).getBoolean("pulse",true));pulse.setOnCheckedChangeListener((b,on)->Config.prefs(this).edit().putBoolean("pulse",on).apply());body.addView(pulse);
  Switch alerts=new Switch(this);alerts.setText("Show warning banner when VPN is off or unverified");alerts.setTextSize(18);alerts.setPadding(dp(12),dp(16),dp(12),dp(16));alerts.setChecked(Config.prefs(this).getBoolean("banner",true));alerts.setOnCheckedChangeListener((b,on)->Config.prefs(this).edit().putBoolean("banner",on).apply());body.addView(alerts);
  Switch boot=new Switch(this);boot.setText("Resume monitoring after reboot");boot.setTextSize(18);boot.setPadding(dp(12),dp(16),dp(12),dp(16));boot.setChecked(Config.prefs(this).getBoolean("boot",false));boot.setOnCheckedChangeListener((b,on)->Config.prefs(this).edit().putBoolean("boot",on).apply());body.addView(boot);
  button("Show diagnostics",()->new AlertDialog.Builder(this).setTitle("Service and crash history").setMessage("Current status: "+MonitorService.label(MonitorService.currentState(this))+"\nIP: "+MonitorService.lastIp+"\n"+MonitorService.detail+"\n\n"+Diagnostics.report(this)).setPositiveButton("Close",null).show());
  text("Privacy & limits",22,0xFFF8FAFC);
  text("This app contacts api.ipify.org, with checkip.amazonaws.com as a fallback, and sends each new public IP to proxycheck.io for VPN classification. Those services see your exit IP. Settings stay on this device. No account, router password, analytics, storage access or accessibility service is used.\n\nGreen means proxycheck.io classified the current IP as VPN. The last successful classification is saved across restarts. It is reused only when a fresh public-IP check matches; database classifications can become outdated. It does not verify every app, DNS or IPv6. Checks may be delayed during sleep. Android can stop the service, and some screens can hide overlays. Confirm the dot is visible in the full-screen apps you use.",14,0xFF94A3B8);
  toggle.requestFocus();
 }
 private void brightness(String key,String name,int defaultValue){
  Button b=button(name+": "+Config.prefs(this).getInt(key,defaultValue)+"%",()->{});
  b.setOnClickListener(v->{int value=Config.prefs(this).getInt(key,defaultValue);int[] levels={5,10,15,20,30,35,40,60,80,100};int i=0;while(i<levels.length && levels[i]<=value)i++;value=levels[i%levels.length];Config.prefs(this).edit().putInt(key,value).apply();b.setText(name+": "+value+"%");});
 }
 private void updateDot(){if(MonitorService.running)startForegroundService(new Intent(this,MonitorService.class));}
 private void permission(){
  try{startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:"+getPackageName())));}
  catch(ActivityNotFoundException e){new AlertDialog.Builder(this).setTitle("Overlay permission").setMessage("Open Settings → Apps → Special app access → Display over other apps, then allow Router VPN Dot.").setPositiveButton("OK",null).show();}
 }
 private TextView text(String value,int size,int color){TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(color);t.setPadding(0,dp(8),0,dp(8));body.addView(t);return t;}
 private Button button(String name,Runnable run){
  Button b=new Button(this);b.setText(name);b.setAllCaps(false);b.setTextSize(17);b.setTextColor(Color.WHITE);b.setGravity(Gravity.CENTER_VERTICAL|Gravity.LEFT);b.setPadding(dp(20),0,dp(20),0);
  LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(52));p.setMargins(0,dp(4),0,dp(4));body.addView(b,p);
  b.setOnFocusChangeListener((v,focus)->background(b,focus));background(b,false);b.setOnClickListener(v->run.run());return b;
 }
 private void background(View v,boolean focus){GradientDrawable d=new GradientDrawable();d.setColor(focus?0xFF164E63:0xFF1E293B);d.setCornerRadius(dp(8));d.setStroke(dp(focus?2:1),focus?0xFF67E8F9:0xFF334155);v.setBackground(d);}
 // Yellow outline that grows around the toggle as today's free proxycheck.io lookups are used up.
 private final class QuotaRing extends Drawable{
  float used;
  private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
  QuotaRing(){paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(3));paint.setColor(0xFFFACC15);}
  @Override public void draw(Canvas c){
   if(used<=0)return;
   RectF r=new RectF(getBounds());float half=paint.getStrokeWidth()/2;r.inset(half,half);
   Path outline=new Path(),part=new Path();outline.addRoundRect(r,dp(8),dp(8),Path.Direction.CW);
   PathMeasure m=new PathMeasure(outline,false);m.getSegment(0,m.getLength()*used,part,true);c.drawPath(part,paint);
  }
  @Override public void setAlpha(int a){paint.setAlpha(a);}
  @Override public void setColorFilter(ColorFilter f){paint.setColorFilter(f);}
  @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
 }
 private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
 @Override protected void onResume(){super.onResume();MonitorService.settingsVisible=true;AutoStart.resume(this,"App opened");handler.post(refresh);}
 @Override protected void onPause(){MonitorService.settingsVisible=false;handler.removeCallbacks(refresh);super.onPause();}
}
