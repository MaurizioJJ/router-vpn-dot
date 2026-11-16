package local.routervpndot;
public final class UpdateCheck {
 // True when a release tag such as "v0.18" is a higher version than the installed "0.17".
 public static boolean newer(String tag,String current){
  String[] a=tag.replaceFirst("^[vV]","").split("\\."),b=current.split("\\.");
  for(int i=0;i<Math.max(a.length,b.length);i++){int x=part(a,i),y=part(b,i);if(x!=y)return x>y;}
  return false;
 }
 // "Not now" asks again next time; "Skip this version" stays quiet until a newer tag appears.
 public static boolean shouldAsk(String tag,String current,String skipped){return newer(tag,current)&&!tag.equals(skipped);}
 private static int part(String[] p,int i){
  if(i>=p.length)return 0;
  String digits=p[i].replaceAll("\\D.*","");
  return digits.isEmpty()?0:Integer.parseInt(digits);
 }
}
