package fr.cyu.campusexplorer.data;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import fr.cyu.campusexplorer.R;

/**
 * Les lieux predefinis du campus, embarques dans l'application.
 *
 * Les 4 premiers sont ceux de la maquette Figma ; les suivants completent la carte avec les
 * autres sites de CY, a Cergy-Pontoise (5 a 9) puis en Ile-de-France (10 a 14).
 * Les textes sont dans res/values/places.xml (anglais) et res/values-fr/places.xml (francais).
 * Coordonnees relevees dans OpenStreetMap (Nominatim et Overpass, 6 et 12 novembre 2026) :
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
                    R.string.place7_description, R.string.place7_details, 49.0307235, 2.0716912),
            // autres sites de Cergy-Pontoise
            new Place(8, R.string.place8_name, Place.Category.BUILDINGS, R.string.type_building,
                    R.string.place8_description, R.string.place8_details, 49.0168500, 2.0745100),
            new Place(9, R.string.place9_name, Place.Category.BUILDINGS, R.string.type_building,
                    R.string.place9_description, R.string.place9_details, 49.0350200, 2.0695630),
            // sites hors de Cergy-Pontoise : hors de la vue d'ensemble, trouves par la recherche
            new Place(10, R.string.place10_name, Place.Category.BUILDINGS, R.string.type_building,
                    R.string.place10_description, R.string.place10_details, 48.8997520, 2.0698854),
            new Place(11, R.string.place11_name, Place.Category.BUILDINGS, R.string.type_building,
                    R.string.place11_description, R.string.place11_details, 48.8007918, 2.1273796),
            new Place(12, R.string.place12_name, Place.Category.BUILDINGS, R.string.type_building,
                    R.string.place12_description, R.string.place12_details, 48.9492854, 2.2332909),
            new Place(13, R.string.place13_name, Place.Category.BUILDINGS, R.string.type_building,
                    R.string.place13_description, R.string.place13_details, 48.9354933, 2.3029980),
            new Place(14, R.string.place14_name, Place.Category.BUILDINGS, R.string.type_building,
                    R.string.place14_description, R.string.place14_details, 48.9748156, 2.3777036)
    ));

    private Places() {
    }

    /** @return les 14 lieux, dans l'ordre des identifiants ; liste non modifiable */
    public static List<Place> all() {
        return ALL;
    }

    /**
     * Recherche lineaire : 14 lieux, une boucle suffit (une HashMap n'apporterait rien ici).
     *
     * @param id identifiant du lieu (1 a 14)
     * @return le lieu, ou null si aucun lieu n'a cet identifiant (par exemple un extra d'Intent invalide)
     */
    public static Place byId(int id) {
        for (Place p : ALL) {
            if (p.id == id) return p;
        }
        return null;
    }

    /**
     * @param ids identifiants dans l'ordre voulu (ex. l'ordre d'enregistrement de SavedPlaces)
     * @return les lieux correspondants, dans le meme ordre ; les identifiants inconnus sont ignores
     */
    public static List<Place> byIds(List<Integer> ids) {
        List<Place> result = new ArrayList<>();
        for (int id : ids) {
            Place p = byId(id);
            if (p != null) result.add(p);
        }
        return result;
    }
}
