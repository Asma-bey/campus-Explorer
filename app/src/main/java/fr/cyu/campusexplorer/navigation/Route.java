package fr.cyu.campusexplorer.navigation;

import java.util.Collections;
import java.util.List;

/**
 * Un itineraire a pied, tel que le serveur de routage l'a calcule : la ligne a suivre et les
 * manoeuvres (tourner a gauche, continuer tout droit...). Objet immuable.
 */
public final class Route {

    /** Une manoeuvre : ce qu'il faut faire en arrivant au point points[pointIndex]. */
    public static final class Step {
        /** type OSRM : "depart", "turn", "new name", "continue", "roundabout", "arrive"... */
        public final String type;
        /** sens du virage : "left", "slight right", "straight", "uturn"... ("" si aucun) */
        public final String modifier;
        /** nom de la rue ou du chemin pris apres la manoeuvre ("" si le chemin n'a pas de nom) */
        public final String name;
        /** direction prise apres la manoeuvre, en degres (0 = nord, 90 = est) */
        public final int bearingAfter;
        /** indice, dans Route.points, du point ou a lieu la manoeuvre */
        public final int pointIndex;

        Step(String type, String modifier, String name, int bearingAfter, int pointIndex) {
            this.type = type;
            this.modifier = modifier;
            this.name = name;
            this.bearingAfter = bearingAfter;
            this.pointIndex = pointIndex;
        }
    }

    /** la ligne a suivre : des paires {latitude, longitude}, du depart a l'arrivee */
    public final List<double[]> points;
    /** les manoeuvres, dans l'ordre du trajet */
    public final List<Step> steps;

    Route(List<double[]> points, List<Step> steps) {
        this.points = Collections.unmodifiableList(points);
        this.steps = Collections.unmodifiableList(steps);
    }
}
