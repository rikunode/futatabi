package dev.rikunode.futatabi.bridge;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** The full initial response contract of use-thread-messages-ByVYyF88.js. */
final class MebukiThreadData {
    final String html;
    final List<String> images;
    final int count;
    private final List<NativePost> posts;
    private static final class NativePost {
        final String date, body, image;
        final Integer parent;
        NativePost(String date, String body, String image, Integer parent) {
            this.date=date;this.body=body;this.image=image;this.parent=parent;
        }
    }

    private MebukiThreadData(String html, List<String> images, int count, List<NativePost> posts) {
        this.html = html;
        this.images = java.util.Collections.unmodifiableList(images);
        this.count = count;
        this.posts = posts;
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("Unsupported Mebuki thread schema");
    }
    private static Map<?,?> object(Object o) {
        if (!(o instanceof Map)) throw invalid();
        return (Map<?,?>)o;
    }
    private static List<?> array(Object o) {
        if (!(o instanceof List)) throw invalid();
        return (List<?>)o;
    }
    private static String string(Object o) {
        if (!(o instanceof String)) throw invalid();
        return (String)o;
    }
    private static String nullable(Object o) { return o == null ? "" : string(o); }
    private static String esc(String s) {
        return s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;")
                .replace("\"","&quot;").replace("'","&#39;");
    }
    static MebukiThreadData convert(String stream) throws Exception {
        Map<?,?> root = object(MebukiTurboStream.decode(stream));
        MebukiTurboStream.dateMillis(string(root.get("timestamp")));
        Object mc = root.get("messageCount");
        if (!(mc instanceof Long)) throw invalid();
        long expected = (Long)mc;
        List<?> posts = array(root.get("messages"));
        // This is a full initial read, not the differential t= response.
        if (expected < 0 || expected >= 2000 || posts.size() != expected + 1) throw invalid();
        Map<String,Integer> numbers = new HashMap<>();
        for (int i=0; i<posts.size(); i++) {
            String id = string(object(posts.get(i)).get("id"));
            if (id.isEmpty() || id.length()>128 || numbers.put(id,i)!=null) throw invalid();
        }
        List<String> images = new ArrayList<>();
        List<NativePost> nativePosts = new ArrayList<>();
        StringBuilder out = new StringBuilder("<!doctype html><html><head><meta charset='UTF-8'>"
                + "<meta name='viewport' content='width=device-width,initial-scale=1'>"
                + "<meta http-equiv='Content-Security-Policy' content=\"default-src 'none'; style-src 'unsafe-inline'\">"
                + "<style>body{font-family:sans-serif;margin:12px;color:#222;background:#fafafa}"
                + "article{padding:12px 0;border-bottom:1px solid #bbb;overflow-wrap:anywhere}"
                + "blockquote{margin:8px 0;white-space:pre-wrap} .meta{color:#555;font-size:small}"
                + "a{color:#466d25} .quote{color:#567b24}</style></head><body><div class='thre'>");
        for (int i=0; i<posts.size(); i++) {
            Map<?,?> post = object(posts.get(i));
            String date = string(post.get("createdAt"));
            MebukiTurboStream.dateMillis(date);
            String body = nullable(post.get("content"));
            if (body.length()>50_000) throw invalid();
            boolean deleted = post.get("deletedAt") != null;
            String parent = nullable(post.get("replyTo"));
            Integer to = numbers.get(parent);
            out.append("<article id='post").append(i).append("'><div class='meta'><span class='cno'>")
                    .append(i==0 ? "\u30b9\u30ec\u4e3b" : "\u30ec\u30b9 "+i)
                    .append("</span> <span class='cnw'>").append(esc(date)).append("</span></div>");
            if (!deleted && !parent.isEmpty()) {
                out.append("<div class='quote'>");
                if (to != null && to < i) out.append("<a href='#post").append(to).append("'>")
                        .append("&gt;&gt;").append(to).append("</a>");
                else out.append("\u8fd4\u4fe1\u5148\u306e\u30ec\u30b9\u306f\u8868\u793a\u3067\u304d\u307e\u305b\u3093");
                out.append("</div>");
            }
            out.append("<blockquote>").append(deleted ? "\u524a\u9664\u30fb\u975e\u8868\u793a\u306e\u30ec\u30b9" :
                    FutabaQuoteMarkup.render(esc(body).replace("\r\n","\n").replace("\r","\n").replace("\n","<br>")))
                    .append("</blockquote>");
            String nativeImage = null;
            if (!deleted && post.get("attachments") != null) {
                List<?> attachments = array(post.get("attachments"));
                if (attachments.size()>32) throw invalid();
                for (Object item : attachments) {
                    String image = thumbnail(object(item));
                    if (image != null && images.size()<200) {
                        out.append("<p><a href='https://futatabi.invalid/attachment/")
                                .append(images.size()).append("'>\u753b\u50cf\u3092\u8868\u793a</a></p>");
                        images.add(image);
                        if (nativeImage == null) nativeImage = image;
                    } else out.append("<p class='meta'>\u6dfb\u4ed8\u3042\u308a\uff08\u516c\u5f0f\u30b5\u30a4\u30c8\u3067\u78ba\u8a8d\uff09</p>");
                }
            }
            nativePosts.add(new NativePost(date, deleted ? "\u524a\u9664\u30fb\u975e\u8868\u793a\u306e\u30ec\u30b9" : body,
                    nativeImage, !deleted && to != null && to < i ? to : null));
            out.append("</article>");
            if (out.length()>2_000_000) throw invalid();
        }
        return new MebukiThreadData(out.append("</div></body></html>").toString(), images, posts.size(), nativePosts);
    }

    /** Futaba compatibility structure used by the stock thread parser. */
    byte[] nativeHtml(String thread, java.util.function.Function<String,String> imageId) {
        if (!thread.matches("[1-9][0-9]{12}")) throw invalid();
        String[] numbers = new String[posts.size()];
        numbers[0] = thread;
        long base=Long.parseLong(thread);
        for (int i=1;i<numbers.length;i++)
            numbers[i]=Long.toString(1000000000000L+(base-1000000000000L+i)%9000000000000L);
        java.time.format.DateTimeFormatter dates=java.time.format.DateTimeFormatter
                .ofPattern("yy/MM/dd(E) HH:mm:ss",java.util.Locale.JAPANESE)
                .withZone(java.time.ZoneId.of("Asia/Tokyo"));
        StringBuilder h=new StringBuilder("<html><head><meta charset='Shift_JIS'></head><body><div class='thre' data-res='")
                .append(thread).append("'>");
        for(int i=0;i<posts.size();i++){
            NativePost p=posts.get(i);
            if(i>0)h.append("<table border=0><tr><td class=rts></td><td class=rtd><span id='delcheck")
                    .append(numbers[i]).append("' class='rsc'>").append(i).append("</span>");
            h.append("<span class='csb'>").append(i==0?"\u3081\u3076\u304d":"\u7121\u5ff5")
                    .append("</span>Name <span class='cnm'>\u3068\u3057\u3042\u304d</span><span class='cnw'>")
                    .append(dates.format(java.time.Instant.ofEpochMilli(MebukiTurboStream.dateMillis(p.date))))
                    .append("</span><span class='cno'>No.").append(numbers[i]).append("</span>");
            if(p.image!=null){
                String id=imageId.apply(p.image);
                if(id!=null)h.append("<a href='/mebuki/src/").append(id).append(".jpg'>").append(id)
                        .append(".jpg</a><br><a href='/mebuki/src/").append(id)
                        .append(".jpg'><img src='/mebuki/thumb/").append(id).append("s.jpg' width=50 height=50></a>");
            }
            String body=esc(p.body).replace("\r\n","\n").replace("\r","\n").replace("\n","<br>");
            if(p.parent!=null)body="&gt;No."+numbers[p.parent]+"<br>"+body;
            h.append("<blockquote>").append(FutabaQuoteMarkup.render(body)).append("</blockquote>");
            if(i>0)h.append("</td></tr></table>");
        }
        h.append("</div></body></html>");
        java.nio.charset.Charset sjis=java.nio.charset.Charset.forName("Shift_JIS");
        java.nio.charset.CharsetEncoder encoder=sjis.newEncoder();
        StringBuilder encoded=new StringBuilder();
        for(int at=0;at<h.length();){
            int cp=h.codePointAt(at);at+=Character.charCount(cp);
            if(cp<128)encoded.append((char)cp);
            else {String ch=new String(Character.toChars(cp));
                if(encoder.canEncode(ch))encoded.append(ch);else encoded.append("&#").append(cp).append(';');}
        }
        return encoded.toString().getBytes(sjis);
    }

    // Mirrors the public utils Yu(filePath, "sm", createdAt), but only
    // for an explicitly non-sensitive image on the known official storage.
    static String thumbnail(Map<?,?> a) {
        try {
            for (String flag : new String[]{"spoiler","nsfw","gore","other"})
                if (!Boolean.FALSE.equals(a.get(flag))) return null;
            if (!string(a.get("fileType")).startsWith("image/")) return null;
            String path = string(a.get("filePath"));
            if (!safeImage(path)) return null;
            long date = MebukiTurboStream.dateMillis(string(a.get("createdAt")));
            String ext = date > MebukiTurboStream.dateMillis("2025-09-30T07:00:00Z") &&
                    date < MebukiTurboStream.dateMillis("2025-10-25T09:30:00Z") ? "jpg" : "webp";
            String thumbnail = path.substring(0,path.lastIndexOf('.')) + "_sm." + ext;
            return safeImage(thumbnail) ? thumbnail : null;
        } catch (Exception ex) { return null; }
    }
    static boolean safeImage(String value) {
        try {
            URI u = URI.create(value);
            return value.length()<=1000 && "https".equals(u.getScheme()) &&
                    "storage.mebuki.moe".equals(u.getHost()) && u.getRawUserInfo()==null &&
                    (u.getPort()==-1 || u.getPort()==443) && u.getRawQuery()==null && u.getRawFragment()==null &&
                    u.getRawPath().matches("/uploads/attachments/(?:[A-Za-z0-9_-]+/)*[A-Za-z0-9_-]+\\.[A-Za-z0-9]{1,8}");
        } catch (Exception ex) { return false; }
    }
}
