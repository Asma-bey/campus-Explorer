package fr.cyu.campusexplorer;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import java.text.DateFormat;
import java.util.Date;
import java.util.List;

import fr.cyu.campusexplorer.data.Place;
import fr.cyu.campusexplorer.data.Places;
import fr.cyu.campusexplorer.data.SavedPlaces;
import fr.cyu.campusexplorer.data.SessionDb;
import fr.cyu.campusexplorer.tracking.TrackingState;

/**
 * Ecran 3 : lieux enregistres (SharedPreferences) et derniers parcours (SQLite).
 * Tout est relu dans onResume : en revenant de la carte, un lieu ajoute ou un parcours
 * termine apparait tout de suite.
 */
public class SavedActivity extends BaseActivity {

    private SavedPlaces savedPlaces;
    private SessionDb sessionDb;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_saved);
        applySystemBarInsets(findViewById(R.id.root), findViewById(R.id.bottom_nav));
        setupBottomNav(Tab.SAVED);

        savedPlaces = new SavedPlaces(this);
        sessionDb = new SessionDb(this);

        findViewById(R.id.btn_empty_explore).setOnClickListener(v -> openTab(MapActivity.class));
        findViewById(R.id.active_session).setOnClickListener(v -> openTab(MapActivity.class));
    }

    @Override
    protected void onResume() {
        super.onResume();
        showSavedPlaces();
        showHistory();
        findViewById(R.id.active_session).setVisibility(TrackingState.isRunning() ? View.VISIBLE : View.GONE);
    }

    /** on ferme la connexion SQLite ouverte par l'ecran : liberer les ressources */
    @Override
    protected void onDestroy() {
        sessionDb.close();
        super.onDestroy();
    }

    /**
     * Reconstruit la liste des lieux enregistres : on vide le conteneur, puis on gonfle un
     * item_saved_place.xml par lieu (une liste courte : un LinearLayout suffit, sans RecyclerView).
     */
    private void showSavedPlaces() {
        List<Place> places = Places.byIds(savedPlaces.ids());
        LinearLayout list = findViewById(R.id.saved_list);
        list.removeAllViews();

        ((TextView) findViewById(R.id.saved_count)).setText(
                getResources().getQuantityString(R.plurals.saved_count, places.size(), places.size()));
        findViewById(R.id.empty_state).setVisibility(places.isEmpty() ? View.VISIBLE : View.GONE);

        // fonds des pastilles en alternance (.saved-place-icon, .c1, .c2)
        int[] colors = {R.color.saved_yellow, R.color.saved_green, R.color.saved_cyan};
        for (int i = 0; i < places.size(); i++) {
            Place place = places.get(i);
            View item = getLayoutInflater().inflate(R.layout.item_saved_place, list, false);
            item.findViewById(R.id.saved_icon_box).setBackgroundTintList(
                    ColorStateList.valueOf(ContextCompat.getColor(this, colors[i % 3])));
            ((TextView) item.findViewById(R.id.saved_category)).setText(place.shortCategory);
            ((TextView) item.findViewById(R.id.saved_name)).setText(place.name);

            // "Voir sur la carte" : l'identifiant du lieu part en extra de l'Intent
            item.findViewById(R.id.saved_open).setOnClickListener(v -> {
                Intent intent = new Intent(this, MapActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                intent.putExtra(MapActivity.EXTRA_PLACE_ID, place.id);
                startActivity(intent);
                noTransition();
            });

            View remove = item.findViewById(R.id.saved_remove);
            remove.setContentDescription(getString(R.string.saved_remove, getString(place.name)));
            remove.setOnClickListener(v -> {
                savedPlaces.remove(place.id);
                showSavedPlaces();
            });
            list.addView(item);
        }
    }

    /** Les 5 derniers parcours lus dans SQLite : date, duree "min:s" et distance en km. */
    private void showHistory() {
        List<SessionDb.Session> sessions = sessionDb.latest(5);
        LinearLayout list = findViewById(R.id.history_list);
        list.removeAllViews();
        findViewById(R.id.history_empty).setVisibility(sessions.isEmpty() ? View.VISIBLE : View.GONE);

        // format de date de la langue du telephone : "6 novembre 2026 18:02" ou "November 6, 2026 6:02 PM"
        DateFormat format = DateFormat.getDateTimeInstance(DateFormat.LONG, DateFormat.SHORT);
        for (SessionDb.Session session : sessions) {
            View item = getLayoutInflater().inflate(R.layout.item_session, list, false);
            ((TextView) item.findViewById(R.id.session_date)).setText(format.format(new Date(session.startTime)));
            ((TextView) item.findViewById(R.id.session_duration)).setText(getString(R.string.session_duration,
                    session.durationSeconds / 60, session.durationSeconds % 60));
            ((TextView) item.findViewById(R.id.session_distance)).setText(
                    getString(R.string.tracking_km, session.distanceMeters / 1000));
            list.addView(item);
        }
    }
}
