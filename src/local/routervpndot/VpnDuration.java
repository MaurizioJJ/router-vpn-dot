package local.routervpndot;
// How long the VPN has been detected, in wall-clock ms so it survives app updates, restarts and reboots.
// A "not detected" result resets it. A gap with no VPN result (checking, service restart, sleep) keeps it
// unless it lasts longer than GRACE.
public final class VpnDuration {
 public static final long GRACE=10*60000;
 private long since=-1,last=-1;
 public VpnDuration(){}
 public VpnDuration(long since,long last){if(since>=0&&last>=since){this.since=since;this.last=last;}}
 public void update(int state,long now){
  if(since>=0&&(state==StateLogic.NORMAL||now<last||now-last>GRACE))since=-1;
  if(state==StateLogic.VPN){if(since<0)since=now;last=now;}
 }
 public long since(){return since;}
 public long last(){return last;}
 public long elapsed(long now){return since<0? -1:Math.max(0,now-since);}
 public static String format(long ms){long m=Math.max(0,ms)/60000;return (m/1440)+"d "+((m/60)%24)+"h "+(m%60)+"m";}
}
