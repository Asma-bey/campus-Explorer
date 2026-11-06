package fr.cyu.campusexplorer.data;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Les lieux predefinis du campus, embarques dans l'application.
 *
 * Les 4 premiers sont ceux de la maquette Figma ; les 3 suivants completent la carte.
 * Coordonnees relevees dans OpenStreetMap (Nominatim et Overpass, 6 novembre 2026) :
 * pour en ajouter un, chercher le lieu sur openstreetmap.org, clic droit > "Afficher l'adresse".
 */
public final class Places {

    private static final List<Place> ALL = Collections.unmodifiableList(Arrays.asList(
            new Place(1, "Bibliothèque universitaire", Place.Category.LIBRARY, "Bibliothèque",
                    "Un espace dédié au travail, à la lecture et à l'étude.",
                    "La bibliothèque universitaire des Cerclades, place des Cerclades, à côté de la gare "
                            + "de Cergy-Préfecture. Salles de travail en groupe et places de lecture.",
                    49.0372263, 2.0808313),
            new Place(2, "Restaurant universitaire", Place.Category.FOOD, "Restauration",
                    "Une pause conviviale pour déjeuner entre deux cours.",
                    "Le restaurant universitaire du Crous, 5 avenue du Parc. Repas à tarif étudiant "
                            + "le midi, en semaine.",
                    49.0338245, 2.0731379),
            new Place(3, "Bâtiment d'enseignement", Place.Category.BUILDINGS, "Bâtiment",
                    "Salles de cours et espaces pédagogiques du campus.",
                    "Le site des Chênes, 33 boulevard du Port : amphithéâtres, salles de TD et salles "
                            + "informatiques.",
                    49.0393961, 2.0747129),
            new Place(4, "Maison des étudiants", Place.Category.SERVICES, "Service étudiant",
                    "Informations, accompagnement et vie associative.",
                    "La Maison des étudiants, boulevard du Port, juste au sud du site des Chênes. "
                            + "Accueil, associations étudiantes et informations sur la vie du campus.",
                    49.0387503, 2.0750257),
            new Place(5, "Site de Saint-Martin", Place.Category.BUILDINGS, "Bâtiment",
                    "Un autre site d'enseignement, près de la place Adolphe Chauvin.",
                    "Le site de Saint-Martin, 2 avenue François Mitterrand, à Pontoise.",
                    49.0426515, 2.0839126),
            new Place(6, "Site Bernard Hirsch", Place.Category.BUILDINGS, "Bâtiment",
                    "Le site de l'avenue Bernard Hirsch, avec l'INSPE.",
                    "Le site Bernard Hirsch, avenue Bernard Hirsch, au sud du parc François "
                            + "Mitterrand.",
                    49.0317703, 2.0780191),
            new Place(7, "Résidence Crous", Place.Category.SERVICES, "Service étudiant",
                    "Logement étudiant et accueil du Crous.",
                    "La résidence Crous Les Châteaux Saint-Sylvère, boulevard de l'Hautil. "
                            + "Logement et informations sur la vie étudiante.",
                    49.0307235, 2.0716912)
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
