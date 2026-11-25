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

    private MebukiThreadData(String html, List<String> images, int count) {
        this.html = html;
        this.images = java.util.Collections.unmodifiableList(images);
        this.count = count;
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
            if (!deleted && post.get("attachments") != null) {
                List<?> attachments = array(post.get("attachments"));
                if (attachments.size()>32) throw invalid();
                for (Object item : attachments) {
                    String image = thumbnail(object(item));
                    if (image != null && images.size()<200) {
                        out.append("<p><a href='https://futatabi.invalid/attachment/")
                                .append(images.size()).append("'>\u753b\u50cf\u3092\u8868\u793a</a></p>");
                        images.add(image);
                    } else out.append("<p class='meta'>\u6dfb\u4ed8\u3042\u308a\uff08\u516c\u5f0f\u30b5\u30a4\u30c8\u3067\u78ba\u8a8d\uff09</p>");
                }
            }
            out.append("</article>");
            if (out.length()>2_000_000) throw invalid();
        }
        return new MebukiThreadData(out.append("</div></body></html>").toString(), images, posts.size());
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
