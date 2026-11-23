import local.routervpndot.RouterVpn;
public class RouterVpnTest {
 public static void main(String[] args){
  check(RouterVpn.gateway("192.168.1.1").equals("https://192.168.1.1"));
  check(RouterVpn.gateway(" https://unifi.local:443/ ").equals("https://unifi.local:443"));
  check(RouterVpn.gateway("http://192.168.1.1").isEmpty());
  check(RouterVpn.gateway("192.168.1.1/proxy").isEmpty());
  check(RouterVpn.wireguardKey("3ZNjosvvIqfvu3/BqaLzNNXs9zWO4jXpcXNOmDMDpX0="));
  check(!RouterVpn.wireguardKey("short="));
  check(RouterVpn.renamed("NordVPN-CZ","de").equals("NordVPN-DE"));
  check(RouterVpn.renamed("Home VPN","de").equals("Home VPN"));
  check(RouterVpn.countryCode("NordVPN-AL").equals("AL"));
  check(RouterVpn.countryCode("Home VPN").isEmpty());
  check(RouterVpn.serverLabel("al168.nordvpn.com","187.15.131.169",15,"187.15.131.169").equals("al168 · 187.15.131.169 · load 15% · current"));
  check(RouterVpn.serverLabel("","1.2.3.4",7,"5.6.7.8").equals("1.2.3.4 · load 7%"));
  check(RouterVpn.unchanged("CONNECTED to 1.2.3.4","CONNECTED to 1.2.3.4"));
  check(!RouterVpn.unchanged("CONNECTED to 1.2.3.4","CONNECTED to 5.6.7.8"));
  check(!RouterVpn.unchanged("DISCONNECTED","DISCONNECTED"));
  check(!RouterVpn.unchanged("",""));
  check(RouterVpn.withServer("CONNECTED to 1.2.3.4","al122").equals("CONNECTED to al122 · 1.2.3.4"));
  check(RouterVpn.withServer("CONNECTED to 1.2.3.4","").equals("CONNECTED to 1.2.3.4"));
  check(RouterVpn.withServer("DISCONNECTED","al122").equals("DISCONNECTED"));
  java.util.List<RouterVpn.Route> routes=java.util.Arrays.asList(
   new RouterVpn.Route("vpn","VPN","v",true,false,java.util.Arrays.asList("AA:BB"),java.util.Arrays.asList("CLIENT")),
   new RouterVpn.Route("wan","Override","w",true,false,java.util.Arrays.asList("aa:bb"),java.util.Arrays.asList("CLIENT")),
   new RouterVpn.Route("group","Group","v",true,false,java.util.Arrays.asList("aa:bb"),java.util.Arrays.asList("NETWORK")));
  check(RouterVpn.myRoutes(routes,"aa:bb","v").size()==1);
  check(RouterVpn.myRoutes(routes,"AA:BB","v").get(0).id.equals("vpn"));
  check(RouterVpn.myRoutes(routes,"aa:bb","missing").isEmpty());
  check(RouterVpn.myRoutes(routes,"","v").isEmpty());
  check(RouterVpn.warning(routes,"aa:bb","v",java.util.Collections.singletonMap("w","WAN2")).contains("WAN2"));
  check(RouterVpn.warning(routes,"other","v",java.util.Collections.emptyMap()).contains("isn't routed"));
  check(RouterVpn.warning(routes.subList(0,1),"aa:bb","v",java.util.Collections.emptyMap()).isEmpty());
  RouterVpn.Route disabled=new RouterVpn.Route("off","Disabled","v",false,true,java.util.Arrays.asList("aa:bb"),java.util.Arrays.asList("CLIENT"));
  check(RouterVpn.enabledIds(java.util.Arrays.asList(routes.get(0),disabled)).equals(java.util.Collections.singleton("vpn")));
  check(RouterVpn.enabledIds(java.util.Collections.emptyList()).isEmpty());
  check(!RouterVpn.allKillSwitch(routes));
  check(RouterVpn.allKillSwitch(java.util.Arrays.asList(disabled)));
  check(!RouterVpn.allKillSwitch(java.util.Collections.emptyList()));
  check(RouterVpn.warning(java.util.Arrays.asList(disabled),"aa:bb","v",java.util.Collections.emptyMap()).contains("isn't routed"));
  check(RouterVpn.warning(routes,"","v",java.util.Collections.emptyMap()).isEmpty());
  check(!routes.get(2).targets("AA:BB"));
  check(!routes.get(0).targets(null));
  java.util.Set<String> ids=new java.util.LinkedHashSet<>(java.util.Arrays.asList("first","second"));
  java.util.List<String> calls=new java.util.ArrayList<>();
  try{RouterVpn.update(ids,(id,restore)->calls.add(id+":"+restore));}catch(Exception e){throw new AssertionError(e);}
  check(calls.equals(java.util.Arrays.asList("first:false","second:false")));
  calls.clear();
  try{
   RouterVpn.update(ids,(id,restore)->{calls.add(id+":"+restore);if(id.equals("second")&&!restore)throw new java.io.IOException("PUT failed");});
   throw new AssertionError("Failure must reach caller");
  }catch(java.io.IOException e){check(e.getMessage().contains("restored"));check(e.getCause().getMessage().equals("PUT failed"));}
  catch(Exception e){throw new AssertionError(e);}
  check(calls.equals(java.util.Arrays.asList("first:false","second:false","second:true","first:true")));
  calls.clear();
  try{
   RouterVpn.update(ids,(id,restore)->{calls.add(id+":"+restore);if(id.equals("second"))throw new java.io.IOException("Offline");});
   throw new AssertionError("Failure must reach caller");
  }catch(java.io.IOException e){check(e.getMessage().contains("rollback failed"));check(e.getCause().getSuppressed().length==1);}
  catch(Exception e){throw new AssertionError(e);}
  check(calls.get(calls.size()-1).equals("first:true"));
  System.out.println("38 router VPN tests passed");
 }
 static void check(boolean b){if(!b)throw new AssertionError();}
}
