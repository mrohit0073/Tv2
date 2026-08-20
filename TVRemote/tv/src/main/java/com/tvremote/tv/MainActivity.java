package com.tvremote.tv;

import android.app.*;
import android.os.*;
import android.content.*;
import android.graphics.Color;
import android.view.*;
import android.widget.*;
import java.net.*;
import java.util.*;

public class MainActivity extends Activity {
    TextView status, ip;
    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        LinearLayout l=new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); l.setPadding(70,45,70,45); l.setBackgroundColor(Color.rgb(8,11,16));
        TextView t=new TextView(this); t.setText("TV REMOTE RECEIVER"); t.setTextSize(32); t.setTextColor(Color.WHITE); t.setTypeface(null,1); l.addView(t);
        status=new TextView(this); status.setTextColor(Color.rgb(0,230,118)); status.setTextSize(21); status.setText("\n● Receiver running"); l.addView(status);
        ip=new TextView(this); ip.setTextColor(Color.LTGRAY); ip.setTextSize(18); ip.setText("\nWi‑Fi: "+localIp()+":45456"); l.addView(ip);
        TextView info=new TextView(this); info.setTextColor(Color.WHITE); info.setTextSize(18); info.setText("\nPHONE SETUP\n1. Put phone and TV on the same Wi‑Fi.\n2. Enable TV Remote Receiver in Accessibility.\n3. Open TV Remote on the phone.\n4. The cursor appears after connection.\n\nAccessibility is required for the real cursor and gestures."); l.addView(info);
        Button settings=new Button(this); settings.setText("OPEN ACCESSIBILITY SETTINGS"); settings.setOnClickListener(v->startActivity(new Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS))); l.addView(settings);
        setContentView(l); startServers();
    }
    void startServers(){
        new Thread(()->{
            try(DatagramSocket ds=new DatagramSocket(45455)){
                byte[] b=new byte[256];
                while(true){
                    DatagramPacket p=new DatagramPacket(b,b.length); ds.receive(p);
                    String s=new String(p.getData(),0,p.getLength());
                    if(s.startsWith("TVREMOTE_DISCOVER")){
                        byte[] out=("TVREMOTE_V2|"+localIp()+"|"+android.os.Build.MODEL).getBytes();
                        ds.send(new DatagramPacket(out,out.length,p.getAddress(),p.getPort()));
                    }
                }
            }catch(Exception e){}
        }).start();
        new Thread(()->{
            try(ServerSocket ss=new ServerSocket(45456)){
                while(true){ Socket s=ss.accept(); RemoteAccessibilityService.attachClient(s); runOnUiThread(()->status.setText("● PHONE CONNECTED • Cursor control active")); }
            }catch(Exception e){}
        }).start();
    }
    String localIp(){
        try{
            for(Enumeration<NetworkInterface> en=NetworkInterface.getNetworkInterfaces();en.hasMoreElements();){
                NetworkInterface n=en.nextElement();
                for(Enumeration<InetAddress> a=n.getInetAddresses();a.hasMoreElements();){
                    InetAddress x=a.nextElement();
                    if(!x.isLoopbackAddress() && x instanceof Inet4Address)return x.getHostAddress();
                }
            }
        }catch(Exception ignored){}
        return "0.0.0.0";
    }
}
