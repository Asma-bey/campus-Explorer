package fr.cyu.campusexplorer.data;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.List;

/**
 * Les lieux enregistres par l'utilisateur, gardes dans les SharedPreferences
 * (comme le nom et l'email au TP2) : quelques entiers ne justifient pas une base SQLite.
 *
 * Stockage : une chaine "3,1,4" pour garder l'ordre d'ajout (un Set ne le garde pas).
 */
public final class SavedPlaces {

    private static final String PREFS = "campus_explorer";
    private static final String KEY_IDS = "saved_place_ids";

    private final SharedPreferences prefs;

    /**
     * Ouvre le fichier de preferences "campus_explorer" (cree au premier ecrit), stocke en XML dans
     * /data/data/fr.cyu.campusexplorer/shared_prefs/. MODE_PRIVATE : lisible par cette appli seule.
     */
    public SavedPlaces(Context context) {
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /**
     * Relit la chaine "3,1,4" et la decoupe.
     *
     * @return les identifiants enregistres, dans l'ordre d'ajout ; liste vide si rien n'est enregistre
     *         (valeur par defaut "")
     */
    public List<Integer> ids() {
        List<Integer> ids = new ArrayList<>();
        String stored = prefs.getString(KEY_IDS, "");
        for (String part : stored.split(",")) {
            if (part.isEmpty()) continue;
            try {
                ids.add(Integer.parseInt(part));
            } catch (NumberFormatException ignored) {
                // valeur abimee : on l'ignore plutot que de planter
            }
        }
        return ids;
    }

    /** @return true si ce lieu est enregistre (bouton jaune sur la fiche de la carte) */
    public boolean contains(int placeId) {
        return ids().contains(placeId);
    }

    /** Ajoute le lieu s'il n'y est pas, le retire sinon. Renvoie true si le lieu est maintenant enregistre. */
    public boolean toggle(int placeId) {
        List<Integer> ids = ids();
        boolean nowSaved;
        if (ids.contains(placeId)) {
            ids.remove(Integer.valueOf(placeId));
            nowSaved = false;
        } else {
            ids.add(placeId);
            nowSaved = true;
        }
        save(ids);
        return nowSaved;
    }

    /** Retire le lieu (icone corbeille de l'ecran Enregistres) ; sans effet s'il n'y est pas. */
    public void remove(int placeId) {
        List<Integer> ids = ids();
        ids.remove(Integer.valueOf(placeId));
        save(ids);
    }

    /**
     * Reecrit toute la liste. apply() ecrit le fichier en arriere-plan sans bloquer l'ecran ;
     * commit() ferait la meme chose de facon synchrone et renverrait un booleen.
     */
    private void save(List<Integer> ids) {
        StringBuilder sb = new StringBuilder();
        for (int id : ids) {
            if (sb.length() > 0) sb.append(',');
            sb.append(id);
        }
        prefs.edit().putString(KEY_IDS, sb.toString()).apply();
    }
}
