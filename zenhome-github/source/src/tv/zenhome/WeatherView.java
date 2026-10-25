package tv.zenhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.View;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Meteo sur 7 jours (Open-Meteo : gratuit, sans cle). 7 tuiles de verre : jour, icone, max / min. */
public class WeatherView extends View {

    public static class Day { long ms; int code, max, min; }

    final float d;
    final boolean fr;
    final Typeface fReg, fMed;
    final Home.Glass glass;
    final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    final Paint tp = new Paint(Paint.ANTI_ALIAS_FLAG);
    final RectF rf = new RectF();
    final Path path = new Path();
    Day[] days;

    WeatherView(Context ctx, float density, boolean french, Typeface reg, Typeface med) {
        super(ctx);
        d = density; fr = french; fReg = reg; fMed = med;
        glass = new Home.Glass(22 * d, 0x24FFFFFF, Math.max(1f, d));
        setFocusable(false);
    }

    void setData(Day[] ds) { days = ds; invalidate(); }

    /** A appeler HORS du thread principal. @return null en cas d'echec. */
    static Day[] fetch(String lat, String lon) {
        try {
            String body = Sports.get("https://api.open-meteo.com/v1/forecast?latitude=" + lat + "&longitude=" + lon
                    + "&daily=weather_code,temperature_2m_max,temperature_2m_min&timezone=auto&forecast_days=7");
            JSONObject dj = new JSONObject(body).getJSONObject("daily");
            JSONArray time = dj.getJSONArray("time"), mx = dj.getJSONArray("temperature_2m_max"), mn = dj.getJSONArray("temperature_2m_min");
            JSONArray code = dj.optJSONArray("weather_code");
            if (code == null) code = dj.optJSONArray("weathercode");
            int n = Math.min(7, time.length());
            Day[] out = new Day[n];
            SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
            for (int i = 0; i < n; i++) {
                Day x = new Day();
                x.ms = f.parse(time.getString(i)).getTime();
                x.max = (int) Math.round(mx.getDouble(i));
                x.min = (int) Math.round(mn.getDouble(i));
                x.code = code == null ? 3 : code.getInt(i);
                out[i] = x;
            }
            return n > 0 ? out : null;
        } catch (Throwable t) { return null; }
    }

    @Override
    protected void onDraw(Canvas c) {
        int w = getWidth(), h = getHeight();
        if (days == null || w <= 0) return;
        int n = days.length;
        float gap = 10 * d, tw = (w - gap * (n - 1)) / n;
        for (int i = 0; i < n; i++) {
            float x = i * (tw + gap), cx = x + tw / 2f;
            glass.setBounds((int) x, 0, (int) (x + tw), h);
            glass.draw(c);
            String lbl = i == 0 ? (fr ? "Auj" : "Today")
                    : new SimpleDateFormat("EEE", Locale.getDefault()).format(new Date(days[i].ms));
            if (lbl.endsWith(".")) lbl = lbl.substring(0, lbl.length() - 1);
            if (lbl.length() > 0) lbl = lbl.substring(0, 1).toUpperCase(Locale.getDefault()) + lbl.substring(1);
            tp.setShader(null); tp.setTextAlign(Paint.Align.CENTER);
            tp.setTypeface(fMed); tp.setTextSize(20 * d); tp.setColor(0xE6FFFFFF);
            c.drawText(lbl, cx, 32 * d, tp);
            icon(c, days[i].code, cx, 68 * d, 46 * d);
            tp.setTextSize(26 * d); tp.setColor(0xFFFFFFFF);
            c.drawText(days[i].max + "°", cx, 106 * d, tp);
            tp.setTypeface(fReg); tp.setTextSize(21 * d); tp.setColor(0x99FFFFFF);
            c.drawText(days[i].min + "°", cx, 129 * d, tp);
        }
    }

    // ------------------------------------------------------------------ icones (codes meteo WMO)
    void icon(Canvas c, int code, float cx, float cy, float s) {
        if (code == 0) sun(c, cx, cy, s);
        else if (code <= 2) { sun(c, cx - .16f * s, cy - .14f * s, s * .8f); cloud(c, cx + .06f * s, cy + .1f * s, s * .9f, 0xF2FFFFFF); }
        else if (code == 3) cloud(c, cx, cy, s, 0xE6E4E8F0);
        else if (code == 45 || code == 48) { cloud(c, cx, cy - .08f * s, s * .8f, 0xCCE4E8F0); lines(c, cx, cy + .22f * s, s, 0xCCFFFFFF); }
        else if (code >= 71 && code <= 77 || code == 85 || code == 86) { cloud(c, cx, cy - .08f * s, s * .9f, 0xE6E4E8F0); dots(c, cx, cy + .3f * s, s); }
        else if (code >= 95) { cloud(c, cx, cy - .08f * s, s * .9f, 0xFFB8BED0); bolt(c, cx, cy + .02f * s, s); }
        else { cloud(c, cx, cy - .08f * s, s * .9f, 0xFFC8CFDD); rain(c, cx, cy + .3f * s, s); }
    }

    void sun(Canvas c, float cx, float cy, float s) {
        p.setStyle(Paint.Style.FILL); p.setColor(0xFFFFD166);
        c.drawCircle(cx, cy, .24f * s, p);
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(.06f * s); p.setStrokeCap(Paint.Cap.ROUND);
        for (int i = 0; i < 8; i++) {
            double a = i * Math.PI / 4;
            float cs = (float) Math.cos(a), sn = (float) Math.sin(a);
            c.drawLine(cx + cs * .34f * s, cy + sn * .34f * s, cx + cs * .46f * s, cy + sn * .46f * s, p);
        }
        p.setStyle(Paint.Style.FILL);
    }

    void cloud(Canvas c, float cx, float cy, float s, int col) {
        p.setStyle(Paint.Style.FILL); p.setColor(col);
        c.drawCircle(cx - .17f * s, cy + .04f * s, .17f * s, p);
        c.drawCircle(cx + .02f * s, cy - .06f * s, .22f * s, p);
        c.drawCircle(cx + .2f * s, cy + .05f * s, .15f * s, p);
        rf.set(cx - .34f * s, cy + .04f * s, cx + .35f * s, cy + .22f * s);
        c.drawRoundRect(rf, .09f * s, .09f * s, p);
    }

    void rain(Canvas c, float cx, float cy, float s) {
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(.06f * s); p.setStrokeCap(Paint.Cap.ROUND); p.setColor(0xFF7CC4FF);
        for (int i = -1; i <= 1; i++) c.drawLine(cx + i * .2f * s, cy - .08f * s, cx + i * .2f * s - .06f * s, cy + .1f * s, p);
        p.setStyle(Paint.Style.FILL);
    }

    void dots(Canvas c, float cx, float cy, float s) {
        p.setStyle(Paint.Style.FILL); p.setColor(0xFFFFFFFF);
        for (int i = -1; i <= 1; i++) c.drawCircle(cx + i * .2f * s, cy, .045f * s, p);
    }

    void lines(Canvas c, float cx, float cy, float s, int col) {
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(.05f * s); p.setStrokeCap(Paint.Cap.ROUND); p.setColor(col);
        c.drawLine(cx - .3f * s, cy, cx + .3f * s, cy, p);
        c.drawLine(cx - .22f * s, cy + .12f * s, cx + .22f * s, cy + .12f * s, p);
        p.setStyle(Paint.Style.FILL);
    }

    void bolt(Canvas c, float cx, float cy, float s) {
        path.reset();
        path.moveTo(cx + .04f * s, cy - .02f * s);
        path.lineTo(cx - .1f * s, cy + .2f * s);
        path.lineTo(cx, cy + .2f * s);
        path.lineTo(cx - .05f * s, cy + .38f * s);
        path.lineTo(cx + .12f * s, cy + .12f * s);
        path.lineTo(cx + .02f * s, cy + .12f * s);
        path.close();
        p.setStyle(Paint.Style.FILL); p.setColor(0xFFFFD166);
        c.drawPath(path, p);
    }
}
