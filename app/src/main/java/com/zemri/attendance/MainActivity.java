package com.zemri.attendance;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {
    private AttendanceDb db;
    private LinearLayout root;
    private Handler handler=new Handler(Looper.getMainLooper());
    private Runnable liveRunnable;
    private byte[] pendingExport;
    private LocalHttpServer localServer;
    private static final int REQ_CREATE_XLSX=901;
    private static final int REQ_NOTIF=902;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().getDecorView().setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        db=AttendanceDb.get(this);
        try {
            localServer=new LocalHttpServer(this,8080);
            localServer.start();
        } catch(Exception e) {
            Toast.makeText(this,"تعذر تشغيل الخادم المحلي: "+e.getMessage(),Toast.LENGTH_LONG).show();
        }
        showPin();
    }

    private void showPin() {
        SharedPreferences p=getSharedPreferences("zemri_settings",MODE_PRIVATE);
        String pin=p.getString("teacher_pin","2468");
        EditText input=new EditText(this);input.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_VARIATION_PASSWORD);input.setHint("PIN");input.setGravity(Gravity.CENTER);input.setTextSize(24);input.setPadding(dp(16),dp(16),dp(16),dp(16));
        AlertDialog dlg=new AlertDialog.Builder(this).setTitle("ZEMRI Attendance").setMessage("أدخل رمز الأستاذ").setView(input).setCancelable(false)
                .setNegativeButton("خروج",(d,w)->finish())
                .setPositiveButton("دخول",null).create();
        dlg.setOnShowListener(x->dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            if(input.getText().toString().equals(pin)){dlg.dismiss();showDashboard();}
            else {input.setError("الرمز غير صحيح");input.selectAll();}
        }));
        dlg.show();
    }

    private void baseScreen(String title) {
        stopLive();
        ScrollView sv=new ScrollView(this);sv.setFillViewport(true);sv.setBackgroundColor(0xfff4f6f8);
        root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(14),dp(14),dp(14),dp(24));root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);sv.addView(root,new ScrollView.LayoutParams(-1,-2));
        TextView h=text(title,28,true);h.setTextColor(0xff111827);root.addView(h,lp(-1,-2,0,8));
        setContentView(sv);
    }

    private void showDashboard() {
        baseScreen("ZEMRI Attendance");
        String date=new SimpleDateFormat("yyyy-MM-dd",Locale.US).format(new Date());
        addCardText(date+"  •  "+arabicDay(Calendar.getInstance().get(Calendar.DAY_OF_WEEK)),16,false,0xff6b7280);
        addNetworkCard();
        for(AttendanceDb.Course c:db.getCourses()) addCourseCard(c);
        LinearLayout row=hrow();
        Button export=button("تحميل Excel كامل",0xff111827);export.setOnClickListener(v->exportExcel());row.addView(export,weight());
        Button settings=button("الإعدادات",0xffe5e7eb);settings.setTextColor(0xff111827);settings.setOnClickListener(v->showSettings());row.addView(settings,weight());
        root.addView(row,lp(-1,-2,8,8));
    }

    private void addNetworkCard() {
        LinearLayout card=card();
        TextView t=text("الشبكة المحلية",18,true);card.addView(t);
        String base=NetworkUtil.baseUrl(this);
        card.addView(text(base,16,true));
        List<NetworkUtil.Candidate> cs=NetworkUtil.candidates();
        if(cs.isEmpty()) card.addView(text("فعّل Mobile Hotspot قبل الحصة.",14,false));
        else {
            for(NetworkUtil.Candidate c:cs) card.addView(text(c.iface+"  →  "+c.ip,13,false));
        }
        Button test=button("اختبار اتصال الطلبة",0xff2563eb);test.setOnClickListener(v->showConnectionTest());card.addView(test,lp(-1,-2,8,0));
        root.addView(card,lp(-1,-2,8,8));
    }

    private void addCourseCard(AttendanceDb.Course c) {
        LinearLayout card=card();
        card.addView(text(c.name,19,true));
        card.addView(text(arabicDay(c.weekday)+" — "+c.startTime,14,false));
        AttendanceDb.Session existing=findTodaySession(c.id);
        if(existing!=null) {
            TextView x=text(existing.open?"الحضور مفتوح • "+db.countCheckins(existing.id)+" حاضر":"الحصة مغلقة • "+db.countCheckins(existing.id)+" حاضر",14,true);
            x.setTextColor(existing.open?0xff047857:0xffb91c1c);card.addView(x);
            Button open=button(existing.open?"عرض الحصة":"عرض / إعادة فتح",existing.open?0xff047857:0xff374151);open.setOnClickListener(v->showSession(existing.id));card.addView(open,lp(-1,-2,8,0));
        } else {
            Button open=button("فتح حضور اليوم",0xff111827);open.setOnClickListener(v->{
                if(Calendar.getInstance().get(Calendar.DAY_OF_WEEK)!=c.weekday) {
                    new AlertDialog.Builder(this).setTitle("تنبيه").setMessage("اليوم ليس "+arabicDay(c.weekday)+". هل تريد فتح حصة استثنائية بتاريخ اليوم؟")
                            .setNegativeButton("إلغاء",null).setPositiveButton("فتح",(d,w)->showSession(db.openTodaySession(c.id).id)).show();
                } else showSession(db.openTodaySession(c.id).id);
            });card.addView(open,lp(-1,-2,8,0));
        }
        LinearLayout mini=hrow();
        Button report=button("السجل",0xffe5e7eb);report.setTextColor(0xff111827);report.setOnClickListener(v->showReport(c.id));mini.addView(report,weight());
        Button edit=button("تعديل",0xffe5e7eb);edit.setTextColor(0xff111827);edit.setOnClickListener(v->editCourse(c));mini.addView(edit,weight());
        card.addView(mini,lp(-1,-2,6,0));
        root.addView(card,lp(-1,-2,8,8));
    }

    private AttendanceDb.Session findTodaySession(long cid) {
        String today=new SimpleDateFormat("yyyy-MM-dd",Locale.US).format(new Date());
        for(AttendanceDb.Session s:db.getSessionsForCourse(cid)) if(s.sessionDate.equals(today)) return s;
        return null;
    }

    private void showSession(long sid) {
        AttendanceDb.Session s=db.getSession(sid);if(s==null){showDashboard();return;}
        baseScreen(s.courseName);
        addCardText(s.sessionDate+" — "+s.startTime,18,true,0xff111827);
        String url=NetworkUtil.baseUrl(this)+"/s/"+s.token;
        LinearLayout card=card();
        TextView state=text(s.open?"الحضور مفتوح":"الحضور مغلق",18,true);state.setTextColor(s.open?0xff047857:0xffb91c1c);card.addView(state);
        if(s.open) {
            try { Bitmap qr=QrUtil.make(url,760);ImageView iv=new ImageView(this);iv.setImageBitmap(qr);iv.setAdjustViewBounds(true);card.addView(iv,new LinearLayout.LayoutParams(-1,dp(330))); } catch(Exception e) { card.addView(text("تعذر إنشاء QR: "+e.getMessage(),14,false)); }
            TextView link=text(url,16,true);link.setTextIsSelectable(true);link.setGravity(Gravity.CENTER);card.addView(link);
            Button bigQr=button("عرض QR كبير",0xff2563eb);bigQr.setOnClickListener(v->showBigQr(url));card.addView(bigQr,lp(-1,-2,8,0));
            Button copy=button("نسخ الرابط",0xffe5e7eb);copy.setTextColor(0xff111827);copy.setOnClickListener(v->copyText(url));card.addView(copy,lp(-1,-2,6,0));
        }
        final TextView count=text("",22,true);count.setGravity(Gravity.CENTER);card.addView(count,lp(-1,-2,10,2));
        final TextView list=text("",15,false);card.addView(list,lp(-1,-2,4,8));
        if(s.open) {
            Button close=button("غلق الحضور",0xffb91c1c);close.setOnClickListener(v->{db.closeSession(s.id);Toast.makeText(this,"تم غلق الحضور",Toast.LENGTH_SHORT).show();showSession(s.id);});card.addView(close);
        } else {
            Button reopen=button("إعادة فتح الحضور",0xff047857);reopen.setOnClickListener(v->{db.reopenSession(s.id);showSession(s.id);});card.addView(reopen);
        }
        root.addView(card,lp(-1,-2,8,8));
        Button back=button("رجوع",0xff374151);back.setOnClickListener(v->showDashboard());root.addView(back,lp(-1,-2,8,8));
        liveRunnable=new Runnable(){@Override public void run(){List<AttendanceDb.CheckinRow> rows=db.getCheckins(s.id);count.setText(rows.size()+" حاضر");StringBuilder b=new StringBuilder();int i=1;for(AttendanceDb.CheckinRow r:rows){b.append(i++).append(". ").append(r.fullName).append(" — ").append(r.matricule).append("   ").append(r.checkedAt.length()>=16?r.checkedAt.substring(11,16):"").append("\n");}list.setText(b.toString());handler.postDelayed(this,2000);}};handler.post(liveRunnable);
    }

    private void showReport(long cid) {
        AttendanceDb.Course c=db.getCourse(cid);if(c==null){showDashboard();return;}
        baseScreen("سجل — "+c.name);
        List<AttendanceDb.Session> sessions=db.getSessionsForCourse(cid);List<AttendanceDb.Student> students=db.getStudentsForCourse(cid);
        addCardText(students.size()+" طالب • "+sessions.size()+" حصة",15,false,0xff6b7280);
        for(AttendanceDb.Student st:students){int p=0,a=0;StringBuilder b=new StringBuilder();for(AttendanceDb.Session s:sessions){boolean yes=db.isPresent(st.id,s.id);if(yes)p++;else a++;b.append(s.sessionDate).append(" : ").append(yes?"حاضر ✓":"غائب ✗").append("\n");}LinearLayout card=card();card.addView(text(st.fullName,18,true));card.addView(text(st.matricule,14,true));card.addView(text(b.toString(),14,false));card.addView(text("الحضور: "+p+"   الغياب: "+a+"   النسبة: "+((p+a)==0?0:(int)Math.round(100.0*p/(p+a)))+"%",14,true));root.addView(card,lp(-1,-2,6,6));}
        Button export=button("تحميل Excel كامل",0xff111827);export.setOnClickListener(v->exportExcel());root.addView(export,lp(-1,-2,8,4));
        Button back=button("رجوع",0xff374151);back.setOnClickListener(v->showDashboard());root.addView(back,lp(-1,-2,4,8));
    }

    private void showConnectionTest() {
        baseScreen("اختبار اتصال الطلبة");
        LinearLayout card=card();
        String url=NetworkUtil.baseUrl(this)+"/health";
        card.addView(text("عنوان الاختبار",17,true));TextView u=text(url,15,true);u.setTextIsSelectable(true);card.addView(u);
        try{ImageView iv=new ImageView(this);iv.setImageBitmap(QrUtil.make(url,700));iv.setAdjustViewBounds(true);card.addView(iv,new LinearLayout.LayoutParams(-1,dp(320)));}catch(Exception ignored){}
        card.addView(text("1) فعّل Hotspot.\n2) اجعل هاتف طالب يتصل به.\n3) امسح هذا QR.\n4) إذا ظهرت ZEMRI Attendance ✓ فالاتصال صحيح.",15,false));
        root.addView(card,lp(-1,-2,8,8));
        Button settings=button("إذا لم يعمل: ضبط عنوان Hotspot",0xff2563eb);settings.setOnClickListener(v->showSettings());root.addView(settings,lp(-1,-2,6,6));
        Button back=button("رجوع",0xff374151);back.setOnClickListener(v->showDashboard());root.addView(back,lp(-1,-2,6,8));
    }

    private void showSettings() {
        baseScreen("الإعدادات");
        SharedPreferences p=getSharedPreferences("zemri_settings",MODE_PRIVATE);
        LinearLayout card=card();
        card.addView(text("عنوان Hotspot اليدوي",18,true));
        card.addView(text("اتركه فارغاً للاكتشاف التلقائي. مثال: http://10.208.67.1:8080",13,false));
        EditText url=input(p.getString("manual_base_url",""));url.setHint("http://10.208.67.1:8080");card.addView(url);
        Button save=button("حفظ العنوان",0xff111827);save.setOnClickListener(v->{p.edit().putString("manual_base_url",url.getText().toString().trim()).apply();Toast.makeText(this,"تم الحفظ",Toast.LENGTH_SHORT).show();showSettings();});card.addView(save);
        root.addView(card,lp(-1,-2,8,8));

        LinearLayout pinCard=card();pinCard.addView(text("تغيير PIN الأستاذ",18,true));EditText pin=input("");pin.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_VARIATION_PASSWORD);pin.setHint("PIN جديد (4 أرقام أو أكثر)");pinCard.addView(pin);Button savePin=button("تغيير PIN",0xff111827);savePin.setOnClickListener(v->{if(pin.length()<4){pin.setError("4 أرقام على الأقل");return;}p.edit().putString("teacher_pin",pin.getText().toString()).apply();Toast.makeText(this,"تم تغيير PIN",Toast.LENGTH_SHORT).show();});pinCard.addView(savePin);root.addView(pinCard,lp(-1,-2,8,8));

        LinearLayout info=card();info.addView(text("العناوين المكتشفة",18,true));for(NetworkUtil.Candidate c:NetworkUtil.candidates())info.addView(text(c.iface+"  →  "+c.ip+"  (score "+c.score+")",14,false));root.addView(info,lp(-1,-2,8,8));
        Button appSettings=button("إعدادات التطبيق في Android",0xffe5e7eb);appSettings.setTextColor(0xff111827);appSettings.setOnClickListener(v->{Intent i=new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:"+getPackageName()));startActivity(i);});root.addView(appSettings,lp(-1,-2,6,6));
        Button back=button("رجوع",0xff374151);back.setOnClickListener(v->showDashboard());root.addView(back,lp(-1,-2,6,8));
    }

    private void editCourse(AttendanceDb.Course c) {
        LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(20),0,dp(20),0);
        EditText name=input(c.name);EditText time=input(c.startTime);time.setHint("13:00");EditText day=input(String.valueOf(c.weekday));day.setInputType(InputType.TYPE_CLASS_NUMBER);day.setHint("2=الثلاثاء، 3=الأربعاء");box.addView(name);box.addView(time);box.addView(day);
        new AlertDialog.Builder(this).setTitle("تعديل الحصة").setView(box).setNegativeButton("إلغاء",null).setPositiveButton("حفظ",(d,w)->{int wd=c.weekday;try{wd=Integer.parseInt(day.getText().toString());}catch(Exception ignored){};if(wd<1||wd>7)wd=c.weekday;db.updateCourse(c.id,name.getText().toString().trim(),wd,time.getText().toString().trim());showDashboard();}).show();
    }

    private void showBigQr(String url) {
        LinearLayout box=new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(14),dp(14),dp(14),dp(14));
        box.setGravity(Gravity.CENTER);
        box.setBackgroundColor(0xffffffff);
        try {
            ImageView iv=new ImageView(this);
            iv.setImageBitmap(QrUtil.make(url,1200));
            iv.setAdjustViewBounds(true);
            iv.setBackgroundColor(0xffffffff);
            iv.setPadding(dp(18),dp(18),dp(18),dp(18));
            box.addView(iv,new LinearLayout.LayoutParams(-1,dp(520)));
        } catch(Exception e) {
            box.addView(text("تعذر إنشاء QR: "+e.getMessage(),16,false));
        }
        TextView u=text(url,18,true);
        u.setGravity(Gravity.CENTER);
        u.setTextIsSelectable(true);
        box.addView(u,lp(-1,-2,10,8));
        Button c=button("نسخ الرابط",0xff111827);
        c.setOnClickListener(v->copyText(url));
        box.addView(c,lp(-1,-2,6,0));
        new AlertDialog.Builder(this)
                .setTitle("QR الحضور")
                .setView(box)
                .setPositiveButton("إغلاق",null)
                .show();
    }

    private void copyText(String value) {
        ClipboardManager cm=(ClipboardManager)getSystemService(Context.CLIPBOARD_SERVICE);
        if(cm!=null) cm.setPrimaryClip(ClipData.newPlainText("ZEMRI Attendance",value));
        Toast.makeText(this,"تم نسخ الرابط",Toast.LENGTH_SHORT).show();
    }

    private void exportExcel() {
        try {pendingExport=XlsxExporter.exportAll(this);Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");i.putExtra(Intent.EXTRA_TITLE,"ZEMRI_Attendance_"+new SimpleDateFormat("yyyy-MM-dd",Locale.US).format(new Date())+".xlsx");startActivityForResult(i,REQ_CREATE_XLSX);} catch(Exception e){new AlertDialog.Builder(this).setTitle("خطأ Excel").setMessage(e.toString()).setPositiveButton("حسناً",null).show();}
    }

    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data){super.onActivityResult(requestCode,resultCode,data);if(requestCode==REQ_CREATE_XLSX&&resultCode==RESULT_OK&&data!=null&&data.getData()!=null&&pendingExport!=null){try(OutputStream o=getContentResolver().openOutputStream(data.getData())){o.write(pendingExport);Toast.makeText(this,"تم حفظ Excel",Toast.LENGTH_LONG).show();}catch(Exception e){Toast.makeText(this,"فشل الحفظ: "+e.getMessage(),Toast.LENGTH_LONG).show();}pendingExport=null;}}

    @Override public void onBackPressed(){showDashboard();}
    @Override protected void onDestroy(){stopLive();if(localServer!=null)localServer.stop();super.onDestroy();}
    private void stopLive(){if(liveRunnable!=null){handler.removeCallbacks(liveRunnable);liveRunnable=null;}}

    private LinearLayout card(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);l.setPadding(dp(16),dp(16),dp(16),dp(16));l.setBackground(bg(0xffffffff,18));return l;}
    private void addCardText(String s,int size,boolean bold,int color){LinearLayout c=card();TextView t=text(s,size,bold);t.setTextColor(color);c.addView(t);root.addView(c,lp(-1,-2,8,8));}
    private TextView text(String s,int size,boolean bold){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(0xff111827);t.setGravity(Gravity.RIGHT);t.setLineSpacing(0,1.15f);if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);t.setPadding(0,dp(3),0,dp(3));return t;}
    private Button button(String s,int color){Button b=new Button(this);b.setText(s);b.setTextSize(15);b.setTextColor(0xffffffff);b.setAllCaps(false);b.setBackground(bg(color,14));b.setPadding(dp(10),dp(11),dp(10),dp(11));return b;}
    private EditText input(String s){EditText e=new EditText(this);e.setText(s);e.setTextSize(17);e.setPadding(dp(12),dp(10),dp(12),dp(10));return e;}
    private LinearLayout hrow(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.HORIZONTAL);l.setGravity(Gravity.CENTER);return l;}
    private LinearLayout.LayoutParams weight(){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-2,1);p.setMargins(dp(4),dp(4),dp(4),dp(4));return p;}
    private LinearLayout.LayoutParams lp(int w,int h,int top,int bottom){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(w,h);p.setMargins(0,dp(top),0,dp(bottom));return p;}
    private android.graphics.drawable.GradientDrawable bg(int color,int radius){android.graphics.drawable.GradientDrawable g=new android.graphics.drawable.GradientDrawable();g.setColor(color);g.setCornerRadius(dp(radius));return g;}
    private int dp(int n){return (int)(n*getResources().getDisplayMetrics().density+.5f);}
    private String arabicDay(int day){switch(day){case Calendar.SUNDAY:return"الأحد";case Calendar.MONDAY:return"الاثنين";case Calendar.TUESDAY:return"الثلاثاء";case Calendar.WEDNESDAY:return"الأربعاء";case Calendar.THURSDAY:return"الخميس";case Calendar.FRIDAY:return"الجمعة";case Calendar.SATURDAY:return"السبت";default:return"";}}
}
