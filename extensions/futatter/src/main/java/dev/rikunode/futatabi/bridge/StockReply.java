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
        return "/aimoge/futaba.php?guid=on".equals(path)||"/animanch/futaba.php?guid=on".equals(path)
                ||"/mebuki/futaba.php?guid=on".equals(path);
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
            String board=path.startsWith("/aimoge/")?"aimoge":path.startsWith("/mebuki/")?"mebuki":"animanch";
            String id=request.get("resto");
            if("mebuki".equals(board)&&!id.matches("[1-9][0-9]{12}"))
                return error("\u3081\u3076\u304d\u306e\u30b9\u30ec\u7acb\u3066\u306f\u672a\u5bfe\u5fdc\u3067\u3059\u3002\u516c\u5f0f\u30b5\u30a4\u30c8\u3092\u4f7f\u3063\u3066\u304f\u3060\u3055\u3044\u3002");
            if(!id.matches("[0-9]{13}"))return error("\u30b9\u30ec\u7acb\u3066\u306f\u62e1\u5f35\u30db\u30fc\u30e0\u3092\u4f7f\u3063\u3066\u304f\u3060\u3055\u3044\u3002");
            if("mebuki".equals(board))return submitMebuki(request,id);
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
    private BridgeServer.BridgeResponse submitMebuki(StockReplyRequest request,String id)throws Exception{
        if(request.image!=null)return error("\u3081\u3076\u304d\u306e\u6dfb\u4ed8\u6295\u7a3f\u306f\u307e\u3060\u5bfe\u5fdc\u3057\u3066\u3044\u307e\u305b\u3093\u3002");
        if(!request.get("sub").isEmpty()||!request.get("name").isEmpty()||!request.get("email").isEmpty())
            return error("\u3081\u3076\u304d\u3067\u306f\u984c\u540d\u30fb\u540d\u524d\u30fbE-mail\u6b04\u3092\u7a7a\u306b\u3057\u3066\u304f\u3060\u3055\u3044\u3002");
        JSONObject payload=new JSONObject().put("threadId",id).put("content",request.get("com"))
                .put("stockQuote",true).put("showUsername",false).put("showThreadOwner",false);
        BridgeServer.BridgeResponse result=api.submitMebuki(payload);
        if(result.status==201){
            JSONObject data=new JSONObject(new String(result.bytes,StandardCharsets.UTF_8));
            String no=data.optString("thisno");
            if(data.optBoolean("confirmed")&&no.matches("[1-9][0-9]{12}"))
                return new BridgeServer.BridgeResponse(200,"application/json; charset=Shift_JIS",
                    ("{\"status\":\"ok\",\"thisno\":"+no+"}").getBytes(StandardCharsets.US_ASCII));
        }
        if(result.status==401)return error("\u3081\u3076\u304d\u306e\u30ed\u30b0\u30a4\u30f3\u304c\u5fc5\u8981\u3067\u3059\u3002\u62e1\u5f35\u30db\u30fc\u30e0\u3067\u518d\u8a8d\u8a3c\u30fb\u4fdd\u5b58\u3057\u3066\u304f\u3060\u3055\u3044\u3002");
        if(result.status==405)return error("\u3081\u3076\u304d\u306e\u6295\u7a3f\u5148\u304c\u53d7\u4ed8\u4e0d\u53ef\u3067\u3059\uff08HTTP 405\uff09\u3002\u6295\u7a3f\u8a3a\u65ad\u3092\u78ba\u8a8d\u3057\u3066\u304f\u3060\u3055\u3044\u3002");
        if(result.status==429)return error("\u3081\u3076\u304d\u306e\u6295\u7a3f\u5236\u9650\u4e2d\u3067\u3059\u3002\u6642\u9593\u3092\u304a\u3044\u3066\u304f\u3060\u3055\u3044\u3002");
        if(result.status==403)return error("\u3081\u3076\u304d\u304c\u6295\u7a3f\u3092\u62d2\u5426\u3057\u307e\u3057\u305f\u3002\u516c\u5f0f\u30b5\u30a4\u30c8\u3067\u6295\u7a3f\u6a29\u9650\u3092\u78ba\u8a8d\u3057\u3066\u304f\u3060\u3055\u3044\u3002");
        if(result.status==400||result.status==422||result.status==404)return error("\u3081\u3076\u304d\u306e\u6295\u7a3f\u6761\u4ef6\u3092\u78ba\u8a8d\u3057\u3066\u304f\u3060\u3055\u3044\u3002");
        if(result.status==503)return error("\u3081\u3076\u304d\u306e\u30bb\u30c3\u30b7\u30e7\u30f3\u307e\u305f\u306f\u30b9\u30ec\u3092\u78ba\u8a8d\u3067\u304d\u307e\u305b\u3093\u3002\u6295\u7a3f\u306f\u9001\u4fe1\u3057\u3066\u3044\u307e\u305b\u3093\u3002");
        return error("\u3081\u3076\u304d\u306e\u6295\u7a3f\u7d50\u679c\u304c\u672a\u78ba\u5b9a\u3067\u3059\u3002\u518d\u6295\u7a3f\u305b\u305a\u30b9\u30ec\u3092\u66f4\u65b0\u3057\u3066\u304f\u3060\u3055\u3044\u3002\u62e1\u5f35\u30db\u30fc\u30e0\u306e\u3081\u3076\u304d\u6295\u7a3f\u8a3a\u65ad\u3082\u78ba\u8a8d\u3057\u3066\u304f\u3060\u3055\u3044\u3002");
    }
    private static BridgeServer.BridgeResponse error(String message){
        // Stock client decodes Windows31J and treats non-success text as a failure.
        return new BridgeServer.BridgeResponse(200,"text/plain; charset=Shift_JIS",
                message.getBytes(Charset.forName("Shift_JIS")));
    }
}
