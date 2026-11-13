package fr.cyu.campusexplorer.navigation;

import android.location.Location;

import java.util.ArrayList;
import java.util.List;

/**
 * Le guidage vers un lieu : a chaque nouvelle position, calcule ou en est l'utilisateur sur
 * l'itineraire (distance restante, prochaine manoeuvre, ecart a la ligne).
 *
 * Deux modes :
 *  - avec un itineraire (Route) : on suit les chemins pietons calcules par le serveur ;
 *  - "a vol d'oiseau" (route == null) : pas de reseau ou serveur en panne, on donne seulement
 *    la direction et la distance en ligne droite jusqu'au lieu.
 */
public final class Guidance {

    /** a moins de 20 m du lieu, on considere que l'utilisateur est arrive */
    public static final double ARRIVED_METERS = 20;
    /** a plus de 40 m de la ligne, l'utilisateur a quitte l'itineraire : on en recalcule un */
    public static final double OFF_ROUTE_METERS = 40;

    /** Ce que l'ecran affiche apres une position. */
    public static final class Progress {
        /** distance restante en suivant l'itineraire (ou en ligne droite) */
        public double remainingMeters;
        /** distance entre l'utilisateur et la ligne de l'itineraire (0 a vol d'oiseau) */
        public double offRouteMeters;
        /** prochaine manoeuvre ; null a vol d'oiseau */
        public Route.Step next;
        /** distance jusqu'a cette manoeuvre */
        public double toNextMeters;
        /** a vol d'oiseau : direction du lieu, en degres (0 = nord) */
        public float bearing;
        /** la partie de la ligne qui reste a parcourir, a dessiner sur la carte */
        public List<double[]> remaining;
        public boolean arrived;
    }

    private final double destLat;
    private final double destLng;
    /** la ligne a suivre ; null a vol d'oiseau */
    private final List<double[]> points;
    private final List<Route.Step> steps;
    /** cumulated[k] = distance du depart jusqu'au point k, en suivant la ligne */
    private final double[] cumulated;
    /** segment (points[i] -> points[i+1]) sur lequel l'utilisateur etait a la position precedente */
    private int segment = 0;

    /**
     * @param route itineraire du serveur, ou null pour le mode a vol d'oiseau
     */
    public Guidance(Route route, double destLat, double destLng) {
        this.destLat = destLat;
        this.destLng = destLng;
        if (route == null) {
            points = null;
            steps = null;
            cumulated = null;
            return;
        }
        points = new ArrayList<>(route.points);
        steps = route.steps;
        // le serveur arrete la ligne sur le chemin le plus proche du lieu : on ajoute les
        // derniers metres jusqu'au marqueur, pour que la ligne touche bien le lieu
        double[] end = points.get(points.size() - 1);
        if (distance(end[0], end[1], destLat, destLng) > 5) points.add(new double[]{destLat, destLng});

        cumulated = new double[points.size()];
        for (int k = 1; k < points.size(); k++) {
            double[] a = points.get(k - 1);
            double[] b = points.get(k);
            cumulated[k] = cumulated[k - 1] + distance(a[0], a[1], b[0], b[1]);
        }
    }

    /** true si on guide en ligne droite, sans itineraire */
    public boolean isDirect() {
        return points == null;
    }

    /** la ligne complete, pour la dessiner au depart */
    public List<double[]> points() {
        return points;
    }

    /**
     * Situe l'utilisateur sur l'itineraire.
     *
     * Principe : on projette sa position sur chaque segment de la ligne et on garde le segment
     * le plus proche. La distance deja parcourue est celle du depart jusqu'au point projete ;
     * la distance restante est la longueur totale moins celle-la.
     */
    public Progress update(double lat, double lng) {
        Progress p = new Progress();
        double straight = distance(lat, lng, destLat, destLng);

        if (points == null) {
            // a vol d'oiseau : une ligne droite de l'utilisateur au lieu
            p.remainingMeters = straight;
            p.bearing = bearing(lat, lng, destLat, destLng);
            p.remaining = new ArrayList<>();
            p.remaining.add(new double[]{lat, lng});
            p.remaining.add(new double[]{destLat, destLng});
            p.arrived = straight < ARRIVED_METERS;
            return p;
        }

        // on cherche a partir du segment precedent (un peu avant, a cause du bruit du GPS) :
        // si le trajet repasse par le meme chemin, on ne saute pas en arriere
        Projection best = closest(lat, lng, Math.max(0, segment - 2));
        if (best.meters > OFF_ROUTE_METERS && segment > 0) {
            // trop loin : l'utilisateur est peut-etre revenu en arriere, on cherche partout
            Projection anywhere = closest(lat, lng, 0);
            if (anywhere.meters < best.meters) best = anywhere;
        }
        segment = best.segment;

        double along = cumulated[segment] + best.t * (cumulated[segment + 1] - cumulated[segment]);
        p.remainingMeters = cumulated[cumulated.length - 1] - along;
        p.offRouteMeters = best.meters;
        p.arrived = straight < ARRIVED_METERS || p.remainingMeters < ARRIVED_METERS;

        // prochaine manoeuvre : la premiere dont le point est apres le debut du segment courant
        for (Route.Step step : steps) {
            if (step.pointIndex > segment) {
                p.next = step;
                p.toNextMeters = Math.max(0, cumulated[step.pointIndex] - along);
                break;
            }
        }
        if (p.next == null) {
            // derniere ligne droite (les derniers metres ajoutes jusqu'au marqueur)
            p.next = steps.get(steps.size() - 1);
            p.toNextMeters = p.remainingMeters;
        }

        p.remaining = new ArrayList<>();
        p.remaining.add(new double[]{best.lat, best.lng});
        p.remaining.addAll(points.subList(segment + 1, points.size()));
        return p;
    }

    /** resultat d'une projection : point le plus proche de la ligne */
    private static final class Projection {
        int segment;
        /** position sur le segment : 0 au debut, 1 a la fin */
        double t;
        double lat;
        double lng;
        double meters = Double.MAX_VALUE;
    }

    /**
     * Projection orthogonale sur chaque segment a partir de "from".
     *
     * A l'echelle d'un campus (quelques km), on peut traiter la Terre comme plate autour de
     * l'utilisateur : 1 degre de latitude ~ 110,5 km, 1 degre de longitude ~ 111,3 km x cos(lat).
     * Les coordonnees deviennent des metres (x vers l'est, y vers le nord), l'utilisateur a (0, 0).
     */
    private Projection closest(double lat, double lng, int from) {
        double kx = 111_320 * Math.cos(Math.toRadians(lat));
        double ky = 110_540;
        Projection best = new Projection();
        for (int i = from; i < points.size() - 1; i++) {
            double[] a = points.get(i);
            double[] b = points.get(i + 1);
            double ax = (a[1] - lng) * kx, ay = (a[0] - lat) * ky;
            double dx = (b[1] - a[1]) * kx, dy = (b[0] - a[0]) * ky;
            double len2 = dx * dx + dy * dy;
            // t = position du pied de la perpendiculaire, ramenee dans [0, 1] (dans le segment)
            double t = len2 == 0 ? 0 : Math.max(0, Math.min(1, -(ax * dx + ay * dy) / len2));
            double px = ax + t * dx, py = ay + t * dy;
            double meters = Math.sqrt(px * px + py * py);
            if (meters < best.meters) {
                best.meters = meters;
                best.segment = i;
                best.t = t;
                best.lat = a[0] + t * (b[0] - a[0]);
                best.lng = a[1] + t * (b[1] - a[1]);
            }
        }
        return best;
    }

    /** distance en metres sur l'ellipsoide WGS84 (la meme que Location.distanceTo du suivi) */
    private static double distance(double lat1, double lng1, double lat2, double lng2) {
        float[] result = new float[1];
        Location.distanceBetween(lat1, lng1, lat2, lng2, result);
        return result[0];
    }

    /** cap initial de 1 vers 2, en degres de 0 a 360 */
    private static float bearing(double lat1, double lng1, double lat2, double lng2) {
        float[] result = new float[2];
        Location.distanceBetween(lat1, lng1, lat2, lng2, result);
        return (result[1] + 360) % 360;
    }
}
