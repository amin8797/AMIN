package com.zemri.attendance;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

public class AttendanceServerService extends Service {
    public static final String CHANNEL_ID="zemri_attendance_server";
    private LocalHttpServer server;

    @Override public void onCreate() {
        super.onCreate();
        createChannel();
        Intent i=new Intent(this,MainActivity.class);
        PendingIntent pi=PendingIntent.getActivity(this,0,i,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b= Build.VERSION.SDK_INT>=26?new Notification.Builder(this,CHANNEL_ID):new Notification.Builder(this);
        b.setContentTitle("ZEMRI Attendance")
                .setContentText("خادم الحضور المحلي يعمل")
                .setSmallIcon(android.R.drawable.ic_menu_info_details)
                .setOngoing(true)
                .setContentIntent(pi);
        startForeground(42,b.build());
        server=new LocalHttpServer(this,8080);
        server.start();
    }

    private void createChannel() {
        if(Build.VERSION.SDK_INT>=26) {
            NotificationChannel ch=new NotificationChannel(CHANNEL_ID,"خدمة الحضور المحلي", NotificationManager.IMPORTANCE_LOW);
            NotificationManager nm=getSystemService(NotificationManager.class);
            if(nm!=null) nm.createNotificationChannel(ch);
        }
    }

    @Override public int onStartCommand(Intent intent,int flags,int startId) { return START_STICKY; }

    @Override public void onDestroy() {
        if(server!=null) server.stop();
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
