package local.routervpndot;
public final class StartupBanner {
 public static final int CHECKING=3;
 private boolean starting=true;
 public int mode(int state,long elapsed){
  if(state!=StateLogic.UNKNOWN||elapsed>=25000||elapsed<0)starting=false;
  return starting?CHECKING:state;
 }
}
