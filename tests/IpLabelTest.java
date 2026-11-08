import local.routervpndot.IpLabel;
public class IpLabelTest {
 public static void main(String[] args){
  check(IpLabel.format(null,"PacketHub S.A.","Prague","Czechia").equals("PacketHub S.A. · Prague, Czechia"));
  check(IpLabel.format("example.net","Provider","","Czechia").equals("example.net · Provider · Czechia"));
  check(IpLabel.format(null,null,null,null).equals("Location unavailable"));
  check(IpLabel.format("null","","Prague",null).equals("Prague"));
  System.out.println("4 IP label tests passed");
 }
 static void check(boolean b){if(!b)throw new AssertionError();}
}
