package local.routervpndot;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import java.util.*;

public class MainActivity extends Activity {
 private LinearLayout body;
 private TextView status;
 
 private LinearLayout root,statusCard;
 private TextView headline,liveDot;
 private Button pause,toggle,gatewayButton,apiKeyButton;
 private Switch kill;
 private boolean routerWorking=false,updatingKill=false,initialFocus=true;
 private QuotaRing quota;
 private final Handler handler=new Handler(Looper.getMainLooper());
 private final Runnable refresh=new Runnable(){public void run(){
  if(status!=null){
   int state=MonitorService.currentState(MainActivity.this);
   String age=MonitorService.checkedAt==0?"":("  •  "+((SystemClock.elapsedRealtime()-MonitorService.checkedAt)/1000)+"s ago");
   boolean paused=Config.prefs(MainActivity.this).getBoolean("vpnPaused",false);
   headline.setText(paused?"VPN paused for this TV":MonitorService.running?(state==StateLogic.VPN?"VPN ON · "+MonitorService.ipDescription():MonitorService.label(state)):"Monitoring stopped");
   // Light card for the three clear states (paused / stopped / VPN on), dark card otherwise.
   int light=paused?Ui.LIGHT_YELLOW:!MonitorService.running?Ui.LIGHT_RED:state==StateLogic.VPN?Ui.LIGHT_GREEN:0;
   int color=paused?Ui.DARK_YELLOW:!MonitorService.running?Ui.DARK_RED:state==StateLogic.VPN?(light!=0?Ui.DARK_GREEN:Ui.GREEN):state==StateLogic.NORMAL?Ui.RED:Ui.AMBER;
   Ui.tint(statusCard,light);
   headline.setTextColor(light!=0?Ui.INK:Ui.TEXT);status.setTextColor(light!=0?Ui.INK_MUTED:Ui.MUTED);
   liveDot.setTextColor(color);
   String tunnel=MonitorService.tunnelLine();
   status.setText((MonitorService.lastIp.isEmpty()?"":MonitorService.lastIp+age+" · "+MonitorService.ipDescription()+"\n")+(paused?"Resume VPN to restore this TV's routes":tunnel.isEmpty()?MonitorService.detail:tunnel+"\n"+MonitorService.detail)+MonitorService.connectionAge());
   toggle.setText(MonitorService.running?"Stop monitoring":"Start monitoring");
   toggle.setEnabled(!paused&&!routerWorking);
   pause.setText(paused?"Resume VPN":"Pause VPN");
   quota.used=Math.min(1f,Config.queriesUsed(MainActivity.this)/(float)Config.queryLimit(MainActivity.this));
   quota.setBounds(0,0,toggle.getWidth(),toggle.getHeight());quota.invalidateSelf();
  }
  handler.postDelayed(this,1000);
 }};
 private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
 // API 26 fullscreen compatibility; the API 30 replacement is unavailable on older TVs.
 @SuppressWarnings("deprecation")
 @Override public void onCreate(Bundle state){
  super.onCreate(state);
  getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
  ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setBackgroundColor(Ui.BG);
  FrameLayout frame=new FrameLayout(this);scroll.addView(frame);
  root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(32),dp(28),dp(32),dp(32));
  FrameLayout.LayoutParams content=new FrameLayout.LayoutParams(Math.min(dp(1100),getResources().getDisplayMetrics().widthPixels),-2,Gravity.TOP|Gravity.CENTER_HORIZONTAL);frame.addView(root,content);body=root;setContentView(scroll);
  text("ROUTER VPN DOT · Version 0.19",18,Ui.MUTED);
  statusCard=card(null,null);
  LinearLayout statusRow=new LinearLayout(this);statusRow.setGravity(Gravity.CENTER_VERTICAL);body.addView(statusRow);
  liveDot=new TextView(this);liveDot.setText("●");liveDot.setTextSize(48);liveDot.setPadding(0,0,dp(20),0);statusRow.addView(liveDot);
  headline=new TextView(this);headline.setTextSize(28);headline.setTextColor(Ui.TEXT);headline.setTypeface(null,Typeface.BOLD);statusRow.addView(headline,new LinearLayout.LayoutParams(0,-2,1));
  statusRow.addView(infoButton("Status","Card colour:\n• Light green: VPN on, this TV's public IP is a VPN exit.\n• Light yellow: VPN paused for this TV (Resume VPN to turn it back on).\n• Light red: monitoring stopped, nothing is being checked.\n• Dark: checking, VPN not detected, or a warning (read the text).\n\nThe lines below show the public IP, how long ago it was checked, who owns it, and the router tunnel status."));
  status=text("Checking status",18,Ui.MUTED);
  body=root;LinearLayout actions=new LinearLayout(this);root.addView(actions);body=actions;
  pause=button("Pause VPN",()->routeAction("pause",false));
  toggle=button("Start monitoring",()->{
   if(MonitorService.running){Config.prefs(this).edit().putBoolean("enabled",false).apply();stopService(new Intent(this,MonitorService.class));}
   else startMonitoring("start");
  });
  quota=new QuotaRing();toggle.getOverlay().add(quota);
  Button country=button("Switch server",this::switchCountry);
  for(Button action:new Button[]{pause,toggle,country}){LinearLayout.LayoutParams ap=new LinearLayout.LayoutParams(0,dp(72),1);ap.setMargins(dp(4),dp(12),dp(4),dp(12));action.setLayoutParams(ap);}
  body=root;routerSection();
  card("APPEARANCE","How the small dot looks on top of other apps.\n\n• Corner: where the dot sits.\n• Dot size: its diameter.\n• Green brightness / Red & amber brightness: how strong it shows in each state (keep green dim so it doesn't distract).\n• Pulse: green pulses slowly, red and amber faster.\n• Warning banner: a short text banner when the VPN is off or can't be verified.");
  final String[] corners={"Top right","Top left","Bottom right","Bottom left"};
  Button corner=button("Corner: "+corners[Config.prefs(this).getInt("corner",0)],()->{});
  corner.setOnClickListener(v->{int n=(Config.prefs(this).getInt("corner",0)+1)%4;Config.prefs(this).edit().putInt("corner",n).apply();corner.setText("Corner: "+corners[n]);updateDot();});
  final int[] sizes={4,6,8,10,14,20};
  Button size=button("Dot size: "+Config.prefs(this).getInt("size",8)+" dp",()->{});
  size.setOnClickListener(v->{int now=Config.prefs(this).getInt("size",8),i=0;while(i<sizes.length && sizes[i]!=now)i++;int next=sizes[(i+1)%sizes.length];Config.prefs(this).edit().putInt("size",next).apply();size.setText("Dot size: "+next+" dp");updateDot();});
  brightness("greenBrightness","Green brightness",15);
  brightness("alertBrightness","Red / amber brightness",35);
  preferenceSwitch("pulse","Pulse the dot (slow green / faster red)",true);
  preferenceSwitch("banner","Show warning banner",true);
  card("ADVANCED","• Resume monitoring after reboot: starts the dot by itself after a power cut or restart.\n• Allow display over other apps: the Android permission the dot needs.\n• Diagnostics: service and error history, useful when something looks wrong.\n• Forget gateway: removes the router address and API key from this TV (Resume VPN first if paused).\n• About & privacy: what the app contacts and what green really means."+(Updater.enabled()?"\n• Check for updates: looks for a newer release on GitHub now. The app also asks by itself when it opens and after a restart: Not now asks again next time, Skip this version waits for the next one.":""));
  preferenceSwitch("boot","Resume monitoring after reboot",false);
  button("Allow display over other apps",this::permission);
  button("Diagnostics",()->new AlertDialog.Builder(this).setTitle("Service and crash history").setMessage("Current status: "+MonitorService.label(MonitorService.currentState(this))+"\nIP: "+MonitorService.lastIp+"\n"+MonitorService.detail+"\n\n"+Diagnostics.report(this)).setPositiveButton("Close",null).show());
  button("Forget gateway",()->{
   if(routerWorking){routerStatus.setText("Wait for the current router operation.");return;}
   if(Config.prefs(this).getBoolean("vpnPaused",false)){routerStatus.setText("Resume VPN before forgetting the gateway so the paused routes can be restored.");return;}
   Config.prefs(this).edit().remove("unifiGateway").remove("unifiApiKey").remove("unifiPin").remove("unifiVpnId").remove("unifiRouteIds").remove("killSwitch").apply();routerStatus.setText("Gateway forgotten.");gatewayButton.setText("Gateway: not set");apiKeyButton.setText("UniFi API key: not set");setKill(false);
  });
  if(Updater.enabled())button("Check for updates",()->checkUpdate(true));
  button("About & privacy ▸",this::about);
  pause.requestFocus();
  if(new UnifiClient(this).configured())checkClient();
 }
 // Section label with an (i) button on the right that explains the card, then the card itself.
 private LinearLayout card(String label,String info){
  body=root;
  if(label!=null){
   LinearLayout head=new LinearLayout(this);head.setGravity(Gravity.CENTER_VERTICAL);root.addView(head);
   TextView t=new TextView(this);t.setText(label);t.setTextSize(18);t.setTextColor(Ui.MUTED);head.addView(t,new LinearLayout.LayoutParams(0,-2,1));
   head.addView(infoButton(label,info));
  }
  LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.VERTICAL);card.setPadding(dp(20),dp(14),dp(20),dp(14));Ui.surface(card,false);
  LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,dp(8),0,dp(12));root.addView(card,p);body=card;
  return card;
 }
 private Button infoButton(String title,String info){
  Button b=new Button(this);b.setText("i");b.setAllCaps(false);b.setTextSize(18);b.setTypeface(null,Typeface.BOLD);b.setTextColor(Ui.ACCENT);b.setPadding(0,0,0,0);
  Ui.focus(b);b.setOnClickListener(v->new AlertDialog.Builder(this).setTitle(title).setMessage(info).setPositiveButton("Close",null).show());
  b.setLayoutParams(new LinearLayout.LayoutParams(dp(48),dp(48)));return b;
 }
 private void preferenceSwitch(String key,String label,boolean fallback){Switch v=Ui.toggle(this,label,Config.prefs(this).getBoolean(key,fallback));body.addView(v);v.setOnCheckedChangeListener((b,on)->Config.prefs(this).edit().putBoolean(key,on).apply());}
 private boolean startMonitoring(String source){
  if(!Settings.canDrawOverlays(this)){permission();return false;}
  Config.prefs(this).edit().putBoolean("enabled",true).apply();
  try{startForegroundService(new Intent(this,MonitorService.class).setAction("CHECK").putExtra("source",source));return true;}
  catch(RuntimeException e){Config.prefs(this).edit().putBoolean("enabled",false).apply();routerStatus.setText("Monitoring could not start: "+e.getClass().getSimpleName());Diagnostics.event(this,"monitor_start result=failure error_type="+e.getClass().getSimpleName());return false;}
 }
 private void about(){new AlertDialog.Builder(this).setTitle("Router VPN Dot 0.19 · About & privacy").setMessage(
  "GREEN = VPN detected · RED = VPN not detected · AMBER = unknown or routing warning.\n\nPublic IP checked every 60 seconds and at once on any network change or router switch. UniFi routers only: the gateway's tunnel status is read every 15 seconds and replaces the public-IP check while green. Successful classification is saved across restarts and reused only after a fresh matching IP check. No fixed IP list. Failed classification stays amber and retries automatically with increasing delays. Detection is a public database classification, not proof of your router tunnel.\n\nPause VPN disables only this TV's enabled VPN routes. Resume restores exactly those routes and starts monitoring. Stop monitoring leaves router routes alone. Kill switch blocks internet when the tunnel drops; off allows fallback. A connected tunnel with a normal public IP is checked for missing or overriding routes once per new IP.\n\nThis app contacts api.ipify.org, with checkip.amazonaws.com as a fallback, and sends each new public IP to proxycheck.io for VPN classification. Those services see your exit IP. Settings stay on this device. If you set up the router VPN switch, the UniFi API key is stored on this device and sent only to your gateway; the country list and server choice come from api.nordvpn.com. "+(Updater.enabled()?"Update checks call api.github.com (or github.com) when the app opens and after a restart, and the APK is downloaded from GitHub only when you choose Update. ":"")+"No account login, router password, analytics, storage access or accessibility service is used.\n\nGreen means proxycheck.io classified the current IP as VPN. Database classifications can become outdated. It does not verify every app, DNS or IPv6. Checks may be delayed during sleep. Android can stop the service, and some screens can hide overlays. Confirm the dot is visible in the full-screen apps you use.").setPositiveButton("Close",null).show();}
 private void brightness(String key,String name,int defaultValue){
  Button b=button(name+": "+Config.prefs(this).getInt(key,defaultValue)+"%",()->{});
  b.setOnClickListener(v->{int value=Config.prefs(this).getInt(key,defaultValue);int[] levels={5,10,15,20,30,35,40,60,80,100};int i=0;while(i<levels.length && levels[i]<=value)i++;value=levels[i%levels.length];Config.prefs(this).edit().putInt(key,value).apply();b.setText(name+": "+value+"%");});
 }
 // Router VPN server switch: UniFi gateway + NordVPN WireGuard client. See README "Switch the router's VPN server".
 private TextView routerStatus;
 private void routerSection(){
  card("ROUTER","Controls the VPN on your UniFi router for this TV.\n\n• Kill switch: ON = if the VPN tunnel drops, this TV has no internet (nothing leaks out on your home line). OFF = it falls back to the normal internet and the dot turns red.\n• Refresh VPN status: re-reads the router's NordVPN tunnel and whether it's connected.\n• Pick another VPN client: only needed if the router has more than one NordVPN tunnel.\n• Re-detect this TV's routes: finds again which UniFi route sends this TV into the VPN. Pause VPN and Kill switch act on that route. Use it after changing routes in UniFi.\n• Gateway / UniFi API key: the router address and the key the app uses to talk to it.");
  kill=Ui.toggle(this,"Kill switch",Config.prefs(this).getBoolean("killSwitch",false));body.addView(kill);
  kill.setOnCheckedChangeListener((b,on)->{if(!updatingKill)routeAction("kill",on);});
  routerStatus=text("Set the gateway and API key, then Refresh VPN status.",18,Ui.MUTED);
  button("Refresh VPN status",this::checkClient);
  button("Pick another VPN client",()->background(c->{List<UnifiClient.VpnClient> all=c.clients();if(all.isEmpty())return "No WireGuard VPN client on this gateway.";runOnUiThread(()->pickClient(all));return "Pick the VPN client to control.";}));
  button("Re-detect this TV's routes",()->{if(routerWorking||Config.prefs(this).getBoolean("vpnPaused",false)){routerStatus.setText("Resume VPN and wait for the current router operation before changing routes.");return;}Config.prefs(this).edit().remove("unifiRouteIds").apply();routeAction("read",false);});
  gatewayButton=button("Gateway: "+Config.prefs(this).getString("unifiGateway","not set"),()->ask("UniFi gateway address","192.168.1.1",false,value->{
   if(routerWorking){routerStatus.setText("Wait for the current router operation.");return;}
   if(Config.prefs(this).getBoolean("vpnPaused",false)){routerStatus.setText("Resume VPN before changing gateway.");return;}
   String url=RouterVpn.gateway(value);if(url.isEmpty()){routerStatus.setText("Use an https gateway IP or name.");return;}
   Config.prefs(this).edit().putString("unifiGateway",url).remove("unifiPin").remove("unifiVpnId").remove("unifiRouteIds").remove("killSwitch").apply();routerStatus.setText("Gateway: "+url);gatewayButton.setText("Gateway: "+url);setKill(false);
  }));
  apiKeyButton=button("UniFi API key: "+(Config.prefs(this).getString("unifiApiKey","").isEmpty()?"not set":"saved"),()->ask("UniFi API key","",true,value->{if(routerWorking){routerStatus.setText("Wait for the current router operation.");return;}if(!value.trim().isEmpty())Config.prefs(this).edit().putString("unifiApiKey",value.trim()).apply();apiKeyButton.setText("UniFi API key: "+(Config.prefs(this).getString("unifiApiKey","").isEmpty()?"not set":"saved"));routerStatus.setText("UniFi API key setting saved.");}));
 }
 private void checkClient(){final boolean[] readRoutes={false};background(c->{
   List<UnifiClient.VpnClient> all=c.clients();
   UnifiClient.VpnClient v=c.selected(all);
   if(v==null){if(!all.isEmpty())runOnUiThread(()->pickClient(all));return "No VPN client selected. Use Pick another VPN client.";}
   String st=c.status(v);readRoutes[0]=true;
   return "VPN client: "+v.name+" · "+st;
  },()->{if(readRoutes[0])routeAction("read",false);});}
 private void switchCountry(){
  background(c->{
   List<UnifiClient.VpnClient> all=c.clients();UnifiClient.VpnClient v=c.selected(all);
   if(v==null)return all.isEmpty()?"No WireGuard VPN client on this gateway. Create one first: see the README.":"Use Pick another VPN client first.";
   List<UnifiClient.Country> countries=UnifiClient.countries();
   runOnUiThread(()->pickCountry(v,countries));
   return "Pick a country for "+v.name+".";
  });
 }
 private void pickClient(List<UnifiClient.VpnClient> all){
  String[] names=new String[all.size()];for(int i=0;i<names.length;i++)names[i]=all.get(i).name+" ("+all.get(i).peerIp+")";
  new AlertDialog.Builder(this).setTitle("WireGuard VPN client to control").setItems(names,(d,i)->{if(routerWorking){routerStatus.setText("Wait for the current router operation.");return;}if(Config.prefs(this).getBoolean("vpnPaused",false)){routerStatus.setText("Resume VPN before changing client.");return;}Config.prefs(this).edit().putString("unifiVpnId",all.get(i).id).remove("unifiRouteIds").apply();routeAction("read",false);routerStatus.setText("Using "+all.get(i).name+".");}).show();
 }
 // The client's current country goes first, so a server change in the same country is one click.
 private void pickCountry(UnifiClient.VpnClient v,List<UnifiClient.Country> all){
  List<UnifiClient.Country> countries=new ArrayList<>(all);String now=RouterVpn.countryCode(v.name);
  for(int i=0;i<countries.size();i++)if(countries.get(i).code.equalsIgnoreCase(now)){countries.add(0,countries.remove(i));break;}
  String[] names=new String[countries.size()];for(int i=0;i<names.length;i++)names[i]=countries.get(i).name+(i==0&&countries.get(0).code.equalsIgnoreCase(now)?" (current, other server)":"");
  new AlertDialog.Builder(this).setTitle("Switch "+v.name+" to").setItems(names,(d,i)->background(c->{
   UnifiClient.Country country=countries.get(i);
   List<UnifiClient.Server> servers=UnifiClient.servers(country);
   runOnUiThread(()->pickServer(v,country,servers));
   return "Pick a "+country.name+" server.";
  })).show();
 }
 private void pickServer(UnifiClient.VpnClient v,UnifiClient.Country country,List<UnifiClient.Server> servers){
  String[] names=new String[servers.size()];
  for(int i=0;i<names.length;i++){UnifiClient.Server s=servers.get(i);names[i]=RouterVpn.serverLabel(s.hostname,s.ip,s.load,v.peerIp);}
  new AlertDialog.Builder(this).setTitle(country.name+" servers, least loaded first").setItems(names,(d,i)->background(c->{
   if(servers.get(i).ip.equals(v.peerIp))return "Already on "+servers.get(i).ip+".";
   String server=c.switchTo(v,country,servers.get(i));
   runOnUiThread(()->routerStatus.setText("Switching to "+server+"…"));
   String ip=server.substring(server.lastIndexOf('(')+1,server.length()-1);
   for(int n=0;n<20;n++){ // the gateway applies the change within a minute
    Thread.sleep(3000);
    String st=c.status(v);
    if(st.equals("CONNECTED to "+ip)){
     if(MonitorService.running)startForegroundService(new Intent(this,MonitorService.class).setAction("CHECK").putExtra("source","router switch")); // new exit IP: check now, not at the next poll
     return country.name+": connected to "+server+".\nThe dot turns green once the new exit is classified.";}
   }
   return country.name+": "+server+" saved, but the tunnel hasn't reported connected after 60 s. Press Refresh VPN status.";
  })).show();
 }
 private void setKill(boolean on){updatingKill=true;kill.setChecked(on);updatingKill=false;}
 private void routeAction(String action,boolean on){
  final boolean paused=Config.prefs(this).getBoolean("vpnPaused",false);
  if(action.equals("pause")&&paused&&!Settings.canDrawOverlays(this)){permission();return;}
  // Show the persisted value until the router confirms every write.
  if(action.equals("kill"))setKill(Config.prefs(this).getBoolean("killSwitch",false));
  final boolean[] resume={false};
  background(c->{
   UnifiClient.VpnClient vpn=c.selected(c.clients());
   if(vpn==null)return "Use Pick another VPN client first.";
   UnifiClient.Routes routes=c.routes(vpn);
   if(routes.mine.isEmpty()&&!(action.equals("pause")&&paused)){
    runOnUiThread(()->pickRoutes(routes,vpn,action,on));return "Choose this TV's VPN route, then retry the action.";
   }
   Set<String> ids=new HashSet<>();boolean allKill=RouterVpn.allKillSwitch(routes.mine);
   for(RouterVpn.Route r:routes.mine)ids.add(r.id);
   if(action.equals("kill")){
    c.changeRoutes(routes,ids,"kill_switch_enabled",on);
    Config.prefs(this).edit().putBoolean("killSwitch",on).apply();
    Diagnostics.event(this,"route_action=kill_switch result=success enabled="+on+" route_count="+ids.size());
    runOnUiThread(()->setKill(on));return "Kill switch "+(on?"on: internet blocked if tunnel drops.":"off: internet can fall back if tunnel drops.");
   }
   if(action.equals("pause")){
    if(paused){
     Set<String> restore=new HashSet<>(Config.prefs(this).getStringSet("pausedRouteIds",Collections.emptySet()));
     Set<String> valid=new HashSet<>();for(RouterVpn.Route r:routes.all)if(r.networkId.equals(vpn.id))valid.add(r.id);
     if(!valid.containsAll(restore))throw new java.io.IOException("Paused routes changed. Restore them in UniFi before resuming.");
     c.changeRoutes(routes,restore,"enabled",true);
     Config.prefs(this).edit().remove("pausedRouteIds").remove("vpnPaused").apply();
     resume[0]=true;Diagnostics.event(this,"route_action=resume result=success route_count="+restore.size());return "VPN routes restored.";
    }
    ids=RouterVpn.enabledIds(routes.mine);
    c.changeRoutes(routes,ids,"enabled",false);
    Config.prefs(this).edit().putStringSet("pausedRouteIds",ids).putString("unifiVpnId",vpn.id).putBoolean("vpnPaused",true).putBoolean("enabled",false).apply();
    Diagnostics.event(this,"route_action=pause result=success route_count="+ids.size());
    stopService(new Intent(this,MonitorService.class));return "VPN paused for this TV";
   }
   final boolean flag=allKill;Config.prefs(this).edit().putBoolean("killSwitch",flag).apply();runOnUiThread(()->setKill(flag));
   return "VPN client: "+vpn.name+" · "+routes.mine.size()+" TV route(s) · "+c.status(vpn);
  },()->{if(resume[0])startMonitoring("resume");});
 }
 private void pickRoutes(UnifiClient.Routes routes,UnifiClient.VpnClient vpn,String action,boolean on){
  List<RouterVpn.Route> options=new ArrayList<>();
  for(RouterVpn.Route r:routes.all)if(r.networkId.equals(vpn.id))options.add(r);
  if(options.isEmpty()){routerStatus.setText("No traffic routes use "+vpn.name+". Create one in UniFi first.");return;}
  String[] labels=new String[options.size()];boolean[] selected=new boolean[options.size()];
  for(int i=0;i<labels.length;i++)labels[i]=options.get(i).description+" · "+options.get(i).macs;
  new AlertDialog.Builder(this).setTitle("VPN routes for this TV").setMultiChoiceItems(labels,selected,(dialog,index,checked)->selected[index]=checked)
   .setPositiveButton("Use routes",(dialog,which)->{
    Set<String> ids=new HashSet<>();for(int i=0;i<selected.length;i++)if(selected[i])ids.add(options.get(i).id);
    if(ids.isEmpty()){routerStatus.setText("No route selected. No router changes made.");return;}
    Config.prefs(this).edit().putStringSet("unifiRouteIds",ids).apply();routeAction(action,on);
   }).setNegativeButton("Cancel",null).show();
 }
 private interface RouterTask{String run(UnifiClient c) throws Exception;}
 private void background(RouterTask task){background(task,()->{});}
 private void background(RouterTask task,Runnable complete){
  if(routerWorking){routerStatus.setText("A router operation is already running.");return;}
  UnifiClient c=new UnifiClient(this);
  if(!c.configured()){routerStatus.setText("Set the gateway and API key first.");return;}
  routerWorking=true;pause.setEnabled(false);kill.setEnabled(false);
  routerStatus.setText("Working…");
  new Thread(()->{
   String out;
   try{out=task.run(c);}catch(Exception e){Diagnostics.event(this,"router_operation result=failure error_type="+e.getClass().getSimpleName());out="Failed: "+(e.getMessage()==null?e.getClass().getSimpleName():e.getMessage());}
   String shown=out;runOnUiThread(()->{routerWorking=false;pause.setEnabled(true);kill.setEnabled(true);routerStatus.setText(shown);complete.run();if(!routerWorking&&initialFocus){pause.requestFocus();initialFocus=false;}});
  }).start();
 }
 // Asks about a newer GitHub release. Automatic checks stay silent on errors and skipped versions.
 private static long lastAutoCheck;
 private AlertDialog updateDialog;
 private void checkUpdate(boolean manual){
  if(!Updater.enabled()||updateDialog!=null&&updateDialog.isShowing())return;
  // ponytail: automatic checks at most hourly per app process, so returning from Settings doesn't re-ask.
  if(!manual){if(lastAutoCheck!=0&&SystemClock.elapsedRealtime()-lastAutoCheck<3600000)return;lastAutoCheck=SystemClock.elapsedRealtime();}
  new Thread(()->{
   Updater.Release r=null;String error=null;
   try{r=Updater.latest();}catch(Exception e){error=e.getMessage()==null?e.getClass().getSimpleName():e.getMessage();Diagnostics.event(this,"update check failed: "+error);}
   Updater.Release found=r;String failed=error;
   runOnUiThread(()->{
    if(isFinishing()||isDestroyed()||updateDialog!=null&&updateDialog.isShowing())return;
    String have=Updater.current(this);
    if(found==null){if(manual)toast("Update check failed: "+failed);return;}
    boolean ask=manual?found.apk!=null&&UpdateCheck.newer(found.tag,have):Updater.shouldAsk(this,found);
    if(!ask){if(manual)toast("You have the latest version ("+have+").");return;}
    String notes=found.notes.length()>1500?found.notes.substring(0,1500)+"…":found.notes;
    updateDialog=new AlertDialog.Builder(this).setTitle("Update available: "+found.tag).setMessage("You have "+have+".\n\n"+notes)
     .setPositiveButton("Update",(d,w)->install(found))
     .setNegativeButton("Not now",null)
     .setNeutralButton("Skip this version",(d,w)->Config.prefs(this).edit().putString("updateSkipped",found.tag).apply()).show();
   });
  }).start();
 }
 private void install(Updater.Release r){
  if(!getPackageManager().canRequestPackageInstalls()){
   try{startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:"+getPackageName())));}catch(ActivityNotFoundException e){Diagnostics.event(this,"unknown sources screen missing");}
   toast("Allow Router VPN Dot to install apps (Settings → Security → Unknown sources), then choose Advanced → Check for updates.");
   return;
  }
  toast("Downloading "+r.tag+"…");
  new Thread(()->{
   try{Updater.install(this,r);}
   catch(Exception e){String m=e.getMessage()==null?e.getClass().getSimpleName():e.getMessage();runOnUiThread(()->toast("Update download failed: "+m));}
  }).start();
 }
 private void toast(String m){Toast.makeText(this,m,Toast.LENGTH_LONG).show();}
 private interface Answer{void take(String s);}
 private void ask(String title,String hint,boolean secret,Answer answer){
  EditText in=new EditText(this);in.setHint(hint);in.setSingleLine(true);
  if(secret)in.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
  new AlertDialog.Builder(this).setTitle(title).setView(in).setPositiveButton("Save",(d,w)->answer.take(in.getText().toString())).setNegativeButton("Cancel",null).show();
 }
 private void updateDot(){if(MonitorService.running)startForegroundService(new Intent(this,MonitorService.class));}
 private void permission(){
  try{startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:"+getPackageName())));}
  catch(ActivityNotFoundException e){new AlertDialog.Builder(this).setTitle("Overlay permission").setMessage("Open Settings → Apps → Special app access → Display over other apps, then allow Router VPN Dot.").setPositiveButton("OK",null).show();}
 }
 private TextView text(String value,int size,int color){TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(color);t.setPadding(0,dp(8),0,dp(8));body.addView(t);return t;}
 private Button button(String name,Runnable run){
  Button b=new Button(this);b.setText(name);b.setAllCaps(false);b.setTextSize(18);b.setTextColor(Ui.TEXT);b.setGravity(Gravity.CENTER_VERTICAL|Gravity.LEFT);b.setPadding(dp(20),0,dp(20),0);
  LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(52));p.setMargins(0,dp(4),0,dp(4));body.addView(b,p);
  Ui.focus(b);b.setOnClickListener(v->run.run());return b;
 }
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
  @SuppressWarnings("deprecation")
  @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
 }
 @Override protected void onResume(){super.onResume();MonitorService.settingsVisible=true;AutoStart.resume(this,"App opened");handler.post(refresh);checkUpdate(false);}
 @Override protected void onPause(){MonitorService.settingsVisible=false;handler.removeCallbacks(refresh);super.onPause();}
}
