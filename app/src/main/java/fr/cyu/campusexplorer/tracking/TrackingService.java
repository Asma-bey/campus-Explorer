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
 * - Une notification permanente montre qu'il est actif, avec un bouton ARRETER.
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

    private static final String CHANNEL_ID = "tracking";
    private static final int NOTIFICATION_ID = 42;

    /** une position toutes les 3 s au plus, et seulement si on a bouge de 3 m */
    private static final long MIN_TIME_MS = 3000;
    private static final float MIN_DISTANCE_M = 3f;
    /** on ignore les positions trop imprecises, qui feraient "sauter" la distance */
    private static final float MAX_ACCURACY_M = 35f;

    private LocationManager locationManager;
    private Location lastLocation;

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

    /** Demarre une session. A appeler depuis un ecran au premier plan, permission accordee. */
    public static void start(Context context) {
        Intent intent = new Intent(context, TrackingService.class).setAction(ACTION_START);
        ContextCompat.startForegroundService(context, intent);
    }

    /** Termine la session : le service enregistre le parcours puis s'arrete. */
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

        if (!TrackingState.isRunning()) {
            TrackingState.start(SystemClock.elapsedRealtime(), System.currentTimeMillis());
            lastLocation = null;
        }

        // startForeground doit etre appele dans les 5 secondes qui suivent startForegroundService
        ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotification(),
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                        ? ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION : 0);
        requestUpdates();

        // si Android tue le processus, on ne relance pas une session toute seule
        return START_NOT_STICKY;
    }

    private boolean hasLocationPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void requestUpdates() {
        try {
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

    private void onNewLocation(Location location) {
        if (!TrackingState.isRunning()) return;
        if (location.hasAccuracy() && location.getAccuracy() > MAX_ACCURACY_M) return;

        double added = lastLocation == null ? 0 : lastLocation.distanceTo(location);
        lastLocation = location;
        TrackingState.addPoint(location.getLatitude(), location.getLongitude(), added);

        // la notification affiche la distance a jour
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        nm.notify(NOTIFICATION_ID, buildNotification());

        Intent update = new Intent(BROADCAST_UPDATE)
                .setPackage(getPackageName())   // diffusion reservee a notre appli
                .putExtra(EXTRA_RUNNING, true)
                .putExtra(EXTRA_LAT, location.getLatitude())
                .putExtra(EXTRA_LNG, location.getLongitude());
        sendBroadcast(update);
    }

    /** Arret : enregistre le resume, libere le GPS, retire la notification, detruit le service. */
    private void finishSession() {
        locationManager.removeUpdates(listener);

        if (TrackingState.isRunning()) {
            long seconds = (SystemClock.elapsedRealtime() - TrackingState.startElapsed()) / 1000;
            double meters = TrackingState.distanceMeters();
            SessionDb db = new SessionDb(this);
            db.insert(TrackingState.startWallClock(), seconds, meters);
            db.close();
            Log.d(TAG, "Session enregistree : " + seconds + " s, " + Math.round(meters) + " m");
            TrackingState.stop();
        }

        sendBroadcast(new Intent(BROADCAST_UPDATE)
                .setPackage(getPackageName())
                .putExtra(EXTRA_RUNNING, false));

        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE);
        stopSelf();
    }

    private void createChannel() {
        // obligatoire depuis Android 8 (notre minSdk) ; importance basse : visible, mais sans son
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                getString(R.string.channel_name), NotificationManager.IMPORTANCE_LOW);
        channel.setDescription(getString(R.string.channel_description));
        ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(channel);
    }

    private Notification buildNotification() {
        // toucher la notification ramene a la carte (sans creer une deuxieme carte dans la pile)
        Intent open = new Intent(this, MapActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent openPending = PendingIntent.getActivity(this, 0, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Intent stop = new Intent(this, TrackingService.class).setAction(ACTION_STOP);
        PendingIntent stopPending = PendingIntent.getService(this, 1, stop,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        // "0,66 km" en francais, "0.66 km" en anglais : getString suit la langue du telephone
        String distance = getString(R.string.tracking_km, TrackingState.distanceMeters() / 1000);
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
