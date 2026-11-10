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
  System.out.println("8 router VPN tests passed");
 }
 static void check(boolean b){if(!b)throw new AssertionError();}
}
