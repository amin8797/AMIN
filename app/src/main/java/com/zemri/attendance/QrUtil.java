package com.zemri.attendance;

import android.graphics.Bitmap;
import android.graphics.Color;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

public class QrUtil {
    public static Bitmap make(String text,int size) throws WriterException {
        BitMatrix m=new QRCodeWriter().encode(text, BarcodeFormat.QR_CODE,size,size);
        Bitmap b=Bitmap.createBitmap(size,size,Bitmap.Config.ARGB_8888);
        for(int y=0;y<size;y++) for(int x=0;x<size;x++) b.setPixel(x,y,m.get(x,y)? Color.BLACK:Color.WHITE);
        return b;
    }
}
