package com.tvremote.mobile;

import android.app.*;
import android.os.*;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.content.*;
import android.view.*;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import java.io.*;
import java.net.*;
import java.util.concurrent.*;

public class MainActivity extends Activity {
    RemoteClient client;
    TextView status, device;
    float sensitivity = 1.55f;

    int dp(float n){ return (int)(n*getResources().getDisplayMetrics().density+0.5f); }
    TextView text(String s,float size){
        TextView v=new TextView(this); v.setText(s); v.setTextColor(Color.WHITE); v.setTextSize(size); v.setGravity(Gravity.CENTER_VERTICAL); return v;
    }
    GradientDrawable bg(int color,int radius){
        GradientDrawable g=new GradientDrawable(); g.setColor(color); g.setCornerRadius(dp(radius)); return g;
    }
    Button key(String s){
        Button b=new Button(this); b.setText(s); b.setTextColor(Color.WHITE); b.setTextSize(12);
        b.setAllCaps(false); b.setPadding(dp(2),0,dp(2),0);
        b.setBackground(bg(Color.rgb(28,34,44),14)); return b;
    }
    void addRow(LinearLayout parent, View... views){
        LinearLayout r=new LinearLayout(this); r.setGravity(Gravity.CENTER); r.setPadding(0,dp(3),0,dp(3));
        for(View v:views) r.addView(v,new LinearLayout.LayoutParams(0,dp(52),1));
        parent.addView(r,new LinearLayout.LayoutParams(-1,dp(58)));
    }
    void send(String s){ if(client!=null) client.send(s); }

    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        getWindow().setStatusBarColor(Color.rgb(8,11,16));
        getWindow().setNavigationBarColor(Color.rgb(8,11,16));

        LinearLayout root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(14),dp(10),dp(14),dp(10)); root.setBackgroundColor(Color.rgb(8,11,16));

        LinearLayout head=new LinearLayout(this); head.setGravity(Gravity.CENTER_VERTICAL);
        TextView title=text("TV REMOTE",22); title.setTypeface(null,1);
        head.addView(title,new LinearLayout.LayoutParams(0,dp(48),1));
        status=text("●  Searching…",12); status.setTextColor(Color.rgb(255,193,7));
        head.addView(status,new LinearLayout.LayoutParams(dp(150),dp(48)));
        root.addView(head);

        device=text("No TV connected",12); device.setPadding(dp(12),0,dp(12),0);
        device.setBackground(bg(Color.rgb(17,23,31),12)); root.addView(device,new LinearLayout.LayoutParams(-1,dp(38)));

        TouchPad pad=new TouchPad(this);
        TextView hint=text("TOUCHPAD  •  TAP = CLICK  •  TWO FINGERS = SCROLL",11);
        hint.setGravity(Gravity.CENTER); hint.setTextColor(Color.rgb(140,155,170));
        LinearLayout padWrap=new LinearLayout(this); padWrap.setOrientation(LinearLayout.VERTICAL); padWrap.setPadding(0,dp(8),0,dp(8));
        padWrap.addView(pad,new LinearLayout.LayoutParams(-1,0,1)); padWrap.addView(hint,new LinearLayout.LayoutParams(-1,dp(28)));
        root.addView(padWrap,new LinearLayout.LayoutParams(-1,0,1));

        LinearLayout top=new LinearLayout(this);
        Button back=key("↩ BACK"), home=key("⌂ HOME"), recent=key("▣ RECENTS"), textBtn=key("⌨ KEYBOARD");
        back.setOnClickListener(v->send("BACK")); home.setOnClickListener(v->send("HOME")); recent.setOnClickListener(v->send("RECENT"));
        textBtn.setOnClickListener(v->showTextDialog());
        for(Button q:new Button[]{back,home,recent,textBtn}) top.addView(q,new LinearLayout.LayoutParams(0,dp(50),1));
        root.addView(top,new LinearLayout.LayoutParams(-1,dp(56)));

        LinearLayout dpad=new LinearLayout(this); dpad.setOrientation(LinearLayout.VERTICAL);
        Button up=key("▲"), left=key("◀"), ok=key("OK"), right=key("▶"), down=key("▼");
        up.setOnClickListener(v->send("DPAD_UP")); left.setOnClickListener(v->send("DPAD_LEFT")); right.setOnClickListener(v->send("DPAD_RIGHT")); down.setOnClickListener(v->send("DPAD_DOWN")); ok.setOnClickListener(v->send("CLICK"));
        LinearLayout r1=new LinearLayout(this); r1.setGravity(Gravity.CENTER); r1.addView(up,new LinearLayout.LayoutParams(dp(90),dp(46)));
        LinearLayout r2=new LinearLayout(this); r2.setGravity(Gravity.CENTER); r2.addView(left,new LinearLayout.LayoutParams(dp(90),dp(46))); r2.addView(ok,new LinearLayout.LayoutParams(dp(90),dp(46))); r2.addView(right,new LinearLayout.LayoutParams(dp(90),dp(46)));
        LinearLayout r3=new LinearLayout(this); r3.setGravity(Gravity.CENTER); r3.addView(down,new LinearLayout.LayoutParams(dp(90),dp(46)));
        dpad.addView(r1); dpad.addView(r2); dpad.addView(r3);
        root.addView(dpad,new LinearLayout.LayoutParams(-1,dp(150)));

        Button volDown=key("VOL −"), mute=key("MUTE"), volUp=key("VOL +"), play=key("▶ / ❚❚");
        volDown.setOnClickListener(v->send("VOL_DOWN")); mute.setOnClickListener(v->send("MUTE")); volUp.setOnClickListener(v->send("VOL_UP")); play.setOnClickListener(v->send("PLAY_PAUSE"));
        addRow(root,volDown,mute,volUp,play);

        LinearLayout bottom=new LinearLayout(this); Button reconnect=key("⟳  RECONNECT"), hide=key("HIDE CURSOR");
        reconnect.setOnClickListener(v->discover()); hide.setOnClickListener(v->send("CURSOR_TOGGLE"));
        bottom.addView(reconnect,new LinearLayout.LayoutParams(0,dp(48),1)); bottom.addView(hide,new LinearLayout.LayoutParams(0,dp(48),1));
        root.addView(bottom);

        setContentView(root);
        client=new RemoteClient();
        discover();
    }

    void discover(){
        status.setText("●  Searching…"); status.setTextColor(Color.rgb(255,193,7));
        new Thread(()->{
            try(DatagramSocket ds=new DatagramSocket()){
                ds.setBroadcast(true); ds.setSoTimeout(1800);
                byte[] data="TVREMOTE_DISCOVER_V2".getBytes();
                ds.send(new DatagramPacket(data,data.length,InetAddress.getByName("255.255.255.255"),45455));
                byte[] buf=new byte[1024]; DatagramPacket r=new DatagramPacket(buf,buf.length); ds.receive(r);
                String msg=new String(r.getData(),0,r.getLength());
                if(msg.startsWith("TVREMOTE_V2|")){
                    String[] a=msg.split("\\|");
                    String ip=a[1]; String name=a.length>2?a[2]:"Android TV";
                    client.connect(ip,45456);
                    runOnUiThread(()->{status.setText("●  CONNECTED");status.setTextColor(Color.rgb(0,230,118));device.setText("Connected • "+name+" • "+ip);});
                }
            }catch(Exception e){
                runOnUiThread(()->{status.setText("●  TV NOT FOUND");status.setTextColor(Color.rgb(255,82,82));device.setText("Same Wi‑Fi required • tap RECONNECT");});
            }
        }).start();
    }

    void showTextDialog(){
        final EditText input=new EditText(this); input.setSingleLine(false); input.setTextColor(Color.WHITE); input.setHintTextColor(Color.GRAY); input.setHint("Type text for the TV…");
        AlertDialog d=new AlertDialog.Builder(this).setTitle("Send text to TV").setView(input)
            .setNegativeButton("Cancel",null).setPositiveButton("SEND",(x,w)->send("TEXT:"+input.getText().toString().replace("\n"," "))).create();
        d.setOnShowListener(x->{input.requestFocus(); d.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);});
        d.show();
    }

    class TouchPad extends View {
        float lx,ly; long downTime; boolean moved; int pointers=0;
        Paint p=new Paint(1);
        TouchPad(Context c){ super(c); setBackground(bg(Color.rgb(15,21,29),18)); p.setColor(Color.rgb(55,70,85)); p.setStrokeWidth(dp(1)); setFocusable(true); }
        protected void onDraw(Canvas c){ super.onDraw(c); float cx=getWidth()/2f,cy=getHeight()/2f; c.drawLine(cx-dp(20),cy,cx+dp(20),cy,p); c.drawLine(cx,cy-dp(20),cx,cy+dp(20),p); }
        public boolean onTouchEvent(MotionEvent e){
            int act=e.getActionMasked();
            if(act==MotionEvent.ACTION_DOWN){ lx=e.getX();ly=e.getY();downTime=System.currentTimeMillis();moved=false;pointers=1; return true; }
            if(act==MotionEvent.ACTION_POINTER_DOWN){pointers=e.getPointerCount();return true;}
            if(act==MotionEvent.ACTION_MOVE){
                if(e.getPointerCount()>=2){ float y=e.getY(0); float dy=y-ly; if(Math.abs(dy)>1){send("SCROLL:"+Math.round(dy*1.4f));ly=y;} return true; }
                float dx=e.getX()-lx,dy=e.getY()-ly;
                if(Math.abs(dx)+Math.abs(dy)>0.4){send("MOVE:"+Math.round(dx*sensitivity)+","+Math.round(dy*sensitivity));lx=e.getX();ly=e.getY();moved=true;}
                return true;
            }
            if(act==MotionEvent.ACTION_UP){
                if(!moved && System.currentTimeMillis()-downTime<450) send("CLICK");
                else if(!moved) send("LONG_CLICK");
                return true;
            }
            return true;
        }
    }

    static class RemoteClient {
        volatile Socket socket; volatile BufferedWriter out;
        void connect(String host,int port){
            new Thread(()->{try{
                close(); Socket s=new Socket(); s.setTcpNoDelay(true); s.connect(new InetSocketAddress(host,port),3000);
                socket=s; out=new BufferedWriter(new OutputStreamWriter(s.getOutputStream()));
                send("HELLO");
            }catch(Exception ignored){}}).start();
        }
        synchronized void send(String msg){
            final BufferedWriter w=out; if(w==null)return;
            try{w.write(msg);w.write('\n');w.flush();}catch(Exception e){close();}
        }
        synchronized void close(){try{if(socket!=null)socket.close();}catch(Exception ignored){} socket=null;out=null;}
    }
}
