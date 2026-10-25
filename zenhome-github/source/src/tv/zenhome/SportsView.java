package tv.zenhome;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.view.View;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Bandeau sport colle a gauche : affiche du match (en direct ou a venir) + liste des prochains matchs. */
public class SportsView extends View {
    final float d;
    final boolean fr;
    final Typeface fReg, fMed;
    final Home.Glass glass, glass2;
    static final float POSTER_SCALE = 0.75f;
    final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    final Paint tp = new Paint(Paint.ANTI_ALIAS_FLAG);
    final RectF rf = new RectF();
    final Rect rc = new Rect();
    final Path clip = new Path();
    List<Sports.Game> games = new ArrayList<Sports.Game>();
    List<Sports.Game> board = new ArrayList<Sports.Game>();   // 3 matchs en direct (ou derniers resultats)
    Sports.Game featured;
    String status;

    SportsView(Context ctx, float density, boolean french, Typeface reg, Typeface med) {
        super(ctx);
        d = density; fr = french; fReg = reg; fMed = med;
        status = french ? "Chargement des matchs…" : "Loading matches…";
        glass = new Home.Glass(28 * d, 0x24FFFFFF, Math.max(1f, d));
        glass2 = new Home.Glass(28 * d, 0x24FFFFFF, Math.max(1f, d));
        setFocusable(false);
    }

    void setData(List<Sports.Game> list) {
        games = list;
        featured = Sports.pick(list);
        List<Sports.Game> lv = new ArrayList<Sports.Game>(), ps = new ArrayList<Sports.Game>();
        for (Sports.Game x : list) {
            if ("in".equals(x.state)) lv.add(x); else if ("post".equals(x.state)) ps.add(x);
        }
        Collections.sort(lv, new Comparator<Sports.Game>() {
            public int compare(Sports.Game a, Sports.Game b) { return b.interest - a.interest; } });
        Collections.sort(ps, new Comparator<Sports.Game>() {
            public int compare(Sports.Game a, Sports.Game b) { return a.startMs < b.startMs ? 1 : (a.startMs > b.startMs ? -1 : 0); } });
        board = new ArrayList<Sports.Game>(lv);
        board.addAll(ps);
        while (board.size() > 3) board.remove(board.size() - 1);
        status = fr ? "Aucun match à venir" : "No upcoming matches";
        final List<Sports.Game> need = new ArrayList<Sports.Game>(board);
        if (featured != null && !need.contains(featured)) need.add(0, featured);
        if (!need.isEmpty()) {
            new Thread(new Runnable() { public void run() {
                for (Sports.Game x : need) {
                    Bitmap a = Sports.logo(x.homeLogo), b = Sports.logo(x.awayLogo);
                    if (a != null || b != null) postInvalidate();
                }
            } }).start();
        }
        invalidate();
    }

    void setOffline() {
        if (games.isEmpty()) { status = (fr ? "Matchs indisponibles" : "Matches unavailable") + "|" + Sports.lastErr; invalidate(); }
    }

    // ------------------------------------------------------------------ dessin
    @Override
    protected void onDraw(Canvas c) {
        int w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;
        if (featured == null) {
            glass.setBounds(0, 0, w, h); glass.draw(c);
            tp.setShader(null); tp.setTypeface(fReg); tp.setColor(0xCCFFFFFF); tp.setTextSize(22 * d); tp.setTextAlign(Paint.Align.CENTER);
            String[] parts = status.split("\\|", 2);
            c.drawText(parts[0], w / 2f, 170 * d, tp);
            if (parts.length > 1 && parts[1].length() > 0) {
                tp.setTextSize(12 * d); tp.setColor(0x99FFFFFF);
                String e = parts[1]; if (e.length() > 44) e = e.substring(0, 44);
                c.drawText(e, w / 2f, 200 * d, tp);
            }
            return;
        }
        float bottomH = 156 * d, gap = 10 * d, topH = h - bottomH - gap;
        glass.setBounds(0, 0, w, (int) topH); glass.draw(c);
        glass2.setBounds(0, (int) (topH + gap), w, h); glass2.draw(c);

        drawPoster(c, featured);

        float y = (14 + 300 * POSTER_SCALE + 38) * d;
        tp.setShader(null); tp.setTypeface(fMed); tp.setTextAlign(Paint.Align.LEFT); tp.setTextSize(20 * d); tp.setColor(0xCCFFFFFF);
        c.drawText(fr ? "À venir" : "Upcoming", 28 * d, y, tp);
        int shown = 0;
        y += 38 * d;
        for (Sports.Game g : games) {
            if (g == featured || !"pre".equals(g.state)) continue;
            if (shown >= 6 || y > topH - 14 * d) break;
            drawRow(c, g, y);
            y += 40 * d; shown++;
        }
        drawBoard(c, topH + gap);
    }

    /** Fenetre du bas : jusqu'a 3 cartes (matchs en direct, ou a defaut derniers resultats). */
    void drawBoard(Canvas c, float top) {
        int w = getWidth();
        float x0 = 20 * d, hy = top + 30 * d;
        boolean anyLive = !board.isEmpty() && "in".equals(board.get(0).state);
        tp.setShader(null); tp.setTypeface(fMed); tp.setTextAlign(Paint.Align.LEFT); tp.setTextSize(18 * d); tp.setColor(0xCCFFFFFF);
        if (anyLive) {
            p.setShader(null); p.setStyle(Paint.Style.FILL); p.setColor(0xFFE5322D);
            c.drawCircle(x0 + 5 * d, hy - 6 * d, 5 * d, p);
            c.drawText(fr ? "En direct" : "Live", x0 + 20 * d, hy, tp);
        } else {
            c.drawText(fr ? "Derniers résultats" : "Latest results", x0, hy, tp);
        }
        if (board.isEmpty()) {
            tp.setTypeface(fReg); tp.setTextSize(16 * d); tp.setColor(0x99FFFFFF);
            c.drawText(fr ? "Aucun match en cours" : "No match in progress", x0, hy + 40 * d, tp);
            return;
        }
        float m = 14 * d, gp = 8 * d, cw = (w - 2 * m - 2 * gp) / 3f, cy0 = top + 44 * d, chh = 100 * d;
        for (int i = 0; i < board.size() && i < 3; i++) {
            drawCard(c, board.get(i), m + i * (cw + gp), cy0, cw, chh);
        }
    }

    /** Carte facon score en direct : pastille d'etat en haut, puis une ligne par equipe (logo, sigle, score). */
    void drawCard(Canvas c, Sports.Game g, float x, float y, float cw, float chh) {
        boolean live = "in".equals(g.state);
        rf.set(x, y, x + cw, y + chh);
        p.setShader(null); p.setStyle(Paint.Style.FILL); p.setColor(0x1AFFFFFF);
        c.drawRoundRect(rf, 14 * d, 14 * d, p);
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(Math.max(1f, 0.75f * d)); p.setColor(0x40FFFFFF);
        c.drawRoundRect(rf, 14 * d, 14 * d, p);
        p.setStyle(Paint.Style.FILL);
        // etat : pastille rouge + minute, ou "Termine"
        tp.setShader(null); tp.setTypeface(fMed); tp.setTextAlign(Paint.Align.LEFT); tp.setTextSize(12.5f * d);
        if (live) {
            p.setColor(0xFFE5322D); c.drawCircle(x + 12 * d, y + 17 * d, 4 * d, p);
            tp.setColor(0xFFFF6B63);
            String dt = g.detail.length() > 0 ? g.detail : "LIVE";
            if (dt.length() > 8) dt = dt.substring(0, 8);
            c.drawText(dt, x + 21 * d, y + 22 * d, tp);
        } else {
            tp.setColor(0x99FFFFFF);
            c.drawText(fr ? "Terminé" : "Final", x + 12 * d, y + 22 * d, tp);
        }
        // separateur en pointilles
        p.setColor(0x33FFFFFF);
        for (float dx = x + 10 * d; dx < x + cw - 10 * d; dx += 7 * d) c.drawRect(dx, y + 31 * d, dx + 3.5f * d, y + 32 * d, p);
        int hs = num(g.homeScore), as = num(g.awayScore);
        drawTeamRow(c, g.homeLogo, g.homeAbbr, g.homeScore, g.homeColor, x, y + 52 * d, cw, hs >= as);
        drawTeamRow(c, g.awayLogo, g.awayAbbr, g.awayScore, g.awayColor, x, y + 80 * d, cw, as >= hs);
    }

    void drawTeamRow(Canvas c, String logoUrl, String abbr, String score, int color, float x, float cy, float cw, boolean lead) {
        float rad = 11 * d, bx = x + 12 * d + rad;
        p.setShader(null); p.setStyle(Paint.Style.FILL); p.setColor(0xFFFFFFFF);
        c.drawCircle(bx, cy, rad, p);
        Bitmap b = Sports.cached(logoUrl);
        if (b != null) {
            float in = rad * 0.8f;
            rc.set((int) (bx - in), (int) (cy - in), (int) (bx + in), (int) (cy + in));
            p.setFilterBitmap(true);
            c.drawBitmap(b, null, rc, p);
        } else {
            p.setColor(color); c.drawCircle(bx, cy, rad * 0.7f, p);
        }
        tp.setTypeface(fMed); tp.setTextAlign(Paint.Align.LEFT); tp.setTextSize(15 * d);
        tp.setColor(lead ? 0xFFFFFFFF : 0xB3FFFFFF);
        c.drawText(abbr, x + 12 * d + 2 * rad + 7 * d, cy + 5.5f * d, tp);
        tp.setTextAlign(Paint.Align.RIGHT); tp.setTextSize(16 * d);
        tp.setColor(lead ? 0xFFFFFFFF : 0x99FFFFFF);
        c.drawText(score, x + cw - 10 * d, cy + 6 * d, tp);
    }

    static int num(String s) {
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return 0; }
    }

    void drawRow(Canvas c, Sports.Game g, float y) {
        boolean live = "in".equals(g.state);
        p.setShader(null); p.setStyle(Paint.Style.FILL); p.setColor(Sports.DOTS[g.leagueIdx]);
        c.drawCircle(34 * d, y - 7 * d, 6 * d, p);
        tp.setTypeface(fMed); tp.setTextAlign(Paint.Align.LEFT); tp.setTextSize(21 * d);
        tp.setColor(live ? 0xFFFF6B63 : 0xFFFFFFFF);
        c.drawText(live ? (g.detail.length() > 0 ? g.detail : "LIVE") : dayLabel(g.startMs, false) + " " + hour(g.startMs), 52 * d, y, tp);
        tp.setTypeface(fReg); tp.setColor(0xFFFFFFFF);
        String teams = live ? g.homeAbbr + " " + g.homeScore + "–" + g.awayScore + " " + g.awayAbbr : g.homeAbbr + " – " + g.awayAbbr;
        c.drawText(teams, 190 * d, y, tp);
    }

    void drawPoster(Canvas c, Sports.Game g) {
        final float s = POSTER_SCALE;
        float x = 16 * d / s, y = 14 * d / s, pw = (getWidth() - 32 * d) / s, ph = 300 * d, r = 24 * d, mid = x + pw / 2f;
        c.save();
        c.scale(s, s);
        boolean live = "in".equals(g.state), post = "post".equals(g.state);
        rf.set(x, y, x + pw, y + ph);
        c.save();
        clip.reset(); clip.addRoundRect(rf, r, r, Path.Direction.CW); c.clipPath(clip);
        p.setStyle(Paint.Style.FILL); p.setShader(null);
        p.setColor(g.homeColor); c.drawRect(x, y, mid, y + ph, p);
        p.setColor(g.awayColor); c.drawRect(mid, y, x + pw, y + ph, p);
        p.setShader(new LinearGradient(0, y, 0, y + ph, 0x38FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
        c.drawRect(x, y, x + pw, y + ph, p);
        p.setShader(null);
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(20 * d); p.setColor(0xD9FFFFFF);
        c.drawLine(mid + 55 * d, y, mid - 55 * d, y + ph, p);
        p.setStyle(Paint.Style.FILL);
        // grands sigles en filigrane
        tp.setShader(null); tp.setTypeface(fMed); tp.setColor(0x2EFFFFFF); tp.setTextSize(96 * d);
        tp.setTextAlign(Paint.Align.LEFT); c.drawText(g.homeAbbr, x + 14 * d, y + 150 * d, tp);
        tp.setTextAlign(Paint.Align.RIGHT); c.drawText(g.awayAbbr, x + pw - 14 * d, y + 200 * d, tp);
        // barre du bas
        p.setColor(0x73000000); c.drawRect(x, y + ph - 64 * d, x + pw, y + ph, p);
        c.restore();
        rf.set(x, y, x + pw, y + ph);
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(Math.max(1f, d)); p.setColor(0x8CFFFFFF);
        c.drawRoundRect(rf, r, r, p);
        p.setStyle(Paint.Style.FILL);

        // pastille championnat
        tp.setTypeface(fMed); tp.setTextSize(17 * d); tp.setTextAlign(Paint.Align.CENTER); tp.setColor(0xFFFFFFFF);
        float lw = tp.measureText(g.league) + 36 * d;
        rf.set(mid - lw / 2f, y + 16 * d, mid + lw / 2f, y + 46 * d);
        p.setColor(0x38FFFFFF); c.drawRoundRect(rf, 15 * d, 15 * d, p);
        c.drawText(g.league, mid, y + 37 * d, tp);
        if (live) {
            tp.setTextSize(17 * d);
            float dw = tp.measureText("DIRECT") + 54 * d;
            rf.set(mid - dw / 2f, y + 54 * d, mid + dw / 2f, y + 84 * d);
            p.setColor(0xFFE5322D); c.drawRoundRect(rf, 15 * d, 15 * d, p);
            p.setColor(0xFFFFFFFF); c.drawCircle(mid - dw / 2f + 20 * d, y + 69 * d, 5 * d, p);
            tp.setTextAlign(Paint.Align.LEFT); c.drawText("DIRECT", mid - dw / 2f + 32 * d, y + 75 * d, tp);
            tp.setTextAlign(Paint.Align.CENTER);
        }

        // badges + score
        float cy = y + ph - 64 * d - 66 * d;
        drawBadge(c, mid - 112 * d, cy, 46 * d, g.homeLogo, g.homeAbbr, g.homeColor);
        drawBadge(c, mid + 112 * d, cy, 46 * d, g.awayLogo, g.awayAbbr, g.awayColor);
        tp.setTypeface(fMed); tp.setColor(0xFFFFFFFF); tp.setTextAlign(Paint.Align.CENTER);
        if (live || post) { tp.setTextSize(52 * d); c.drawText(g.homeScore + " – " + g.awayScore, mid, cy + 18 * d, tp); }
        else { tp.setTextSize(36 * d); c.drawText("VS", mid, cy + 13 * d, tp); }

        // texte de la barre du bas
        String bottom = live ? (g.detail.length() > 0 ? g.detail : "") : (post ? (fr ? "Terminé" : "Full time") : dayLabel(g.startMs, true) + " " + hour(g.startMs));
        tp.setTextSize(26 * d); tp.setTextAlign(Paint.Align.CENTER);
        c.drawText(bottom, mid, y + ph - 22 * d, tp);
        c.restore();
    }

    void drawBadge(Canvas c, float cx, float cy, float rad, String logoUrl, String abbr, int color) {
        p.setShader(null); p.setStyle(Paint.Style.FILL); p.setColor(0xFFFFFFFF);
        c.drawCircle(cx, cy, rad, p);
        Bitmap b = Sports.cached(logoUrl);
        if (b != null) {
            float in = rad * 0.78f;
            rc.set((int) (cx - in), (int) (cy - in), (int) (cx + in), (int) (cy + in));
            p.setFilterBitmap(true);
            c.drawBitmap(b, null, rc, p);
        } else {
            tp.setTypeface(fMed); tp.setTextAlign(Paint.Align.CENTER); tp.setColor(color); tp.setTextSize(abbr.length() > 3 ? 17 * d : 22 * d);
            c.drawText(abbr, cx, cy + 8 * d, tp);
        }
    }

    // ------------------------------------------------------------------ dates
    String hour(long ms) { return new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date(ms)); }

    String dayLabel(long ms, boolean full) {
        Calendar now = Calendar.getInstance(), t = Calendar.getInstance();
        t.setTimeInMillis(ms);
        int diff = (t.get(Calendar.YEAR) * 400 + t.get(Calendar.DAY_OF_YEAR)) - (now.get(Calendar.YEAR) * 400 + now.get(Calendar.DAY_OF_YEAR));
        if (diff == 0) return fr ? (full ? "Aujourd'hui" : "Auj") : "Today";
        if (diff == 1) return fr ? (full ? "Demain" : "Dem") : "Tmrw";
        String s = new SimpleDateFormat(full ? "EEEE" : "EEE", Locale.getDefault()).format(new Date(ms));
        if (!full && s.endsWith(".")) s = s.substring(0, s.length() - 1);
        return s.length() == 0 ? s : s.substring(0, 1).toUpperCase(Locale.getDefault()) + s.substring(1);
    }
}
