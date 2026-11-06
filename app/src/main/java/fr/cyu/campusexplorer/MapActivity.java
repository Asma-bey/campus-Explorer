package fr.cyu.campusexplorer;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Chronometer;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.core.content.ContextCompat;
import androidx.webkit.WebViewAssetLoader;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import fr.cyu.campusexplorer.data.Place;
import fr.cyu.campusexplorer.data.Places;
import fr.cyu.campusexplorer.data.SavedPlaces;
import fr.cyu.campusexplorer.tracking.TrackingService;
import fr.cyu.campusexplorer.tracking.TrackingState;

/**
 * Ecran 2 : la carte du campus.
 *
 * - La carte est une page Leaflet (assets/map) dans une WebView.
 * - Les lieux sont des marqueurs ; un clic ouvre leur fiche en bas de l'ecran.
 * - La position de l'utilisateur est suivie SEULEMENT pendant que l'ecran est visible
 *   (onResume -> onPause) : c'est la localisation "d'interface".
 * - Le suivi d'un parcours, lui, est confie a TrackingService, qui continue sans cet ecran.
 */
public class MapActivity extends BaseActivity {

    private static final String TAG = "MapActivity";

    /** extras des Intents qui ouvrent la carte */
    public static final String EXTRA_PLACE_ID = "fr.cyu.campusexplorer.PLACE_ID";
    public static final String EXTRA_CATEGORY = "fr.cyu.campusexplorer.CATEGORY";

    /** l'utilisateur a demande a voir sa position : on la reprend a chaque retour sur l'ecran */
    private static final String PREF_SHOW_LOCATION = "show_location";
    /** apres une rotation d'ecran, on rouvre la meme fiche */
    private static final String STATE_SELECTED = "selected_place";

    private WebView webView;
    private boolean mapReady = false;

    private EditText search;
    private RadioGroup filters;
    private String currentCategory = "all";

    private View sheet;
    private boolean sheetExpanded = false;
    private Place selected;

    private View locationPrompt;
    private View startTrackingButton;
    private View trackingPanel;
    private Chronometer trackingTime;
    private TextView trackingDistance;
    private View statusDot;
    private TextView statusText;
    private View toast;
    private TextView toastText;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private SavedPlaces savedPlaces;

    private LocationManager locationManager;
    private boolean listeningLocation = false;
    private Location lastLocation;
    /** au premier fix apres "Afficher ma position", on centre la carte dessus */
    private boolean centerOnNextFix = false;
    /** que faire une fois la permission accordee */
    private Runnable afterPermission;

    /* ====================================================================================
     * Cycle de vie
     * ==================================================================================== */

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_map);
        applySystemBarInsets(findViewById(R.id.root), findViewById(R.id.bottom_nav));
        setupBottomNav(Tab.MAP);

        savedPlaces = new SavedPlaces(this);
        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);

        bindViews();
        setupWebView();
        setupSearchAndFilters();
        setupControls();
        setupSheet();
        setupTracking();

        if (savedInstanceState != null) {
            selected = Places.byId(savedInstanceState.getInt(STATE_SELECTED, -1));
        }
        handleIntent(getIntent());
    }

    /**
     * L'Activity existe deja (FLAG_ACTIVITY_REORDER_TO_FRONT) et on lui envoie un nouvel Intent,
     * par exemple "Voir sur la carte" depuis l'ecran Enregistres : on lit ses extras ici.
     */
    @Override
    protected void onNewIntent(@NonNull Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        webView.onResume();

        // les mises a jour du service n'arrivent que pendant que l'ecran est visible
        ContextCompat.registerReceiver(this, trackingReceiver,
                new IntentFilter(TrackingService.BROADCAST_UPDATE), ContextCompat.RECEIVER_NOT_EXPORTED);

        if (wantsLocation() && hasLocationPermission()) {
            startLocationUpdates();
        }
        updateOfflineBanner();
        refreshTrackingUi();
        refreshOverlays();
        if (selected != null) showSheet(selected, false);
    }

    @Override
    protected void onPause() {
        // l'ecran n'est plus visible : on coupe le GPS "d'interface" (le service, lui, continue)
        stopLocationUpdates();
        unregisterReceiver(trackingReceiver);
        trackingTime.stop();
        webView.onPause();
        super.onPause();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (selected != null) outState.putInt(STATE_SELECTED, selected.id);
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        webView.removeJavascriptInterface("Android");
        webView.destroy();
        super.onDestroy();
    }

    private void bindViews() {
        webView = findViewById(R.id.map_web);
        search = findViewById(R.id.search);
        filters = findViewById(R.id.filters);
        sheet = findViewById(R.id.place_sheet);
        locationPrompt = findViewById(R.id.location_prompt);
        startTrackingButton = findViewById(R.id.btn_start_tracking);
        trackingPanel = findViewById(R.id.tracking_panel);
        trackingTime = findViewById(R.id.tracking_time);
        trackingDistance = findViewById(R.id.tracking_distance);
        statusDot = findViewById(R.id.status_dot);
        statusText = findViewById(R.id.status_text);
        toast = findViewById(R.id.toast);
        toastText = findViewById(R.id.toast_text);
    }

    private void handleIntent(Intent intent) {
        String category = intent.getStringExtra(EXTRA_CATEGORY);
        if (category != null) {
            filters.check(filterIdFor(category));
            intent.removeExtra(EXTRA_CATEGORY);
        }
        int placeId = intent.getIntExtra(EXTRA_PLACE_ID, -1);
        if (placeId > 0) {
            Place place = Places.byId(placeId);
            if (place != null) {
                selected = place;
                showSheet(place, true);
            }
            // un extra lu une fois : un retour sur l'ecran ne rouvre pas la fiche fermee
            intent.removeExtra(EXTRA_PLACE_ID);
        }
    }

    /* ====================================================================================
     * La carte : WebView + Leaflet
     * ==================================================================================== */

    @SuppressLint("SetJavaScriptEnabled")
    private void setupWebView() {
        // Le WebViewAssetLoader sert les fichiers de assets/ sous une vraie adresse https.
        // Avec file:///android_asset/, la page n'aurait pas d'origine web : les serveurs de
        // tuiles d'OpenStreetMap exigent un "Referer" et refuseraient les images (erreur 403).
        WebViewAssetLoader assetLoader = new WebViewAssetLoader.Builder()
                .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
                .build();

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);        // Leaflet est du JavaScript
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false);         // rien a lire hors des assets
        settings.setAllowContentAccess(false);
        // la politique d'OpenStreetMap demande un User-Agent qui identifie l'application
        settings.setUserAgentString(settings.getUserAgentString() + " CampusExplorer/1.0 (projet etudiant CY Cergy)");

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                return assetLoader.shouldInterceptRequest(request.getUrl());
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                // un clic sur le lien "OpenStreetMap" de l'attribution ne doit pas remplacer la carte
                return true;
            }
        });
        // le pont JavaScript -> Java : la page appelle Android.onPlaceClicked(id), etc.
        webView.addJavascriptInterface(new MapBridge(), "Android");
        webView.loadUrl("https://appassets.androidplatform.net/assets/map/index.html");
    }

    /**
     * Methodes appelees par map.js. Attention : elles tournent sur un thread de la WebView,
     * pas sur le thread principal. Toute modification de l'ecran passe par runOnUiThread.
     */
    private class MapBridge {
        @JavascriptInterface
        public void onMapReady() {
            runOnUiThread(MapActivity.this::onMapLoaded);
        }

        @JavascriptInterface
        public void onPlaceClicked(int id) {
            runOnUiThread(() -> {
                Place place = Places.byId(id);
                if (place != null) showSheet(place, false);
            });
        }

        @JavascriptInterface
        public void onTilesError() {
            runOnUiThread(() -> findViewById(R.id.offline_banner).setVisibility(View.VISIBLE));
        }

        @JavascriptInterface
        public void onTilesLoaded() {
            runOnUiThread(MapActivity.this::updateOfflineBanner);
        }
    }

    /** la page est chargee : on lui envoie les lieux et l'etat courant */
    private void onMapLoaded() {
        mapReady = true;
        js("CampusMap.setPlaces(" + placesJson() + ")");
        js("CampusMap.fitPlaces()");
        applyFilter();
        if (selected != null) js("CampusMap.select(" + selected.id + ", true)");
        if (lastLocation != null) {
            js("CampusMap.showUser(" + coords(lastLocation.getLatitude(), lastLocation.getLongitude()) + ", false)");
        }
        if (TrackingState.isRunning()) drawFullTrack();
    }

    /** Appelle une fonction de map.js. Ignore l'appel si la page n'est pas encore prete. */
    private void js(String code) {
        if (mapReady) webView.evaluateJavascript(code, null);
    }

    /** les lieux en JSON : [{"id":1,"name":"...","cat":"library","lat":49.03,"lng":2.08}, ...] */
    private String placesJson() {
        JSONArray array = new JSONArray();
        try {
            for (Place p : Places.all()) {
                array.put(new JSONObject()
                        .put("id", p.id)
                        .put("name", p.name)
                        .put("cat", p.category.key)
                        .put("lat", p.lat)
                        .put("lng", p.lng));
            }
        } catch (JSONException e) {
            Log.e(TAG, "JSON des lieux", e);
        }
        return array.toString();
    }

    /** Locale.US : un point decimal, sinon "49,03" casserait l'appel JavaScript */
    private static String coords(double lat, double lng) {
        return String.format(Locale.US, "%.6f,%.6f", lat, lng);
    }

    /* ====================================================================================
     * Recherche et filtres
     * ==================================================================================== */

    private void setupSearchAndFilters() {
        View clear = findViewById(R.id.search_clear);
        search.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                clear.setVisibility(s.length() > 0 ? View.VISIBLE : View.GONE);
                applyFilter();
            }
        });
        clear.setOnClickListener(v -> search.setText(""));

        // touche "Rechercher" du clavier : si un seul lieu correspond, on ouvre sa fiche
        search.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId != EditorInfo.IME_ACTION_SEARCH) return false;
            List<Place> matches = matchingPlaces();
            if (matches.size() == 1) {
                showSheet(matches.get(0), true);
            } else if (matches.isEmpty()) {
                showToast(R.string.map_no_place);
            }
            hideKeyboard();
            return true;
        });

        filters.setOnCheckedChangeListener((group, checkedId) -> {
            currentCategory = categoryFor(checkedId);
            applyFilter();
        });
    }

    private void applyFilter() {
        // JSONObject.quote entoure le texte de guillemets et echappe ' " \ : ce que tape
        // l'utilisateur ne peut pas casser (ni detourner) l'appel JavaScript
        String query = JSONObject.quote(search.getText().toString());
        js("CampusMap.filter('" + currentCategory + "', " + query + ")");
    }

    private List<Place> matchingPlaces() {
        String q = search.getText().toString().toLowerCase(Locale.FRANCE);
        List<Place> result = new ArrayList<>();
        for (Place p : Places.all()) {
            boolean inCategory = currentCategory.equals("all") || p.category.key.equals(currentCategory);
            if (inCategory && p.name.toLowerCase(Locale.FRANCE).contains(q)) result.add(p);
        }
        return result;
    }

    /** bouton radio du filtre -> categorie envoyee a map.js (Map.of n'existe qu'a partir d'Android 11) */
    private static final Map<Integer, String> FILTERS = new HashMap<>();

    static {
        FILTERS.put(R.id.filter_all, "all");
        FILTERS.put(R.id.filter_buildings, "buildings");
        FILTERS.put(R.id.filter_library, "library");
        FILTERS.put(R.id.filter_food, "food");
        FILTERS.put(R.id.filter_services, "services");
    }

    private static String categoryFor(int checkedId) {
        String key = FILTERS.get(checkedId);
        return key != null ? key : "all";
    }

    private static int filterIdFor(String category) {
        for (Map.Entry<Integer, String> e : FILTERS.entrySet()) {
            if (e.getValue().equals(category)) return e.getKey();
        }
        return R.id.filter_all;
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(search.getWindowToken(), 0);
        search.clearFocus();
    }

    /* ====================================================================================
     * Boutons de la carte
     * ==================================================================================== */

    private void setupControls() {
        findViewById(R.id.btn_zoom_in).setOnClickListener(v -> js("CampusMap.zoomIn()"));
        findViewById(R.id.btn_zoom_out).setOnClickListener(v -> js("CampusMap.zoomOut()"));
        findViewById(R.id.btn_locate).setOnClickListener(v -> showMyPosition());
        findViewById(R.id.btn_prompt_locate).setOnClickListener(v -> showMyPosition());
    }

    /** "Afficher ma position" : permission d'abord, puis le point bleu, centre sur la carte */
    private void showMyPosition() {
        withLocationPermission(() -> {
            setWantsLocation(true);
            centerOnNextFix = true;
            if (lastLocation != null) {
                js("CampusMap.showUser(" + coords(lastLocation.getLatitude(), lastLocation.getLongitude()) + ", true)");
                centerOnNextFix = false;
            }
            startLocationUpdates();
            refreshOverlays();
        });
    }

    /* ====================================================================================
     * Fiche d'un lieu
     * ==================================================================================== */

    private void setupSheet() {
        findViewById(R.id.sheet_close).setOnClickListener(v -> hideSheet());
        findViewById(R.id.sheet_details_button).setOnClickListener(v -> {
            sheetExpanded = !sheetExpanded;
            findViewById(R.id.sheet_details_box).setVisibility(sheetExpanded ? View.VISIBLE : View.GONE);
            ((TextView) findViewById(R.id.sheet_details_label))
                    .setText(sheetExpanded ? R.string.sheet_reduce : R.string.sheet_details);
        });
        findViewById(R.id.sheet_save).setOnClickListener(v -> {
            if (selected == null) return;
            boolean nowSaved = savedPlaces.toggle(selected.id);
            updateSaveButton();
            showToast(nowSaved ? R.string.place_saved : R.string.place_removed);
        });
    }

    private void showSheet(Place place, boolean pan) {
        selected = place;
        sheetExpanded = false;
        findViewById(R.id.sheet_details_box).setVisibility(View.GONE);
        ((TextView) findViewById(R.id.sheet_details_label)).setText(R.string.sheet_details);

        int color = ContextCompat.getColor(this, place.category.markerColor);
        findViewById(R.id.sheet_icon_box).setBackgroundTintList(ColorStateList.valueOf(color));
        ImageView icon = findViewById(R.id.sheet_icon);
        icon.setImageTintList(ColorStateList.valueOf(ContextCompat.getColor(this,
                place.category == Place.Category.BUILDINGS ? R.color.white : R.color.black)));
        ((TextView) findViewById(R.id.sheet_category)).setText(place.shortCategory);
        ((TextView) findViewById(R.id.sheet_name)).setText(place.name);
        ((TextView) findViewById(R.id.sheet_description)).setText(place.description);
        ((TextView) findViewById(R.id.sheet_details)).setText(place.details);
        updateSaveButton();

        js("CampusMap.select(" + place.id + ", " + pan + ")");
        refreshOverlays();
    }

    private void hideSheet() {
        selected = null;
        js("CampusMap.select(null, false)");
        refreshOverlays();
    }

    /** bouton enregistrer : jaune si le lieu est deja enregistre (.save-button.saved) */
    private void updateSaveButton() {
        View save = findViewById(R.id.sheet_save);
        boolean saved = selected != null && savedPlaces.contains(selected.id);
        save.setBackgroundTintList(saved
                ? ColorStateList.valueOf(ContextCompat.getColor(this, R.color.yellow)) : null);
    }

    /* ====================================================================================
     * Ce qui s'affiche en bas de la carte, selon l'etat (meme logique que la maquette)
     * ==================================================================================== */

    private void refreshOverlays() {
        boolean tracking = TrackingState.isRunning();
        boolean located = lastLocation != null || (wantsLocation() && hasLocationPermission());

        trackingPanel.setVisibility(tracking ? View.VISIBLE : View.GONE);
        sheet.setVisibility(selected != null && !tracking ? View.VISIBLE : View.GONE);
        startTrackingButton.setVisibility(!tracking && selected == null ? View.VISIBLE : View.GONE);
        locationPrompt.setVisibility(!located && selected == null && !tracking ? View.VISIBLE : View.GONE);

        // pastille d'etat en haut a droite
        if (tracking) {
            setStatus(R.string.status_tracking, true);
        } else if (located) {
            setStatus(R.string.status_available, true);
        } else {
            setStatus(R.string.status_off, false);
        }
    }

    private void setStatus(@StringRes int text, boolean on) {
        statusText.setText(text);
        statusDot.setBackgroundTintList(ColorStateList.valueOf(
                ContextCompat.getColor(this, on ? R.color.green : R.color.dot_off)));
    }

    /** message noir avec coche, 2,2 s, comme le .toast de la maquette */
    private void showToast(@StringRes int text) {
        toastText.setText(text);
        handler.removeCallbacksAndMessages(null);
        toast.animate().alpha(1f).setDuration(150).start();
        handler.postDelayed(() -> toast.animate().alpha(0f).setDuration(250).start(), 2200);
    }

    /* ====================================================================================
     * Permission de localisation
     * ==================================================================================== */

    /** demande des deux permissions de localisation ; la reponse arrive dans ce callback */
    private final ActivityResultLauncher<String[]> locationPermission =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                if (hasLocationPermission()) {
                    if (afterPermission != null) afterPermission.run();
                } else if (!shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION)
                        && !shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_COARSE_LOCATION)) {
                    // refus "definitif" : Android n'affichera plus la fenetre
                    Toast.makeText(this, R.string.permission_settings, Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(this, R.string.permission_denied, Toast.LENGTH_LONG).show();
                }
                afterPermission = null;
                refreshOverlays();
            });

    /** Android 13+ : la notification du service a besoin de cette permission pour etre visible */
    private final ActivityResultLauncher<String> notificationPermission =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                // accordee ou non, le suivi demarre : sans permission, la notification est
                // seulement masquee du volet (le service reste visible dans le gestionnaire d'activites)
                startTrackingService();
            });

    private boolean hasLocationPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * Execute action si la permission est accordee. Sinon, explique d'abord POURQUOI avec le
     * dialogue de la maquette ("Votre position"), puis seulement demande la permission a Android.
     */
    private void withLocationPermission(Runnable action) {
        if (hasLocationPermission()) {
            action.run();
            return;
        }
        Dialogs.confirm(this, R.drawable.ic_locate, R.color.dialog_icon, R.color.dialog_icon_bg,
                R.string.dialog_location_title, R.string.dialog_location_text,
                R.string.dialog_continue, R.string.dialog_not_now,
                () -> {
                    afterPermission = action;
                    locationPermission.launch(new String[]{
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION});
                });
    }

    private boolean wantsLocation() {
        return getPreferences(MODE_PRIVATE).getBoolean(PREF_SHOW_LOCATION, false);
    }

    private void setWantsLocation(boolean value) {
        getPreferences(MODE_PRIVATE).edit().putBoolean(PREF_SHOW_LOCATION, value).apply();
    }

    /* ====================================================================================
     * Position "d'interface" : le point bleu, seulement quand l'ecran est visible
     * ==================================================================================== */

    private final LocationListener uiListener = new LocationListener() {
        @Override
        public void onLocationChanged(@NonNull Location location) {
            onUiLocation(location);
        }

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

    @SuppressLint("MissingPermission")   // verifie juste au-dessus par hasLocationPermission()
    private void startLocationUpdates() {
        if (listeningLocation || !hasLocationPermission()) return;

        boolean gps = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
        boolean network = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
        if (!gps && !network) {
            // GPS coupe dans les reglages : on le dit, sans planter
            Toast.makeText(this, R.string.gps_off, Toast.LENGTH_LONG).show();
            return;
        }

        // derniere position connue : le point s'affiche tout de suite, sans attendre le GPS
        Location last = gps ? locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER) : null;
        if (last == null && network) last = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
        if (last != null) onUiLocation(last);

        if (gps) {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 2000, 2f, uiListener);
        }
        if (network) {
            locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 2000, 2f, uiListener);
        }
        listeningLocation = true;
        Log.d(TAG, "GPS d'interface demarre");
    }

    private void stopLocationUpdates() {
        if (!listeningLocation) return;
        locationManager.removeUpdates(uiListener);
        listeningLocation = false;
        Log.d(TAG, "GPS d'interface arrete");
    }

    private void onUiLocation(Location location) {
        boolean first = lastLocation == null;
        lastLocation = location;
        js("CampusMap.showUser(" + coords(location.getLatitude(), location.getLongitude()) + ", "
                + centerOnNextFix + ")");
        centerOnNextFix = false;
        if (first) refreshOverlays();
    }

    /* ====================================================================================
     * Suivi d'un parcours (TrackingService)
     * ==================================================================================== */

    private void setupTracking() {
        startTrackingButton.setOnClickListener(v -> withLocationPermission(() -> {
            setWantsLocation(true);
            startLocationUpdates();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                    && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS);
            } else {
                startTrackingService();
            }
        }));

        findViewById(R.id.tracking_stop).setOnClickListener(v ->
                Dialogs.confirm(this, R.drawable.ic_route, R.color.black, R.color.stop_icon_bg,
                        R.string.dialog_stop_title, R.string.dialog_stop_text,
                        R.string.dialog_stop, R.string.dialog_keep,
                        () -> TrackingService.stop(this)));
    }

    private void startTrackingService() {
        if (!hasLocationPermission()) return;
        selected = null;
        js("CampusMap.select(null, false)");
        js("CampusMap.clearTrack()");
        TrackingService.start(this);
        // l'etat du service est pret des que onStartCommand a tourne ; on rafraichit juste apres
        handler.postDelayed(this::refreshTrackingUi, 300);
    }

    /** messages du service : nouvelle position, ou fin de la session */
    private final BroadcastReceiver trackingReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getBooleanExtra(TrackingService.EXTRA_RUNNING, false)) {
                double lat = intent.getDoubleExtra(TrackingService.EXTRA_LAT, 0);
                double lng = intent.getDoubleExtra(TrackingService.EXTRA_LNG, 0);
                js("CampusMap.addTrackPoint(" + coords(lat, lng) + ")");
                refreshTrackingUi();
            } else {
                js("CampusMap.clearTrack()");
                refreshTrackingUi();
                showToast(R.string.tracking_finished);
            }
        }
    };

    private void refreshTrackingUi() {
        if (TrackingState.isRunning()) {
            trackingTime.setBase(TrackingState.startElapsed());
            trackingTime.start();
            trackingDistance.setText(getString(R.string.tracking_km, TrackingState.distanceMeters() / 1000));
        } else {
            trackingTime.stop();
        }
        refreshOverlays();
    }

    /** au retour sur la carte pendant un suivi : on redessine tout le trace d'un coup */
    private void drawFullTrack() {
        JSONArray array = new JSONArray();
        try {
            for (double[] p : TrackingState.points()) {
                array.put(new JSONArray().put(p[0]).put(p[1]));
            }
        } catch (JSONException e) {
            Log.e(TAG, "JSON du trace", e);
        }
        js("CampusMap.setTrack(" + array + ")");
    }

    /* ====================================================================================
     * Hors connexion
     * ==================================================================================== */

    private void updateOfflineBanner() {
        findViewById(R.id.offline_banner).setVisibility(isOnline() ? View.GONE : View.VISIBLE);
    }

    private boolean isOnline() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        Network network = cm.getActiveNetwork();
        if (network == null) return false;
        NetworkCapabilities caps = cm.getNetworkCapabilities(network);
        return caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
    }
}
