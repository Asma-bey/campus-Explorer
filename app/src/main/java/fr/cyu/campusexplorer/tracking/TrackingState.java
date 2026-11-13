package fr.cyu.campusexplorer.tracking;

import android.content.Context;

import java.util.ArrayList;
import java.util.List;

import fr.cyu.campusexplorer.R;

/**
 * L'etat de la session en cours, partage entre le service et les ecrans.
 *
 * Le service et les Activities tournent dans le meme processus : un objet statique suffit pour
 * que la carte, en revenant au premier plan (onResume), retrouve le chrono, la distance et le
 * trace sans attendre la prochaine position. Seul TrackingService ecrit ici.
 */
public final class TrackingState {

    private static boolean running;
    /** instant du depart, horloge SystemClock.elapsedRealtime() (celle du Chronometer) */
    private static long startElapsed;
    /** instant du depart, en heure murale, pour l'historique */
    private static long startWallClock;
    private static double distanceMeters;
    /** trace du parcours : des paires {latitude, longitude} */
    private static final List<double[]> points = new ArrayList<>();
    /** derniere position recue, meme si elle n'a pas ajoute de point au trace (point bleu) */
    private static double[] position;

    private TrackingState() {
    }

    /*
     * Toutes les methodes sont synchronized : un seul thread a la fois lit ou ecrit l'etat.
     * Aujourd'hui le service et les ecrans tournent tous les deux sur le thread principal, mais
     * cette protection evite une lecture a moitie ecrite si une ecriture passait un jour sur un
     * autre thread. Les methodes d'ecriture n'ont pas de "public" : seules les classes du paquet
     * tracking (TrackingService) peuvent modifier l'etat.
     */

    /**
     * Nouvelle session : remet la distance a 0 et vide le trace.
     *
     * @param elapsed   SystemClock.elapsedRealtime() au depart (pour la duree et le Chronometer)
     * @param wallClock System.currentTimeMillis() au depart (pour la date dans l'historique)
     */
    static synchronized void start(long elapsed, long wallClock) {
        running = true;
        startElapsed = elapsed;
        startWallClock = wallClock;
        distanceMeters = 0;
        points.clear();
        position = null;
    }

    /**
     * Ajoute un point au trace et sa longueur a la distance totale.
     *
     * @param addedMeters longueur du segment depuis le point precedent (0 pour le premier point)
     */
    static synchronized void addPoint(double lat, double lng, double addedMeters) {
        points.add(new double[]{lat, lng});
        distanceMeters += addedMeters;
    }

    static synchronized void setPosition(double lat, double lng) {
        position = new double[]{lat, lng};
    }

    /** Fin de session : l'etat est vide (le resume a deja ete enregistre dans SQLite). */
    static synchronized void stop() {
        running = false;
        points.clear();
        position = null;
    }

    /** {latitude, longitude}, ou null tant que le GPS n'a rien donne */
    public static synchronized double[] position() {
        return position == null ? null : position.clone();
    }

    /** "350 m" sous le kilometre, puis "1,25 km" (virgule en francais, point en anglais) */
    public static String formatDistance(Context context, double meters) {
        return meters < 1000
                ? context.getString(R.string.tracking_m, (int) meters)
                : context.getString(R.string.tracking_km, meters / 1000);
    }

    public static synchronized boolean isRunning() {
        return running;
    }

    public static synchronized long startElapsed() {
        return startElapsed;
    }

    static synchronized long startWallClock() {
        return startWallClock;
    }

    public static synchronized double distanceMeters() {
        return distanceMeters;
    }

    /** une copie, pour que l'appelant ne voie pas la liste changer pendant qu'il la lit */
    public static synchronized List<double[]> points() {
        return new ArrayList<>(points);
    }
}
