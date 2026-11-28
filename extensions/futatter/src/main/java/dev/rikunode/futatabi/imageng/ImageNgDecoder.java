package dev.rikunode.futatabi.imageng;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ColorSpace;
import android.os.Build;

/** Bounded in-memory decoding of the catalog image formats; no disk/network access. */
public final class ImageNgDecoder {
    private static final int MAX_ENCODED=12_000_000, MAX_EDGE=16000;
    private static final long MAX_PIXELS=40_000_000L;
    private ImageNgDecoder(){}

    public static ImageNgFeatures extract(byte[] encoded,ImageNgFeatures.Kind kind){
        if(encoded==null||encoded.length==0||encoded.length>MAX_ENCODED)
            throw new IllegalArgumentException("Invalid image size");
        int orientation=orientation(encoded);
        BitmapFactory.Options options=new BitmapFactory.Options();
        options.inJustDecodeBounds=true;
        BitmapFactory.decodeByteArray(encoded,0,encoded.length,options);
        int w=options.outWidth,h=options.outHeight;
        if(w<=0||h<=0||w>MAX_EDGE||h>MAX_EDGE||(long)w*h>MAX_PIXELS)
            throw new IllegalArgumentException("Invalid image dimensions");
        int sample=1;
        while((long)((w+sample-1)/sample)*((h+sample-1)/sample)>1_048_576L)sample*=2;
        options.inJustDecodeBounds=false;options.inSampleSize=sample;
        options.inScaled=false;options.inPreferredConfig=Bitmap.Config.ARGB_8888;
        if(Build.VERSION.SDK_INT>=26)options.inPreferredColorSpace=ColorSpace.get(ColorSpace.Named.SRGB);
        Bitmap bitmap=BitmapFactory.decodeByteArray(encoded,0,encoded.length,options);
        if(bitmap==null)throw new IllegalArgumentException("Image decode failed");
        try{
            int bw=bitmap.getWidth(),bh=bitmap.getHeight();
            if(bw<=0||bh<=0||(long)bw*bh>1_048_576L)
                throw new IllegalArgumentException("Decoded raster too large");
            int[] argb=new int[bw*bh];
            bitmap.getPixels(argb,0,bw,0,0,bw,bh);
            return ImageNgFeatures.extract(encoded,argb,bw,bh,w,h,orientation,kind);
        }finally{bitmap.recycle();}
    }

    // BitmapFactory does not apply EXIF orientation. Parse only the bounded
    // orientation tag, including JPEG APP1, PNG eXIf and WebP EXIF containers.
    // This avoids a file-backed EXIF reader on the target's API 23 minimum.
    static int orientation(byte[] b){
        if(b==null||b.length<2)throw new IllegalArgumentException("Invalid image");
        if(u(b,0)==255&&u(b,1)==216)return jpeg(b);
        if(tag(b,0,137,80,78,71,13,10,26,10))return png(b);
        if(tag(b,0,82,73,70,70)&&tag(b,8,87,69,66,80))return webp(b);
        if(tag(b,0,71,73,70,56,55,97)||tag(b,0,71,73,70,56,57,97))return 1;
        throw new IllegalArgumentException("Unsupported image format");
    }
    private static int jpeg(byte[] b){
        int p=2;
        while(p<b.length){
            if(u(b,p++)!=255)throw new IllegalArgumentException("Invalid JPEG marker");
            while(p<b.length&&u(b,p)==255)p++;
            if(p==b.length)throw new IllegalArgumentException("Truncated JPEG marker");
            int marker=u(b,p++);
            if(marker==218||marker==217)return 1;
            if(marker==1||(marker>=208&&marker<=215))continue;
            if(p+2>b.length)throw new IllegalArgumentException("Truncated JPEG segment");
            int len=(u(b,p)<<8)|u(b,p+1);
            if(len<2||len>b.length-p)throw new IllegalArgumentException("Invalid JPEG segment");
            if(marker==225&&len>=8&&tag(b,p+2,69,120,105,102,0,0))return tiff(b,p+8,len-8);
            p+=len;
        }
        return 1;
    }
    private static int png(byte[] b){
        int p=8;
        while(p+12<=b.length){
            long len=u32(b,p,false);
            if(len>b.length-p-12L)throw new IllegalArgumentException("Invalid PNG chunk");
            if(tag(b,p+4,101,88,73,102))return tiff(b,p+8,(int)len);
            if(tag(b,p+4,73,69,78,68))return 1;
            p+=(int)len+12;
        }
        return 1;
    }
    private static int webp(byte[] b){
        int p=12;
        while(p+8<=b.length){
            long len=u32(b,p+4,true);
            if(len>b.length-p-8L)throw new IllegalArgumentException("Invalid WebP chunk");
            if(tag(b,p,69,88,73,70)){
                int start=p+8,size=(int)len;
                if(size>=6&&tag(b,start,69,120,105,102,0,0)){start+=6;size-=6;}
                return tiff(b,start,size);
            }
            long next=p+8L+len+(len&1);
            if(next>b.length)throw new IllegalArgumentException("Truncated WebP padding");
            p=(int)next;
        }
        return 1;
    }
    private static int tiff(byte[] b,int base,int size){
        if(size<8||base<0||size>b.length-base)throw new IllegalArgumentException("Invalid EXIF");
        boolean le=tag(b,base,73,73);
        if(!le&&!tag(b,base,77,77))throw new IllegalArgumentException("Invalid EXIF byte order");
        if(u16(b,base+2,le)!=42)throw new IllegalArgumentException("Invalid EXIF TIFF");
        long offset=u32(b,base+4,le);
        if(offset<8||offset>size-2L)throw new IllegalArgumentException("Invalid EXIF IFD");
        int pos=base+(int)offset;
        int count=u16(b,pos,le);
        if((long)count*12>size-offset-2)throw new IllegalArgumentException("Truncated EXIF IFD");
        for(int i=0;i<count;i++){
            int entry=pos+2+i*12;
            if(u16(b,entry,le)==0x112){
                if(u16(b,entry+2,le)!=3||u32(b,entry+4,le)!=1)
                    throw new IllegalArgumentException("Invalid orientation tag");
                int value=u16(b,entry+8,le);
                if(value<1||value>8)throw new IllegalArgumentException("Invalid EXIF orientation");
                return value;
            }
        }
        return 1;
    }
    private static int u(byte[] b,int i){return b[i]&255;}
    private static int u16(byte[] b,int i,boolean le){return le?u(b,i)|(u(b,i+1)<<8):(u(b,i)<<8)|u(b,i+1);}
    private static long u32(byte[] b,int i,boolean le){
        return le?(long)u16(b,i,true)|((long)u16(b,i+2,true)<<16):
            ((long)u16(b,i,false)<<16)|u16(b,i+2,false);
    }
    private static boolean tag(byte[] b,int pos,int... bytes){
        if(pos<0||bytes.length>b.length-pos)return false;
        for(int i=0;i<bytes.length;i++)if(u(b,pos+i)!=bytes[i])return false;
        return true;
    }
}
