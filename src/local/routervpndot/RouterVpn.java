package local.routervpndot;

// Pure helpers for the router VPN switch, kept free of Android classes so tests run on a plain JDK.
public final class RouterVpn {
 private RouterVpn(){}

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

 // WireGuard public keys are 32 bytes in base64: 43 characters plus "=".
 public static boolean wireguardKey(String key){return key!=null&&key.matches("[A-Za-z0-9+/]{43}=");}

 // Keeps "NordVPN-CZ"-style names in step with the country. Any other name is the owner's choice and stays.
 public static String renamed(String name,String countryCode){
  return name!=null&&name.matches("NordVPN-[A-Z]{2}")&&countryCode!=null&&countryCode.matches("[A-Za-z]{2}")?"NordVPN-"+countryCode.toUpperCase(java.util.Locale.US):name;
 }
}
