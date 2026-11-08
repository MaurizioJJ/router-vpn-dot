import local.routervpndot.LogTimeline;
import java.util.*;
public class LogTimelineTest {
 static int n; static void check(boolean b){n++;if(!b)throw new AssertionError("Case "+n);}
 public static void main(String[] args){
  List<LogTimeline.Entry> list=LogTimeline.parse("Thu Oct 01 08:10:54 GMT+02:00 2026 App opened\nThu Oct 01 08:05:37 GMT+02:00 2026 CRASH\n stack line\n");
  check(list.size()==2);check(list.get(0).time>list.get(1).time);check(list.get(1).message.contains("stack line"));
  list.add(new LogTimeline.Entry(list.get(0).time-32000,"Android exit"));
  String out=LogTimeline.render(list);check(out.indexOf("App opened")<out.indexOf("Android exit"));check(out.indexOf("Android exit")<out.indexOf("CRASH"));
  check(LogTimeline.parse("@1000 A\n@2000 B\n").get(1).time==2000);
  check(LogTimeline.parse("fragment\n@1000 A").size()==2);
  check(LogTimeline.parse("").isEmpty());
  check(LogTimeline.render(LogTimeline.parse("@1000 A\n@2000 B")).indexOf("B")<LogTimeline.render(LogTimeline.parse("@1000 A\n@2000 B")).indexOf("A"));
  check(LogTimeline.parse("Thu Oct 01 06:10:54 UTC 2026 X").get(0).time==list.get(0).time);
  check(LogTimeline.parse("@1000 A\nThu Oct 01 08:10:54 GMT+02:00 2026 B").size()==2);
  System.out.println(n+" timeline tests passed");
 }
}
