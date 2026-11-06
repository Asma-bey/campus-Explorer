package fr.cyu.campusexplorer.data;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import fr.cyu.campusexplorer.R;

/**
 * Les lieux predefinis du campus, embarques dans l'application.
 *
 * Les 4 premiers sont ceux de la maquette Figma ; les 3 suivants completent la carte.
 * Les textes sont dans res/values/places.xml (anglais) et res/values-fr/places.xml (francais).
 * Coordonnees relevees dans OpenStreetMap (Nominatim et Overpass, 6 novembre 2026) :
 * pour en ajouter un, chercher le lieu sur openstreetmap.org, clic droit > "Afficher l'adresse".
 */
public final class Places {

    private static final List<Place> ALL = Collections.unmodifiableList(Arrays.asList(
            new Place(1, R.string.place1_name, Place.Category.LIBRARY, R.string.type_library,
                    R.string.place1_description, R.string.place1_details, 49.0372263, 2.0808313),
            new Place(2, R.string.place2_name, Place.Category.FOOD, R.string.type_food,
                    R.string.place2_description, R.string.place2_details, 49.0338245, 2.0731379),
            new Place(3, R.string.place3_name, Place.Category.BUILDINGS, R.string.type_building,
                    R.string.place3_description, R.string.place3_details, 49.0393961, 2.0747129),
            new Place(4, R.string.place4_name, Place.Category.SERVICES, R.string.type_service,
                    R.string.place4_description, R.string.place4_details, 49.0387503, 2.0750257),
            new Place(5, R.string.place5_name, Place.Category.BUILDINGS, R.string.type_building,
                    R.string.place5_description, R.string.place5_details, 49.0426515, 2.0839126),
            new Place(6, R.string.place6_name, Place.Category.BUILDINGS, R.string.type_building,
                    R.string.place6_description, R.string.place6_details, 49.0317703, 2.0780191),
            new Place(7, R.string.place7_name, Place.Category.SERVICES, R.string.type_service,
                    R.string.place7_description, R.string.place7_details, 49.0307235, 2.0716912)
    ));

    private Places() {
    }

    public static List<Place> all() {
        return ALL;
    }

    /** null si aucun lieu n'a cet identifiant (par exemple un extra d'Intent invalide) */
    public static Place byId(int id) {
        for (Place p : ALL) {
            if (p.id == id) return p;
        }
        return null;
    }

    public static List<Place> byIds(List<Integer> ids) {
        List<Place> result = new ArrayList<>();
        for (int id : ids) {
            Place p = byId(id);
            if (p != null) result.add(p);
        }
        return result;
    }
}
