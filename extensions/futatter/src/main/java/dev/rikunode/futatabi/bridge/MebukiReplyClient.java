package dev.rikunode.futatabi.bridge;

import android.content.Context;
import java.util.Map;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import android.util.Base64;

/** Explicit user-initiated text reply; official single-fetch action and authenticated readback. */
final class MebukiReplyClient {
    private static final String SITE = "https://mebuki.moe";
    private final Context context;
    private final MebukiAdapter adapter;

    MebukiReplyClient(Context context, MebukiAdapter adapter) {
        this.context = context;
        this.adapter = adapter;
    }

    synchronized BridgeServer.BridgeResponse reply(JSONObject payload) throws Exception {
        String id = payload.optString("threadId", "");
        if (!id.matches("[0-9]{13}")) return BridgeServer.BridgeResponse.text(400, "Select a valid thread");
        String slug = adapter.slugForThreadId(id);
        if (slug == null || !slug.matches("[A-Za-z0-9_-]{8,24}"))
            return BridgeServer.BridgeResponse.text(404, "Unknown thread");

        String content = payload.optString("content", "").replace("\r\n","\n").replace('\r','\n').replaceFirst("[\\s\\uFEFF]+$","");
        if (content.trim().isEmpty() || content.length() > 5000 ||
                content.split("\\r?\\n", -1).length > 100)
            return BridgeServer.BridgeResponse.text(400, "Reply must contain 1-5000 characters, 100 lines max");
        if (payload.has("attachments")) return BridgeServer.BridgeResponse.text(400, "Attachments not yet verified");
        String replyToId = payload.optString("replyToId", "");
        if (!replyToId.isEmpty() && !replyToId.matches("[A-Za-z0-9_-]{1,128}"))
            return BridgeServer.BridgeResponse.text(400, "Invalid replyToId");
        String cookie = MebukiSession.saved(context);
        if (!MebukiSession.safeCookie(cookie)) return failure(401,"Mebuki login required");
        MebukiThreadData before;
        try { before=MebukiSession.readThread(slug,cookie); }
        catch(MebukiSession.ReadFailure ex) {
            String reason=ex.getMessage();
            return failure("ANONYMOUS".equals(reason)||"NOT_SAVED".equals(reason)||"HTTP 401".equals(reason)
                    ?401:503,"Session or thread unavailable");
        } catch(Exception ex){return failure(503,"Session or thread unavailable");}
        if(!cookie.equals(MebukiSession.saved(context)))return failure(401,"Session changed");
        if(payload.optBoolean("stockQuote")&&replyToId.isEmpty()){
            java.util.regex.Matcher quote=java.util.regex.Pattern.compile("^>No\\.([0-9]{13})\\n").matcher(content);
            if(quote.find()){
                replyToId=before.replyTarget(id,quote.group(1));
                if(replyToId==null)return failure(400,"Unknown quote target");
                content=content.substring(quote.end());
                if(content.trim().isEmpty())return failure(400,"Empty reply");
            }
        }
        if(!replyToId.isEmpty()&&!before.hasTarget(replyToId))return failure(400,"Unknown reply target");

        byte[] nonce = new byte[18];
        new SecureRandom().nextBytes(nonce);
        String boundary = "futatabi-" + Base64.encodeToString(nonce,
                Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        append(buffer, boundary, "content", content);
        if (!replyToId.isEmpty()) append(buffer, boundary, "replyToId", replyToId);
        append(buffer, boundary, "showUsername", payload.optBoolean("showUsername", false) ? "on" : "");
        append(buffer, boundary, "showThreadOwner", payload.optBoolean("showThreadOwner", false) ? "on" : "");
        buffer.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));

        URL url = new URL(SITE + "/t/" + slug + ".data");
        if (!("https".equals(url.getProtocol()) && "mebuki.moe".equals(url.getHost())))
            throw new IllegalArgumentException("Unexpected submission host");
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setInstanceFollowRedirects(false);
        connection.setConnectTimeout(7000);
        connection.setReadTimeout(12000);
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        connection.setRequestProperty("Cookie", cookie);
        connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
        connection.setRequestProperty("Origin", SITE);
        connection.setRequestProperty("Referer", SITE + "/t/" + slug);
        connection.setRequestProperty("Accept", "text/x-script");
        connection.setFixedLengthStreamingMode(buffer.size());
        try {
            if(!cookie.equals(MebukiSession.saved(context)))return failure(401,"Session changed");
            try(java.io.OutputStream output=connection.getOutputStream()){buffer.writeTo(output);}
            int status=connection.getResponseCode();
            if(status==401||status==403||status==429||status==400||status==422)
                return failure(status,"Action rejected");
            InputStream stream=status<400?connection.getInputStream():connection.getErrorStream();
            String response="";
            if(stream!=null)try(InputStream in=stream){
                response=new String(AimogeWriteClient.bounded(in,64_000),StandardCharsets.UTF_8);
            }
            if(!actionCompleted(status,connection.getContentType(),connection.getHeaderField("X-Remix-Redirect"),response))
                return uncertain();
        }catch(Exception ex){return uncertain();}
        finally{connection.disconnect();}
        if(!cookie.equals(MebukiSession.saved(context)))return uncertain();
        try{
            MebukiThreadData after=MebukiSession.readThread(slug,cookie);
            String number=after.confirmedReplyNumber(id,before,content,replyToId);
            if(number==null||!cookie.equals(MebukiSession.saved(context)))return uncertain();
            return BridgeServer.BridgeResponse.json(201,new JSONObject().put("confirmed",true)
                    .put("thisno",number).toString());
        }catch(Exception ex){return uncertain();}
    }

    // Single-fetch envelope alone is not proof of a successful write.
    static boolean actionCompleted(int status,String mime,String redirect,String response){
        if(redirect!=null)return false;
        if(status==204)return response.isEmpty();
        if(status!=200||mime==null||!mime.toLowerCase(java.util.Locale.ROOT).startsWith("text/x-script"))return false;
        try{
            Object value=MebukiTurboStream.decode(response);
            if(!(value instanceof Map))return false;
            Map<?,?> root=(Map<?,?>)value;
            if(root.containsKey("error")||root.containsKey("redirect")||!root.containsKey("data"))return false;
            Object data=root.get("data");
            if(data==null)return true;
            return data instanceof Map&&"success".equals(((Map<?,?>)data).get("status"))
                    &&!((Map<?,?>)data).containsKey("error");
        }catch(Exception ex){return false;}
    }
    private static BridgeServer.BridgeResponse failure(int code,String message){
        return BridgeServer.BridgeResponse.text(code,message);
    }
    private static BridgeServer.BridgeResponse uncertain(){
        return BridgeServer.BridgeResponse.json(202,"{\"confirmed\":false}");
    }

    private static void append(ByteArrayOutputStream out, String boundary, String key, String value)
            throws Exception {
        out.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"" +
                key + "\"\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(value.getBytes(StandardCharsets.UTF_8));
        out.write("\r\n".getBytes(StandardCharsets.US_ASCII));
    }
}
