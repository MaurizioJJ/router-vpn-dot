import local.routervpndot.*;
public class UpdateCheckTest {
 public static void main(String[] args){
  check(UpdateCheck.newer("v0.18","0.17"));check(!UpdateCheck.newer("v0.17","0.17"));check(!UpdateCheck.newer("v0.16","0.17"));
  check(UpdateCheck.newer("v0.17","0.9"));check(UpdateCheck.newer("v1.0","0.17"));check(UpdateCheck.newer("0.17.1","0.17"));
  check(!UpdateCheck.newer("v0.18-beta","0.18"));
  check(UpdateCheck.shouldAsk("v0.18","0.17",""));check(!UpdateCheck.shouldAsk("v0.18","0.17","v0.18"));check(UpdateCheck.shouldAsk("v0.19","0.17","v0.18"));
  System.out.println("10 update tests passed");
 }
 static void check(boolean b){if(!b)throw new AssertionError();}
}
