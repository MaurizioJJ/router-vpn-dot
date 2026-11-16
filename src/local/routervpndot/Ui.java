package local.routervpndot;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.widget.*;

final class Ui {
 static final int BG=0xFF0B1020,CARD=0xFF131A2E,BORDER=0xFF24304D,TEXT=0xFFF1F5F9,MUTED=0xFF94A3B8,FOCUS=0xFF203654,ACCENT=0xFF38BDF8,GREEN=0xFF34D399,RED=0xFFF87171,AMBER=0xFFFBBF24;
 static final int LIGHT_GREEN=0xFFD1FAE5,LIGHT_YELLOW=0xFFFEF3C7,LIGHT_RED=0xFFFEE2E2,DARK_GREEN=0xFF059669,DARK_YELLOW=0xFFD97706,DARK_RED=0xFFDC2626,INK=0xFF0F172A,INK_MUTED=0xFF334155;
 // color 0 = the normal dark card.
 static void tint(View v,int color){
  if(color==0){surface(v,false);return;}
  GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(v.getContext(),16));v.setBackground(d);
 }
 static int dp(Context c,int n){return Math.round(n*c.getResources().getDisplayMetrics().density);}
 static void surface(View v,boolean focused){
  GradientDrawable d=new GradientDrawable();d.setColor(focused?FOCUS:CARD);d.setCornerRadius(dp(v.getContext(),16));d.setStroke(dp(v.getContext(),focused?2:1),focused?ACCENT:BORDER);v.setBackground(d);
 }
 static void focus(View v){v.setFocusable(true);surface(v,false);v.setOnFocusChangeListener((view,on)->{surface(view,on);view.setScaleX(on?1.03f:1);view.setScaleY(on?1.03f:1);});}
 static Switch toggle(Context c,String label,boolean checked){Switch s=new Switch(c);s.setText(label);s.setTextColor(TEXT);s.setTextSize(18);s.setPadding(dp(c,20),dp(c,16),dp(c,20),dp(c,16));s.setShowText(true);s.setTextOn("ON");s.setTextOff("OFF");s.setChecked(checked);focus(s);return s;}
}
