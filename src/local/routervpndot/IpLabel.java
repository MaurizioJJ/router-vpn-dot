package local.routervpndot;
public final class IpLabel {
 public static String format(String host,String provider,String city,String country){
  StringBuilder out=new StringBuilder();
  for(String value:new String[]{host,provider})add(out,value," · ");
  StringBuilder place=new StringBuilder();add(place,city,", ");add(place,country,", ");
  add(out,place.toString()," · ");
  return out.length()==0?"Location unavailable":out.toString();
 }
 private static void add(StringBuilder out,String value,String separator){
  if(value==null||value.trim().isEmpty()||"null".equals(value))return;
  value=value.replaceAll("[\\p{Cntrl}]"," ").trim();
  if(value.length()>160)value=value.substring(0,160);
  if(out.length()>0)out.append(separator);out.append(value);
 }
}
