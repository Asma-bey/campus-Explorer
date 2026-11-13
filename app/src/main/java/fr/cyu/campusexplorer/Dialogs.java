package fr.cyu.campusexplorer;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.ColorRes;
import androidx.annotation.DrawableRes;
import androidx.annotation.StringRes;
import androidx.core.content.ContextCompat;

/**
 * Les dialogues de la maquette (.dialog) : un AlertDialog avec notre propre layout,
 * dialog_confirm.xml, au lieu du style par defaut d'Android.
 */
final class Dialogs {

    private Dialogs() {
    }

    /**
     * Affiche un dialogue a deux boutons. Seul le bouton principal declenche une action ;
     * le second ferme le dialogue sans rien faire.
     *
     * Utilise par MapActivity : explication avant la permission de localisation, et
     * confirmation de l'arret du suivi.
     *
     * @param icon      icone en haut du dialogue ; iconColor et iconBackground la colorent
     * @param title     titre ; text : explication
     * @param primary   texte du bouton principal ; secondary : texte du bouton "annuler"
     * @param onPrimary code execute apres un clic sur le bouton principal
     */
    static void confirm(Activity activity,
                        @DrawableRes int icon, @ColorRes int iconColor, @ColorRes int iconBackground,
                        @StringRes int title, @StringRes int text,
                        @StringRes int primary, @StringRes int secondary,
                        Runnable onPrimary) {
        View view = activity.getLayoutInflater().inflate(R.layout.dialog_confirm, null);

        ImageView iconView = view.findViewById(R.id.dialog_icon);
        iconView.setImageResource(icon);
        iconView.setImageTintList(ColorStateList.valueOf(ContextCompat.getColor(activity, iconColor)));
        view.findViewById(R.id.dialog_icon_box).setBackgroundTintList(
                ColorStateList.valueOf(ContextCompat.getColor(activity, iconBackground)));
        ((TextView) view.findViewById(R.id.dialog_title)).setText(title);
        ((TextView) view.findViewById(R.id.dialog_text)).setText(text);
        TextView primaryView = view.findViewById(R.id.dialog_primary);
        TextView secondaryView = view.findViewById(R.id.dialog_secondary);
        primaryView.setText(primary);
        secondaryView.setText(secondary);

        AlertDialog dialog = new AlertDialog.Builder(activity).setView(view).create();
        // fond transparent : seuls les coins arrondis de dialog_confirm.xml se voient
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
        primaryView.setOnClickListener(v -> {
            dialog.dismiss();
            onPrimary.run();
        });
        secondaryView.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }
}
