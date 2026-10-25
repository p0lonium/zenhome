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
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Bandeau sport colle a gauche : affiche du match (en direct ou a venir) + liste des prochains matchs. */
public class SportsView extends View {
    final float d;
    final boolean fr;
    final Typeface fReg, fMed;
    final Home.Glass glass;
    final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    final Paint tp = new Paint(Paint.ANTI_ALIAS_FLAG);
    final RectF rf = new RectF();
    final Rect rc = new Rect();
    final Path clip = new Path();
    List<Sports.Game> games = new ArrayList<Sports.Game>();
    Sports.Game featured;
    String status;

    SportsView(Context ctx, float density, boolean french, Typeface reg, Typeface med) {
        super(ctx);
        d = density; fr = french; fReg = reg; fMed = med;
        status = french ? "Chargement des matchs…" : "Loading matches…";
        glass = new Home.Glass(28 * d, 0x24FFFFFF, Math.max(1f, d));
        setFocusable(false);
    }

    void setData(List<Sports.Game> list) {
        games = list;
        featured = Sports.pick(list);
        status = fr ? "Aucun match à venir" : "No upcoming matches";
        final Sports.Game g = featured;
        if (g != null) {
            new Thread(new Runnable() { public void run() {
                Bitmap a = Sports.logo(g.homeLogo), b = Sports.logo(g.awayLogo);
                if (a != null || b != null) postInvalidate();
            } }).start();
        }
        invalidate();
    }

    void setOffline() {
        if (games.isEmpty()) { status = fr ? "Matchs indisponibles (hors ligne)" : "Matches unavailable (offline)"; invalidate(); }
    }

    // ------------------------------------------------------------------ dessin
    @Override
    protected void onDraw(Canvas c) {
        int w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;
        c.save();
        c.clipRect(0, 0, w, h);
        glass.setBounds((int) (-60 * d), 0, w, h);   // coins gauches hors ecran : bandeau colle au bord
        glass.draw(c);
        c.restore();

        if (featured == null) {
            tp.setShader(null); tp.setTypeface(fReg); tp.setColor(0xCCFFFFFF); tp.setTextSize(22 * d); tp.setTextAlign(Paint.Align.CENTER);
            c.drawText(status, w / 2f, 170 * d, tp);
            return;
        }
        drawPoster(c, featured);

        float y = 318 * d + 44 * d;
        tp.setShader(null); tp.setTypeface(fMed); tp.setTextAlign(Paint.Align.LEFT); tp.setTextSize(20 * d); tp.setColor(0xCCFFFFFF);
        c.drawText(fr ? "À venir" : "Upcoming", 28 * d, y, tp);
        int shown = 0;
        y += 42 * d;
        for (Sports.Game g : games) {
            if (g == featured || "post".equals(g.state)) continue;
            if (shown >= 6 || y > h - 20 * d) break;
            drawRow(c, g, y);
            y += 46 * d; shown++;
        }
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
        float x = 16 * d, y = 18 * d, pw = 348 * d, ph = 300 * d, r = 24 * d, mid = x + pw / 2f;
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
