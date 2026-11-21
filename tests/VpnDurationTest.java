import local.routervpndot.*;
public class VpnDurationTest {
 public static void main(String[] args){
  long g=VpnDuration.GRACE;
  VpnDuration d=new VpnDuration();d.update(1,0);d.update(1,5000);check(d.elapsed(6000)==6000);
  d.update(0,60000);check(d.elapsed(60000)==60000); // checking keeps the time
  d.update(1,70000);check(d.elapsed(70000)==70000);
  d.update(2,71000);check(d.elapsed(71000)==-1); // not detected resets
  d.update(1,80000);d.update(0,80000+g+1);d.update(1,80000+g+1);check(d.elapsed(80000+g+1)==0); // long gap resets
  VpnDuration r=new VpnDuration(1000,500000);r.update(0,520000);r.update(1,530000);check(r.elapsed(530000)==529000); // restart keeps it
  VpnDuration old=new VpnDuration(1000,2000);old.update(1,2000+g+1);check(old.elapsed(2000+g+1)==0); // stale save starts over
  VpnDuration back=new VpnDuration(1000,5000);back.update(1,4000);check(back.elapsed(4000)==0); // clock went back
  check(new VpnDuration(5000,1000).elapsed(6000)==-1);
  check(VpnDuration.format(90061000).equals("1d 1h 1m"));check(VpnDuration.format(0).equals("0d 0h 0m"));
  System.out.println("11 duration tests passed");
 }
 static void check(boolean b){if(!b)throw new AssertionError();}
}
