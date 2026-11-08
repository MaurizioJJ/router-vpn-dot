package local.routervpndot;
public final class VpnDuration {
 private long since=-1,last=-1;
 public void update(int state,long now){
  if(state!=StateLogic.VPN||last<0||now-last>9000||now<last)since=-1;
  if(state==StateLogic.VPN&&since<0)since=now;
  last=now;
 }
 public long elapsed(long now){return since<0? -1:Math.max(0,now-since);}
 public static String format(long ms){long m=Math.max(0,ms)/60000;return (m/1440)+"d "+((m/60)%24)+"h "+(m%60)+"m";}
}
