package local.routervpndot;

// Pure helpers for the router VPN switch, kept free of Android classes so tests run on a plain JDK.
public final class RouterVpn {
 private RouterVpn(){}

 public static final class Route {
  public final String id,description,networkId;
  public final boolean enabled,killSwitch;
  public final java.util.List<String> macs,types;
  public Route(String id,String description,String networkId,boolean enabled,boolean killSwitch,java.util.List<String> macs,java.util.List<String> types){
   this.id=id;this.description=description;this.networkId=networkId;this.enabled=enabled;this.killSwitch=killSwitch;
   this.macs=new java.util.ArrayList<>(macs);this.types=new java.util.ArrayList<>(types);
  }
  public boolean targets(String mac){
   if(mac==null||mac.isEmpty())return false;
   for(int i=0;i<Math.min(macs.size(),types.size());i++)if("CLIENT".equals(types.get(i))&&mac.equalsIgnoreCase(macs.get(i)))return true;
   return false;
  }
 }
 public static java.util.List<Route> myRoutes(java.util.List<Route> routes,String mac,String networkId){
  java.util.List<Route> out=new java.util.ArrayList<>();
  for(Route r:routes)if(r.targets(mac)&&r.networkId.equals(networkId))out.add(r);
  return out;
 }
 public static String warning(java.util.List<Route> routes,String mac,String networkId,java.util.Map<String,String> names){
  if(mac==null||mac.isEmpty())return ""; // absence of identity is not evidence of missing routing
  boolean enabled=false;
  for(Route r:routes)if(r.targets(mac)&&r.enabled){
   if(!r.networkId.equals(networkId))return "Tunnel up, but route \""+r.description+"\" sends this TV to "+names.getOrDefault(r.networkId,r.networkId);
   enabled=true;
  }
  return enabled?"":"Tunnel up, but this TV isn't routed through it";
 }

 public static java.util.Set<String> enabledIds(java.util.List<Route> routes){
  java.util.Set<String> ids=new java.util.HashSet<>();for(Route r:routes)if(r.enabled)ids.add(r.id);return ids;
 }
 public static boolean allKillSwitch(java.util.List<Route> routes){
  if(routes.isEmpty())return false;for(Route r:routes)if(!r.killSwitch)return false;return true;
 }
 public interface RouteWrite {void write(String id,boolean restore) throws Exception;}
 public static void update(java.util.Set<String> ids,RouteWrite writer) throws Exception {
  java.util.List<String> attempted=new java.util.ArrayList<>();
  try{for(String id:ids){attempted.add(id);writer.write(id,false);}}
  catch(Exception failure){
   java.util.Collections.reverse(attempted);boolean rollbackFailed=false;
   for(String id:attempted)try{writer.write(id,true);}catch(Exception rollback){failure.addSuppressed(rollback);rollbackFailed=true;}
   throw new java.io.IOException("Route update failed"+(rollbackFailed?"; rollback failed, inspect routes in UniFi":"; previous route settings restored")+": "+failure.getMessage(),failure);
  }
 }

 // "192.168.1.1" -> "https://192.168.1.1". Returns "" for anything that isn't a bare https host.
 public static String gateway(String input){
  if(input==null)return "";
  String s=input.trim();
  while(s.endsWith("/"))s=s.substring(0,s.length()-1);
  if(s.startsWith("http://"))return "";
  if(!s.startsWith("https://"))s="https://"+s;
  String host=s.substring(8);
  return host.matches("[A-Za-z0-9.-]+(:[0-9]{1,5})?")?s:"";
 }

 // Router tunnel status now vs at the last public-IP check. Same and connected = same exit, so the
 // last IP is still valid without asking ipify again.
 public static boolean unchanged(String baseline,String now){return now!=null&&now.startsWith("CONNECTED")&&now.equals(baseline);}

 // "CONNECTED to 1.2.3.4" + "al122" -> "CONNECTED to al122 · 1.2.3.4". Unchanged without a name.
 public static String withServer(String status,String host){
  int at=status==null?-1:status.indexOf(" to ");
  return at<0||host==null||host.isEmpty()?status:status.substring(0,at+4)+host+" · "+status.substring(at+4);
 }

 // WireGuard public keys are 32 bytes in base64: 43 characters plus "=".
 public static boolean wireguardKey(String key){return key!=null&&key.matches("[A-Za-z0-9+/]{43}=");}

 // "NordVPN-AL" -> "AL": the country the client is in now, so the list can offer it first. "" otherwise.
 public static String countryCode(String name){return name!=null&&name.matches("NordVPN-[A-Z]{2}")?name.substring(8):"";}

 // One row of the server list: "al168 · 187.15.131.169 · load 15% · current".
 public static String serverLabel(String hostname,String ip,int load,String currentIp){
  String host=hostname==null?"":hostname.replace(".nordvpn.com","");
  return (host.isEmpty()?"":host+" · ")+ip+" · load "+load+"%"+(ip.equals(currentIp)?" · current":"");
 }

 // Keeps "NordVPN-CZ"-style names in step with the country. Any other name is the owner's choice and stays.
 public static String renamed(String name,String countryCode){
  return name!=null&&name.matches("NordVPN-[A-Z]{2}")&&countryCode!=null&&countryCode.matches("[A-Za-z]{2}")?"NordVPN-"+countryCode.toUpperCase(java.util.Locale.US):name;
 }
}
