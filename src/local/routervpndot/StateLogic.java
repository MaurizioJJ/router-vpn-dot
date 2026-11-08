package local.routervpndot;

public final class StateLogic {
 public static final int UNKNOWN=0, VPN=1, NORMAL=2;
 public static boolean ipv4(String ip) {
  if (ip == null || !ip.matches("[0-9]{1,3}(\\.[0-9]{1,3}){3}")) return false;
  for (String part : ip.split("\\.")) {
   if ((part.length()>1 && part.startsWith("0")) || Integer.parseInt(part)>255) return false;
  }
  return true;
 }
 public static boolean contains(String list, String ip) {
  if (!ipv4(ip)) return false;
  for (String item : list.trim().split("[\\s,;]+")) if (ip.equals(item)) return true;
  return false;
 }
 public static boolean validList(String list) {
  if (list.trim().isEmpty()) return true;
  for (String ip : list.trim().split("[\\s,;]+")) if (!ipv4(ip)) return false;
  return true;
 }
 public static int count(String list) {
  java.util.Set<String> ips=new java.util.HashSet<String>();
  for(String ip:list.trim().split("[\\s,;]+")) if(ipv4(ip)) ips.add(ip);
  return ips.size();
 }
 public static boolean overlap(String vpn, String normal) {
  for (String ip : vpn.trim().split("[\\s,;]+")) if (contains(normal,ip)) return true;
  return false;
 }
 public static int classify(String ip, String vpn, String normal, long ageMs) {
  if (ageMs<0 || ageMs>9000 || !ipv4(ip)) return UNKNOWN;
  boolean v=contains(vpn,ip), n=contains(normal,ip);
  if (v==n) return UNKNOWN;
  return v ? VPN : NORMAL;
 }
}
