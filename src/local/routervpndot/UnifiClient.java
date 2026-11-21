package local.routervpndot;

import android.content.Context;
import android.content.SharedPreferences;
import java.io.*;
import java.net.*;
import java.security.MessageDigest;
import java.security.cert.X509Certificate;
import java.util.*;
import javax.net.ssl.*;
import org.json.*;

// Talks to a UniFi gateway's Network API (with an API key) and to NordVPN's public server API.
// Changes the WireGuard peer and traffic routes (enabled, kill_switch_enabled).
// The private key and tunnel address stay as the owner set them up.
final class UnifiClient {
 static final class VpnClient {
  final String id,name,peerIp;
  VpnClient(String id,String name,String peerIp){this.id=id;this.name=name;this.peerIp=peerIp;}
 }
 static final class Country {
  final int id;final String name,code;
  Country(int id,String name,String code){this.id=id;this.name=name;this.code=code;}
 }

 static final class Routes {
  final List<RouterVpn.Route> all=new ArrayList<>(),mine=new ArrayList<>();
  final Map<String,JSONObject> bodies=new HashMap<>();
  final Map<String,String> networks=new HashMap<>();
  String mac="";
 }
 private final Context context;
 private final SharedPreferences prefs;
 private final String gateway,apiKey;

 UnifiClient(Context c){
  context=c; prefs=Config.prefs(c);
  gateway=prefs.getString("unifiGateway","");
  apiKey=prefs.getString("unifiApiKey","");
 }
 boolean configured(){return !gateway.isEmpty()&&!apiKey.isEmpty();}

 // Every WireGuard VPN client on the gateway's default site.
 List<VpnClient> clients() throws Exception {
  JSONArray data=new JSONObject(unifi("GET","/proxy/network/api/s/default/rest/networkconf",null)).getJSONArray("data");
  List<VpnClient> out=new ArrayList<>();
  for(int i=0;i<data.length();i++){
   JSONObject n=data.getJSONObject(i);
   if("vpn-client".equals(n.optString("purpose"))&&"wireguard-client".equals(n.optString("vpn_type")))
    out.add(new VpnClient(n.getString("_id"),n.optString("name"),n.optString("wireguard_client_peer_ip")));
  }
  return out;
 }

 // The client the owner picked, or the only one there is. Null when there is none.
 VpnClient selected(List<VpnClient> all){
  String id=prefs.getString("unifiVpnId","");
  for(VpnClient v:all)if(v.id.equals(id))return v;
  return all.size()==1?all.get(0):null;
 }

 // "CONNECTED" / "DISCONNECTED" / "" for the tunnel whose remote end is peerIp.
 String status(VpnClient v) throws Exception {
  JSONArray list=new JSONObject(unifi("GET","/proxy/network/v2/api/site/default/vpn/connections",null)).optJSONArray("connections");
  if(list!=null)for(int i=0;i<list.length();i++){
   JSONObject c=list.getJSONObject(i);
   if(v.id.equals(c.optString("network_id")))return c.optString("status")+(c.optString("remote_ip").isEmpty()?"":" to "+c.optString("remote_ip"));
  }
  return "";
 }

 // Resolve the active LAN IPv4 through Android's network, then the gateway's station table.
 Routes routes(VpnClient vpn) throws Exception {
  Routes out=new Routes();
  android.net.ConnectivityManager cm=context.getSystemService(android.net.ConnectivityManager.class);
  android.net.LinkProperties lp=cm.getLinkProperties(cm.getActiveNetwork());
  Set<String> ips=new HashSet<>();
  if(lp!=null)for(android.net.LinkAddress a:lp.getLinkAddresses())if(a.getAddress() instanceof java.net.Inet4Address)ips.add(a.getAddress().getHostAddress());
  JSONArray stations=new JSONObject(unifi("GET","/proxy/network/api/s/default/stat/sta",null)).getJSONArray("data");
  for(int i=0;i<stations.length();i++){JSONObject sta=stations.getJSONObject(i);if(ips.contains(sta.optString("ip"))){out.mac=sta.optString("mac").toLowerCase(Locale.US);break;}}
  JSONArray data=new JSONArray(unifi("GET","/proxy/network/v2/api/site/default/trafficroutes",null));
  for(int i=0;i<data.length();i++){
   JSONObject r=data.getJSONObject(i);List<String> macs=new ArrayList<>(),types=new ArrayList<>();
   JSONArray targets=r.optJSONArray("target_devices");
   if(targets!=null)for(int j=0;j<targets.length();j++){JSONObject t=targets.getJSONObject(j);macs.add(t.optString("client_mac"));types.add(t.optString("type"));}
   String routeId=r.getString("_id");
   if(!routeId.matches("[A-Za-z0-9_-]+"))throw new IOException("Invalid traffic route id");
   RouterVpn.Route route=new RouterVpn.Route(routeId,r.optString("description"),r.optString("network_id"),r.optBoolean("enabled"),r.optBoolean("kill_switch_enabled"),macs,types);
   out.all.add(route);out.bodies.put(route.id,r);
  }
  Set<String> saved=prefs.getStringSet("unifiRouteIds",Collections.emptySet());
  if(saved.isEmpty())out.mine.addAll(RouterVpn.myRoutes(out.all,out.mac,vpn.id));
  else {
   for(RouterVpn.Route r:out.all)if(saved.contains(r.id)&&r.networkId.equals(vpn.id))out.mine.add(r);
   if(out.mine.size()!=saved.size())throw new IOException("Saved VPN routes changed. Use Re-detect this TV's routes.");
  }
  JSONArray networks=new JSONObject(unifi("GET","/proxy/network/api/s/default/rest/networkconf",null)).getJSONArray("data");
  for(int i=0;i<networks.length();i++){JSONObject n=networks.getJSONObject(i);out.networks.put(n.optString("_id"),n.optString("name"));}
  return out;
 }
 // Full GET objects are preserved. Compensate earlier writes on failure; preferences change only on success.
 void changeRoutes(Routes snapshot,Set<String> ids,String field,boolean value) throws Exception {
  if(!field.equals("enabled")&&!field.equals("kill_switch_enabled"))throw new IOException("Unsupported route field");
  for(String id:ids)if(!snapshot.bodies.containsKey(id))throw new IOException("Saved route no longer exists: "+id);
  RouterVpn.update(ids,(id,restore)->{
   JSONObject original=snapshot.bodies.get(id);
   unifi("PUT","/proxy/network/v2/api/site/default/trafficroutes/"+id,restore?original.toString():new JSONObject(original.toString()).put(field,value).toString());
  });
 }

 static List<Country> countries() throws Exception {
  JSONArray a=new JSONArray(get("https://api.nordvpn.com/v1/servers/countries",4<<20));
  List<Country> out=new ArrayList<>();
  for(int i=0;i<a.length();i++){JSONObject c=a.getJSONObject(i);out.add(new Country(c.getInt("id"),c.getString("name"),c.getString("code")));}
  Collections.sort(out,(x,y)->x.name.compareToIgnoreCase(y.name));
  return out;
 }

 static final class Server {
  final String hostname,ip,key;final int load;
  Server(String hostname,String ip,String key,int load){this.hostname=hostname;this.ip=ip;this.key=key;this.load=load;}
 }

 // NordVPN's recommended WireGuard servers in the country, least loaded first. Servers without a key are skipped.
 static List<Server> servers(Country country) throws Exception {
  JSONArray rec=new JSONArray(get("https://api.nordvpn.com/v1/servers/recommendations?filters%5Bcountry_id%5D="+country.id+"&filters%5Bservers_technologies%5D%5Bidentifier%5D=wireguard_udp&limit=15",2<<20));
  List<Server> out=new ArrayList<>();
  for(int s=0;s<rec.length();s++){
   JSONObject server=rec.getJSONObject(s);String key="";
   JSONArray tech=server.optJSONArray("technologies");
   for(int i=0;tech!=null&&i<tech.length();i++){
    JSONObject t=tech.getJSONObject(i);if(!"wireguard_udp".equals(t.optString("identifier")))continue;
    JSONArray meta=t.optJSONArray("metadata");
    if(meta!=null)for(int j=0;j<meta.length();j++)if("public_key".equals(meta.getJSONObject(j).optString("name")))key=meta.getJSONObject(j).optString("value");
   }
   if(RouterVpn.wireguardKey(key)&&!server.optString("station").isEmpty())out.add(new Server(server.optString("hostname"),server.getString("station"),key,server.optInt("load")));
  }
  if(out.isEmpty())throw new IOException("NordVPN has no WireGuard server in "+country.name);
  return out;
 }

 // Points the VPN client at the chosen server. Returns "hostname (ip)".
 String switchTo(VpnClient v,Country country,Server server) throws Exception {
  JSONObject body=new JSONObject().put("wireguard_client_peer_ip",server.ip).put("wireguard_client_peer_public_key",server.key).put("name",RouterVpn.renamed(v.name,country.code));
  unifi("PUT","/proxy/network/api/s/default/rest/networkconf/"+v.id,body.toString());
  return (server.hostname.isEmpty()?server.ip:server.hostname)+" ("+server.ip+")";
 }

 private String unifi(String method,String path,String body) throws Exception {
  HttpsURLConnection c=(HttpsURLConnection)open(gateway+path);
  c.setSSLSocketFactory(pinned());
  String host=new URL(gateway).getHost();
  c.setHostnameVerifier((h,s)->h.equals(host)); // the pin, not the name, authenticates a self-signed gateway
  c.setRequestMethod(method);c.setRequestProperty("X-API-KEY",apiKey);c.setRequestProperty("Accept","application/json");
  if(body!=null){c.setDoOutput(true);c.setRequestProperty("Content-Type","application/json");try(OutputStream o=c.getOutputStream()){o.write(body.getBytes("UTF-8"));}}
  return read(c,4<<20);
 }

 private static String get(String url,int limit) throws Exception {
  return read(open(url),limit);
 }

 // All settings go on before anything is sent: once a request body is written the connection is
 // open and Android throws "Already connected" on any further setter.
 private static HttpURLConnection open(String url) throws IOException {
  HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection(Proxy.NO_PROXY);
  c.setConnectTimeout(10000);c.setReadTimeout(15000);c.setUseCaches(false);c.setInstanceFollowRedirects(false);
  c.setRequestProperty("User-Agent","RouterVPNDot/0.19");
  return c;
 }

 private static String read(HttpURLConnection c,int limit) throws IOException {
  try{
   int code=c.getResponseCode();
   if(code==401||code==403)throw new IOException("the gateway refused the API key (HTTP "+code+")");
   if(code!=200)throw new IOException("HTTP "+code+" from "+c.getURL().getHost());
   ByteArrayOutputStream b=new ByteArrayOutputStream();byte[] buf=new byte[8192];int n;
   try(InputStream in=c.getInputStream()){while((n=in.read(buf))>0){b.write(buf,0,n);if(b.size()>limit)throw new IOException("reply too large");}}
   return b.toString("UTF-8");
  }finally{c.disconnect();}
 }

 // Trust on first use: UniFi gateways ship a self-signed certificate, so the first one seen is
 // remembered and any later change is refused. "Forget gateway" in settings clears it.
 private SSLSocketFactory pinned() throws Exception {
  TrustManager tm=new X509TrustManager(){
   public void checkClientTrusted(X509Certificate[] chain,String type){}
   public void checkServerTrusted(X509Certificate[] chain,String type) throws java.security.cert.CertificateException {
    String seen=sha256(chain[0]),pin=prefs.getString("unifiPin","");
    if(pin.isEmpty())prefs.edit().putString("unifiPin",seen).apply();
    else if(!pin.equals(seen))throw new java.security.cert.CertificateException("gateway certificate changed: use Forget gateway if you replaced it");
   }
   public X509Certificate[] getAcceptedIssuers(){return new X509Certificate[0];}
  };
  SSLContext ctx=SSLContext.getInstance("TLS");ctx.init(null,new TrustManager[]{tm},null);
  return ctx.getSocketFactory();
 }
 private static String sha256(X509Certificate cert) throws java.security.cert.CertificateException {
  try{StringBuilder s=new StringBuilder();for(byte b:MessageDigest.getInstance("SHA-256").digest(cert.getEncoded()))s.append(String.format("%02x",b));return s.toString();}
  catch(java.security.NoSuchAlgorithmException e){throw new java.security.cert.CertificateException(e);}
 }
}
