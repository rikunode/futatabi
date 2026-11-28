package dev.rikunode.futatabi.imageng;

import java.nio.file.*;
import java.util.*;

/** Uses real feature extraction and the real feature-only rule store. */
public final class ImageNgCatalogIndexContractTest {
    private static final String[] BASES={
        "https://img.2chan.net/b/","https://may.2chan.net/b/","https://jun.2chan.net/jun/",
        "http://127.0.0.1:18765/aimoge/","http://127.0.0.1:18765/animanch/",
        "http://127.0.0.1:18765/mebuki/"};
    private static final String NUMBER="1234567890123";
    private static int checks;
    private static void check(boolean b,String message){
        checks++;if(!b)throw new AssertionError(message);
    }
    private static ImageNgFeatures features(int salt,ImageNgFeatures.Kind kind){
        int[] pixels=new int[64*64];
        for(int y=0;y<64;y++)for(int x=0;x<64;x++)
            pixels[y*64+x]=0xff000000|(((x*7+y*3+salt)&255)*0x010101);
        return ImageNgFeatures.extract(new byte[]{(byte)salt},pixels,64,64,64,64,1,kind);
    }
    public static void main(String[] args)throws Exception{
        Path tmp=Files.createTempDirectory("image-ng-index");
        try{
            ImageNgStore store=new ImageNgStore(tmp.toFile());
            ImageNgCatalogIndex index=new ImageNgCatalogIndex();
            ImageNgFeatures a=features(17,ImageNgFeatures.Kind.THUMBNAIL);
            ImageNgFeatures b=features(42,ImageNgFeatures.Kind.THUMBNAIL);
            ImageNgFeatures original=features(17,ImageNgFeatures.Kind.ORIGINAL);
            List<ImageNgCatalogIndex.Key> keys=new ArrayList<>();
            List<Long> epochs=new ArrayList<>();
            for(int i=0;i<BASES.length;i++){
                String board=ImageNgCatalogIndex.BOARDS.get(i);
                long epoch=index.begin(board);epochs.add(epoch);
                for(ImageNgCatalogIndex.Variant v:ImageNgCatalogIndex.Variant.values()){
                    ImageNgCatalogIndex.Key key=ImageNgCatalogIndex.key(BASES[i],NUMBER,v);
                    keys.add(key);
                    check(key!=null&&key.board.equals(board),"board identity");
                    check(!index.observe(epoch,key,original),"reject original");
                    check(index.observe(epoch,key,a),"observe "+board);
                    check(index.candidates(board,v).size()==1,"preview scope");
                    check(!index.hidden(key,store.list()),"no rules no hide");
                    if(i<3)check(key.equals(ImageNgCatalogIndex.key(BASES[i].replace("https:","http:"),NUMBER,v)),"stock alias");
                }
            }
            check(ImageNgCatalogIndex.key("https://img.2chan.net.evil/b/",NUMBER,ImageNgCatalogIndex.Variant.CAT)==null,"foreign host");
            check(ImageNgCatalogIndex.key(BASES[0],"123456789012X",ImageNgCatalogIndex.Variant.CAT)==null,"bad ID");
            ImageNgStore.Rule local=store.add(a,ImageNgStore.Mode.EXACT,"img","local");
            for(ImageNgCatalogIndex.Key key:keys)
                check(index.hidden(key,store.list())==key.board.equals("img"),"local scope "+key.board);
            check(index.decisionRows(store.list()).length==48,"two variants local");
            ImageNgStore.Rule global=store.add(a,ImageNgStore.Mode.EXACT,"all","all");
            for(ImageNgCatalogIndex.Key key:keys)check(index.hidden(key,store.list()),"global "+key.board);
            check(index.decisionRows(store.list()).length==288,"six boards two variants");
            // Host fixture only: no file is written by the production index.
            if(args.length>0)Files.write(Path.of(args[0]),index.decisionRows(store.list()));
            store.setEnabled(global.id,false);
            check(index.decisionRows(store.list()).length==48,"disabled global");
            store.remove(local.id);
            check(index.decisionRows(store.list()).length==0,"removed local");
            store.setEnabled(global.id,true);
            check(index.observe(epochs.get(0),keys.get(0),b),"replace changed bytes");
            check(!index.hidden(keys.get(0),store.list()),"changed image visible");
            long newEpoch=index.begin("img");
            check(!index.observe(epochs.get(0),keys.get(0),a),"late old snapshot rejected");
            check(!index.hidden(keys.get(1),store.list()),"new snapshot unknown visible");
            check(index.observe(newEpoch,keys.get(0),a),"fresh snapshot");
            check(index.hidden(keys.get(0),store.list()),"fresh observation matches");
            for(int i=0;i<2050;i++){
                ImageNgCatalogIndex.Key k=ImageNgCatalogIndex.key(BASES[0],
                    String.format(Locale.ROOT,"%013d",1000000000000L+i),ImageNgCatalogIndex.Variant.CAT);
                check(index.observe(newEpoch,k,a),"bounded cache observation");
            }
            check(index.decisionRows(store.list()).length==2048*24,"bounded decision rows");
            System.out.println("PASS "+checks+" assertions; six boards, two variants, dynamic rule changes, stale epochs, 2048-entry bound");
        }finally{
            try(java.util.stream.Stream<Path> files=Files.list(tmp)){
                for(Path path:(Iterable<Path>)files::iterator)Files.delete(path);
            }
            Files.delete(tmp);
        }
    }
}
