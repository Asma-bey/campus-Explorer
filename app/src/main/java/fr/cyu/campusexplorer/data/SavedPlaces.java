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

    public SavedPlaces(Context context) {
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

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

    public void remove(int placeId) {
        List<Integer> ids = ids();
        ids.remove(Integer.valueOf(placeId));
        save(ids);
    }

    private void save(List<Integer> ids) {
        StringBuilder sb = new StringBuilder();
        for (int id : ids) {
            if (sb.length() > 0) sb.append(',');
            sb.append(id);
        }
        prefs.edit().putString(KEY_IDS, sb.toString()).apply();
    }
}
