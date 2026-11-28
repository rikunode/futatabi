package dev.rikunode.futatabi.imageng;

import android.content.Context;
import android.util.AtomicFile;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Feature-only rules, separate from the stock hide/history stores. */
public final class ImageNgStore {
    private static final String NAME="futatabi-image-ng.json";
    private static final int MAX_RULES=2048, MAX_BYTES=2*1024*1024;
    private static final Set<String> SCOPES=Collections.unmodifiableSet(new HashSet<>(
        Arrays.asList("all","img","may","jun","aimoge","animanch","mebuki")));
    public enum Mode { EXACT, SIMILAR }
    public static final class Rule {
        public final String id,scope,memo;
        public final ImageNgFeatures features;
        public final Mode matchMode;
        public final long createdAt;
        public final boolean enabled;
        Rule(String id,ImageNgFeatures features,Mode mode,String scope,String memo,long createdAt,boolean enabled){
            if(id==null||!id.matches("[0-9a-f-]{36}")||features==null||mode==null||
                !SCOPES.contains(scope)||memo==null||memo.length()>256||createdAt<=0)
                throw new IllegalArgumentException("Invalid image NG rule");
            this.id=id;this.features=features;this.matchMode=mode;this.scope=scope;
            this.memo=memo;this.createdAt=createdAt;this.enabled=enabled;
        }
        boolean matches(String board,ImageNgFeatures candidate){
            return enabled&&("all".equals(scope)||scope.equals(board))&&
                features.matches(candidate,matchMode==Mode.SIMILAR);
        }
        JSONObject json()throws Exception{
            return new JSONObject().put("id",id).put("algorithmVersion",ImageNgFeatures.VERSION)
                .put("sha256",features.sha256).put("phash",features.phash==null?JSONObject.NULL:features.phash)
                .put("dhash",features.dhash==null?JSONObject.NULL:features.dhash)
                .put("aspectRatio",features.aspectRatio).put("imageKind",features.kind.name())
                .put("matchMode",matchMode.name()).put("scope",scope).put("memo",memo)
                .put("createdAt",createdAt).put("enabled",enabled);
        }
        static Rule decode(JSONObject o)throws Exception{
            if(o.getInt("algorithmVersion")!=ImageNgFeatures.VERSION)
                throw new IOException("Unsupported image NG algorithm");
            ImageNgFeatures f=new ImageNgFeatures(o.getString("sha256"),
                o.isNull("phash")?null:o.getString("phash"),o.isNull("dhash")?null:o.getString("dhash"),
                o.getDouble("aspectRatio"),ImageNgFeatures.Kind.valueOf(o.getString("imageKind")));
            return new Rule(o.getString("id"),f,Mode.valueOf(o.getString("matchMode")),
                o.getString("scope"),o.getString("memo"),o.getLong("createdAt"),o.getBoolean("enabled"));
        }
    }

    private final AtomicFile file;
    private List<Rule> rules;
    private static ImageNgStore instance;
    public static synchronized ImageNgStore open(Context context)throws Exception{
        if(instance==null)instance=new ImageNgStore(context.getNoBackupFilesDir());
        return instance;
    }
    ImageNgStore(File directory)throws Exception{
        if(directory==null||!directory.isDirectory())throw new IOException("Missing private NG directory");
        file=new AtomicFile(new File(directory,NAME));
        rules=load();
    }
    private List<Rule> load()throws Exception{
        try(FileInputStream in=file.openRead()){
            ByteArrayOutputStream b=new ByteArrayOutputStream();
            byte[] buf=new byte[8192];
            int n;
            while((n=in.read(buf))!=-1){
                if(b.size()+n>MAX_BYTES)throw new IOException("Image NG store too large");
                b.write(buf,0,n);
            }
            JSONObject root=new JSONObject(new String(b.toByteArray(),StandardCharsets.UTF_8));
            if(root.getInt("format")!=1)throw new IOException("Unsupported image NG store");
            JSONArray a=root.getJSONArray("rules");
            if(a.length()>MAX_RULES)throw new IOException("Too many image NG rules");
            List<Rule> result=new ArrayList<>();
            Set<String> ids=new HashSet<>();
            for(int i=0;i<a.length();i++){
                Rule r=Rule.decode(a.getJSONObject(i));
                if(!ids.add(r.id))throw new IOException("Duplicate image NG rule");
                result.add(r);
            }
            return result;
        }catch(FileNotFoundException e){
            if(file.getBaseFile().exists()||new File(file.getBaseFile().getPath()+".bak").exists()||
                new File(file.getBaseFile().getPath()+".new").exists())throw e;
            return new ArrayList<>();
        }
    }
    private void save(List<Rule> next)throws Exception{
        JSONArray a=new JSONArray();
        for(Rule r:next)a.put(r.json());
        byte[] bytes=new JSONObject().put("format",1).put("rules",a).toString().getBytes(StandardCharsets.UTF_8);
        if(bytes.length>MAX_BYTES)throw new IOException("Image NG store too large");
        FileOutputStream out=file.startWrite();
        try{
            out.write(bytes);
            out.getFD().sync();
            file.finishWrite(out);
        }catch(Exception e){file.failWrite(out);throw e;}
        // AtomicFile.finishWrite may log rather than throw on rename failure.
        List<Rule> read=load();
        if(read.size()!=next.size())throw new IOException("Image NG write verification failed");
        for(int i=0;i<read.size();i++)
            if(!read.get(i).json().toString().equals(next.get(i).json().toString()))
                throw new IOException("Image NG write verification failed");
        rules=read;
    }
    public synchronized List<Rule> list(){return new ArrayList<>(rules);}
    public synchronized Rule add(ImageNgFeatures f,Mode mode,String scope,String memo)throws Exception{
        if(rules.size()>=MAX_RULES)throw new IOException("Image NG rule limit");
        Rule r=new Rule(UUID.randomUUID().toString(),f,mode,scope,memo,System.currentTimeMillis(),true);
        List<Rule> next=new ArrayList<>(rules);next.add(r);save(next);return r;
    }
    public synchronized boolean setEnabled(String id,boolean enabled)throws Exception{
        List<Rule> next=new ArrayList<>(rules);
        for(int i=0;i<next.size();i++){
            Rule r=next.get(i);
            if(r.id.equals(id)){
                if(r.enabled==enabled)return true;
                next.set(i,new Rule(r.id,r.features,r.matchMode,r.scope,r.memo,r.createdAt,enabled));
                save(next);return true;
            }
        }
        return false;
    }
    public synchronized boolean remove(String id)throws Exception{
        List<Rule> next=new ArrayList<>(rules);
        for(int i=0;i<next.size();i++)if(next.get(i).id.equals(id)){
            next.remove(i);save(next);return true;
        }
        return false;
    }
    public synchronized List<String> matchingRuleIds(String board,ImageNgFeatures candidate){
        List<String> result=new ArrayList<>();
        if(!SCOPES.contains(board)||"all".equals(board))return result;
        for(Rule r:rules)if(r.matches(board,candidate))result.add(r.id);
        return result;
    }
}
