package fr.cyu.campusexplorer.navigation;

import android.content.Context;

import fr.cyu.campusexplorer.R;

/**
 * Transforme une manoeuvre OSRM en phrase ("Tournez a gauche sur Rue X") et en angle pour la
 * fleche du panneau. Le serveur ne renvoie que des mots-cles anglais (type, modifier) : les
 * phrases viennent de nos strings.xml, donc en francais ou en anglais selon le telephone.
 */
public final class Instructions {

    private Instructions() {
    }

    /** la phrase a afficher pour cette manoeuvre */
    public static String text(Context context, Route.Step step) {
        if ("arrive".equals(step.type)) return context.getString(R.string.nav_arrive);
        String action;
        if ("depart".equals(step.type)) {
            action = context.getString(R.string.nav_head, compass(context, step.bearingAfter));
        } else if (step.type.startsWith("roundabout") || "rotary".equals(step.type)) {
            action = context.getString(R.string.nav_roundabout);
        } else {
            action = context.getString(turn(step.modifier));
        }
        return step.name.isEmpty() ? action : context.getString(R.string.nav_onto, action, step.name);
    }

    private static int turn(String modifier) {
        switch (modifier) {
            case "slight left":
                return R.string.nav_slight_left;
            case "left":
                return R.string.nav_left;
            case "sharp left":
                return R.string.nav_sharp_left;
            case "slight right":
                return R.string.nav_slight_right;
            case "right":
                return R.string.nav_right;
            case "sharp right":
                return R.string.nav_sharp_right;
            case "uturn":
                return R.string.nav_uturn;
            default:
                return R.string.nav_straight;
        }
    }

    /**
     * Angle de la fleche, en degres dans le sens des aiguilles d'une montre (0 = tout droit).
     * Pour le depart, la carte est orientee nord en haut : la fleche montre directement le cap.
     */
    public static float arrowAngle(Route.Step step) {
        if ("depart".equals(step.type)) return step.bearingAfter;
        switch (step.modifier) {
            case "slight left":
                return -45;
            case "left":
                return -90;
            case "sharp left":
                return -135;
            case "slight right":
                return 45;
            case "right":
                return 90;
            case "sharp right":
                return 135;
            case "uturn":
                return 180;
            default:
                return 0;
        }
    }

    /** "le nord-est", "l'ouest"... : les 8 directions, une tous les 45 degres */
    public static String compass(Context context, float degrees) {
        String[] names = context.getResources().getStringArray(R.array.nav_compass);
        int index = (int) Math.round(((degrees % 360) + 360) % 360 / 45.0) % 8;
        return names[index];
    }
}
