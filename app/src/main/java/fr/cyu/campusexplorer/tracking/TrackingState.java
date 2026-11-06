package fr.cyu.campusexplorer.tracking;

import java.util.ArrayList;
import java.util.List;

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

    private TrackingState() {
    }

    static synchronized void start(long elapsed, long wallClock) {
        running = true;
        startElapsed = elapsed;
        startWallClock = wallClock;
        distanceMeters = 0;
        points.clear();
    }

    static synchronized void addPoint(double lat, double lng, double addedMeters) {
        points.add(new double[]{lat, lng});
        distanceMeters += addedMeters;
    }

    static synchronized void stop() {
        running = false;
        points.clear();
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
