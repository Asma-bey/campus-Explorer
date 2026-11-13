package fr.cyu.campusexplorer.tracking;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.ServiceCompat;
import androidx.core.content.ContextCompat;

import fr.cyu.campusexplorer.MapActivity;
import fr.cyu.campusexplorer.R;
import fr.cyu.campusexplorer.data.SessionDb;

/**
 * Service au premier plan qui enregistre un parcours.
 *
 * - Il ne tourne QUE pendant une session lancee par l'utilisateur (bouton "Demarrer le suivi").
 *- Une notification permanente montre qu'il est actif, avec un bouton ARRETER.
 * - Il recoit les positions GPS meme quand l'ecran est eteint ou qu'une autre appli est ouverte.
 * - A l'arret, il enregistre le resume dans SQLite, coupe le GPS et se detruit.
 *
 * Son cycle de vie est independant de MapActivity : quitter la carte n'arrete pas la session.
 */
public class TrackingService extends Service {

    private static final String TAG = "TrackingService";

    public static final String ACTION_START = "fr.cyu.campusexplorer.action.START_TRACKING";
    public static final String ACTION_STOP = "fr.cyu.campusexplorer.action.STOP_TRACKING";

    /** diffuse a chaque nouvelle position, et a l'arret, pour mettre a jour l'ecran de la carte */
    public static final String BROADCAST_UPDATE = "fr.cyu.campusexplorer.TRACKING_UPDATE";
    public static final String EXTRA_LAT = "lat";
    public static final String EXTRA_LNG = "lng";
    public static final String EXTRA_RUNNING = "running";
    /** true si la position a ajoute un point au trace (sinon : seul le point bleu bouge) */
    public static final String EXTRA_COUNTED = "counted";
    /** direction du deplacement en degres (0 = nord), -1 si on est a l'arret */
    public static final String EXTRA_BEARING = "bearing";
    /** a l'arret de la session : le resume, pour le message de fin */
    public static final String EXTRA_METERS = "meters";
    public static final String EXTRA_SECONDS = "seconds";

    private static final String CHANNEL_ID = "tracking";
    private static final int NOTIFICATION_ID = 42;

    /*
     * Une position par seconde, sans distance minimale : comme une appli de navigation, le point
     * bleu, le trace et les kilometres avancent en direct. Le tri se fait ensuite dans onNewLocation.
     */
    private static final long MIN_TIME_MS = 1000;
    private static final float MIN_DISTANCE_M = 0f;
    /** on ignore les positions trop imprecises, qui feraient "sauter" la distance */
    private static final float MAX_ACCURACY_M = 35f;
    /** a l'arret, le GPS "tremble" de quelques metres : ces petits ecarts ne comptent pas */
    private static final float MIN_STEP_M = 5f;
    /** plus de 50 m/s (180 km/h) entre deux positions : c'est un saut du GPS, pas un deplacement */
    private static final float MAX_SPEED_MS = 50f;
    /** une position du reseau (Wi-Fi, antennes) n'est prise que si le GPS se tait depuis 10 s */
    private static final long GPS_FRESH_MS = 10_000;
    /** sous 0,5 m/s, la direction donnee par le GPS n'a pas de sens */
    private static final float MIN_SPEED_FOR_BEARING = 0.5f;

    /** service systeme de localisation (schema "manager + listener" du cours 6) */
    private LocationManager locationManager;
    /** dernier point retenu dans le trace : la distance se mesure a partir de lui */
    private Location anchor;
    /** instant (SystemClock.elapsedRealtime) de la derniere position GPS ; 0 = jamais */
    private long lastGpsFix;

    /*
     * Le listener que le LocationManager appelle a chaque nouvelle position (rappel asynchrone :
     * c'est le systeme qui appelle onLocationChanged, jamais notre code). Il est enregistre sur
     * le Looper du thread principal (voir requestUpdates), donc onNewLocation tourne sur ce thread.
     */
    private final LocationListener listener = new LocationListener() {
        @Override
        public void onLocationChanged(@NonNull Location location) {
            onNewLocation(location);
        }

        // ces trois methodes sont obligatoires avant Android 11 (minSdk 26)
        @Override
        public void onProviderEnabled(@NonNull String provider) {
        }

        @Override
        public void onProviderDisabled(@NonNull String provider) {
        }

        @Override
        public void onStatusChanged(String provider, int status, Bundle extras) {
        }
    };

    /**
     * Demarre une session. A appeler depuis un ecran au premier plan, permission accordee.
     * startForegroundService (et pas startService) : depuis Android 8, une appli ne peut lancer
     * un service durable que si elle promet de le passer au premier plan (startForeground) dans
     * les 5 secondes, ce que fait onStartCommand.
     *
     * @param context l'ecran appelant (MapActivity)
     */
    public static void start(Context context) {
        Intent intent = new Intent(context, TrackingService.class).setAction(ACTION_START);
        ContextCompat.startForegroundService(context, intent);
    }

    /**
     * Termine la session : le service enregistre le parcours puis s'arrete.
     * On envoie un Intent ACTION_STOP au service deja demarre : c'est onStartCommand qui le
     * recoit et appelle finishSession. Le bouton STOP de la notification envoie le meme Intent.
     *
     * @param context l'ecran appelant (MapActivity, apres le dialogue de confirmation)
     */
    public static void stop(Context context) {
        Intent intent = new Intent(context, TrackingService.class).setAction(ACTION_STOP);
        context.startService(intent);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "onCreate");
        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        createChannel();
    }

    /**
     * Appele par le systeme a chaque startService / startForegroundService, avec l'Intent envoye.
     * Un service demarre une seule fois (onCreate) peut recevoir plusieurs commandes : ici START
     * puis STOP.
     *
     * @return START_NOT_STICKY : si Android tue le processus, il ne recree pas le service
     */
    @Override
    public int onStartCommand(@Nullable Intent intent, int flags, int startId) {
        String action = intent != null ? intent.getAction() : null;
        Log.d(TAG, "onStartCommand " + action);

        if (ACTION_STOP.equals(action)) {
            finishSession();
            return START_NOT_STICKY;
        }

        if (!hasLocationPermission()) {
            // ne jamais demarrer sans permission : Android leverait une SecurityException
            Log.w(TAG, "Permission de localisation absente, le service s'arrete");
            stopSelf();
            return START_NOT_STICKY;
        }

        // deux horloges : elapsedRealtime pour mesurer la duree (elle ne recule jamais, meme si
        // l'utilisateur change l'heure), currentTimeMillis pour dater le parcours dans l'historique
        if (!TrackingState.isRunning()) {
            TrackingState.start(SystemClock.elapsedRealtime(), System.currentTimeMillis());
            anchor = null;
            lastGpsFix = 0;
        }

        // startForeground doit etre appele dans les 5 secondes qui suivent startForegroundService
        ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotification(),
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                        ? ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION : 0);
        requestUpdates();

        // si Android tue le processus, on ne relance pas une session toute seule
        return START_NOT_STICKY;
    }

    /** true si l'une des deux permissions de localisation est accordee (precise ou approximative) */
    private boolean hasLocationPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * Abonne le listener aux deux fournisseurs actives (GPS et reseau).
     * requestLocationUpdates(fournisseur, minTime, minDistance, listener, looper) : au plus une
     * position par MIN_TIME_MS, sans seuil de distance ; le Looper du thread principal livre les
     * positions (un service tourne sur ce thread, il a donc deja une boucle de messages, sans le
     * Looper.prepare() du Thread de l'exemple du cours).
     */
    private void requestUpdates() {
        try {
            // on retire d'abord un eventuel abonnement precedent : jamais deux fois le meme listener
            locationManager.removeUpdates(listener);
            boolean any = false;
            // le GPS est le capteur principal ; le reseau complete a l'interieur des batiments
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER,
                        MIN_TIME_MS, MIN_DISTANCE_M, listener, Looper.getMainLooper());
                any = true;
            }
            if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER,
                        MIN_TIME_MS, MIN_DISTANCE_M, listener, Looper.getMainLooper());
                any = true;
            }
            if (!any) {
                Log.w(TAG, "Aucun fournisseur de position actif : la session attend le GPS");
            }
        } catch (SecurityException e) {
            Log.e(TAG, "Permission retiree pendant le suivi", e);
            finishSession();
        }
    }

    /**
     * Le coeur du suivi : decide si une position fait avancer le parcours.
     *
     * Etapes : (1) GPS prioritaire sur le reseau ; (2) rejet des positions imprecises ;
     * (3) premier point, ou (4) pas assez grand et pas un saut -> on l'ajoute au trace et a la
     * distance ; (5) le point bleu bouge dans tous les cas ; (6) la notification et l'ecran
     * sont prevenus.
     *
     * La distance parcourue est la somme des segments anchor -> nouvelle position. Elle se
     * mesure avec Location.distanceTo, qui calcule la distance sur l'ellipsoide terrestre WGS84
     * (pas une distance "a plat" entre deux nombres).
     *
     * @param location la position livree par le LocationManager (latitude, longitude,
     *                 precision, horodatage, et parfois vitesse et direction)
     */
    private void onNewLocation(Location location) {
        if (!TrackingState.isRunning()) return;

        // GPS et reseau donnent deux positions differentes : les melanger fait zigzaguer le trace.
        // Le reseau ne sert que tant que le GPS ne repond pas (a l'interieur d'un batiment).
        long now = SystemClock.elapsedRealtime();
        if (LocationManager.GPS_PROVIDER.equals(location.getProvider())) {
            lastGpsFix = now;
        } else if (lastGpsFix != 0 && now - lastGpsFix < GPS_FRESH_MS) {
            return;
        }
        // getAccuracy() = rayon en metres du cercle ou la vraie position a 68 % de chances d'etre
        if (location.hasAccuracy() && location.getAccuracy() > MAX_ACCURACY_M) return;

        // counted : cette position a-t-elle allonge le trace ? (envoye a l'ecran dans le broadcast)
        boolean counted = false;
        if (anchor == null) {
            // premier point du parcours
            TrackingState.addPoint(location.getLatitude(), location.getLongitude(), 0);
            anchor = location;
            counted = true;
        } else {
            // step : distance en metres entre le dernier point retenu et la nouvelle position
            float step = anchor.distanceTo(location);
            // on n'avance que si le deplacement depasse l'imprecision des deux positions :
            // seuil = 60 % de la plus mauvaise precision, et jamais moins de 5 m
            // (ex. precisions 10 m et 20 m -> seuil 12 m ; precisions 4 m et 6 m -> seuil 5 m)
            float noise = Math.max(MIN_STEP_M, 0.6f * Math.max(accuracy(anchor), accuracy(location)));
            // temps ecoule entre les deux positions, d'apres leurs horodatages (nanosecondes -> s)
            double seconds = (location.getElapsedRealtimeNanos() - anchor.getElapsedRealtimeNanos()) / 1e9;
            // vitesse moyenne = distance / temps ; au-dela de 50 m/s, c'est une erreur du GPS
            boolean jump = seconds > 0 && step / seconds > MAX_SPEED_MS;
            if (step >= noise && !jump) {
                TrackingState.addPoint(location.getLatitude(), location.getLongitude(), step);
                anchor = location;
                counted = true;
            } else if (jump) {
                Log.d(TAG, "Saut du GPS ignore : " + Math.round(step) + " m en " + seconds + " s");
                return;
            }
            // sinon (petit ecart d'une personne immobile) : pas de point, mais le point bleu bouge
        }
        TrackingState.setPosition(location.getLatitude(), location.getLongitude());

        // la notification affiche la distance a jour (inutile de la refaire si rien n'a change)
        if (counted) {
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            nm.notify(NOTIFICATION_ID, buildNotification());
        }

        // direction de la fleche du point bleu. getSpeed() est la vitesse en m/s calculee par le
        // fournisseur de position (en general a partir de l'effet Doppler) ; elle sert ici seulement a
        // savoir si l'utilisateur bouge : elle n'est ni affichee ni enregistree
        float bearing = location.hasBearing() && location.hasSpeed()
                && location.getSpeed() >= MIN_SPEED_FOR_BEARING ? location.getBearing() : -1f;
        // diffusion (broadcast) vers MapActivity : schema "GPS dans un service + sendBroadcast"
        // du cours 6 ; l'ecran la recoit dans trackingReceiver s'il est visible
        Intent update = new Intent(BROADCAST_UPDATE)
                .setPackage(getPackageName())   // diffusion reservee a notre appli
                .putExtra(EXTRA_RUNNING, true)
                .putExtra(EXTRA_COUNTED, counted)
                .putExtra(EXTRA_BEARING, bearing)
                .putExtra(EXTRA_LAT, location.getLatitude())
                .putExtra(EXTRA_LNG, location.getLongitude());
        sendBroadcast(update);
    }

    /** precision en metres ; une position sans precision connue compte comme moyenne (20 m) */
    private static float accuracy(Location location) {
        return location.hasAccuracy() ? location.getAccuracy() : 20f;
    }

    /** Arret : enregistre le resume, libere le GPS, retire la notification, detruit le service. */
    private void finishSession() {
        locationManager.removeUpdates(listener);

        long seconds = 0;
        double meters = 0;
        if (TrackingState.isRunning()) {
            seconds = (SystemClock.elapsedRealtime() - TrackingState.startElapsed()) / 1000;
            meters = TrackingState.distanceMeters();
            SessionDb db = new SessionDb(this);
            db.insert(TrackingState.startWallClock(), seconds, meters);
            db.close();
            Log.d(TAG, "Session enregistree : " + seconds + " s, " + Math.round(meters) + " m");
            TrackingState.stop();
        }

        sendBroadcast(new Intent(BROADCAST_UPDATE)
                .setPackage(getPackageName())
                .putExtra(EXTRA_RUNNING, false)
                .putExtra(EXTRA_METERS, meters)
                .putExtra(EXTRA_SECONDS, seconds));

        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE);
        stopSelf();
    }

    /** Cree le canal "tracking" des notifications (sans effet s'il existe deja). */
    private void createChannel() {
        // obligatoire depuis Android 8 (notre minSdk) ; importance basse : visible, mais sans son
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                getString(R.string.channel_name), NotificationManager.IMPORTANCE_LOW);
        channel.setDescription(getString(R.string.channel_description));
        ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(channel);
    }

    /**
     * La notification permanente du service au premier plan.
     * Un PendingIntent est un Intent "confie" au systeme, qui l'executera plus tard au nom de
     * l'appli (quand l'utilisateur touche la notification ou son bouton STOP).
     * FLAG_IMMUTABLE : obligatoire depuis Android 12, personne ne peut modifier l'Intent.
     *
     * @return la notification, avec le chrono, la distance et l'action STOP
     */
    private Notification buildNotification() {
        // toucher la notification ramene a la carte (sans creer une deuxieme carte dans la pile)
        Intent open = new Intent(this, MapActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent openPending = PendingIntent.getActivity(this, 0, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Intent stop = new Intent(this, TrackingService.class).setAction(ACTION_STOP);
        PendingIntent stopPending = PendingIntent.getService(this, 1, stop,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        // "350 m", puis "1,25 km" en francais, "1.25 km" en anglais
        String distance = TrackingState.formatDistance(this, TrackingState.distanceMeters());
        // chrono de la notification : on donne l'heure murale du depart
        long startWall = System.currentTimeMillis()
                - (SystemClock.elapsedRealtime() - TrackingState.startElapsed());

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_pin)
                .setColor(ContextCompat.getColor(this, R.color.yellow))
                .setContentTitle(getString(R.string.notif_title))
                .setContentText(getString(R.string.notif_text))
                .setSubText(distance)
                .setWhen(startWall)
                .setShowWhen(true)
                .setUsesChronometer(true)
                .setOngoing(true)                 // impossible a balayer pendant le suivi
                .setOnlyAlertOnce(true)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
                .setContentIntent(openPending)
                .addAction(R.drawable.ic_close, getString(R.string.notif_stop), stopPending)
                .build();
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy");
        locationManager.removeUpdates(listener);
        super.onDestroy();
    }

    /** service "demarre", pas "lie" : pas de bind */
    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
