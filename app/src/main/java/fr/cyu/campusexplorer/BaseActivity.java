package fr.cyu.campusexplorer;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

/**
 * Ce que les 3 ecrans ont en commun :
 * 1. le journal du cycle de vie (onCreate, onResume, onPause, onStop) dans Logcat, filtre "Lifecycle",
 *    pour tester les transitions comme le demande la declaration du projet ;
 * 2. la barre de navigation du bas ;
 * 3. les marges pour la barre d'etat et la barre de navigation du systeme.
 */
public abstract class BaseActivity extends AppCompatActivity {

    protected static final String LIFECYCLE = "Lifecycle";

    /** les 3 onglets de la barre du bas */
    protected enum Tab { HOME, MAP, SAVED }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(LIFECYCLE, getClass().getSimpleName() + ".onCreate");
    }

    @Override
    protected void onStart() {
        super.onStart();
        Log.d(LIFECYCLE, getClass().getSimpleName() + ".onStart");
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(LIFECYCLE, getClass().getSimpleName() + ".onResume");
    }

    @Override
    protected void onPause() {
        Log.d(LIFECYCLE, getClass().getSimpleName() + ".onPause");
        super.onPause();
    }

    @Override
    protected void onStop() {
        Log.d(LIFECYCLE, getClass().getSimpleName() + ".onStop");
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        Log.d(LIFECYCLE, getClass().getSimpleName() + ".onDestroy");
        super.onDestroy();
    }

    /**
     * Depuis Android 15 (targetSdk 35 et plus), l'appli dessine sous les barres du systeme.
     * On decale le contenu de la hauteur de ces barres : en haut sur la racine (dont la couleur
     * de fond remplit la zone de la barre d'etat), en bas sur la barre de navigation de l'appli.
     */
    protected void applySystemBarInsets(View root, View bottomNav) {
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(bars.left, bars.top, bars.right, 0);
            bottomNav.setPadding(0, 0, 0, bars.bottom);
            return insets;
        });
    }

    /** Remplit les 3 onglets, marque celui de cet ecran et branche les clics. */
    protected void setupBottomNav(Tab current) {
        setupTab(R.id.nav_home, R.drawable.ic_home, R.string.nav_home, current == Tab.HOME, MainActivity.class);
        setupTab(R.id.nav_map, R.drawable.ic_map, R.string.nav_map, current == Tab.MAP, MapActivity.class);
        setupTab(R.id.nav_saved, R.drawable.ic_bookmark, R.string.nav_saved, current == Tab.SAVED, SavedActivity.class);
    }

    private void setupTab(int id, @DrawableRes int icon, @StringRes int label, boolean selected,
                          Class<? extends BaseActivity> target) {
        View tab = findViewById(id);
        ((ImageView) tab.findViewById(R.id.nav_icon)).setImageResource(icon);
        ((TextView) tab.findViewById(R.id.nav_label)).setText(label);
        tab.setSelected(selected);
        tab.setContentDescription(getString(label));
        if (!selected) {
            tab.setOnClickListener(v -> openTab(target));
        }
    }

    /**
     * Intent explicite vers un autre ecran.
     * FLAG_ACTIVITY_REORDER_TO_FRONT : si l'ecran existe deja dans la pile, il est ramene devant
     * au lieu d'en creer un deuxieme. Sans ce drapeau, chaque clic sur un onglet empilerait une
     * nouvelle Activity, et le bouton retour repasserait par tous les ecrans visites.
     */
    protected void openTab(Class<? extends BaseActivity> target) {
        Intent intent = new Intent(this, target);
        intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        startActivity(intent);
        noTransition();
    }

    /**
     * Les onglets changent sans animation de glissement, comme dans la maquette.
     * overridePendingTransition est appele juste apres startActivity ; il est marque "deprecated"
     * depuis Android 14 mais fonctionne toujours, et marche aussi sur les versions plus anciennes.
     */
    @SuppressWarnings("deprecation")
    protected void noTransition() {
        overridePendingTransition(0, 0);
    }
}
