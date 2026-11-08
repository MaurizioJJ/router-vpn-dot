import local.routervpndot.StateLogic;
public class StateLogicTest {
 static int cases=0;
 static void check(boolean pass){cases++;if(!pass)throw new AssertionError("Case "+cases);}
 public static void main(String[] args){
  String vpn="192.0.2.10", normal="198.51.100.2";
  check(StateLogic.classify(vpn,vpn,normal,4000)==StateLogic.VPN);
  check(StateLogic.classify(normal,vpn,normal,4000)==StateLogic.NORMAL);
  check(StateLogic.classify("203.0.113.7",vpn,normal,0)==StateLogic.UNKNOWN);
  check(StateLogic.classify(vpn,vpn,normal,9000)==StateLogic.VPN);
  check(StateLogic.classify(vpn,vpn,normal,9001)==StateLogic.UNKNOWN);
  check(StateLogic.classify(vpn,vpn,normal,-1)==StateLogic.UNKNOWN);
  check(StateLogic.classify("",vpn,normal,0)==StateLogic.UNKNOWN);
  check(StateLogic.classify(vpn,vpn,vpn,0)==StateLogic.UNKNOWN);
  check(!StateLogic.ipv4("999.1.1.1"));check(!StateLogic.ipv4("01.2.3.4"));
  check(!StateLogic.ipv4("192.0.2.10.attacker"));check(!StateLogic.ipv4("::1"));
  check(StateLogic.validList(vpn+", "+normal));check(StateLogic.validList(""));
  check(!StateLogic.validList("1.1.1"));check(StateLogic.overlap(vpn,normal+","+vpn));
  check(StateLogic.classify("203.0.113.9",vpn+",203.0.113.9",normal,0)==StateLogic.VPN);
  check(StateLogic.classify("203.0.113.9",vpn+"\n203.0.113.9",normal,0)==StateLogic.VPN);
  check(StateLogic.count(vpn+", "+vpn+"\n203.0.113.9")==2);
  System.out.println(cases+" classification tests passed");
 }
}
