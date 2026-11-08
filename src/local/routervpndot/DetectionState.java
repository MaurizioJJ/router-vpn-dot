package local.routervpndot;
public final class DetectionState {
 private String observed="";
 private int classification=StateLogic.UNKNOWN;
 private boolean pending=false;
 private int failures=0;
 private long retryAt=0;
 public boolean observe(String ip){return observe(ip,System.currentTimeMillis());}
 public boolean observe(String ip,long now){
  if(!StateLogic.ipv4(ip))return false;
  if(!ip.equals(observed)){observed=ip;classification=StateLogic.UNKNOWN;pending=false;}
  if(classification!=StateLogic.UNKNOWN||pending||now<retryAt)return false;
  pending=true;return true;
 }
 public void restore(String ip,int value){
  if(StateLogic.ipv4(ip)&&(value==StateLogic.VPN||value==StateLogic.NORMAL)){observed=ip;classification=value;pending=false;}
 }
 public void restoreRetry(int count,long at,long now){
  failures=Math.max(0,Math.min(5,count));retryAt=Math.max(0,Math.min(at,now+1800000));
 }
 public void complete(String ip,int result){complete(ip,result,System.currentTimeMillis());}
 public void complete(String ip,int result,long now){
  if(!ip.equals(observed))return;
  pending=false;
  classification=(result==StateLogic.VPN||result==StateLogic.NORMAL)?result:StateLogic.UNKNOWN;
  if(classification==StateLogic.UNKNOWN){long[] delays={15000,30000,60000,300000,1800000};retryAt=now+delays[Math.min(failures,4)];failures=Math.min(5,failures+1);}
  else{failures=0;retryAt=0;}
 }
 public long retryAt(){return retryAt;}
 public int failures(){return failures;}
 public int state(String current,long age){
  if(age<0||age>9000||!StateLogic.ipv4(current)||!current.equals(observed))return StateLogic.UNKNOWN;
  return classification;
 }
}
