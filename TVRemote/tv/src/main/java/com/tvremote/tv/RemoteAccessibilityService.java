package com.tvremote.tv;

import android.accessibilityservice.*;
import android.graphics.*;
import android.os.*;
import android.view.*;
import android.view.accessibility.*;
import android.widget.*;
import android.content.*;
import android.media.AudioManager;
import java.io.*;
import java.net.*;
import java.util.concurrent.*;

public class RemoteAccessibilityService extends AccessibilityService {
    static RemoteAccessibilityService instance;
    static Socket pending;
    WindowManager wm; TextView cursor; Handler h=new Handler(Looper.getMainLooper());
    float x=400,y=250; boolean cursorVisible=true; int screenW,screenH;

    public static void attachClient(Socket s){ pending=s; if(instance!=null) instance.startReader(s); }
    @Override public void onServiceConnected(){
        instance=this; screenW=getResources().getDisplayMetrics().widthPixels; screenH=getResources().getDisplayMetrics().heightPixels; wm=(WindowManager)getSystemService(WINDOW_SERVICE); showCursor();
        if(pending!=null)startReader(pending);
    }
    void showCursor(){
        if(cursor!=null)return;
        cursor=new TextView(this); cursor.setText("➤"); cursor.setTextSize(34); cursor.setTextColor(Color.WHITE); cursor.setShadowLayer(8,2,2,Color.BLACK);
        WindowManager.LayoutParams lp=new WindowManager.LayoutParams(70,70,WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,PixelFormat.TRANSLUCENT);
        lp.gravity=Gravity.TOP|Gravity.LEFT; lp.x=(int)x;lp.y=(int)y;wm.addView(cursor,lp);
    }
    void move(float dx,float dy){
        x=Math.max(0,Math.min(screenW-50,x+dx));y=Math.max(0,Math.min(screenH-50,y+dy));
        h.post(()->{if(cursor!=null){WindowManager.LayoutParams lp=(WindowManager.LayoutParams)cursor.getLayoutParams();lp.x=(int)x;lp.y=(int)y;wm.updateViewLayout(cursor,lp);}});
    }
    void toggleCursor(){cursorVisible=!cursorVisible;h.post(()->{if(cursor!=null)cursor.setVisibility(cursorVisible?View.VISIBLE:View.INVISIBLE);});}
    void gesture(float ex,float ey,long duration){
        Path p=new Path();p.moveTo(x+25,y+25);p.lineTo(ex,ey);
        GestureDescription.StrokeDescription st=new GestureDescription.StrokeDescription(p,0,Math.max(1,duration));
        dispatchGesture(new GestureDescription.Builder().addStroke(st).build(),null,null);
    }
    void click(){gesture(x+25,y+25,1);}
    void longClick(){gesture(x+25,y+25,650);}
    void scroll(float dy){gesture(x+25,y+25+Math.max(-500,Math.min(500,dy*2)),220);}
    void dpad(int direction){
        AccessibilityNodeInfo current=findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
        if(current==null) current=findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY);
        AccessibilityNodeInfo next=null;
        if(current!=null) next=current.focusSearch(direction);
        if(next!=null){
            boolean ok=next.performAction(AccessibilityNodeInfo.ACTION_FOCUS);
            if(!ok) next.performAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS);
            next.recycle();
        }
        if(current!=null) current.recycle();
    }

    void activateFocused(){
        AccessibilityNodeInfo current=findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
        if(current==null) current=findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY);
        if(current!=null){
            if(!current.performAction(AccessibilityNodeInfo.ACTION_CLICK))
                current.performAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS);
            current.recycle();
        } else {
            click();
        }
    }

    void global(String c){
        if(c.equals("BACK"))performGlobalAction(GLOBAL_ACTION_BACK);
        else if(c.equals("HOME"))performGlobalAction(GLOBAL_ACTION_HOME);
        else if(c.equals("RECENT"))performGlobalAction(GLOBAL_ACTION_RECENTS);
        else if(c.equals("VOL_UP"))((AudioManager)getSystemService(AUDIO_SERVICE)).adjustVolume(AudioManager.ADJUST_RAISE,AudioManager.FLAG_SHOW_UI);
        else if(c.equals("VOL_DOWN"))((AudioManager)getSystemService(AUDIO_SERVICE)).adjustVolume(AudioManager.ADJUST_LOWER,AudioManager.FLAG_SHOW_UI);
        else if(c.equals("MUTE"))((AudioManager)getSystemService(AUDIO_SERVICE)).adjustVolume(AudioManager.ADJUST_TOGGLE_MUTE,AudioManager.FLAG_SHOW_UI);
        else if(c.equals("DPAD_UP"))dpad(View.FOCUS_UP);
        else if(c.equals("DPAD_DOWN"))dpad(View.FOCUS_DOWN);
        else if(c.equals("DPAD_LEFT"))dpad(View.FOCUS_LEFT);
        else if(c.equals("DPAD_RIGHT"))dpad(View.FOCUS_RIGHT);
        else if(c.equals("PLAY_PAUSE")){
            AudioManager a=(AudioManager)getSystemService(AUDIO_SERVICE);
            a.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE));
            a.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE));
        }
    }
    void setText(String s){
        AccessibilityNodeInfo root=getRootInActiveWindow(); if(root==null)return;
        AccessibilityNodeInfo f=root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
        if(f!=null && f.isEditable()) f.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, bundle(s));
        root.recycle();
    }
    Bundle bundle(String s){Bundle b=new Bundle();b.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,s);return b;}
    void handle(String c){
        try{
            if(c.equals("HELLO"))return;
            if(c.startsWith("MOVE:")){String[]p=c.substring(5).split(",");move(Float.parseFloat(p[0]),Float.parseFloat(p[1]));}
            else if(c.equals("CLICK"))activateFocused();
            else if(c.equals("LONG_CLICK"))longClick();
            else if(c.startsWith("SCROLL:"))scroll(Float.parseFloat(c.substring(7)));
            else if(c.equals("CURSOR_TOGGLE"))toggleCursor();
            else if(c.startsWith("TEXT:"))setText(c.substring(5));
            else global(c);
        }catch(Exception ignored){}
    }
    void startReader(Socket s){
        new Thread(()->{try{BufferedReader r=new BufferedReader(new InputStreamReader(s.getInputStream()));String line;while((line=r.readLine())!=null){String c=line;h.post(()->handle(c));}}catch(Exception ignored){}}).start();
    }
    @Override public void onAccessibilityEvent(AccessibilityEvent e){}
    @Override public void onInterrupt(){}
    @Override public void onDestroy(){try{if(cursor!=null)wm.removeView(cursor);}catch(Exception ignored){}cursor=null;instance=null;super.onDestroy();}
}
