import local.routervpndot.*;
public class StartupBannerTest {
 static int n;static void check(boolean b){n++;if(!b)throw new AssertionError("Case "+n);}
 public static void main(String[] args){
  StartupBanner s=new StartupBanner();check(s.mode(0,0)==3);check(s.mode(0,20000)==3);
  check(s.mode(1,20001)==1);check(s.mode(0,20002)==0);
  s=new StartupBanner();check(s.mode(2,100)==2);check(s.mode(0,200)==0);
  // Early lookup failures leave the state unknown; only a result or 25 s ends checking.
  s=new StartupBanner();check(s.mode(0,1)==3);check(s.mode(0,15000)==3);check(s.mode(1,15001)==1);
  s=new StartupBanner();check(s.mode(0,24999)==3);check(s.mode(0,25000)==0);
  check(s.mode(0,0)==0);check(new StartupBanner().mode(0,0)==3);
  System.out.println(n+" startup banner tests passed");
 }
}
