package local.routervpndot;
import java.util.*;
import java.text.*;
import java.util.regex.*;
public final class LogTimeline {
 public static final class Entry {
  public final long time; public final String message;
  public Entry(long time,String message){this.time=time;this.message=message;}
 }
 public static List<Entry> parse(String log){
  List<Entry> result=new ArrayList<>();
  Pattern start=Pattern.compile("(?m)^(?:@(\\d+) |([A-Za-z]{3} [A-Za-z]{3} \\d{2} \\d{2}:\\d{2}:\\d{2} \\S+ \\d{4}) )");
  Matcher m=start.matcher(log);long time=0;int body=-1;
  SimpleDateFormat legacy=new SimpleDateFormat("EEE MMM dd HH:mm:ss z yyyy",Locale.US);
  legacy.setLenient(false);
  while(m.find()){
   long next;
   try{next=m.group(1)!=null?Long.parseLong(m.group(1)):legacy.parse(m.group(2)).getTime();}
   catch(Exception e){continue;}
   if(body>=0)result.add(new Entry(time,log.substring(body,m.start()).trim()));
   else if(m.start()>0)result.add(new Entry(Long.MIN_VALUE,"[Undated retained fragment] "+log.substring(0,m.start()).trim()));
   time=next;body=m.end();
  }
  if(body>=0)result.add(new Entry(time,log.substring(body).trim()));
  else if(!log.trim().isEmpty())result.add(new Entry(Long.MIN_VALUE,"[Undated retained fragment] "+log.trim()));
  return result;
 }
 public static String render(List<Entry> entries){
  List<Entry> sorted=new ArrayList<>(entries);
  Collections.sort(sorted,(a,b)->Long.compare(b.time,a.time));
  SimpleDateFormat format=new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS XXX",Locale.US);
  StringBuilder out=new StringBuilder("Newest first · device local time\n\n");
  for(Entry e:sorted){if(e.time!=Long.MIN_VALUE)out.append(format.format(new Date(e.time))).append("\n");out.append(e.message).append("\n\n");}
  if(sorted.isEmpty())out.append("No events recorded yet.");
  return out.toString();
 }
}
