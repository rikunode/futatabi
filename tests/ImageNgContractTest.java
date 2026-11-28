package dev.rikunode.futatabi.imageng;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import javax.imageio.*;
import javax.imageio.stream.ImageOutputStream;
import org.json.*;

public final class ImageNgContractTest {
    private static int checks;
    private static void check(boolean condition,String message){
        checks++; if(!condition)throw new AssertionError(message);
    }
    private static byte[] encode(BufferedImage image,String format,float quality)throws Exception{
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        if(format.equals("jpeg")){
            ImageWriter writer=ImageIO.getImageWritersByFormatName(format).next();
            try(ImageOutputStream io=ImageIO.createImageOutputStream(out)){
                writer.setOutput(io);ImageWriteParam p=writer.getDefaultWriteParam();
                p.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);p.setCompressionQuality(quality);
                writer.write(null,new IIOImage(image,null,null),p);
            }finally{writer.dispose();}
        }else check(ImageIO.write(image,format,out),"encode");
        return out.toByteArray();
    }
    private static BufferedImage pattern(int seed){
        BufferedImage b=new BufferedImage(192,128,BufferedImage.TYPE_INT_RGB);
        Graphics2D g=b.createGraphics();
        Random random=new Random(seed);
        g.setColor(new Color(230,230,230));g.fillRect(0,0,192,128);
        for(int i=0;i<18;i++){
            g.setColor(new Color(random.nextInt(256),random.nextInt(256),random.nextInt(256)));
            int x=random.nextInt(160),y=random.nextInt(96);
            g.fillRect(x,y,20+random.nextInt(40),15+random.nextInt(30));
        }
        g.dispose();return b;
    }
    private static ImageNgFeatures feature(byte[] bytes,ImageNgFeatures.Kind kind)throws Exception{
        BufferedImage b=ImageIO.read(new ByteArrayInputStream(bytes));
        return ImageNgFeatures.extract(bytes,b.getRGB(0,0,b.getWidth(),b.getHeight(),null,0,b.getWidth()),
            b.getWidth(),b.getHeight(),b.getWidth(),b.getHeight(),1,kind);
    }
    private static ImageNgFeatures fixture(String sha,String p,String d,double r){
        return new ImageNgFeatures(sha,p,d,r,ImageNgFeatures.Kind.THUMBNAIL);
    }
    private static String bits(int n){return String.format(Locale.ROOT,"%016x",(1L<<n)-1);}
    public static void main(String[] args)throws Exception{
        ImageNgFeatures.Kind thumb=ImageNgFeatures.Kind.THUMBNAIL,original=ImageNgFeatures.Kind.ORIGINAL;
        int recompressed=0,resized=0,unrelated=0,comparisons=0;
        ArrayList<ImageNgFeatures> features=new ArrayList<>();
        for(int seed=0;seed<32;seed++){
            BufferedImage source=pattern(seed);
            byte[] png=encode(source,"png",1),jpeg=encode(source,"jpeg",.55f);
            ImageNgFeatures a=feature(png,thumb),b=feature(jpeg,thumb);
            features.add(a);
            check(a.matches(feature(png,thumb),false),"same encoded bytes");
            check(!a.matches(feature(png,original),false),"exact type separation");
            check(!a.matches(b,false),"recompression not exact");
            if(a.matches(b,true))recompressed++;
            BufferedImage small=new BufferedImage(96,64,BufferedImage.TYPE_INT_RGB);
            Graphics2D g=small.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(source,0,0,96,64,null);g.dispose();
            if(a.matches(feature(encode(small,"png",1),thumb),true))resized++;
        }
        for(int i=0;i<features.size();i++)for(int j=i+1;j<features.size();j++){
            comparisons++;if(features.get(i).matches(features.get(j),true))unrelated++;
        }
        System.out.println("Synthetic 32 patterns: JPEG55 matches="+recompressed+"/32, half-size="+resized+"/32, unrelated pairs="+unrelated+"/"+comparisons);
        check(recompressed>=28&&resized>=28,"synthetic transform coverage");
        check(unrelated==0,"synthetic unrelated pairs");
        String shaA=String.join("",Collections.nCopies(64,"a")),shaB=String.join("",Collections.nCopies(64,"b"));
        ImageNgFeatures zero=fixture(shaA,bits(0),bits(0),1);
        check(zero.matches(fixture(shaB,bits(6),bits(8),Math.exp(.079)),true),"threshold within");
        check(!zero.matches(fixture(shaB,bits(7),bits(8),1),true),"pHash beyond");
        check(!zero.matches(fixture(shaB,bits(6),bits(9),1),true),"dHash beyond");
        check(!zero.matches(fixture(shaB,bits(6),bits(8),Math.exp(.081)),true),"aspect beyond");
        int[] flat=new int[64];Arrays.fill(flat,0xff777777);
        ImageNgFeatures f=ImageNgFeatures.extract(new byte[]{1},flat,8,8,8,8,1,thumb);
        ImageNgFeatures f2=ImageNgFeatures.extract(new byte[]{2},flat,8,8,8,8,1,thumb);
        check(f.phash==null&&f.dhash==null&&!f.matches(f2,true),"low variance excluded");
        check(f.matches(f,false),"flat exact");
        int[] transparent=new int[64];Arrays.fill(transparent,0x00ff0000);
        check(ImageNgFeatures.extract(new byte[]{3},transparent,8,8,8,8,1,thumb).phash==null,"transparent composite");
        BufferedImage src=pattern(22);int w=src.getWidth(),h=src.getHeight();
        int[] pixels=src.getRGB(0,0,w,h,null,0,w);
        for(int o=1;o<=8;o++){
            int ow=o>=5?h:w,oh=o>=5?w:h;int[] oriented=new int[ow*oh];
            for(int y=0;y<oh;y++)for(int x=0;x<ow;x++){
                int sx=x,sy=y;
                switch(o){case 2:sx=w-1-x;break;case 3:sx=w-1-x;sy=h-1-y;break;
                    case 4:sy=h-1-y;break;case 5:sx=y;sy=x;break;case 6:sx=y;sy=h-1-x;break;
                    case 7:sx=w-1-y;sy=h-1-x;break;case 8:sx=w-1-y;sy=x;break;}
                oriented[y*ow+x]=pixels[sy*w+sx];
            }
            ImageNgFeatures x=ImageNgFeatures.extract(new byte[]{4},pixels,w,h,w,h,o,thumb),
                y=ImageNgFeatures.extract(new byte[]{5},oriented,ow,oh,ow,oh,1,thumb);
            check(x.phash.equals(y.phash)&&x.dhash.equals(y.dhash)&&x.aspectRatio==y.aspectRatio,"orientation "+o);
        }

        Path dir=Files.createTempDirectory("image-ng-fixture");
        try{
            ImageNgStore store=new ImageNgStore(dir.toFile());
            ImageNgFeatures a=features.get(0);
            ImageNgStore.Rule rule=store.add(a,ImageNgStore.Mode.EXACT,"img","fixture only");
            check(store.matchingRuleIds("img",a).equals(Collections.singletonList(rule.id)),"board exact");
            check(store.matchingRuleIds("may",a).isEmpty(),"other board");
            ImageNgStore.Rule global=store.add(a,ImageNgStore.Mode.SIMILAR,"all","global");
            store=new ImageNgStore(dir.toFile());
            for(String board:Arrays.asList("img","may","jun","aimoge","animanch","mebuki"))
                check(store.matchingRuleIds(board,a).contains(global.id),"scope "+board);
            check(store.setEnabled(global.id,false),"disable");
            check(store.matchingRuleIds("may",a).isEmpty(),"disable removes match");
            store=new ImageNgStore(dir.toFile());
            check(!store.list().get(1).enabled,"disabled persists");
            check(store.setEnabled(global.id,true),"enable");
            check(store.remove(global.id),"delete");
            check(store.matchingRuleIds("may",a).isEmpty(),"delete removes match");
            check(store.remove(rule.id)&&store.list().isEmpty(),"delete local");
            store.add(f,ImageNgStore.Mode.SIMILAR,"all","");
            Path saved=dir.resolve("futatabi-image-ng.json");
            JSONObject root=new JSONObject(new String(Files.readAllBytes(saved),java.nio.charset.StandardCharsets.UTF_8));
            JSONObject row=root.getJSONArray("rules").getJSONObject(0);
            Set<String> expected=new HashSet<>(Arrays.asList("id","algorithmVersion","sha256","phash","dhash","aspectRatio","imageKind","matchMode","scope","memo","createdAt","enabled"));
            Set<String> keys=new HashSet<>(); for(Iterator<?> it=row.keys();it.hasNext();)keys.add((String)it.next()); check(keys.equals(expected),"exact feature-only schema");
            check(row.isNull("phash")&&row.isNull("dhash"),"low variance survives serialization");
            try(java.util.stream.Stream<Path> files=Files.list(dir)){check(files.count()==1,"no image or temporary file remains");}
            // Corrupt data must not be silently replaced.
            byte[] corrupt="{bad".getBytes(java.nio.charset.StandardCharsets.UTF_8);
            Files.write(saved,corrupt);
            boolean rejected=false;
            try{new ImageNgStore(dir.toFile());}catch(Exception e){rejected=true;}
            check(rejected&&Arrays.equals(Files.readAllBytes(saved),corrupt),"corruption preserved");
        }finally{
            try(java.util.stream.Stream<Path> files=Files.list(dir)){
                for(Path path:(Iterable<Path>)files::iterator)Files.delete(path);
            }
            Files.delete(dir);
        }
        System.out.println("PASS "+checks+" assertions (feature extraction, thresholds, 8 orientations, scopes, persistence, disable/delete, no image files, malformed store)");
    }
}
