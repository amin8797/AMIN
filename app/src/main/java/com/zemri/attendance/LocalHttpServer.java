package com.zemri.attendance;

import android.content.Context;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class LocalHttpServer {
    private final Context ctx; private final int port;
    private ServerSocket serverSocket; private Thread acceptThread;
    private final ExecutorService pool=Executors.newCachedThreadPool();
    private volatile boolean running=false;

    public LocalHttpServer(Context c,int p){ctx=c.getApplicationContext();port=p;}

    public synchronized void start() {
        if(running) return;
        running=true;
        acceptThread=new Thread(()->{
            try {
                serverSocket=new ServerSocket(port);
                serverSocket.setReuseAddress(true);
                while(running) {
                    Socket s=serverSocket.accept();
                    pool.execute(()->handle(s));
                }
            } catch(Exception ignored) {
            } finally { running=false; }
        },"zemri-http-accept");
        acceptThread.start();
    }

    public synchronized void stop() {
        running=false;
        try { if(serverSocket!=null) serverSocket.close(); } catch(Exception ignored) {}
        pool.shutdownNow();
    }

    private void handle(Socket socket) {
        try(Socket s=socket) {
            s.setSoTimeout(10000);
            BufferedInputStream in=new BufferedInputStream(s.getInputStream());
            BufferedOutputStream out=new BufferedOutputStream(s.getOutputStream());
            String requestLine=readLine(in);
            if(requestLine==null||requestLine.isEmpty()) return;
            String[] first=requestLine.split(" ");
            if(first.length<2) return;
            String method=first[0].toUpperCase();
            String path=first[1];
            Map<String,String> headers=new HashMap<>();
            String line;
            while((line=readLine(in))!=null && !line.isEmpty()) {
                int k=line.indexOf(':');
                if(k>0) headers.put(line.substring(0,k).trim().toLowerCase(),line.substring(k+1).trim());
            }
            int len=0;
            try { len=Integer.parseInt(headers.getOrDefault("content-length","0")); } catch(Exception ignored) {}
            byte[] body=new byte[Math.max(0,len)];
            int off=0; while(off<body.length) { int n=in.read(body,off,body.length-off); if(n<0) break; off+=n; }
            Response r=route(method,path,new String(body,StandardCharsets.UTF_8));
            byte[] bytes=r.body.getBytes(StandardCharsets.UTF_8);
            String head="HTTP/1.1 "+r.status+"\r\nContent-Type: "+r.contentType+"\r\nContent-Length: "+bytes.length+"\r\nCache-Control: no-store\r\nConnection: close\r\n\r\n";
            out.write(head.getBytes(StandardCharsets.UTF_8)); out.write(bytes); out.flush();
        } catch(Exception ignored) {}
    }

    private Response route(String method,String rawPath,String body) {
        String path=rawPath.split("\\?",2)[0];
        if(path.equals("/")||path.equals("/health")) return html(200,"<html><meta charset='utf-8'><body dir='rtl'><h2>ZEMRI Attendance ✓</h2><p>الخادم المحلي يعمل.</p></body></html>");
        if(path.startsWith("/s/")) {
            String token=path.substring(3).replaceAll("[^A-Za-z0-9]","");
            AttendanceDb db=AttendanceDb.get(ctx);
            AttendanceDb.Session ses=db.getSessionByToken(token);
            if(ses==null) return html(404,page("الرابط غير صالح","راجع الأستاذ."));
            if(method.equals("POST")) {
                if(!ses.open) return html(403,page("تم غلق الحضور","لا يمكن التسجيل الآن."));
                Map<String,String> f=parseForm(body);
                AttendanceDb.RegisterResult rr=db.register(ses.id,f.get("full_name"),f.get("matricule"),f.get("device_token"));
                switch(rr) {
                    case OK: return html(200,page("تم تسجيل حضورك ✓",esc(f.get("full_name"))+"<br>"+esc(ses.courseName)+"<br><small>"+esc(ses.sessionDate)+"</small>"));
                    case DUPLICATE_STUDENT: return html(200,page("تم تسجيل حضورك مسبقاً ✓","لا حاجة لإعادة التسجيل."));
                    case DEVICE_ALREADY_USED: return html(409,page("هذا الهاتف سجّل حضوراً بالفعل","إذا كان هناك خطأ، راجع الأستاذ."));
                    case CLOSED: return html(403,page("تم غلق الحضور","لا يمكن التسجيل الآن."));
                    default: return html(400,page("بيانات غير صحيحة","أدخل الاسم ورقم التسجيل بشكل صحيح."));
                }
            }
            if(!ses.open) return html(403,page("الحضور مغلق","راجع الأستاذ."));
            return html(200,formPage(ses));
        }
        return html(404,page("404","الصفحة غير موجودة."));
    }

    private String formPage(AttendanceDb.Session s) {
        String key="zemri_student_"+s.courseId;
        return "<!doctype html><html dir='rtl'><head><meta charset='utf-8'><meta name='viewport' content='width=device-width,initial-scale=1'>"+style()+"</head><body><main>"+
                "<section><h2>"+esc(s.courseName)+"</h2><p>"+esc(s.sessionDate)+" — "+esc(s.startTime)+"</p>"+
                "<form method='post' id='f'><label>الاسم واللقب</label><input id='name' name='full_name' required autocomplete='name'>"+
                "<label>رقم التسجيل / Matricule</label><input id='mat' name='matricule' required autocomplete='off'>"+
                "<input type='hidden' id='dev' name='device_token'><button>تسجيل حضوري</button></form>"+
                "<p class='small'>يحتفظ هاتفك بهذه البيانات لهذا المقياس فقط لتسهيل الحصص القادمة.</p></section></main>"+
                "<script>const key='"+key+"';let d={};try{d=JSON.parse(localStorage.getItem(key)||'{}')}catch(e){};"+
                "if(d.name)name.value=d.name;if(d.mat)mat.value=d.mat;let dv=localStorage.getItem('zemri_device');"+
                "if(!dv){dv=(crypto.randomUUID?crypto.randomUUID():Math.random().toString(36).slice(2)+Date.now());localStorage.setItem('zemri_device',dv)}dev.value=dv;"+
                "f.addEventListener('submit',()=>localStorage.setItem(key,JSON.stringify({name:name.value,mat:mat.value})));</script></body></html>";
    }

    private String page(String title,String content) {
        return "<!doctype html><html dir='rtl'><head><meta charset='utf-8'><meta name='viewport' content='width=device-width,initial-scale=1'>"+style()+"</head><body><main><section class='center'><h2>"+title+"</h2><p>"+content+"</p></section></main></body></html>";
    }

    private String style() { return "<style>*{box-sizing:border-box}body{margin:0;background:#f4f6f8;font-family:Arial,Tahoma,sans-serif;color:#111827}main{max-width:620px;margin:auto;padding:18px}section{background:white;border-radius:18px;padding:20px;margin-top:20px;box-shadow:0 2px 12px #0001}label{display:block;font-weight:700;margin-top:12px}input{width:100%;padding:14px;border:1px solid #d1d5db;border-radius:12px;font-size:17px;margin-top:6px}button{width:100%;padding:15px;border:0;border-radius:12px;background:#047857;color:white;font-size:18px;font-weight:700;margin-top:18px}.small{font-size:13px;color:#6b7280}.center{text-align:center}</style>"; }
    private Response html(int code,String s){return new Response(code+" "+reason(code),"text/html; charset=utf-8",s);}
    private String reason(int c){if(c==200)return"OK";if(c==400)return"Bad Request";if(c==403)return"Forbidden";if(c==404)return"Not Found";if(c==409)return"Conflict";return"OK";}

    private Map<String,String> parseForm(String body) {
        Map<String,String> m=new HashMap<>();
        if(body==null)return m;
        for(String p:body.split("&")) { int k=p.indexOf('='); String a=k>=0?p.substring(0,k):p;String b=k>=0?p.substring(k+1):""; try{m.put(URLDecoder.decode(a,"UTF-8"),URLDecoder.decode(b,"UTF-8"));}catch(Exception ignored){} }
        return m;
    }
    private String esc(String s){if(s==null)return"";return s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;").replace("'","&#39;");}
    private String readLine(InputStream in)throws Exception{ByteArrayOutputStream b=new ByteArrayOutputStream();int prev=-1;while(true){int x=in.read();if(x<0){if(b.size()==0)return null;break;}if(x=='\n')break;if(prev=='\r')b.write('\r');if(x!='\r')b.write(x);prev=x;}return b.toString("UTF-8");}
    private static class Response{String status,contentType,body;Response(String s,String c,String b){status=s;contentType=c;body=b;}}
}
