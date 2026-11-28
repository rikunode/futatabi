package dev.rikunode.futatabi.imageng;

import android.app.Activity;
import android.app.AlertDialog;
import android.text.InputFilter;
import android.view.ViewGroup;
import android.widget.*;
import java.text.DateFormat;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Native rule UI. Entry points accept already extracted features, never an
 * image URI, an encoded image, or an external storage path.
 * The caller is responsible for supplying current-catalog features and
 * refreshing its dynamic filter after a successful mutation.
 */
public final class ImageNgDialogs {
    private static final ExecutorService IO=Executors.newSingleThreadExecutor();
    private static final Set<String> BOARDS=new HashSet<>(Arrays.asList("img","may","jun","aimoge","animanch","mebuki"));
    private ImageNgDialogs(){}
    private interface Job<T>{T run()throws Exception;}
    private interface Result<T>{void accept(T result);}
    private static boolean active(Activity a){return !a.isFinishing()&&!a.isDestroyed();}
    private static <T> void run(Activity a,Job<T> job,Result<T> onSuccess,Runnable onFailure){
        IO.execute(()->{
            try{
                T result=job.run();
                a.runOnUiThread(()->{if(active(a))onSuccess.accept(result);});
            }catch(Exception e){
                a.runOnUiThread(()->{
                    if(active(a)){
                        Toast.makeText(a,"画像NGの処理に失敗しました。",Toast.LENGTH_LONG).show();
                        if(onFailure!=null)onFailure.run();
                    }
                });
            }
        });
    }
    private static LinearLayout column(Activity a){
        LinearLayout l=new LinearLayout(a);l.setOrientation(LinearLayout.VERTICAL);
        int p=Math.round(16*a.getResources().getDisplayMetrics().density);
        l.setPadding(p,p,p,p);return l;
    }
    private static TextView label(Activity a,LinearLayout root,String text){
        TextView v=new TextView(a);v.setText(text);v.setPadding(0,8,0,8);root.addView(v);return v;
    }
    private static RadioButton radio(Activity a,RadioGroup group,int id,String text){
        RadioButton button=new RadioButton(a);button.setId(id);button.setText(text);group.addView(button);return button;
    }
    private static String boardName(String board){
        switch(board){
            case "aimoge":return "あいもげ";
            case "animanch":return "あにまん";
            case "mebuki":return "めぶきちゃんねる";
            case "all":return "全板";
            default:return board;
        }
    }
    static int previewMatches(ImageNgFeatures selected,List<ImageNgFeatures> catalog,boolean similar){
        int n=0;
        for(ImageNgFeatures candidate:catalog)if(selected.matches(candidate,similar))n++;
        return n;
    }
    /**
     * Call on the main thread. Catalog candidates must be features for the
     * current board only. Unknown/uncomputed candidates are not claimed to
     * be checked. No preview images are retained.
     */
    public static void showRegistration(Activity a,String board,ImageNgFeatures selected,
            List<ImageNgFeatures> catalog,Runnable onChanged){
        if(!active(a))return;
        if(!BOARDS.contains(board)||selected==null){
            Toast.makeText(a,"画像の特徴量を取得できませんでした。",Toast.LENGTH_LONG).show();return;
        }
        final List<ImageNgFeatures> snapshot=catalog==null?Collections.emptyList():
            new ArrayList<>(catalog.subList(0,Math.min(catalog.size(),2048)));
        LinearLayout root=column(a);
        label(a,root,"画像ファイル・画像URLはNGデータに保存しません。");
        label(a,root,"判定方法");
        RadioGroup mode=new RadioGroup(a);
        radio(a,mode,1,"完全一致のみ");
        RadioButton similar=radio(a,mode,2,"同じ画像・類似画像");
        similar.setEnabled(selected.phash!=null);
        mode.check(selected.phash==null?1:2);
        root.addView(mode);
        if(selected.phash==null)label(a,root,"単調な画像のため、完全一致のみ登録できます。");
        label(a,root,"適用範囲");
        RadioGroup scope=new RadioGroup(a);
        radio(a,scope,3,"この板のみ（"+boardName(board)+"）");radio(a,scope,4,"全板");
        scope.check(3);root.addView(scope);
        EditText memo=new EditText(a);memo.setHint("管理用メモ（任意）");
        memo.setFilters(new InputFilter[]{new InputFilter.LengthFilter(256)});root.addView(memo);
        TextView preview=label(a,root,"");
        Runnable update=()->preview.setText(snapshot.isEmpty()?
            "現在のカタログの照合結果は未取得です。":
            "計算済み候補 "+snapshot.size()+" 件中 "+previewMatches(selected,snapshot,mode.getCheckedRadioButtonId()==2)+" 件一致（未判定画像を除く）");
        mode.setOnCheckedChangeListener((g,id)->update.run());update.run();
        ScrollView scroll=new ScrollView(a);scroll.addView(root);
        AlertDialog dialog=new AlertDialog.Builder(a).setTitle("画像NG登録").setView(scroll)
            .setNegativeButton("キャンセル",null).setPositiveButton("登録",null).create();
        dialog.setOnShowListener(ignored->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            final ImageNgStore.Mode m=mode.getCheckedRadioButtonId()==2?ImageNgStore.Mode.SIMILAR:ImageNgStore.Mode.EXACT;
            final String target=scope.getCheckedRadioButtonId()==4?"all":board;
            final String comment=memo.getText().toString();
            v.setEnabled(false);dialog.setCancelable(false);
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setEnabled(false);
            run(a,()->ImageNgStore.open(a.getApplicationContext()).add(selected,m,target,comment),rule->{
                dialog.dismiss();
                Toast.makeText(a,"画像NGを登録しました。",Toast.LENGTH_SHORT).show();
                if(onChanged!=null)onChanged.run();
            },()->{
                if(dialog.isShowing()){
                    v.setEnabled(true);dialog.setCancelable(true);
                    dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setEnabled(true);
                }
            });
        }));
        dialog.show();
    }
    /** Rule management is independent of the Flutter catalog integration. */
    public static void showManagement(Activity a,Runnable onChanged){
        if(!active(a))return;
        run(a,()->ImageNgStore.open(a.getApplicationContext()).list(),rules->{
            if(rules.isEmpty()){
                new AlertDialog.Builder(a).setTitle("画像NG管理").setMessage("登録済みの画像NGはありません。")
                    .setPositiveButton("閉じる",null).show();return;
            }
            String[] items=new String[rules.size()];
            DateFormat date=DateFormat.getDateTimeInstance(DateFormat.SHORT,DateFormat.SHORT);
            for(int i=0;i<items.length;i++){
                ImageNgStore.Rule r=rules.get(i);
                items[i]=(r.memo.trim().isEmpty()?"画像NG "+r.id.substring(0,8):r.memo)+"\n"+
                    (r.enabled?"有効":"無効")+" / "+boardName(r.scope)+" / "+
                    (r.matchMode==ImageNgStore.Mode.SIMILAR?"類似":"完全一致")+"\n"+
                    date.format(new Date(r.createdAt))+" / "+r.features.sha256.substring(0,12)+"…";
            }
            new AlertDialog.Builder(a).setTitle("画像NG管理（"+items.length+"件）")
                .setItems(items,(dialog,which)->manageRule(a,rules.get(which),onChanged))
                .setNegativeButton("閉じる",null).show();
        },null);
    }
    private static void manageRule(Activity a,ImageNgStore.Rule rule,Runnable onChanged){
        new AlertDialog.Builder(a).setTitle(rule.memo.trim().isEmpty()?"画像NG "+rule.id.substring(0,8):rule.memo)
            .setItems(new String[]{rule.enabled?"無効にする":"有効にする","削除する"},(dialog,which)->{
                if(which==0)mutate(a,rule,false,onChanged);
                else new AlertDialog.Builder(a).setTitle("画像NGを削除しますか？")
                    .setNegativeButton("キャンセル",(d,w)->manageRule(a,rule,onChanged))
                    .setPositiveButton("削除",(d,w)->mutate(a,rule,true,onChanged)).show();
            }).setNegativeButton("一覧へ",(d,w)->showManagement(a,onChanged)).show();
    }
    private static void mutate(Activity a,ImageNgStore.Rule rule,boolean delete,Runnable onChanged){
        run(a,()->{
            ImageNgStore store=ImageNgStore.open(a.getApplicationContext());
            boolean ok=delete?store.remove(rule.id):store.setEnabled(rule.id,!rule.enabled);
            if(!ok)throw new IllegalStateException("Rule no longer exists");
            return ok;
        },ok->{
            if(onChanged!=null)onChanged.run();
            showManagement(a,onChanged);
        },()->showManagement(a,onChanged));
    }
}
