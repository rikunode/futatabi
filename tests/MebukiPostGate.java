package dev.rikunode.futatabi.mebukiposttest;
import android.app.*;
import android.content.*;
import android.os.*;
import android.graphics.*;
import android.view.*;
import android.view.accessibility.AccessibilityNodeInfo;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

public final class MebukiPostGate extends Instrumentation {
 static final String PKG="com.Nanasaki.ftutter.morphe";
 static final String STORE="futatabi_extension_private", KEY="mebuki_cookie_header";
 static final String COOKIE="mock_session=synthetic-beta21-only";
 static final String FIXTURE="{\"timestamp\":D\"2026-11-25T15:00:00.000Z\",\"messageCount\":2,\"messages\":[{\"id\":\"fixture-op\",\"createdAt\":@1,\"content\":\"fixture <script>no execution</script>\",\"replyTo\":null,\"deletedAt\":null,\"attachments\":[{\"fileType\":\"image/jpeg\",\"filePath\":\"https://storage.mebuki.moe/uploads/attachments/aa/bb/fixture.jpg\",\"createdAt\":@1,\"spoiler\":false,\"nsfw\":false,\"gore\":false,\"other\":false}]},{\"id\":\"fixture-reply\",\"createdAt\":D\"2026-11-25T15:01:00.000Z\",\"content\":\"reply fixture\\n>quote\",\"replyTo\":\"fixture-op\",\"deletedAt\":u,\"attachments\":[{\"fileType\":\"image/png\",\"filePath\":\"https://storage.mebuki.moe/uploads/attachments/aa/bb/hidden.png\",\"createdAt\":@1,\"spoiler\":true,\"nsfw\":false,\"gore\":false,\"other\":false}]},{\"id\":\"fixture-deleted\",\"createdAt\":@1,\"content\":\"never display this deleted content\",\"replyTo\":null,\"deletedAt\":@1,\"attachments\":[]}]}\n";
 static final String CATALOG="{\"threads\":[{\"slug\":\"fixture123\",\"name\":\"Fixture first\",\"messageCount\":2,\"imageUrl\":\"https://storage.mebuki.moe/uploads/attachments/aa/bb/fixture.jpg\"},{\"slug\":\"fixture456\",\"name\":\"Fixture second\",\"messageCount\":2,\"imageUrl\":\"https://storage.mebuki.moe/uploads/attachments/aa/bb/fixture2.jpg\"}]}";
 final AtomicInteger sessionReads=new AtomicInteger(),threadReads=new AtomicInteger(),imageReads=new AtomicInteger();
 volatile String lastRoute="";
 volatile String scenario="success", added="";
 volatile int submissions=0;
 volatile boolean expired=false;
 volatile String expectedContent="beta21 fixture reply",expectedParent="";

 byte[] jpg;
 boolean baselineTrace;
 @Override public void onCreate(Bundle b){super.onCreate(b);baselineTrace="true".equals(b.getString("baselineTrace"));start();}
 void check(boolean b,String name){if(!b)throw new AssertionError(name);}
 void setupNetwork() throws Exception{
   Bitmap bmp=Bitmap.createBitmap(48,48,Bitmap.Config.ARGB_8888);bmp.eraseColor(Color.GRAY);
   ByteArrayOutputStream out=new ByteArrayOutputStream();bmp.compress(Bitmap.CompressFormat.JPEG,80,out);bmp.recycle();jpg=out.toByteArray();
   URL.setURLStreamHandlerFactory(protocol->"https".equals(protocol)?new URLStreamHandler(){
    @Override protected URLConnection openConnection(URL u)throws IOException{
     String host=u.getHost(),path=u.getFile();
     if(!"mebuki.moe".equals(host)&&!"storage.mebuki.moe".equals(host))throw new IOException("fixture denies non-Mebuki host");
     return new HttpURLConnection(u){
       public void connect(){} public boolean usingProxy(){return false;} public void disconnect(){}
       ByteArrayOutputStream sent=new ByteArrayOutputStream();
       boolean posted;
       int code=200;
       public OutputStream getOutputStream(){return sent;}
       public int getResponseCode(){
         if("/t/fixture123.data".equals(path)&&!posted){
           posted=true;submissions++;
           check("POST".equals(getRequestMethod()),"official action method");
           check(COOKIE.equals(getRequestProperty("Cookie")),"official action cookie");
           check("https://mebuki.moe".equals(getRequestProperty("Origin")),"official origin");
           check("https://mebuki.moe/t/fixture123".equals(getRequestProperty("Referer")),"official referer");
           check(!getInstanceFollowRedirects(),"no redirects");
           String type=getRequestProperty("Content-Type");
           check(type.startsWith("multipart/form-data; boundary="),"multipart");
           String boundary=type.substring(type.indexOf("boundary=")+9);
           String body=new String(sent.toByteArray(),StandardCharsets.UTF_8);
           check(body.endsWith("--"+boundary+"--\r\n"),"multipart terminator");
           java.util.Map<String,String> fields=new java.util.HashMap<>();
           java.util.regex.Matcher m=java.util.regex.Pattern.compile("Content-Disposition: form-data; name=\"([A-Za-z]+)\"\r\n\r\n(.*?)\r\n--"+java.util.regex.Pattern.quote(boundary),java.util.regex.Pattern.DOTALL).matcher(body);
           while(m.find())fields.put(m.group(1),m.group(2));
           check(fields.size()==(expectedParent.isEmpty()?3:4),"field count");
           check(expectedContent.equals(fields.get("content")),"content field");
           check(expectedParent.equals(fields.getOrDefault("replyToId","")),"parent field");
           check("".equals(fields.get("showUsername"))&&"".equals(fields.get("showThreadOwner")),"anonymous switches");
           if("rate".equals(scenario))code=429;
           else if("forbidden".equals(scenario))code=403;
           else if("unauthorized".equals(scenario))code=401;
           else if("success".equals(scenario)||"ambiguous".equals(scenario)){
             String bodyJson=org.json.JSONObject.quote(expectedContent),parent=expectedParent.isEmpty()?"null":org.json.JSONObject.quote(expectedParent);
             added=",{\"id\":\"fixture-new\",\"createdAt\":D\"2026-11-25T15:04:00Z\",\"content\":"+bodyJson+",\"replyTo\":"+parent+",\"isAuthor\":true,\"deletedAt\":null,\"attachments\":[]}";
             if("ambiguous".equals(scenario))added+=added.replace("fixture-new","fixture-other");
           }
         }
         return code;
       }
       public String getContentType(){return "/t/fixture123.data".equals(path)?"text/x-script":"application/json";}
       public InputStream getInputStream()throws IOException{
         getResponseCode();
         if("/t/fixture123.data".equals(path)){
           String body="error".equals(scenario)?"{\"data\":{\"status\":\"error\"}}":
                  "unknown".equals(scenario)?"<html>unknown</html>":"{\"data\":{\"status\":\"success\"}}";
           return new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8));
         }
         if("storage.mebuki.moe".equals(host)){imageReads.incrementAndGet();return new ByteArrayInputStream(jpg);}
         String body;
         if("/api/trial/trending".equals(path))body=CATALOG;
         else if("/api/auth/get-session".equals(path)){
           check(COOKIE.equals(getRequestProperty("Cookie")),"unexpected session cookie");sessionReads.incrementAndGet();
           body=expired?"null":"{\"user\":{},\"session\":{}}";
         }else if(path.matches("/api/thread/fixture(123|456)/messages\\?initial=1")){
           check(COOKIE.equals(getRequestProperty("Cookie")),"unexpected read cookie");threadReads.incrementAndGet();lastRoute=path;body=FIXTURE;
           if(!added.isEmpty()){
             body=body.replace("\"messageCount\":2","\"messageCount\":"+(added.contains("fixture-other")?4:3));
             body=body.replace("2026-11-25T15:00:00.000Z","2026-11-25T15:05:00.000Z");
             int pos=body.lastIndexOf("]}");body=body.substring(0,pos)+added+body.substring(pos);
           }
         }else throw new IOException("fixture denies other route");
         return new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8));
       }
     };
    }
   }:null);
 }
 AccessibilityNodeInfo find(AccessibilityNodeInfo n,String text){
   if(n==null)return null;
   CharSequence t=n.getText(),c=n.getContentDescription();
   if((t!=null&&t.toString().contains(text))||(c!=null&&c.toString().contains(text)))return n;
   for(int i=0;i<n.getChildCount();i++){
      AccessibilityNodeInfo v=find(n.getChild(i),text);if(v!=null)return v;
   }return null;
 }
 AccessibilityNodeInfo await(String text,int ms)throws Exception{
   long end=SystemClock.uptimeMillis()+ms;
   do{AccessibilityNodeInfo n=find(getUiAutomation().getRootInActiveWindow(),text);if(n!=null)return n;Thread.sleep(250);}while(SystemClock.uptimeMillis()<end);
   throw new AssertionError("missing "+text);
 }
 void tap(float x,float y){
   long time=SystemClock.uptimeMillis();MotionEvent down=MotionEvent.obtain(time,time,MotionEvent.ACTION_DOWN,x,y,0);
   down.setSource(InputDevice.SOURCE_TOUCHSCREEN);getUiAutomation().injectInputEvent(down,true);down.recycle();
   MotionEvent up=MotionEvent.obtain(time,SystemClock.uptimeMillis(),MotionEvent.ACTION_UP,x,y,0);
   up.setSource(InputDevice.SOURCE_TOUCHSCREEN);getUiAutomation().injectInputEvent(up,true);up.recycle();
 }
 void press(String text,int ms)throws Exception{
   Rect bounds=new Rect();await(text,ms).getBoundsInScreen(bounds);check(bounds.width()>0&&bounds.height()>0,"bounds");tap(bounds.centerX(),bounds.centerY());
 }
 String raw(String path)throws Exception {
    try(Socket sock=new Socket("127.0.0.1",18765)){
        sock.setSoTimeout(12000);sock.getOutputStream().write(("GET "+path+" HTTP/1.1\r\nHost: 127.0.0.1:18765\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
        ByteArrayOutputStream b=new ByteArrayOutputStream();byte[] tmp=new byte[4096];int n;
        while((n=sock.getInputStream().read(tmp))!=-1)b.write(tmp,0,n);
        return new String(b.toByteArray(),java.nio.charset.Charset.forName("Shift_JIS"));
    }
 }
 String shellGet(String path)throws Exception{
    android.os.ParcelFileDescriptor[] fds=getUiAutomation().executeShellCommandRw("toybox nc -q 3 -W 5 -w 5 127.0.0.1 18765");
    try(OutputStream out=new android.os.ParcelFileDescriptor.AutoCloseOutputStream(fds[1])){
        out.write(("GET "+path+" HTTP/1.1\r\nHost: 127.0.0.1:18765\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));out.flush();
    }
    try(InputStream in=new android.os.ParcelFileDescriptor.AutoCloseInputStream(fds[0])){
        ByteArrayOutputStream b=new ByteArrayOutputStream();byte[] tmp=new byte[4096];int n;
        while((n=in.read(tmp))!=-1)b.write(tmp,0,n);
        return new String(b.toByteArray(),java.nio.charset.Charset.forName("Shift_JIS"));
    }
 }
 void edits(AccessibilityNodeInfo n,java.util.List<AccessibilityNodeInfo> out){
    if(n==null)return;if(n.isEditable())out.add(n);for(int j=0;j<n.getChildCount();j++)edits(n.getChild(j),out);
 }
 void compose(String body)throws Exception{
    tap(188,594);await("\u66f8\u304d\u8fbc\u3080",6000);
    java.util.List<AccessibilityNodeInfo> fields=new java.util.ArrayList<>();edits(getUiAutomation().getRootInActiveWindow(),fields);
    check(fields.size()==4,"stock composer fields");
    Bundle arg=new Bundle();arg.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,body);
    check(fields.get(3).performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,arg),"stock body input");Thread.sleep(600);
    press("\u66f8\u304d\u8fbc\u3080",6000);
 }
 byte[] request(String id,String body)throws Exception{
   ByteArrayOutputStream out=new ByteArrayOutputStream();
   String[] fields={"resto",id,"com",body,"sub","","name","","email","","mode","regist","js","on","responsemode","ajax"};
   for(int j=0;j<fields.length;j+=2)out.write(("--fixture-boundary\r\nContent-Disposition: form-data; name=\""+fields[j]+"\"\r\n\r\n"+fields[j+1]+"\r\n").getBytes(StandardCharsets.UTF_8));
   out.write("--fixture-boundary--\r\n".getBytes(StandardCharsets.US_ASCII));
   byte[] data=out.toByteArray();out.reset();
   out.write(("POST /mebuki/futaba.php?guid=on HTTP/1.1\r\nHost: 127.0.0.1:18765\r\nContent-Type: multipart/form-data; boundary=fixture-boundary\r\nContent-Length: "+data.length+"\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));out.write(data);return out.toByteArray();
 }
 String send(byte[] req,boolean external)throws Exception{
   if(external){
     android.os.ParcelFileDescriptor[] fds=getUiAutomation().executeShellCommandRw("toybox nc -q 3 -W 5 -w 5 127.0.0.1 18765");
     try(OutputStream out=new android.os.ParcelFileDescriptor.AutoCloseOutputStream(fds[1])){out.write(req);out.flush();}
     try(InputStream in=new android.os.ParcelFileDescriptor.AutoCloseInputStream(fds[0])){return read(in);}
   }
   try(Socket socket=new Socket("127.0.0.1",18765)){
     socket.setSoTimeout(20000);socket.getOutputStream().write(req);socket.getOutputStream().flush();return read(socket.getInputStream());
   }
 }
 String read(InputStream in)throws Exception{
   ByteArrayOutputStream b=new ByteArrayOutputStream();byte[] tmp=new byte[4096];int n;
   while((n=in.read(tmp))!=-1)b.write(tmp,0,n);
   return new String(b.toByteArray(),java.nio.charset.Charset.forName("Shift_JIS"));
 }
 String number(String id,int n){return Long.toString(1000000000000L+(Long.parseLong(id)-1000000000000L+n)%9000000000000L);}
 void noSuccess(String response,String label){check(!response.contains("\"status\":\"ok\"")&&!response.contains("\"thisno\"")&&!response.contains("Read only"),label);}
 @Override public void onStart(){
   Bundle result=new Bundle();Context ctx=getTargetContext();android.content.SharedPreferences prefs=ctx.getSharedPreferences(STORE,0);
   try{
     check(prefs.getString(KEY,"").isEmpty(),"isolated test must not have real cookie");
     setupNetwork();check(prefs.edit().putString(KEY,COOKIE).commit(),"synthetic cookie");
     Intent i=new Intent().setClassName(PKG,PKG.replace(".morphe","")+".MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK);
     startActivitySync(i);Thread.sleep(1600);press("http://127.0.0.1:18765/mebuki/",7000);await("Fixture first",8000);
     String catalog=raw("/mebuki/futaba.php?mode=cat");java.util.regex.Matcher m=java.util.regex.Pattern.compile("res/([0-9]{13})[.]htm").matcher(catalog);check(m.find(),"catalog ID");
     String id=m.group(1),path="/mebuki/res/"+id+".htm";byte[] req=request(id,expectedContent);
     if(baselineTrace){
       press("Fixture first",3000);await("reply fixture",12000);
       android.util.Log.i("MebukiPostGate","TRACE_READY");
       Thread.sleep(4500);compose(expectedContent);Thread.sleep(800);
       check(find(getUiAutomation().getRootInActiveWindow(),"Read only")!=null,"baseline read only");
       check(submissions==0,"baseline zero upstream calls");result.putString("result","PASS beta20 baseline read only");return;
     }
     String external=send(req,true);check(external.startsWith("HTTP/1.1 403")&&external.contains("Not authorized")&&submissions==0,"external denied");
     result.putBoolean("externalPostDenied",true);
     prefs.edit().remove(KEY).commit();String denied=send(req,false);
     noSuccess(denied,"missing session");check(denied.contains("\u30ed\u30b0\u30a4\u30f3\u304c\u5fc5\u8981")&&submissions==0,"missing session explanation");
     prefs.edit().putString(KEY,COOKIE).commit();expired=true;denied=send(req,false);
     noSuccess(denied,"expired session");check(denied.contains("\u30ed\u30b0\u30a4\u30f3\u304c\u5fc5\u8981")&&submissions==0,"expired explanation");expired=false;
     result.putBoolean("missingExpiredDenied",true);
     press("Fixture first",3000);await("reply fixture",12000);Thread.sleep(900);
     prefs.edit().remove(KEY).commit();compose("fixture rejected without session");Thread.sleep(600);
     check(find(getUiAutomation().getRootInActiveWindow(),"\u30ed\u30b0\u30a4\u30f3\u304c\u5fc5\u8981")!=null&&submissions==0,"actual stock login notice");
     result.putBoolean("stockLoginNotice",true);
     prefs.edit().putString(KEY,COOKIE).commit();Thread.sleep(2000);compose(expectedContent);
     long deadline=SystemClock.uptimeMillis()+15000;while(submissions==0&&SystemClock.uptimeMillis()<deadline)Thread.sleep(200);
     check(submissions==1,"stock button reached official mock exactly once");Thread.sleep(1800);
     String html=raw(path);check(html.contains(expectedContent)&&html.contains("No."+number(id,3)),"native number readback");
     result.putString("expectedThisno",number(id,3));result.putBoolean("stockMockPost",true);
     // Each raw test resets only synthetic upstream state. No repeat of an actual request.
     for(String mode:new String[]{"error","unknown","absent","ambiguous","rate","forbidden","unauthorized"}){
       added="";scenario=mode;int before=submissions;String res=send(req,false);
       check(submissions==before+1,"one mock call "+mode);noSuccess(res,mode);
       if(mode.equals("rate"))check(res.contains("\u6295\u7a3f\u5236\u9650"),"rate explanation");
       if(mode.equals("unauthorized"))check(res.contains("\u30ed\u30b0\u30a4\u30f3"),"401 explanation");
       if(mode.equals("forbidden"))check(res.contains("\u62d2\u5426"),"403 explanation");
     }
     result.putInt("negativeActionCases",7);
     added="";scenario="success";expectedParent="fixture-reply";expectedContent="quoted fixture";
     String quoted=send(request(id,">No."+number(id,1)+"\n"+expectedContent),false);
     check(quoted.contains("\"thisno\":"+number(id,3)),"quoted reply number");
     result.putBoolean("quoteMapping",true);
     check(raw(path).contains("&gt;No."+number(id,1)),"native quote readback");
     result.putString("result","PASS stock UI, authenticated mock action, number mapping, no-session, expired, external and uncertain cases");
   }catch(Throwable ex){result.putString("failure",ex.getClass().getSimpleName()+": "+ex.getMessage());}
   finally{
     if(COOKIE.equals(prefs.getString(KEY,"")))prefs.edit().remove(KEY).commit();
     result.putInt("mockPosts",submissions);finish(result.containsKey("failure")?0:-1,result);
   }
 }
}
