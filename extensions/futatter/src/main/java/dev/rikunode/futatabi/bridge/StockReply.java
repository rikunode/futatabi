package dev.rikunode.futatabi.bridge;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.json.JSONObject;

/** Only the stock in-process multipart form can enter the existing official writers. */
final class StockReply {
    private final AimogeAdapter aimoge;
    private final AnimanchAdapter animanch;
    private final ExtensionApi api;
    StockReply(AimogeAdapter a,AnimanchAdapter b,ExtensionApi e){aimoge=a;animanch=b;api=e;}
    static boolean isPath(String path){
        return "/aimoge/futaba.php?guid=on".equals(path)||"/animanch/futaba.php?guid=on".equals(path);
    }
    static boolean stockHeaders(Map<String,String> h){
        if(!"127.0.0.1:18765".equals(h.get("host")) || h.containsKey("origin"))return false;
        for(String key:h.keySet())if(key.startsWith("sec-fetch-")||key.equals("access-control-request-method"))return false;
        return true;
    }
    BridgeServer.BridgeResponse submit(String path,Map<String,String> headers,byte[] body){
        if(!isPath(path)||!stockHeaders(headers))return BridgeServer.BridgeResponse.text(403,"Not authorized");
        try{
            StockReplyRequest request=StockReplyRequest.parse(headers.get("content-type"),body);
            String board=path.startsWith("/aimoge/")?"aimoge":"animanch";
            String id=request.get("resto");
            if(!id.matches("[0-9]{13}"))return error("\u30b9\u30ec\u7acb\u3066\u306f\u62e1\u5f35\u30db\u30fc\u30e0\u3092\u4f7f\u3063\u3066\u304f\u3060\u3055\u3044\u3002");
            boolean a="aimoge".equals(board);
            String source=a?aimoge.stockThreadUuid(id):animanch.stockThreadId(id);
            if(source==null)return error("\u8fd4\u4fe1\u5148\u3092\u7279\u5b9a\u3067\u304d\u307e\u305b\u3093\u3002");
            JSONObject payload;
            try{payload=request.payload(board,a?source:id);}
            catch(IllegalArgumentException ex){return error(ex.getMessage());}
            BridgeServer.BridgeResponse upstream=a?api.submit(payload):animanch.post(payload);
            if(upstream.status!=201){
                if(upstream.status==409 && !a){
                    try{if(new JSONObject(new String(upstream.bytes,StandardCharsets.UTF_8)).optBoolean("postAccepted"))
                        return error("\u672c\u6587\u53d7\u7406\u6e08\u307f\u3002\u753b\u50cf\u306e\u9001\u4fe1\u5931\u6557\u3002\u518d\u6295\u7a3f\u305b\u305a\u66f4\u65b0\u3057\u3066\u304f\u3060\u3055\u3044\u3002");}
                    catch(Exception ignored){}
                }
                return error("\u6295\u7a3f\u3092\u78ba\u8a8d\u3067\u304d\u307e\u305b\u3093 (HTTP "+upstream.status+")\u3002\u518d\u9001\u524d\u306b\u30b9\u30ec\u3092\u78ba\u8a8d\u3057\u3066\u304f\u3060\u3055\u3044\u3002");
            }
            String number;
            try{
                JSONObject result=new JSONObject(new String(upstream.bytes,StandardCharsets.UTF_8));
                number=a?aimoge.confirmedNumber(source,result):animanch.confirmedNumber(id,result);
            }catch(Exception ex){number=null;}
            if(number==null)return error("\u53d7\u7406\u6e08\u307f\u3067\u3059\u304c\u6295\u7a3f\u756a\u53f7\u306e\u78ba\u8a8d\u306b\u5931\u6557\u3002\u518d\u6295\u7a3f\u305b\u305a\u66f4\u65b0\u3057\u3066\u304f\u3060\u3055\u3044\u3002");
            return new BridgeServer.BridgeResponse(200,"application/json; charset=Shift_JIS",
                    ("{\"status\":\"ok\",\"thisno\":"+number+"}").getBytes(StandardCharsets.US_ASCII));
        }catch(Exception ex){
            return error("\u6295\u7a3f\u7d50\u679c\u3092\u78ba\u8a8d\u3067\u304d\u307e\u305b\u3093\u3002\u518d\u9001\u524d\u306b\u30b9\u30ec\u3092\u78ba\u8a8d\u3057\u3066\u304f\u3060\u3055\u3044\u3002");
        }
    }
    private static BridgeServer.BridgeResponse error(String message){
        // Stock client decodes Windows31J and treats non-success text as a failure.
        return new BridgeServer.BridgeResponse(200,"text/plain; charset=Shift_JIS",
                message.getBytes(Charset.forName("Shift_JIS")));
    }
}
