package dev.rikunode.futatabi.imageng;

import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Locale;

/** Independent feature extraction; no file or network I/O. */
public final class ImageNgFeatures {
    public static final int VERSION=1, PHASH_DISTANCE=6, DHASH_DISTANCE=8;
    public static final double ASPECT_LOG_DISTANCE=0.08, MIN_LUMA_VARIANCE=64.0;
    public enum Kind { THUMBNAIL, ORIGINAL }
    public final String sha256, phash, dhash;
    public final double aspectRatio;
    public final Kind kind;

    ImageNgFeatures(String sha256,String phash,String dhash,double ratio,Kind kind) {
        if(sha256==null||!sha256.matches("[0-9a-f]{64}")||
            ((phash==null)!=(dhash==null))||
            (phash!=null&&(!phash.matches("[0-9a-f]{16}")||!dhash.matches("[0-9a-f]{16}")))||
            !(ratio>0)||Double.isInfinite(ratio)||kind==null)
            throw new IllegalArgumentException("Invalid image features");
        this.sha256=sha256;this.phash=phash;this.dhash=dhash;this.aspectRatio=ratio;this.kind=kind;
    }

    /** Decoder must supply unpremultiplied sRGB ARGB, original size and EXIF orientation. */
    public static ImageNgFeatures extract(byte[] encoded,int[] argb,int w,int h,
            int originalWidth,int originalHeight,int orientation,Kind kind) {
        if(encoded==null||encoded.length==0||encoded.length>12_000_000||
            argb==null||w<=0||h<=0||(long)w*h!=argb.length||argb.length>1_048_576||
            originalWidth<=0||originalHeight<=0||orientation<1||orientation>8)
            throw new IllegalArgumentException("Invalid image input");
        double ratio=orientation>=5?(double)originalHeight/originalWidth:(double)originalWidth/originalHeight;
        String sha=sha256(encoded);
        double[] gray=resize(argb,w,h,orientation,32,32);
        double mean=0,squared=0;
        for(double v:gray){mean+=v;squared+=v*v;}
        mean/=gray.length;
        if(squared/gray.length-mean*mean<MIN_LUMA_VARIANCE)
            return new ImageNgFeatures(sha,null,null,ratio,kind);
        long p=perceptualHash(gray),d=0;
        double[] small=resize(argb,w,h,orientation,9,8);
        // Treat luminance differences of at most one level as ties.
        for(int y=0;y<8;y++)for(int x=0;x<8;x++)
            d=(d<<1)|(small[y*9+x]-small[y*9+x+1]>1.0?1:0);
        return new ImageNgFeatures(sha,hex(p),hex(d),ratio,kind);
    }

    private static String sha256(byte[] bytes){
        try{
            byte[] digest=MessageDigest.getInstance("SHA-256").digest(bytes);
            StringBuilder s=new StringBuilder(64);
            for(byte b:digest)s.append(Character.forDigit((b>>>4)&15,16)).append(Character.forDigit(b&15,16));
            return s.toString();
        }catch(java.security.NoSuchAlgorithmException e){throw new AssertionError(e);}
    }
    private static String hex(long v){return String.format(Locale.ROOT,"%016x",v);}
    static int distance(String a,String b){
        int n=0;
        for(int i=0;i<16;i++)n+=Integer.bitCount(Character.digit(a.charAt(i),16)^Character.digit(b.charAt(i),16));
        return n;
    }
    public boolean matches(ImageNgFeatures candidate,boolean similar){
        if(candidate==null)return false;
        if(kind==candidate.kind&&sha256.equals(candidate.sha256))return true;
        return similar&&phash!=null&&candidate.phash!=null&&
            Math.abs(Math.log(aspectRatio)-Math.log(candidate.aspectRatio))<=ASPECT_LOG_DISTANCE&&
            distance(phash,candidate.phash)<=PHASH_DISTANCE&&distance(dhash,candidate.dhash)<=DHASH_DISTANCE;
    }
    private static double luma(int p){
        int a=p>>>24;
        int r=(((p>>>16)&255)*a+255*(255-a)+127)/255;
        int g=(((p>>>8)&255)*a+255*(255-a)+127)/255;
        int b=((p&255)*a+255*(255-a)+127)/255;
        return (299*r+587*g+114*b)/1000.0;
    }
    private static double pixel(int[] a,int w,int h,int o,int x,int y){
        int sx,sy;
        switch(o){
            case 2:sx=w-1-x;sy=y;break;
            case 3:sx=w-1-x;sy=h-1-y;break;
            case 4:sx=x;sy=h-1-y;break;
            case 5:sx=y;sy=x;break;
            case 6:sx=y;sy=h-1-x;break;
            case 7:sx=w-1-y;sy=h-1-x;break;
            case 8:sx=w-1-y;sy=x;break;
            default:sx=x;sy=y;
        }
        return luma(a[sy*w+sx]);
    }
    // Area-weighted sampling, including for small images.
    private static double[] resize(int[] a,int w,int h,int o,int tw,int th){
        int ow=o>=5?h:w,oh=o>=5?w:h;
        double[] out=new double[tw*th];
        for(int y=0;y<th;y++)for(int x=0;x<tw;x++){
            double left=(double)x*ow/tw,right=(double)(x+1)*ow/tw;
            double top=(double)y*oh/th,bottom=(double)(y+1)*oh/th;
            double sum=0,weight=0;
            for(int yy=(int)top;yy<Math.min(oh,(int)Math.ceil(bottom));yy++){
                double wy=Math.min(bottom,yy+1)-Math.max(top,yy);
                for(int xx=(int)left;xx<Math.min(ow,(int)Math.ceil(right));xx++){
                    double wx=Math.min(right,xx+1)-Math.max(left,xx),k=wx*wy;
                    sum+=pixel(a,w,h,o,xx,yy)*k;weight+=k;
                }
            }
            out[y*tw+x]=sum/weight;
        }
        return out;
    }
    private static final double[][] COS=new double[8][32];
    static{
        for(int k=0;k<8;k++)for(int n=0;n<32;n++)COS[k][n]=Math.cos(Math.PI*(n+.5)*k/32);
    }
    private static long perceptualHash(double[] gray){
        double[] coeff=new double[64];
        double[][] tmp=new double[32][8];
        for(int y=0;y<32;y++)for(int u=0;u<8;u++)
            for(int x=0;x<32;x++)tmp[y][u]+=gray[y*32+x]*COS[u][x];
        for(int v=0;v<8;v++)for(int u=0;u<8;u++){
            double val=0;
            for(int y=0;y<32;y++)val+=tmp[y][u]*COS[v][y];
            coeff[v*8+u]=val*(u==0?Math.sqrt(.5):1)*(v==0?Math.sqrt(.5):1);
        }
        double[] sorted=Arrays.copyOfRange(coeff,1,64);
        Arrays.sort(sorted);
        double median=sorted[31];
        long bits=0;
        for(int i=1;i<64;i++)bits=(bits<<1)|(coeff[i]>median?1:0);
        return bits;
    }
}
