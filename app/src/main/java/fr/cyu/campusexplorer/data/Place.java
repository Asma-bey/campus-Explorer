package fr.cyu.campusexplorer.data;

import androidx.annotation.ColorRes;
import androidx.annotation.DrawableRes;

import fr.cyu.campusexplorer.R;

/**
 * Un point d'interet du campus. Les lieux sont fixes (classe Places) :
 * pas de base distante, comme le prevoit la declaration du projet.
 */
public class Place {

    /** Les categories des filtres de la carte. "key" est aussi envoye au JavaScript de la carte. */
    public enum Category {
        BUILDINGS("buildings", R.drawable.ic_building, R.color.black, R.color.cyan_soft),
        LIBRARY("library", R.drawable.ic_book, R.color.yellow, R.color.yellow_soft),
        FOOD("food", R.drawable.ic_food, R.color.green, R.color.green_soft),
        SERVICES("services", R.drawable.ic_users, R.color.cyan, R.color.cyan_soft);

        public final String key;
        @DrawableRes public final int icon;
        /** couleur du marqueur sur la carte */
        @ColorRes public final int markerColor;
        /** fond pastel de la pastille sur l'accueil */
        @ColorRes public final int softColor;

        Category(String key, int icon, int markerColor, int softColor) {
            this.key = key;
            this.icon = icon;
            this.markerColor = markerColor;
            this.softColor = softColor;
        }
    }

    public final int id;
    public final String name;
    public final Category category;
    /** petit libelle au-dessus du nom, en majuscules dans l'interface */
    public final String shortCategory;
    public final String description;
    /** texte affiche par "Voir les details" */
    public final String details;
    public final double lat;
    public final double lng;

    public Place(int id, String name, Category category, String shortCategory,
                 String description, String details, double lat, double lng) {
        this.id = id;
        this.name = name;
        this.category = category;
        this.shortCategory = shortCategory;
        this.description = description;
        this.details = details;
        this.lat = lat;
        this.lng = lng;
    }
}
