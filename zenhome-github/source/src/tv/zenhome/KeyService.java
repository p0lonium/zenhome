package tv.zenhome;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

/**
 * Boutons d'applis de la telecommande (Netflix, YouTube, Prime Video) sur les TV TCL.
 * Ces touches envoient des codes propres a TCL, que seul l'accueil Google TV traitait :
 * une fois cet accueil desactive par le kit, elles ne faisaient plus rien.
 * Le kit n'active ce service que dans ce cas. Seules ces touches sont interceptees,
 * toutes les autres passent sans modification.
 */
public class KeyService extends AccessibilityService {

    /** Codes de touche TCL -> appli a ouvrir. */
    static final int[] TCL_KEYS = {4062, 4065, 4099};
    static final String[] TCL_APPS = {"com.netflix.ninja", "com.google.android.youtube.tv", "com.amazon.amazonvideo.livingroom"};

    static boolean isTcl() {
        String b = (Build.BRAND + " " + Build.MANUFACTURER).toUpperCase(java.util.Locale.ROOT);
        return b.contains("TCL");
    }

    /** Appli associee a ce code de touche sur cette TV, ou null. */
    static String appForKey(int code) {
        if (!isTcl()) return null;
        for (int i = 0; i < TCL_KEYS.length; i++) if (TCL_KEYS[i] == code) return TCL_APPS[i];
        return null;
    }

    static Intent launchIntent(PackageManager pm, String pkg) {
        Intent i = pm.getLeanbackLaunchIntentForPackage(pkg);
        if (i == null) i = pm.getLaunchIntentForPackage(pkg);
        return i;
    }

    @Override
    protected boolean onKeyEvent(KeyEvent e) {
        String pkg = appForKey(e.getKeyCode());
        if (pkg == null) return false;
        Intent i = launchIntent(getPackageManager(), pkg);
        if (i == null) return false;                       // appli absente : la touche suit son chemin normal
        if (e.getAction() == KeyEvent.ACTION_UP) {
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try { startActivity(i); } catch (Exception ignored) { }
        }
        return true;
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) { }
    @Override public void onInterrupt() { }
}
