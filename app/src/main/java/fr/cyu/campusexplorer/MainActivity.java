package fr.cyu.campusexplorer;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.StringRes;
import androidx.core.content.ContextCompat;

import java.util.List;

import fr.cyu.campusexplorer.data.Place;
import fr.cyu.campusexplorer.data.Places;

/**
 * Ecran 1 : Accueil. Point d'entree de l'application (LAUNCHER dans le manifeste).
 * Ouvre la carte (avec ou sans filtre, ou centree sur un lieu) et les lieux enregistres.
 */
public class MainActivity extends BaseActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        applySystemBarInsets(findViewById(R.id.root), findViewById(R.id.bottom_nav));
        setupBottomNav(Tab.HOME);

        findViewById(R.id.btn_explore).setOnClickListener(v -> openMap(null, -1));
        findViewById(R.id.btn_see_all).setOnClickListener(v -> openMap(null, -1));
        findViewById(R.id.btn_saved).setOnClickListener(v -> openTab(SavedActivity.class));

        fillCategories();
        fillDiscovery();
    }

    /** Les 4 cartes "Que recherchez-vous ?" : un clic ouvre la carte filtree sur la categorie. */
    private void fillCategories() {
        GridLayout grid = findViewById(R.id.category_grid);
        LayoutInflater inflater = getLayoutInflater();
        addCategory(grid, inflater, Place.Category.BUILDINGS, R.string.cat_buildings, 0);
        addCategory(grid, inflater, Place.Category.LIBRARY, R.string.cat_library, 1);
        addCategory(grid, inflater, Place.Category.FOOD, R.string.cat_food, 2);
        addCategory(grid, inflater, Place.Category.SERVICES, R.string.cat_services_long, 3);
    }

    private void addCategory(GridLayout grid, LayoutInflater inflater, Place.Category category,
                             @StringRes int label, int index) {
        View card = inflater.inflate(R.layout.item_category, grid, false);
        ((TextView) card.findViewById(R.id.category_label)).setText(label);
        ((ImageView) card.findViewById(R.id.category_icon)).setImageResource(category.icon);
        // la maquette alterne cyan, jaune, vert, cyan pour les pastilles
        int[] soft = {R.color.cyan_soft, R.color.yellow_soft, R.color.green_soft, R.color.cyan_soft};
        card.findViewById(R.id.category_circle).setBackgroundTintList(
                ColorStateList.valueOf(ContextCompat.getColor(this, soft[index])));

        // 2 colonnes de largeur egale, 9dp d'ecart (gap: 9px)
        int gap = Math.round(9 * getResources().getDisplayMetrics().density);
        GridLayout.LayoutParams params = new GridLayout.LayoutParams(
                GridLayout.spec(index / 2), GridLayout.spec(index % 2, 1f));
        params.width = 0;
        params.setMargins(index % 2 == 0 ? 0 : gap / 2, index < 2 ? 0 : gap,
                index % 2 == 0 ? gap / 2 : 0, 0);
        card.setLayoutParams(params);

        card.setOnClickListener(v -> openMap(category, -1));
        grid.addView(card);
    }

    /** Les 3 premiers lieux, numerotes 01, 02, 03 sur fond jaune, vert, cyan. */
    private void fillDiscovery() {
        LinearLayout list = findViewById(R.id.discovery_list);
        List<Place> places = Places.all();
        int[] colors = {R.color.yellow, R.color.number_green, R.color.number_cyan};
        for (int i = 0; i < 3 && i < places.size(); i++) {
            Place place = places.get(i);
            View card = getLayoutInflater().inflate(R.layout.item_discovery, list, false);
            TextView number = card.findViewById(R.id.place_number);
            number.setText(getString(R.string.place_number, i + 1));
            number.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(this, colors[i])));
            ((TextView) card.findViewById(R.id.place_category)).setText(place.shortCategory);
            ((TextView) card.findViewById(R.id.place_name)).setText(place.name);
            ((TextView) card.findViewById(R.id.place_description)).setText(place.description);
            card.setOnClickListener(v -> openMap(null, place.id));
            list.addView(card);
        }
    }

    /**
     * Intent explicite vers la carte. L'identifiant du lieu (ou la categorie) voyage
     * dans les extras, comme le prevoit la declaration du projet.
     */
    private void openMap(Place.Category category, int placeId) {
        Intent intent = new Intent(this, MapActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        if (category != null) intent.putExtra(MapActivity.EXTRA_CATEGORY, category.key);
        if (placeId > 0) intent.putExtra(MapActivity.EXTRA_PLACE_ID, placeId);
        startActivity(intent);
        noTransition();
    }
}
