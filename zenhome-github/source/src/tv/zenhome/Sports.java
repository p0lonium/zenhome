package tv.zenhome;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

/**
 * Donnees sportives pour le bandeau : API publique NON OFFICIELLE d'ESPN (sans cle, non documentee,
 * peut changer sans prevenir). Tout echec est silencieux : le bandeau affiche alors "indisponible".
 */
public class Sports {

    public static class Game {
        public String league = "";
        public int leagueIdx;
        public long startMs;
        public String state = "pre";          // pre | in | post
        public String detail = "";            // minute en direct : 63', HT, Q3 5:42...
        public String homeName = "", awayName = "", homeAbbr = "", awayAbbr = "";
        public String homeScore = "", awayScore = "", homeLogo = "", awayLogo = "";
        public int homeColor = 0xFF0C2A5A, awayColor = 0xFF1B3F94;
        public int interest;
    }

    /** Chemins ESPN, libelles, couleur du point dans la liste, poids dans le choix de l'affiche. */
    public static final String[] PATHS = {"soccer/uefa.champions", "soccer/fra.1", "soccer/eng.1", "soccer/uefa.europa", "basketball/nba"};
    public static final String[] LABELS = {"LIGUE DES CHAMPIONS", "LIGUE 1", "PREMIER LEAGUE", "EUROPA LEAGUE", "NBA"};
    public static final int[] DOTS = {0xFF5DD6B0, 0xFF7CC4FF, 0xFFB7A3FF, 0xFFFFD166, 0xFFFFAB5E};
    static final int[] WEIGHT = {40, 35, 30, 25, 20};
    /** Gros clubs : petit bonus pour l'affiche quand aucune equipe favorite n'est definie. */
    static final String[] BIG = {"paris saint", "marseille", "lyon", "monaco", "lille", "liverpool", "manchester", "arsenal",
            "chelsea", "tottenham", "real madrid", "barcelona", "bayern", "lakers", "warriors", "celtics", "knicks", "spurs"};

    /** @return la liste triee par date, ou null si AUCUN championnat n'a repondu (hors ligne). */
    public static List<Game> fetch(List<String> favs) {
        List<Game> out = new ArrayList<Game>();
        long now = System.currentTimeMillis();
        SimpleDateFormat df = new SimpleDateFormat("yyyyMMdd", Locale.US);
        df.setTimeZone(TimeZone.getTimeZone("UTC"));
        String range = df.format(new Date(now - 86400000L)) + "-" + df.format(new Date(now + 7 * 86400000L));
        int ok = 0;
        for (int i = 0; i < PATHS.length; i++) {
            try {
                String body = get("https://site.api.espn.com/apis/site/v2/sports/" + PATHS[i] + "/scoreboard?limit=100&dates=" + range);
                JSONArray ev = new JSONObject(body).optJSONArray("events");
                ok++;
                if (ev == null) continue;
                for (int k = 0; k < ev.length(); k++) {
                    try {
                        Game g = parse(ev.getJSONObject(k), i);
                        if (g == null) continue;
                        if ("post".equals(g.state) && now - g.startMs > 4 * 3600000L) continue;   // vieux resultats
                        g.interest = interest(g, favs, now);
                        out.add(g);
                    } catch (Throwable ignored) { }
                }
            } catch (Throwable ignored) { }
        }
        if (ok == 0) return null;
        Collections.sort(out, new Comparator<Game>() {
            public int compare(Game a, Game b) {
                boolean la = "in".equals(a.state), lb = "in".equals(b.state);
                if (la != lb) return la ? -1 : 1;
                return a.startMs < b.startMs ? -1 : (a.startMs > b.startMs ? 1 : 0);
            }
        });
        return out;
    }

    /** Le match mis en avant sur l'affiche : en direct d'abord, sinon le plus "interessant". */
    public static Game pick(List<Game> list) {
        Game best = null;
        for (Game g : list) {
            if ("post".equals(g.state)) continue;
            if (best == null || g.interest > best.interest) best = g;
        }
        if (best == null && !list.isEmpty()) best = list.get(list.size() - 1);   // que des matchs termines
        return best;
    }

    static int interest(Game g, List<String> favs, long now) {
        int s = WEIGHT[g.leagueIdx];
        String names = (g.homeName + " " + g.awayName).toLowerCase(Locale.ROOT);
        for (String f : favs) if (f.length() > 0 && names.contains(f)) s += 100;
        for (String b : BIG) if (names.contains(b)) s += 8;
        if ("in".equals(g.state)) s += 200;
        else if ("post".equals(g.state)) s -= 100;
        else {
            long dt = g.startMs - now;
            if (dt >= 0 && dt < 36 * 3600000L) s += 20 - (int) (dt / 3600000L) / 2;
        }
        return s;
    }

    static Game parse(JSONObject e, int li) throws Exception {
        JSONArray comps = e.optJSONArray("competitions");
        if (comps == null || comps.length() == 0) return null;
        JSONObject c = comps.getJSONObject(0);
        JSONArray cs = c.optJSONArray("competitors");
        if (cs == null || cs.length() < 2) return null;
        Game g = new Game();
        g.leagueIdx = li; g.league = LABELS[li];
        g.startMs = parseDate(e.optString("date", c.optString("date", "")));
        if (g.startMs == 0) return null;
        JSONObject st = e.optJSONObject("status");
        if (st == null) st = c.optJSONObject("status");
        if (st != null) {
            JSONObject ty = st.optJSONObject("type");
            if (ty != null) {
                g.state = ty.optString("state", "pre");
                if ("in".equals(g.state)) {
                    g.detail = ty.optString("shortDetail", "");
                    if (g.detail.length() == 0) g.detail = st.optString("displayClock", "");
                }
            }
        }
        for (int j = 0; j < cs.length(); j++) {
            JSONObject co = cs.getJSONObject(j);
            JSONObject t = co.optJSONObject("team");
            boolean home = "home".equals(co.optString("homeAway"));
            String name = t != null ? t.optString("displayName", t.optString("name", "")) : "";
            String ab = t != null ? t.optString("abbreviation", "") : "";
            if (ab.length() == 0) ab = name.length() > 3 ? name.substring(0, 3).toUpperCase(Locale.ROOT) : name;
            String logo = t != null ? t.optString("logo", "") : "";
            int col = color(t != null ? t.optString("color", "") : "", home ? 0xFF0C2A5A : 0xFF1B3F94);
            String sc = co.optString("score", "");
            if (home) { g.homeName = name; g.homeAbbr = ab; g.homeLogo = logo; g.homeColor = col; g.homeScore = sc; }
            else { g.awayName = name; g.awayAbbr = ab; g.awayLogo = logo; g.awayColor = col; g.awayScore = sc; }
        }
        return g;
    }

    /** Couleur "rrggbb" d'ESPN, assombrie si trop claire (texte blanc lisible). */
    static int color(String hex, int fallback) {
        try {
            if (hex == null || hex.length() != 6) return fallback;
            int c = 0xFF000000 | Integer.parseInt(hex, 16);
            float lum = (0.299f * ((c >> 16) & 255) + 0.587f * ((c >> 8) & 255) + 0.114f * (c & 255)) / 255f;
            if (lum > 0.72f) {
                int r = (int) (((c >> 16) & 255) * 0.55f), gr = (int) (((c >> 8) & 255) * 0.55f), b = (int) ((c & 255) * 0.55f);
                c = 0xFF000000 | (r << 16) | (gr << 8) | b;
            }
            return c;
        } catch (Exception e) { return fallback; }
    }

    static long parseDate(String s) {
        String[] fmts = {"yyyy-MM-dd'T'HH:mm'Z'", "yyyy-MM-dd'T'HH:mm:ss'Z'"};
        for (int i = 0; i < fmts.length; i++) {
            try {
                SimpleDateFormat d = new SimpleDateFormat(fmts[i], Locale.US);
                d.setTimeZone(TimeZone.getTimeZone("UTC"));
                return d.parse(s).getTime();
            } catch (Exception ignored) { }
        }
        return 0;
    }

    // ------------------------------------------------------------------ reseau
    static String get(String u) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(u).openConnection();
        c.setConnectTimeout(8000); c.setReadTimeout(10000);
        c.setRequestProperty("User-Agent", "ZenHome/3.0");
        try {
            if (c.getResponseCode() != 200) throw new Exception("HTTP " + c.getResponseCode());
            InputStream in = c.getInputStream();
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
            in.close();
            return bo.toString("UTF-8");
        } finally { c.disconnect(); }
    }

    // ------------------------------------------------------------------ logos
    static final Map<String, Bitmap> LOGOS = new HashMap<String, Bitmap>();

    static synchronized Bitmap cached(String url) { return url == null ? null : LOGOS.get(url); }

    /** Telecharge un logo (a appeler HORS du thread principal). */
    static Bitmap logo(String url) {
        if (url == null || url.length() == 0) return null;
        Bitmap b = cached(url);
        if (b != null) return b;
        try {
            HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
            c.setConnectTimeout(8000); c.setReadTimeout(10000);
            InputStream in = c.getInputStream();
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
            in.close(); c.disconnect();
            byte[] data = bo.toByteArray();
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(data, 0, data.length, o);
            int s = 1;
            while (o.outWidth / (s * 2) >= 160) s *= 2;
            o = new BitmapFactory.Options();
            o.inSampleSize = s;
            b = BitmapFactory.decodeByteArray(data, 0, data.length, o);
            if (b != null) synchronized (Sports.class) { if (LOGOS.size() > 40) LOGOS.clear(); LOGOS.put(url, b); }
        } catch (Throwable ignored) { }
        return b;
    }
}
