package com.zemri.attendance;

import android.content.Context;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class XlsxExporter {
    public static byte[] exportAll(Context ctx) throws Exception {
        AttendanceDb db=AttendanceDb.get(ctx);
        List<AttendanceDb.Course> courses=db.getCourses();
        ByteArrayOutputStream baos=new ByteArrayOutputStream();
        ZipOutputStream z=new ZipOutputStream(baos);
        put(z,"[Content_Types].xml",contentTypes(courses.size()));
        put(z,"_rels/.rels","<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/></Relationships>");
        put(z,"xl/workbook.xml",workbook(courses));
        put(z,"xl/_rels/workbook.xml.rels",workbookRels(courses.size()));
        put(z,"xl/styles.xml",styles());
        for(int i=0;i<courses.size();i++) put(z,"xl/worksheets/sheet"+(i+1)+".xml",sheet(db,courses.get(i)));
        z.finish();z.close();return baos.toByteArray();
    }

    private static String contentTypes(int n){StringBuilder s=new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"><Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/><Default Extension=\"xml\" ContentType=\"application/xml\"/><Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/><Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/>");for(int i=1;i<=n;i++)s.append("<Override PartName=\"/xl/worksheets/sheet").append(i).append(".xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>");return s.append("</Types>").toString();}
    private static String workbook(List<AttendanceDb.Course> cs){StringBuilder s=new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\"><sheets>");for(int i=0;i<cs.size();i++)s.append("<sheet name=\"").append(xml(sheetName(cs.get(i).name,i))).append("\" sheetId=\"").append(i+1).append("\" r:id=\"rId").append(i+1).append("\"/>");return s.append("</sheets></workbook>").toString();}
    private static String workbookRels(int n){StringBuilder s=new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">");for(int i=1;i<=n;i++)s.append("<Relationship Id=\"rId").append(i).append("\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet").append(i).append(".xml\"/>");s.append("<Relationship Id=\"rId").append(n+1).append("\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/>");return s.append("</Relationships>").toString();}
    private static String styles(){return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><styleSheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><fonts count=\"2\"><font><sz val=\"11\"/><name val=\"Arial\"/></font><font><b/><sz val=\"11\"/><name val=\"Arial\"/></font></fonts><fills count=\"2\"><fill><patternFill patternType=\"none\"/></fill><fill><patternFill patternType=\"gray125\"/></fill></fills><borders count=\"1\"><border><left/><right/><top/><bottom/><diagonal/></border></borders><cellStyleXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/></cellStyleXfs><cellXfs count=\"2\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\"/><xf numFmtId=\"0\" fontId=\"1\" fillId=\"0\" borderId=\"0\" xfId=\"0\"/></cellXfs></styleSheet>";}

    private static String sheet(AttendanceDb db,AttendanceDb.Course c){
        List<AttendanceDb.Session> sessions=db.getSessionsForCourse(c.id);List<AttendanceDb.Student> students=db.getStudentsForCourse(c.id);
        List<String> headers=new ArrayList<>();headers.add("الاسم واللقب");headers.add("رقم التسجيل");for(AttendanceDb.Session s:sessions)headers.add(s.sessionDate);headers.add("عدد الحضور");headers.add("عدد الغياب");headers.add("نسبة الحضور");
        StringBuilder x=new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheetViews><sheetView rightToLeft=\"1\" workbookViewId=\"0\"><pane xSplit=\"2\" ySplit=\"1\" topLeftCell=\"C2\" activePane=\"bottomRight\" state=\"frozen\"/></sheetView></sheetViews><sheetData>");
        x.append("<row r=\"1\">");for(int i=0;i<headers.size();i++)x.append(cell(i+1,1,headers.get(i),1));x.append("</row>");
        int r=2;for(AttendanceDb.Student st:students){int p=0,a=0;x.append("<row r=\"").append(r).append("\">");x.append(cell(1,r,st.fullName,0)).append(cell(2,r,st.matricule,0));int col=3;for(AttendanceDb.Session se:sessions){boolean present=db.isPresent(st.id,se.id);if(present)p++;else a++;x.append(cell(col++,r,present?"حاضر":"غائب",0));}x.append(numberCell(col++,r,p)).append(numberCell(col++,r,a));double pct=(p+a)==0?0:(100.0*p/(p+a));x.append(numberCell(col,r,pct));x.append("</row>");r++;}
        x.append("</sheetData></worksheet>");return x.toString();
    }
    private static String cell(int col,int row,String v,int style){String ref=colName(col)+row;return "<c r=\""+ref+"\" t=\"inlineStr\" s=\""+style+"\"><is><t>"+xml(v)+"</t></is></c>";}
    private static String numberCell(int col,int row,double v){String ref=colName(col)+row;return "<c r=\""+ref+"\"><v>"+String.format(Locale.US,"%.6f",v)+"</v></c>";}
    private static String colName(int n){StringBuilder s=new StringBuilder();while(n>0){n--;s.insert(0,(char)('A'+n%26));n/=26;}return s.toString();}
    private static String sheetName(String s,int i){s=s.replaceAll("[:\\\\/?*\\[\\]]","-");if(s.length()>31)s=s.substring(0,31);if(s.trim().isEmpty())s="Course "+(i+1);return s;}
    private static String xml(String s){if(s==null)return"";return s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;").replace("'","&apos;");}
    private static void put(ZipOutputStream z,String path,String data)throws Exception{z.putNextEntry(new ZipEntry(path));z.write(data.getBytes(StandardCharsets.UTF_8));z.closeEntry();}
}
