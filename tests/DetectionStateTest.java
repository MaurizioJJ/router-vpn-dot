import local.routervpndot.*;
public class DetectionStateTest {
 static int n=0;static void check(boolean b){n++;if(!b)throw new AssertionError("Case "+n);}
 public static void main(String[] args){
 DetectionState d=new DetectionState();String a="203.0.113.1",b="203.0.113.2";
 check(d.observe(a,1000));check(!d.observe(a,2000));check(d.state(a,0)==0);
 d.complete(a,1,2000);check(d.state(a,0)==1);check(!d.observe(a,3000));
 check(d.state(a,150001)==0);check(d.state(a,-1)==0);check(d.state("",0)==0);
 check(d.observe(b,3000));check(d.state(b,0)==0);d.complete(a,1,4000);check(d.state(b,0)==0);
 d.complete(b,0,4000);check(d.retryAt()==19000);check(!d.observe(b,18999));check(d.observe(b,19000));
 d.complete(b,0,20000);check(d.retryAt()==50000);check(!d.observe(b,21000));
 DetectionState reboot=new DetectionState();reboot.restoreRetry(d.failures(),d.retryAt(),21000);
 check(!reboot.observe(b,21000));check(reboot.observe(b,50000));reboot.complete(b,1,51000);
 check(reboot.state(b,0)==1);check(reboot.retryAt()==0);check(reboot.failures()==0);
 DetectionState cached=new DetectionState();cached.restore(a,1);
 check(cached.state("",0)==0);check(cached.state(b,0)==0);check(cached.state(a,150001)==0);
 check(!cached.observe(a,1000));check(cached.state(a,0)==1);
 check(cached.observe(b,1001));check(cached.state(b,0)==0);
 cached.complete(b,2,1100);check(cached.state(b,0)==2);
 DetectionState invalid=new DetectionState();invalid.restore(a,0);check(invalid.observe(a,1000));check(!invalid.observe("bad",2000));
 for(int i=0;i<10;i++){long at=invalid.retryAt();invalid.complete(a,0,at);check(invalid.retryAt()-at<=1800000);check(invalid.observe(a,invalid.retryAt()));}
 check(invalid.failures()==5);
 DetectionState changed=new DetectionState();changed.observe(a,0);changed.complete(a,0,0);
 check(!changed.observe(b,100));check(changed.state(b,0)==0);check(changed.observe(b,15000));
 System.out.println(n+" dynamic detection tests passed");
 }
}
