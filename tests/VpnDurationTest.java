import local.routervpndot.*;
public class VpnDurationTest {
 public static void main(String[] args){
  VpnDuration d=new VpnDuration();d.update(1,0);d.update(1,5000);check(d.elapsed(6000)==6000);
  d.update(0,6000);check(d.elapsed(6000)==-1);d.update(1,7000);check(d.elapsed(8000)==1000);
  d.update(1,20000);check(d.elapsed(20000)==0);d.update(2,21000);check(d.elapsed(21000)==-1);
  check(VpnDuration.format(90061000).equals("1d 1h 1m"));check(VpnDuration.format(0).equals("0d 0h 0m"));
  System.out.println("7 duration tests passed");
 }
 static void check(boolean b){if(!b)throw new AssertionError();}
}
