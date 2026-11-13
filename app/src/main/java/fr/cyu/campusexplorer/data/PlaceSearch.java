package fr.cyu.campusexplorer.data;

import android.content.Context;
import android.location.Location;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import fr.cyu.campusexplorer.R;

/**
 * La recherche de lieux de la carte, tolerante :
 *  - sans accents ni majuscules : "chenes" trouve "Chênes" ;
 *  - mots dans n'importe quel ordre : "restaurant crous" = "crous restaurant" ;
 *  - debut de mot : "biblio" trouve "Bibliothèque" ;
 *  - fautes de frappe : "bibliotheqe" ou "restaurent" trouvent quand meme ;
 *  - mots-cles par categorie : "resto", "BU", "cafet", "logement"... (search_keywords_*).
 * Les resultats sont classes : nom qui commence par la recherche d'abord, puis le plus proche
 * de l'utilisateur si sa position est connue.
 */
public final class PlaceSearch {

    private final Map<Place, String> nameText = new HashMap<>();
    private final Map<Place, String[]> words = new HashMap<>();

    /**
     * Prepare le texte de chaque lieu une seule fois (nom, type, description, details,
     * mots-cles de la categorie), dans la langue du telephone.
     */
    public PlaceSearch(Context context) {
        for (Place p : Places.all()) {
            String name = simplify(context.getString(p.name));
            String all = name + " " + simplify(context.getString(p.shortCategory) + " "
                    + context.getString(p.description) + " " + context.getString(p.details) + " "
                    + context.getString(keywords(p.category)));
            nameText.put(p, name);
            words.put(p, all.split("[^a-z0-9]+"));
        }
    }

    private static int keywords(Place.Category category) {
        switch (category) {
            case LIBRARY:
                return R.string.search_keywords_library;
            case FOOD:
                return R.string.search_keywords_food;
            case SERVICES:
                return R.string.search_keywords_services;
            default:
                return R.string.search_keywords_buildings;
        }
    }

    /**
     * @param query    ce que l'utilisateur a tape (vide : tous les lieux de la categorie)
     * @param category "all", "library", "food"...
     * @param here     position de l'utilisateur pour classer par distance, ou null
     * @return les lieux qui correspondent, les meilleurs d'abord
     */
    public List<Place> search(String query, String category, Location here) {
        String[] queryWords = simplify(query).trim().split("[^a-z0-9]+");
        List<Place> result = new ArrayList<>();
        Map<Place, Integer> scores = new HashMap<>();
        for (Place p : Places.all()) {
            if (!category.equals("all") && !p.category.key.equals(category)) continue;
            int score = score(p, queryWords);
            if (score >= 0) {
                result.add(p);
                scores.put(p, score);
            }
        }
        Map<Place, Float> distances = new HashMap<>();
        if (here != null) {
            float[] d = new float[1];
            for (Place p : result) {
                Location.distanceBetween(here.getLatitude(), here.getLongitude(), p.lat, p.lng, d);
                distances.put(p, d[0]);
            }
        }
        // meilleur score d'abord ; a score egal, le plus proche, sinon l'ordre de la liste
        Collections.sort(result, (a, b) -> {
            int byScore = Integer.compare(scores.get(b), scores.get(a));
            if (byScore != 0) return byScore;
            if (here != null) return Float.compare(distances.get(a), distances.get(b));
            return Integer.compare(a.id, b.id);
        });
        return result;
    }

    /**
     * Chaque mot tape doit se retrouver dans le lieu, sinon le lieu est ecarte (-1).
     * Points par mot : 4 si un mot du NOM commence par lui, 3 si un autre mot du lieu commence
     * par lui (ou le contient), 2 pour une faute de frappe dans le nom, 1 ailleurs.
     */
    private int score(Place p, String[] queryWords) {
        int total = 0;
        String name = nameText.get(p);
        String[] nameWords = name.split("[^a-z0-9]+");
        for (String q : queryWords) {
            if (q.isEmpty()) continue;
            int best = -1;
            for (String w : words.get(p)) {
                int s;
                if (w.startsWith(q)) {
                    s = (" " + name).contains(" " + q) ? 4 : 3;
                } else if (q.length() >= 3 && w.contains(q)) {
                    s = 3;
                } else if (typo(q, w)) {
                    s = 1;
                    for (String n : nameWords) if (n.equals(w)) s = 2;
                } else {
                    continue;
                }
                best = Math.max(best, s);
            }
            if (best < 0) return -1;
            total += best;
        }
        return total;
    }

    /**
     * Faute de frappe : la distance de Levenshtein (nombre de lettres a ajouter, retirer ou
     * changer) est d'au plus 1 pour un mot de 4 a 6 lettres, 2 au-dela. On compare au mot entier
     * et a son debut de meme longueur, pour un mot encore en cours de frappe.
     */
    private static boolean typo(String q, String w) {
        if (q.length() < 4) return false;
        int max = q.length() >= 7 ? 2 : 1;
        if (levenshtein(q, w) <= max) return true;
        return w.length() > q.length() && levenshtein(q, w.substring(0, q.length())) <= max;
    }

    private static int levenshtein(String a, String b) {
        int[] prev = new int[b.length() + 1];
        int[] cur = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) prev[j] = j;
        for (int i = 1; i <= a.length(); i++) {
            cur[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int change = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                cur[j] = Math.min(Math.min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + change);
            }
            int[] t = prev;
            prev = cur;
            cur = t;
        }
        return prev[b.length()];
    }

    /** minuscules et sans accents : NFD separe "ê" en "e" + accent, puis on retire les accents */
    public static String simplify(String text) {
        return Normalizer.normalize(text, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT);
    }
}
