package com.zemri.attendance;

import android.content.Context;
import android.content.SharedPreferences;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.List;

public class NetworkUtil {
    public static class Candidate {
        public String iface, ip; public int score;
        Candidate(String i,String p,int s){iface=i;ip=p;score=s;}
    }

    public static List<Candidate> candidates() {
        List<Candidate> list=new ArrayList<>();
        try {
            Enumeration<NetworkInterface> en=NetworkInterface.getNetworkInterfaces();
            if(en==null) return list;
            while(en.hasMoreElements()) {
                NetworkInterface ni=en.nextElement();
                if(!ni.isUp() || ni.isLoopback()) continue;
                String name=ni.getName()==null?"":ni.getName().toLowerCase();
                Enumeration<InetAddress> aa=ni.getInetAddresses();
                while(aa.hasMoreElements()) {
                    InetAddress a=aa.nextElement();
                    if(!(a instanceof Inet4Address) || a.isLoopbackAddress()) continue;
                    String ip=a.getHostAddress();
                    if(!isPrivate(ip)) continue;
                    int score=0;
                    if(name.contains("swlan")||name.contains("softap")||name.equals("ap0")||name.contains("wlan1")||name.contains("wifiap")||name.contains("tether")) score+=100;
                    else if(name.contains("wlan")) score+=50;
                    if(name.contains("rmnet")||name.contains("ccmni")||name.contains("pdp")) score-=100;
                    if(ip.endsWith(".1")) score+=20;
                    list.add(new Candidate(ni.getName(),ip,score));
                }
            }
        } catch(Exception ignored) {}
        Collections.sort(list, Comparator.comparingInt((Candidate c)->c.score).reversed());
        return list;
    }

    public static String detectedIp() {
        List<Candidate> c=candidates();
        return c.isEmpty()?"":c.get(0).ip;
    }

    public static String baseUrl(Context ctx) {
        SharedPreferences p=ctx.getSharedPreferences("zemri_settings",Context.MODE_PRIVATE);
        String manual=p.getString("manual_base_url","").trim();
        if(!manual.isEmpty()) {
            if(!manual.startsWith("http://")&&!manual.startsWith("https://")) manual="http://"+manual;
            while(manual.endsWith("/")) manual=manual.substring(0,manual.length()-1);
            return manual;
        }
        String ip=detectedIp();
        return ip.isEmpty()?"http://127.0.0.1:8080":"http://"+ip+":8080";
    }

    private static boolean isPrivate(String ip) {
        if(ip==null) return false;
        if(ip.startsWith("10.")) return true;
        if(ip.startsWith("192.168.")) return true;
        if(ip.startsWith("172.")) {
            try { int n=Integer.parseInt(ip.split("\\.")[1]); return n>=16&&n<=31; } catch(Exception ignored) {}
        }
        return false;
    }
}
