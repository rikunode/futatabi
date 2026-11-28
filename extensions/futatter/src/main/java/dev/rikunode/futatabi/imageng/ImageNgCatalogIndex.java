package dev.rikunode.futatabi.imageng;

import java.util.*;

/**
 * Process-local catalog feature index. No image bytes, paths or URLs are
 * retained. Decisions are recomputed against the current rules; the index is
 * not a permanent hidden-thread list. A catalog adapter must replace its
 * snapshot when its source data changes.
 */
public final class ImageNgCatalogIndex {
    private static final int LIMIT=2048;
    public static final List<String> BOARDS=Collections.unmodifiableList(
        Arrays.asList("img","may","jun","aimoge","animanch","mebuki"));
    public enum Variant { CAT, THUMB }
    public static final class Key {
        public final String board,number;
        public final Variant variant;
        private Key(String board,String number,Variant variant){
            this.board=board;this.number=number;this.variant=variant;
        }
        @Override public boolean equals(Object o){
            if(!(o instanceof Key))return false;
            Key k=(Key)o;
            return board.equals(k.board)&&number.equals(k.number)&&variant==k.variant;
        }
        @Override public int hashCode(){return Objects.hash(board,number,variant);}
    }

    /** Match only the actual six managed board bases, not arbitrary hosts. */
    public static Key key(String boardBase,String number,Variant variant){
        if(boardBase==null||number==null||!number.matches("[0-9]{10,20}")||variant==null)return null;
        String board=null;
        for(String scheme:Arrays.asList("https://","http://")){
            if(boardBase.equals(scheme+"img.2chan.net/b/"))board="img";
            if(boardBase.equals(scheme+"may.2chan.net/b/"))board="may";
            if(boardBase.equals(scheme+"jun.2chan.net/jun/"))board="jun";
        }
        for(String name:Arrays.asList("aimoge","animanch","mebuki"))
            if(boardBase.equals("http://127.0.0.1:18765/"+name+"/"))board=name;
        return board==null?null:new Key(board,number,variant);
    }

    private final LinkedHashMap<Key,ImageNgFeatures> entries=new LinkedHashMap<>(32,0.75f,true);
    private final Map<String,Long> epochs=new HashMap<>();

    /** Invalidate prior observations before beginning a new source snapshot. */
    public synchronized long begin(String board){
        if(!BOARDS.contains(board))throw new IllegalArgumentException("Unknown board");
        long epoch=epochs.containsKey(board)?epochs.get(board)+1:1;
        epochs.put(board,epoch);
        Iterator<Key> it=entries.keySet().iterator();
        while(it.hasNext())if(it.next().board.equals(board))it.remove();
        return epoch;
    }
    /** Late asynchronous observations cannot overwrite a new snapshot. */
    public synchronized boolean observe(long epoch,Key key,ImageNgFeatures features){
        if(key==null||features==null||features.kind!=ImageNgFeatures.Kind.THUMBNAIL||
            !Objects.equals(epochs.get(key.board),epoch))return false;
        entries.put(key,features);
        while(entries.size()>LIMIT)entries.remove(entries.keySet().iterator().next());
        return true;
    }
    public synchronized ImageNgFeatures get(Key key){return entries.get(key);}
    public synchronized List<ImageNgFeatures> candidates(String board,Variant variant){
        List<ImageNgFeatures> result=new ArrayList<>();
        for(Map.Entry<Key,ImageNgFeatures> e:entries.entrySet())
            if(e.getKey().board.equals(board)&&e.getKey().variant==variant)result.add(e.getValue());
        return result;
    }
    /**
     * Host-tested decision snapshot for the AOT proof. Fixed 24-byte rows:
     * board (1..6), variant (0=CAT, 1=THUMB), digit count, ASCII digits,
     * zero padding. Contains no URL, image, or permanent hide record.
     * Runtime transport and publication are deliberately not wired yet.
     */
    public synchronized byte[] decisionRows(List<ImageNgStore.Rule> rules){
        byte[] rows=new byte[entries.size()*24];
        int offset=0;
        for(Map.Entry<Key,ImageNgFeatures> entry:entries.entrySet()){
            Key k=entry.getKey();
            boolean match=false;
            for(ImageNgStore.Rule rule:rules){
                if(rule.matches(k.board,entry.getValue())){match=true;break;}
            }
            if(!match)continue;
            byte[] digits=k.number.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
            rows[offset]=(byte)(BOARDS.indexOf(k.board)+1);
            rows[offset+1]=(byte)k.variant.ordinal();
            rows[offset+2]=(byte)digits.length;
            System.arraycopy(digits,0,rows,offset+3,digits.length);
            offset+=24;
        }
        return Arrays.copyOf(rows,offset);
    }
    /** A negative/unknown observation leaves the original visibility intact. */
    public boolean hidden(Key key,List<ImageNgStore.Rule> rules){
        ImageNgFeatures f=get(key);
        if(f==null)return false;
        for(ImageNgStore.Rule rule:rules)if(rule.matches(key.board,f))return true;
        return false;
    }
}
