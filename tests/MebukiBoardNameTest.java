package dev.rikunode.futatabi.bridge;

import org.json.JSONArray;
import org.json.JSONObject;

/** Focused host test of the production saved-board transformation. */
public final class MebukiBoardNameTest {
    private static final String PREFIX = "VGhpcyBpcyB0aGUgcHJlZml4IGZvciBhIGxpc3Qu!";
    private static final String URL = "http://127.0.0.1:18765/mebuki/";
    private static final String OLD = "めぶき";
    private static final String NAME = "めぶきちゃんねる";

    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }

    private static JSONArray list(String raw) throws Exception {
        return new JSONArray(raw.substring(PREFIX.length()));
    }

    private static JSONObject mebuki(JSONArray items) throws Exception {
        JSONObject found = null;
        for (int i = 0; i < items.length(); i++) {
            JSONObject entry = new JSONObject(items.getString(i));
            if (!URL.equals(entry.optString("url"))) continue;
            check(found == null, "duplicate Mebuki URL");
            found = entry;
        }
        check(found != null && NAME.equals(found.optString("name")), "official name");
        return found;
    }

    public static void main(String[] args) throws Exception {
        String fresh = FutatabiProvider.completeBoards(null, true);
        JSONArray defaults = list(fresh);
        check(defaults.length() == 6, "six initial boards");
        mebuki(defaults);
        String jun = "https://jun.2chan.net/jun/";
        int junIndex = -1;
        for (int i = 0; i < defaults.length(); i++) {
            JSONObject item = new JSONObject(defaults.getString(i));
            if (jun.equals(item.optString("url"))) {
                check(junIndex < 0, "one Jun board");
                junIndex = i;
                check("二次元裏（jun)".equals(item.optString("name")), "Jun name");
            }
        }
        check(junIndex >= 0, "Jun uses stock direct URL");
        JSONArray previous = new JSONArray();
        for (int i = 0; i < defaults.length(); i++) if (i != junIndex) previous.put(defaults.getString(i));
        String upgraded = FutatabiProvider.completeBoards(PREFIX + previous.toString(), false);
        JSONArray upgradeList = list(upgraded);
        check(upgradeList.length() == 6, "upgrade adds only Jun");
        check(fresh.equals(upgraded), "upgrade inserts Jun between img and external boards");
        check(jun.equals(new JSONObject(upgradeList.getString(2)).optString("url")), "upgrade positions Jun");
        JSONObject customJun = new JSONObject(defaults.getString(junIndex)).put("name", "自分のjun").put("favorite", true).put("resuCount", 9);
        previous.put(customJun.toString());
        String preExisting = PREFIX + previous.toString();
        JSONArray reordered = list(FutatabiProvider.completeBoards(preExisting, false));
        check(customJun.toString().equals(reordered.getString(2)), "preserve existing Jun name and fields");
        String[] expected = {"https://may.2chan.net/b/", "https://img.2chan.net/b/", jun,
                URL, "http://127.0.0.1:18765/aimoge/", "http://127.0.0.1:18765/animanch/"};
        for (int i = 0; i < expected.length; i++)
            check(expected[i].equals(new JSONObject(defaults.getString(i)).getString("url")), "fresh order");
        // Exact beta.37 owner-reported order, with preserved record payloads.
        JSONArray beta37 = new JSONArray();
        for (int index : new int[]{0, 1, 4, 5, 3, 2}) beta37.put(defaults.getString(index));
        check(fresh.equals(FutatabiProvider.completeBoards(PREFIX + beta37.toString(), false)), "owner beta.37 order corrected");
        JSONArray mixed = new JSONArray();
        String custom1 = new JSONObject().put("name", "custom1").put("url", "https://example.invalid/a/").put("favorite", true).toString();
        String custom2 = new JSONObject().put("name", "custom2").put("url", "https://example.invalid/b/").put("resuCount", 77).toString();
        mixed.put(previous.getString(0)).put(custom1).put(previous.getString(1)).put(previous.getString(2))
                .put(custom2).put(previous.getString(3)).put(previous.getString(4)).put(previous.getString(5));
        String mixedResult = FutatabiProvider.completeBoards(PREFIX + mixed.toString(), false);
        JSONArray sorted = list(mixedResult);
        check(custom1.equals(sorted.getString(1)) && custom2.equals(sorted.getString(4)), "custom slots unchanged");
        int known = 0;
        for (int i = 0; i < sorted.length(); i++) {
            if (i == 1 || i == 4) continue;
            check(expected[known++].equals(new JSONObject(sorted.getString(i)).getString("url")), "known relative order");
        }
        java.util.List<String> before = new java.util.ArrayList<>(), after = new java.util.ArrayList<>();
        for (int i = 0; i < mixed.length(); i++) {before.add(mixed.getString(i));after.add(sorted.getString(i));}
        java.util.Collections.sort(before);java.util.Collections.sort(after);
        check(before.equals(after), "every record preserved");
        check(mixedResult.equals(FutatabiProvider.completeBoards(mixedResult, false)), "order idempotent");
        System.out.println("PASS F1 fresh/upgrade/custom slots, six known boards ordered, all records preserved");

        JSONObject old = mebuki(defaults);
        old.put("name", OLD).put("favorite", true).put("resuCount", 17);
        JSONObject custom = new JSONObject().put("name", OLD).put("url", "https://example.invalid/custom/");
        JSONArray saved = new JSONArray();
        for (int i = 0; i < defaults.length(); i++) {
            String entry = defaults.getString(i);
            saved.put(URL.equals(new JSONObject(entry).optString("url")) ? old.toString() : entry);
        }
        saved.put(custom.toString());
        String migrated = FutatabiProvider.completeBoards(PREFIX + saved.toString(), false);
        JSONArray result = list(migrated);
        check(result.length() == saved.length(), "migration must not add a duplicate");
        JSONObject actual = mebuki(result);
        old.put("name", NAME);
        check(old.toString().equals(actual.toString()), "preserve every existing Mebuki field");
        for (int i = 0; i < saved.length(); i++) {
            String entry = saved.getString(i);
            if (!URL.equals(new JSONObject(entry).optString("url"))) {
                check(entry.equals(result.getString(i)), "preserve unrelated board and order");
            }
        }
        check(migrated.equals(FutatabiProvider.completeBoards(migrated, false)), "idempotent migration");
        // The stock backup reads/writes FlutterSharedPreferences.xml as a whole.
        // Model the restored preference payload, not Android system backup.
        android.content.Context restored=new android.content.Context();
        android.content.SharedPreferences prefs=restored.getSharedPreferences("FlutterSharedPreferences",0);
        prefs.edit().putString("flutter.itaList",migrated).commit();
        check(BridgePalette.DARK.equals(BridgePalette.get(restored)),"default dark");
        check(BridgePalette.save(restored,BridgePalette.LIGHT),"light save");
        check(BridgePalette.LIGHT.equals(prefs.getString(BridgePalette.KEY,"")),"palette in stock backup preference file");
        check(migrated.equals(FutatabiProvider.completeBoards(prefs.getString("flutter.itaList",null),true)),"restored list survives fresh migration");
        check(BridgePalette.decorate("<head></head><body></body>",BridgePalette.get(restored)).contains("#fafafa"),"light HTML");
        check(BridgePalette.decorate("<head></head><body></body>",BridgePalette.DARK).contains("#202124"),"dark HTML");
        check(!BridgePalette.save(restored,"invalid")&&BridgePalette.LIGHT.equals(BridgePalette.get(restored)),"invalid palette rejected");
        System.out.println("PASS existing migration: same count, fields, other boards and order; fresh restore, palette in stock preference file, dark/light");
    }
}
