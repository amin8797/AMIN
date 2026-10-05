package com.zemri.attendance;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class AttendanceDb extends SQLiteOpenHelper {
    private static final String DB_NAME = "zemri_attendance.db";
    private static final int DB_VERSION = 1;
    private static AttendanceDb INSTANCE;

    public static synchronized AttendanceDb get(Context c) {
        if (INSTANCE == null) INSTANCE = new AttendanceDb(c.getApplicationContext());
        return INSTANCE;
    }

    private AttendanceDb(Context c) { super(c, DB_NAME, null, DB_VERSION); }

    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE courses(id INTEGER PRIMARY KEY AUTOINCREMENT,name TEXT NOT NULL,weekday INTEGER NOT NULL,start_time TEXT NOT NULL,sort_order INTEGER NOT NULL DEFAULT 0,active INTEGER NOT NULL DEFAULT 1)");
        db.execSQL("CREATE TABLE sessions(id INTEGER PRIMARY KEY AUTOINCREMENT,course_id INTEGER NOT NULL,session_date TEXT NOT NULL,token TEXT NOT NULL UNIQUE,is_open INTEGER NOT NULL DEFAULT 1,created_at TEXT NOT NULL,UNIQUE(course_id,session_date))");
        db.execSQL("CREATE TABLE students(id INTEGER PRIMARY KEY AUTOINCREMENT,course_id INTEGER NOT NULL,matricule TEXT NOT NULL,full_name TEXT NOT NULL,created_at TEXT NOT NULL,UNIQUE(course_id,matricule))");
        db.execSQL("CREATE TABLE checkins(id INTEGER PRIMARY KEY AUTOINCREMENT,session_id INTEGER NOT NULL,student_id INTEGER NOT NULL,device_token TEXT,checked_at TEXT NOT NULL,UNIQUE(session_id,student_id))");
        db.execSQL("CREATE UNIQUE INDEX ux_checkin_device ON checkins(session_id,device_token) WHERE device_token IS NOT NULL AND device_token <> ''");
        seedCourses(db);
    }

    private void seedCourses(SQLiteDatabase db) {
        addCourseInternal(db, "Introduction to Deep Learning", 2, "10:00", 1);
        addCourseInternal(db, "Data Analysis and Internship — Lecture", 2, "13:00", 2);
        addCourseInternal(db, "Data Analysis and Internship — TP", 2, "14:30", 3);
        addCourseInternal(db, "Big Data Economics and Business", 3, "13:00", 4);
        addCourseInternal(db, "Big Data for Marketing", 3, "14:30", 5);
    }

    private void addCourseInternal(SQLiteDatabase db, String name, int weekday, String time, int sort) {
        ContentValues v = new ContentValues();
        v.put("name", name); v.put("weekday", weekday); v.put("start_time", time); v.put("sort_order", sort);
        db.insert("courses", null, v);
    }

    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {}

    public static class Course {
        public long id; public String name; public int weekday; public String startTime; public int sortOrder;
    }
    public static class Session {
        public long id, courseId; public String courseName, sessionDate, token, startTime; public boolean open;
    }
    public static class Student {
        public long id, courseId; public String matricule, fullName;
    }
    public static class CheckinRow {
        public String fullName, matricule, checkedAt;
    }

    public synchronized List<Course> getCourses() {
        List<Course> out = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery("SELECT id,name,weekday,start_time,sort_order FROM courses WHERE active=1 ORDER BY sort_order,weekday,start_time,id", null)) {
            while(c.moveToNext()) {
                Course x = new Course(); x.id=c.getLong(0); x.name=c.getString(1); x.weekday=c.getInt(2); x.startTime=c.getString(3); x.sortOrder=c.getInt(4); out.add(x);
            }
        }
        return out;
    }

    public synchronized Course getCourse(long id) {
        try(Cursor c=getReadableDatabase().rawQuery("SELECT id,name,weekday,start_time,sort_order FROM courses WHERE id=?", new String[]{String.valueOf(id)})) {
            if(c.moveToFirst()) { Course x=new Course(); x.id=c.getLong(0);x.name=c.getString(1);x.weekday=c.getInt(2);x.startTime=c.getString(3);x.sortOrder=c.getInt(4);return x; }
        }
        return null;
    }

    public synchronized void updateCourse(long id, String name, int weekday, String time) {
        ContentValues v=new ContentValues(); v.put("name",name);v.put("weekday",weekday);v.put("start_time",time);
        getWritableDatabase().update("courses",v,"id=?",new String[]{String.valueOf(id)});
    }

    public synchronized Session openTodaySession(long courseId) {
        String date = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
        SQLiteDatabase db=getWritableDatabase();
        try(Cursor c=db.rawQuery("SELECT s.id,s.course_id,c.name,s.session_date,s.token,s.is_open,c.start_time FROM sessions s JOIN courses c ON c.id=s.course_id WHERE s.course_id=? AND s.session_date=?",new String[]{String.valueOf(courseId),date})) {
            if(c.moveToFirst()) {
                long id=c.getLong(0);
                ContentValues u=new ContentValues(); u.put("is_open",1); db.update("sessions",u,"id=?",new String[]{String.valueOf(id)});
                Session s=new Session(); s.id=id;s.courseId=c.getLong(1);s.courseName=c.getString(2);s.sessionDate=c.getString(3);s.token=c.getString(4);s.open=true;s.startTime=c.getString(6);return s;
            }
        }
        ContentValues v=new ContentValues();v.put("course_id",courseId);v.put("session_date",date);v.put("token",UUID.randomUUID().toString().replace("-","").substring(0,12));v.put("is_open",1);v.put("created_at",now());
        long sid=db.insertOrThrow("sessions",null,v);
        return getSession(sid);
    }

    public synchronized Session getSession(long id) {
        try(Cursor c=getReadableDatabase().rawQuery("SELECT s.id,s.course_id,c.name,s.session_date,s.token,s.is_open,c.start_time FROM sessions s JOIN courses c ON c.id=s.course_id WHERE s.id=?",new String[]{String.valueOf(id)})) {
            if(c.moveToFirst()) { Session s=new Session();s.id=c.getLong(0);s.courseId=c.getLong(1);s.courseName=c.getString(2);s.sessionDate=c.getString(3);s.token=c.getString(4);s.open=c.getInt(5)==1;s.startTime=c.getString(6);return s; }
        }
        return null;
    }

    public synchronized Session getSessionByToken(String token) {
        try(Cursor c=getReadableDatabase().rawQuery("SELECT s.id,s.course_id,c.name,s.session_date,s.token,s.is_open,c.start_time FROM sessions s JOIN courses c ON c.id=s.course_id WHERE s.token=?",new String[]{token})) {
            if(c.moveToFirst()) { Session s=new Session();s.id=c.getLong(0);s.courseId=c.getLong(1);s.courseName=c.getString(2);s.sessionDate=c.getString(3);s.token=c.getString(4);s.open=c.getInt(5)==1;s.startTime=c.getString(6);return s; }
        }
        return null;
    }

    public synchronized void closeSession(long sid) {
        ContentValues v=new ContentValues();v.put("is_open",0);getWritableDatabase().update("sessions",v,"id=?",new String[]{String.valueOf(sid)});
    }

    public synchronized void reopenSession(long sid) {
        ContentValues v=new ContentValues();v.put("is_open",1);getWritableDatabase().update("sessions",v,"id=?",new String[]{String.valueOf(sid)});
    }

    public synchronized int countCheckins(long sid) {
        try(Cursor c=getReadableDatabase().rawQuery("SELECT COUNT(*) FROM checkins WHERE session_id=?",new String[]{String.valueOf(sid)})) { c.moveToFirst(); return c.getInt(0); }
    }

    public synchronized List<CheckinRow> getCheckins(long sid) {
        List<CheckinRow> out=new ArrayList<>();
        try(Cursor c=getReadableDatabase().rawQuery("SELECT st.full_name,st.matricule,ch.checked_at FROM checkins ch JOIN students st ON st.id=ch.student_id WHERE ch.session_id=? ORDER BY ch.checked_at",new String[]{String.valueOf(sid)})) {
            while(c.moveToNext()) { CheckinRow r=new CheckinRow();r.fullName=c.getString(0);r.matricule=c.getString(1);r.checkedAt=c.getString(2);out.add(r); }
        }
        return out;
    }

    public enum RegisterResult { OK, DUPLICATE_STUDENT, DEVICE_ALREADY_USED, CLOSED, INVALID }

    public synchronized RegisterResult register(long sessionId, String fullName, String matricule, String deviceToken) {
        Session s=getSession(sessionId); if(s==null || !s.open) return RegisterResult.CLOSED;
        fullName=fullName==null?"":fullName.trim().replaceAll("\\s+"," ");
        matricule=matricule==null?"":matricule.replaceAll("\\s+","").trim();
        if(fullName.length()<3 || matricule.length()<2) return RegisterResult.INVALID;
        SQLiteDatabase db=getWritableDatabase();
        long studentId=-1;
        try(Cursor c=db.rawQuery("SELECT id FROM students WHERE course_id=? AND matricule=?",new String[]{String.valueOf(s.courseId),matricule})) {
            if(c.moveToFirst()) studentId=c.getLong(0);
        }
        if(studentId<0) {
            ContentValues sv=new ContentValues();sv.put("course_id",s.courseId);sv.put("matricule",matricule);sv.put("full_name",fullName);sv.put("created_at",now());studentId=db.insertOrThrow("students",null,sv);
        } else {
            ContentValues uv=new ContentValues();uv.put("full_name",fullName);db.update("students",uv,"id=?",new String[]{String.valueOf(studentId)});
        }
        try(Cursor c=db.rawQuery("SELECT 1 FROM checkins WHERE session_id=? AND student_id=?",new String[]{String.valueOf(sessionId),String.valueOf(studentId)})) { if(c.moveToFirst()) return RegisterResult.DUPLICATE_STUDENT; }
        if(deviceToken!=null && !deviceToken.isEmpty()) {
            try(Cursor c=db.rawQuery("SELECT 1 FROM checkins WHERE session_id=? AND device_token=?",new String[]{String.valueOf(sessionId),deviceToken})) { if(c.moveToFirst()) return RegisterResult.DEVICE_ALREADY_USED; }
        }
        ContentValues cv=new ContentValues();cv.put("session_id",sessionId);cv.put("student_id",studentId);cv.put("device_token",deviceToken);cv.put("checked_at",now());db.insertOrThrow("checkins",null,cv);
        return RegisterResult.OK;
    }

    public synchronized List<Session> getSessionsForCourse(long courseId) {
        List<Session> out=new ArrayList<>();
        try(Cursor c=getReadableDatabase().rawQuery("SELECT s.id,s.course_id,c.name,s.session_date,s.token,s.is_open,c.start_time FROM sessions s JOIN courses c ON c.id=s.course_id WHERE s.course_id=? ORDER BY s.session_date,s.id",new String[]{String.valueOf(courseId)})) {
            while(c.moveToNext()) { Session s=new Session();s.id=c.getLong(0);s.courseId=c.getLong(1);s.courseName=c.getString(2);s.sessionDate=c.getString(3);s.token=c.getString(4);s.open=c.getInt(5)==1;s.startTime=c.getString(6);out.add(s); }
        }
        return out;
    }

    public synchronized List<Student> getStudentsForCourse(long courseId) {
        List<Student> out=new ArrayList<>();
        try(Cursor c=getReadableDatabase().rawQuery("SELECT id,course_id,matricule,full_name FROM students WHERE course_id=? ORDER BY full_name",new String[]{String.valueOf(courseId)})) {
            while(c.moveToNext()) { Student s=new Student();s.id=c.getLong(0);s.courseId=c.getLong(1);s.matricule=c.getString(2);s.fullName=c.getString(3);out.add(s); }
        }
        return out;
    }

    public synchronized boolean isPresent(long studentId,long sessionId) {
        try(Cursor c=getReadableDatabase().rawQuery("SELECT 1 FROM checkins WHERE student_id=? AND session_id=?",new String[]{String.valueOf(studentId),String.valueOf(sessionId)})) { return c.moveToFirst(); }
    }

    public synchronized Map<Long,Boolean> presenceMap(long courseId,long studentId) {
        Map<Long,Boolean> out=new LinkedHashMap<>();
        for(Session s:getSessionsForCourse(courseId)) out.put(s.id,isPresent(studentId,s.id));
        return out;
    }

    private static String now() { return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss",Locale.US).format(new Date()); }
}
