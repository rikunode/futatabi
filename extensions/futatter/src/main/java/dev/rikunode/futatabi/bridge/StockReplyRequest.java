package dev.rikunode.futatabi.bridge;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.json.JSONArray;
import org.json.JSONObject;

/** Strict subset of package:http's stock Futaba multipart reply form. */
final class StockReplyRequest {
    private static final Pattern TYPE=Pattern.compile(
            "(?i)^multipart/form-data\\s*;\\s*boundary=(?:\"([^\"]+)\"|([^\\s;]+))\\s*$");
    private static final Pattern DISPOSITION=Pattern.compile(
            "(?i)^form-data;\\s*name=\"([a-z]+)\"(?:;\\s*filename=\"([^\"\\r\\n]*)\")?$");
    private static final java.util.Set<String> FIELDS=new java.util.HashSet<>(Arrays.asList(
            "resto","email","com","sub","name","mode","js","pwd","chrenc","responsemode"));
    final Map<String,String> fields=new HashMap<>();
    byte[] image;
    String mime;
    private StockReplyRequest(){}
    private static IllegalArgumentException invalid(){return new IllegalArgumentException("Invalid stock multipart form");}
    private static int find(byte[] data,byte[] target,int at){
        outer:for(int i=at;i<=data.length-target.length;i++){
            for(int j=0;j<target.length;j++)if(data[i+j]!=target[j])continue outer;
            return i;
        }return -1;
    }
    private static boolean begins(byte[] d,byte[] v,int at){
        if(at<0||at+v.length>d.length)return false;
        for(int i=0;i<v.length;i++)if(d[at+i]!=v[i])return false;
        return true;
    }
    static StockReplyRequest parse(String contentType,byte[] body)throws Exception{
        if(contentType==null||body.length==0||body.length>22_000_000)throw invalid();
        Matcher t=TYPE.matcher(contentType);
        if(!t.matches())throw invalid();
        String boundary=t.group(1)!=null?t.group(1):t.group(2);
        if(!boundary.matches("[A-Za-z0-9'()+_,./:=?\\-]{1,70}"))throw invalid();
        byte[] start=("--"+boundary+"\r\n").getBytes(StandardCharsets.US_ASCII);
        byte[] next=("\r\n--"+boundary).getBytes(StandardCharsets.US_ASCII);
        byte[] split="\r\n\r\n".getBytes(StandardCharsets.US_ASCII);
        byte[] crlf="\r\n".getBytes(StandardCharsets.US_ASCII);
        byte[] end="--\r\n".getBytes(StandardCharsets.US_ASCII);
        if(!begins(body,start,0))throw invalid();
        StockReplyRequest result=new StockReplyRequest();
        int at=start.length,parts=0;
        while(true){
            if(++parts>16)throw invalid();
            int headerEnd=find(body,split,at);
            if(headerEnd<0||headerEnd-at>4096)throw invalid();
            Map<String,String> h=new HashMap<>();
            for(String line:new String(body,at,headerEnd-at,StandardCharsets.US_ASCII).split("\r\n")){
                int colon=line.indexOf(':');if(colon<1)throw invalid();
                String key=line.substring(0,colon).trim().toLowerCase(java.util.Locale.ROOT);
                if(!key.matches("content-disposition|content-type|content-transfer-encoding")||
                        h.put(key,line.substring(colon+1).trim())!=null)throw invalid();
            }
            String encoding=h.getOrDefault("content-transfer-encoding","binary");
            if(!encoding.matches("(?i)binary|8bit"))throw invalid();
            Matcher disp=DISPOSITION.matcher(h.getOrDefault("content-disposition",""));
            if(!disp.matches())throw invalid();
            int valueStart=headerEnd+split.length;
            int delimiter=find(body,next,valueStart);
            if(delimiter<0)throw invalid();
            int size=delimiter-valueStart;
            String key=disp.group(1);
            if("upfile".equals(key)){
                if(disp.group(2)==null||result.image!=null||size==0||size>20_971_520)throw invalid();
                result.mime=h.getOrDefault("content-type","").toLowerCase(java.util.Locale.ROOT);
                if(!result.mime.matches("image/(jpeg|png|gif|webp)"))throw invalid();
                result.image=Arrays.copyOfRange(body,valueStart,delimiter);
            }else{
                if(!FIELDS.contains(key)||disp.group(2)!=null||size>40_000||result.fields.containsKey(key))throw invalid();
                String v=StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(body,valueStart,size)).toString();
                if(v.indexOf('\0')>=0)throw invalid();
                result.fields.put(key,v);
            }
            at=delimiter+next.length;
            if(begins(body,end,at)){
                if(at+end.length!=body.length)throw invalid();
                break;
            }
            if(!begins(body,crlf,at))throw invalid();
            at+=crlf.length;
        }
        if(!"regist".equals(result.fields.get("mode"))||!"ajax".equals(result.fields.get("responsemode"))
                ||!"on".equals(result.fields.get("js"))||!result.fields.containsKey("com"))throw invalid();
        return result;
    }
    String get(String key){return fields.getOrDefault(key,"");}
    JSONObject payload(String board,String thread)throws Exception{
        String email=get("email").trim();
        if(!get("sub").isEmpty())throw new IllegalArgumentException("\u30ec\u30b9\u306e\u984c\u540d\u306f\u672a\u5bfe\u5fdc");
        if("aimoge".equals(board)){
            if(!get("name").isEmpty())throw new IllegalArgumentException("\u3042\u3044\u3082\u3052\u306e\u540d\u524d\u6b04\u306f\u672a\u5bfe\u5fdc");
            if(!email.isEmpty()&&!"sage".equalsIgnoreCase(email)&&!"age".equalsIgnoreCase(email))
                throw new IllegalArgumentException("\u30e1\u30fc\u30eb\u6b04\u306f sage / age \u306e\u307f");
        }else if(!email.isEmpty())throw new IllegalArgumentException("\u3042\u306b\u307e\u3093\u306e\u30e1\u30fc\u30eb\u6b04\u306f\u672a\u5bfe\u5fdc");
        JSONObject p=new JSONObject().put("threadId",thread).put("body",get("com"));
        if("aimoge".equals(board)){
            p.put("deleteKey",get("pwd"));
            if("sage".equalsIgnoreCase(email))p.put("sage",true);
            if("age".equalsIgnoreCase(email))p.put("age",true);
        }else p.put("name",get("name"));
        if(image!=null){
            if("animanch".equals(board)&&image.length>8_000_000)throw new IllegalArgumentException("\u753b\u50cf\u304c8MB\u3092\u8d85\u3048\u3066\u3044\u307e\u3059");
            String ext="image/jpeg".equals(mime)?"jpg":mime.substring(6);
            p.put("attachments",new JSONArray().put(new JSONObject().put("mime",mime)
                    .put("name","attachment."+ext).put("base64",android.util.Base64.encodeToString(image,android.util.Base64.NO_WRAP))));
        }
        return p;
    }
}
