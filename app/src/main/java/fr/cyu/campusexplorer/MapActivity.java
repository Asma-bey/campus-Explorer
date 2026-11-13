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
import android.os.SystemClock;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.format.DateFormat;
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
import android.widget.LinearLayout;
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
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import fr.cyu.campusexplorer.data.Place;
import fr.cyu.campusexplorer.data.PlaceSearch;
import fr.cyu.campusexplorer.data.Places;
import fr.cyu.campusexplorer.data.SavedPlaces;
import fr.cyu.campusexplorer.navigation.Guidance;
import fr.cyu.campusexplorer.navigation.Instructions;
import fr.cyu.campusexplorer.navigation.Route;
import fr.cyu.campusexplorer.navigation.RouteClient;
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
    /** ... et on reprend le guidage vers le meme lieu */
    private static final String STATE_DESTINATION = "destination_place";

    /** vitesse de marche pour les minutes restantes : 1,3 m/s, soit environ 4,7 km/h */
    private static final double WALK_SPEED = 1.3;
    /** pas plus d'un calcul d'itineraire toutes les 15 s (recalcul apres un ecart) */
    private static final long REROUTE_DELAY_MS = 15_000;
    /** a vol d'oiseau, on retente le serveur toutes les 30 s (le reseau a pu revenir) */
    private static final long RETRY_ROUTE_MS = 30_000;

    private WebView webView;
    private boolean mapReady = false;

    private EditText search;
    private RadioGroup filters;
    private String currentCategory = "all";
    private PlaceSearch placeSearch;
    private LinearLayout suggestions;

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
    private long lastUiGpsFix;
    /** au premier fix apres "Afficher ma position", on centre la carte dessus */
    private boolean centerOnNextFix = false;
    /** que faire une fois la permission accordee */
    private Runnable afterPermission;

    /** guidage : le lieu vise (null sans guidage) et l'itineraire (null tant qu'il n'est pas calcule) */
    private Place destination;
    private Guidance guidance;
    /** le calcul d'itineraire fait du reseau : il tourne sur ce thread, jamais sur le thread principal */
    private final ExecutorService routeThread = Executors.newSingleThreadExecutor();
    /** numero de la derniere demande : une reponse plus ancienne (lieu change entre-temps) est ignoree */
    private int routeRequest = 0;
    private boolean routePending = false;
    private long lastRouteTime;
    private View guidancePanel;
    private View searchBar;
    private View filtersBar;
    private View mapControls;
    private ImageView guideArrow;
    private TextView guideDestination;
    private TextView guideInstruction;
    private TextView guideDetail;
    private TextView guideMinutes;
    private TextView guideInfo;

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
        placeSearch = new PlaceSearch(this);
        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);

        bindViews();
        setupWebView();
        setupSearchAndFilters();
        setupControls();
        setupSheet();
        setupTracking();
        setupGuidance();

        // savedInstanceState n'est pas null apres une rotation : on retrouve le lieu ouvert
        if (savedInstanceState != null) {
            selected = Places.byId(savedInstanceState.getInt(STATE_SELECTED, -1));
            // l'itineraire sera recalcule a la prochaine position
            destination = Places.byId(savedInstanceState.getInt(STATE_DESTINATION, -1));
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

    /*
     * onResume / onPause forment une paire : tout ce qui est demarre ici est arrete dans onPause
     * (receiver, GPS d'interface, Chronometer, WebView). L'ecran visible est le seul a consommer
     * le GPS ; des qu'il est cache, la batterie n'est plus sollicitee pour lui.
     * onResume relit aussi l'etat (TrackingState, reseau) : il a pu changer pendant que l'ecran
     * etait cache, par exemple un suivi arrete depuis la notification.
     */
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
        if (destination != null) outState.putInt(STATE_DESTINATION, destination.id);
    }

    /** liberation : messages en attente du Handler, thread des itineraires, pont JavaScript et WebView */
    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        routeThread.shutdownNow();
        webView.removeJavascriptInterface("Android");
        webView.destroy();
        super.onDestroy();
    }

    private void bindViews() {
        webView = findViewById(R.id.map_web);
        search = findViewById(R.id.search);
        filters = findViewById(R.id.filters);
        suggestions = findViewById(R.id.search_suggestions);
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
        guidancePanel = findViewById(R.id.guidance_panel);
        searchBar = findViewById(R.id.search_bar);
        filtersBar = findViewById(R.id.filters_bar);
        mapControls = findViewById(R.id.map_controls);
        guideArrow = findViewById(R.id.guide_arrow);
        guideDestination = findViewById(R.id.guide_destination);
        guideInstruction = findViewById(R.id.guide_instruction);
        guideDetail = findViewById(R.id.guide_detail);
        guideMinutes = findViewById(R.id.guide_minutes);
        guideInfo = findViewById(R.id.guide_info);
    }

    /**
     * Lit les extras envoyes par l'accueil ou l'ecran Enregistres.
     *
     * @param intent EXTRA_CATEGORY ("library", "food"...) coche le filtre ;
     *               EXTRA_PLACE_ID (1 a 14) ouvre la fiche du lieu et centre la carte dessus
     */
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

    /**
     * la page est chargee : on lui envoie les lieux et l'etat courant.
     * Tant que mapReady est false, js() ignore les appels : la page n'existe pas encore. C'est
     * pourquoi tout l'etat (filtre, fiche ouverte, point bleu, trace) est renvoye ici.
     */
    private void onMapLoaded() {
        mapReady = true;
        js("CampusMap.setPlaces(" + placesJson() + ")");
        js("CampusMap.fitPlaces()");
        applyFilter();
        if (selected != null) {
            js("CampusMap.select(" + selected.id + ", true)");
        } else if (destination != null) {
            js("CampusMap.select(" + destination.id + ", false)");
        }
        if (lastLocation != null) {
            js("CampusMap.showUser(" + coords(lastLocation.getLatitude(), lastLocation.getLongitude()) + ", false)");
        }
        if (TrackingState.isRunning()) drawFullTrack();
    }

    /**
     * Pendant un suivi : le trace complet, le point bleu a la derniere position du service,
     * et la carte qui suit l'utilisateur (comme le mode navigation de Google Maps).
     */
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
        double[] here = TrackingState.position();
        if (here != null) js("CampusMap.showUser(" + coords(here[0], here[1]) + ", false)");
        js("CampusMap.setTracking(true)");
    }

    /**
     * Appelle une fonction de map.js. Ignore l'appel si la page n'est pas encore prete.
     * evaluateJavascript est asynchrone ; le second argument (null) signifie qu'on n'attend pas
     * de valeur de retour.
     *
     * @param code une instruction JavaScript, ex. "CampusMap.select(3, true)"
     */
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
                        .put("name", getString(p.name))
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

        // touche "Rechercher" du clavier : on ouvre la fiche du meilleur resultat
        search.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId != EditorInfo.IME_ACTION_SEARCH) return false;
            List<Place> matches = placeSearch.search(search.getText().toString(), currentCategory, lastLocation);
            if (matches.isEmpty()) {
                showToast(R.string.map_no_place);
            } else {
                showSheet(matches.get(0), true);
            }
            hideKeyboard();
            return true;
        });
        // les suggestions s'affichent quand on touche la barre, et disparaissent quand on la quitte
        search.setOnFocusChangeListener((v, hasFocus) -> applyFilter());

        filters.setOnCheckedChangeListener((group, checkedId) -> {
            currentCategory = categoryFor(checkedId);
            applyFilter();
        });
    }

    /**
     * La recherche est faite en Java (PlaceSearch) ; map.js recoit seulement la liste des
     * lieux a montrer, et comment cadrer la carte :
     *  - "all"  : une recherche, on cadre tous les resultats (meme Versailles) ;
     *  - "near" : une categorie seule, on cadre les resultats proches de Cergy ;
     *  - "none" : ni recherche ni categorie, la carte ne bouge pas.
     */
    private void applyFilter() {
        String query = search.getText().toString().trim();
        List<Place> matches = placeSearch.search(query, currentCategory, lastLocation);
        JSONArray ids = new JSONArray();
        for (Place p : matches) ids.put(p.id);
        String fit = !query.isEmpty() ? "all" : currentCategory.equals("all") ? "none" : "near";
        js("CampusMap.filter(" + ids + ", '" + fit + "')");
        showSuggestions(query, matches);
    }

    /** au plus 5 suggestions sous la barre, pendant que l'utilisateur tape (ou des qu'il la touche) */
    private static final int MAX_SUGGESTIONS = 5;

    /**
     * Remplit la liste de suggestions : un clic ouvre la fiche du lieu.
     * Barre vide : les lieux les plus proches (ou les premiers), comme point de depart.
     */
    private void showSuggestions(String query, List<Place> matches) {
        suggestions.removeAllViews();
        // les boutons de la carte sont dans un autre conteneur, dessine apres : on les cache
        // pendant que la liste est ouverte, sinon ils passent par-dessus
        mapControls.setVisibility(search.hasFocus() ? View.INVISIBLE : View.VISIBLE);
        if (!search.hasFocus()) {
            suggestions.setVisibility(View.GONE);
            return;
        }
        if (matches.isEmpty()) {
            // une ligne "aucun lieu", pour ne pas laisser l'utilisateur sans reponse
            TextView none = new TextView(this);
            none.setText(R.string.map_no_place);
            none.setTextColor(ContextCompat.getColor(this, R.color.muted));
            none.setTextSize(13);
            int pad = Math.round(14 * getResources().getDisplayMetrics().density);
            none.setPadding(pad, pad, pad, pad);
            suggestions.addView(none);
        }
        for (Place p : matches.subList(0, Math.min(MAX_SUGGESTIONS, matches.size()))) {
            View row = getLayoutInflater().inflate(R.layout.item_search_suggestion, suggestions, false);
            row.findViewById(R.id.suggestion_icon_box).setBackgroundTintList(
                    ColorStateList.valueOf(ContextCompat.getColor(this, p.category.markerColor)));
            ImageView icon = row.findViewById(R.id.suggestion_icon);
            icon.setImageResource(p.category.icon);
            icon.setImageTintList(ColorStateList.valueOf(ContextCompat.getColor(this,
                    p.category == Place.Category.BUILDINGS ? R.color.white : R.color.black)));
            ((TextView) row.findViewById(R.id.suggestion_name)).setText(p.name);
            String info = getString(p.shortCategory);
            if (lastLocation != null) {
                float[] d = new float[1];
                Location.distanceBetween(lastLocation.getLatitude(), lastLocation.getLongitude(), p.lat, p.lng, d);
                info = getString(R.string.search_info_distance, info, TrackingState.formatDistance(this, d[0]));
            }
            ((TextView) row.findViewById(R.id.suggestion_info)).setText(info);
            row.setOnClickListener(v -> {
                hideKeyboard();      // retire le focus : les suggestions se ferment
                showSheet(p, true);
            });
            suggestions.addView(row);
        }
        suggestions.setVisibility(View.VISIBLE);
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
        findViewById(R.id.btn_locate).setOnClickListener(v -> {
            // pendant un suivi ou un guidage, le bouton recentre la carte et la refait suivre
            // l'utilisateur
            if ((TrackingState.isRunning() || destination != null) && lastLocation != null) {
                js("CampusMap.follow()");
            } else {
                showMyPosition();
            }
        });
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
        findViewById(R.id.sheet_go).setOnClickListener(v -> {
            if (selected != null) startGuidance(selected);
        });
    }

    /**
     * Remplit la fiche du bas avec le lieu et met son marqueur en avant.
     *
     * @param place le lieu a afficher
     * @param pan   true pour deplacer la carte jusqu'au lieu (venue d'un autre ecran ou recherche),
     *              false s'il vient d'un clic sur son marqueur, deja visible
     */
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

    /*
     * Une seule methode decide de ce qui est visible, a partir de 3 etats :
     *
     *   suivi en cours | lieu selectionne | position connue -> visible
     *   oui            | -                | -               -> panneau de suivi
     *   non            | oui              | -               -> fiche du lieu
     *   non            | non              | oui             -> bouton "Demarrer le suivi"
     *   non            | non              | non             -> bouton + invitation a activer la position
     *
     * Elle est appelee apres chaque changement d'etat : on ne cache ou n'affiche jamais une vue
     * ailleurs, ce qui evite deux vues contradictoires a l'ecran.
     *
     * En haut, independamment : pendant un guidage, son panneau remplace la recherche et les
     * filtres. Le guidage et le suivi peuvent tourner en meme temps.
     */
    private void refreshOverlays() {
        boolean tracking = TrackingState.isRunning();
        boolean located = lastLocation != null || (wantsLocation() && hasLocationPermission());
        boolean guiding = destination != null;

        guidancePanel.setVisibility(guiding ? View.VISIBLE : View.GONE);
        searchBar.setVisibility(guiding ? View.GONE : View.VISIBLE);
        filtersBar.setVisibility(guiding ? View.GONE : View.VISIBLE);

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
        showToast(getString(text), 2200);
    }

    private void showToast(CharSequence text, long durationMs) {
        toastText.setText(text);
        handler.removeCallbacksAndMessages(null);
        toast.animate().alpha(1f).setDuration(150).start();
        handler.postDelayed(() -> toast.animate().alpha(0f).setDuration(250).start(), durationMs);
    }

    /* ====================================================================================
     * Permission de localisation
     * ==================================================================================== */

    /*
     * demande des deux permissions de localisation ; la reponse arrive dans ce callback.
     * registerForActivityResult remplace l'ancien couple requestPermissions /
     * onRequestPermissionsResult : il doit etre appele a la creation de l'Activity (d'ou un
     * champ), et Android affiche la fenetre systeme "Autoriser ... ?".
     * shouldShowRequestPermissionRationale renvoie false apres un refus definitif ("Ne plus
     * demander") : Android ne montrera plus la fenetre, seul l'ecran des reglages peut l'accorder.
     */
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

    /*
     * getPreferences (sans nom) ouvre le fichier de preferences propre a cette Activity, nomme
     * d'apres sa classe : shared_prefs/MapActivity.xml. Cle "show_location", false par defaut.
     * Le choix "afficher ma position" survit ainsi a la fermeture de l'appli.
     */
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

    /**
     * GPS "d'interface" : le point bleu quand aucun suivi ne tourne. Meme schema que le service
     * (LocationManager + LocationListener, une position par seconde), mais sans Looper explicite :
     * appele depuis le thread principal, il utilise la boucle de ce thread.
     * listeningLocation empeche d'abonner deux fois le meme listener.
     */
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
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 0f, uiListener);
        }
        if (network) {
            locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 1000, 0f, uiListener);
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
        // pendant un suivi, c'est le service qui place le point bleu : sinon deux sources se
        // contredisent et le point saute entre la position du trace et celle de l'ecran
        if (TrackingState.isRunning()) return;
        // une position reseau, moins precise, ne remplace pas une position GPS recente
        long now = SystemClock.elapsedRealtime();
        if (LocationManager.GPS_PROVIDER.equals(location.getProvider())) {
            lastUiGpsFix = now;
        } else if (lastUiGpsFix != 0 && now - lastUiGpsFix < 10_000) {
            return;
        }
        boolean first = lastLocation == null;
        lastLocation = location;
        js("CampusMap.showUser(" + coords(location.getLatitude(), location.getLongitude()) + ", "
                + centerOnNextFix + ")");
        centerOnNextFix = false;
        if (first) refreshOverlays();
        onGuidancePosition(location.getLatitude(), location.getLongitude());
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

    /**
     * Lance la session : la carte se vide (fiche fermee, ancien trace efface) puis suit
     * l'utilisateur, et le service demarre. Appele seulement apres l'accord de la permission
     * de localisation (et la question des notifications sur Android 13+).
     */
    private void startTrackingService() {
        if (!hasLocationPermission()) return;
        selected = null;
        // le lieu d'un guidage en cours reste mis en avant
        js("CampusMap.select(" + (destination != null ? destination.id : "null") + ", false)");
        js("CampusMap.clearTrack()");
        // la carte suit l'utilisateur des le depart ; glisser la carte du doigt arrete de suivre
        js("CampusMap.setTracking(true)");
        if (lastLocation != null) js("CampusMap.follow()");
        TrackingService.start(this);
        // l'etat du service est pret des que onStartCommand a tourne ; on rafraichit juste apres
        handler.postDelayed(this::refreshTrackingUi, 300);
    }

    /*
     * messages du service : nouvelle position, ou fin de la session.
     * Receiver dynamique (enregistre dans onResume, retire dans onPause), RECEIVER_NOT_EXPORTED :
     * seule notre appli peut lui envoyer un message. onReceive tourne sur le thread principal.
     */
    private final BroadcastReceiver trackingReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getBooleanExtra(TrackingService.EXTRA_RUNNING, false)) {
                double lat = intent.getDoubleExtra(TrackingService.EXTRA_LAT, 0);
                double lng = intent.getDoubleExtra(TrackingService.EXTRA_LNG, 0);
                float bearing = intent.getFloatExtra(TrackingService.EXTRA_BEARING, -1f);
                boolean first = lastLocation == null;
                Location here = new Location("tracking");
                here.setLatitude(lat);
                here.setLongitude(lng);
                lastLocation = here;
                // le point bleu bouge a chaque position ; le trace ne s'allonge que si on a avance
                if (intent.getBooleanExtra(TrackingService.EXTRA_COUNTED, false)) {
                    js("CampusMap.addTrackPoint(" + coords(lat, lng) + ")");
                }
                js("CampusMap.showUser(" + coords(lat, lng) + ", false, " + Math.round(bearing) + ")");
                if (first) js("CampusMap.follow()");
                refreshTrackingUi();
                // pendant un suivi, le GPS d'interface se tait : le guidage avance avec le service
                onGuidancePosition(lat, lng);
            } else {
                // le trace reste affiche jusqu'au prochain suivi, la carte arrete de suivre
                js("CampusMap.setTracking(false)");
                refreshTrackingUi();
                double meters = intent.getDoubleExtra(TrackingService.EXTRA_METERS, 0);
                long minutes = Math.round(intent.getLongExtra(TrackingService.EXTRA_SECONDS, 0) / 60.0);
                showToast(getString(R.string.tracking_summary,
                        TrackingState.formatDistance(MapActivity.this, meters), minutes), 4000);
            }
        }
    };

    /**
     * Met a jour le panneau de suivi depuis TrackingState. Le Chronometer compte tout seul a
     * partir de sa base (l'instant du depart, horloge elapsedRealtime) : pas besoin de le
     * rafraichir chaque seconde depuis le code.
     */
    private void refreshTrackingUi() {
        if (TrackingState.isRunning()) {
            trackingTime.setBase(TrackingState.startElapsed());
            trackingTime.start();
            trackingDistance.setText(TrackingState.formatDistance(this, TrackingState.distanceMeters()));
        } else {
            trackingTime.stop();
        }
        refreshOverlays();
    }

    /* ====================================================================================
     * Guidage a pied vers un lieu ("Y aller")
     * ==================================================================================== */

    /*
     * Deroulement :
     *  1. "Y aller" sur la fiche -> startGuidance : permission, GPS d'interface, panneau du haut.
     *  2. a la premiere position, requestRoute demande l'itineraire au serveur, sur routeThread.
     *  3. a chaque position suivante (GPS d'interface, ou service pendant un suivi),
     *     onGuidancePosition situe l'utilisateur sur l'itineraire (Guidance.update) et met a
     *     jour la fleche, la phrase, les minutes restantes et la ligne sur la carte.
     *  4. a moins de 20 m du lieu -> "Vous etes arrive", fin du guidage.
     * Sans reseau, l'appli guide "a vol d'oiseau" : direction et distance en ligne droite.
     */

    private void setupGuidance() {
        findViewById(R.id.guide_stop).setOnClickListener(v -> stopGuidance());
        // le panneau de guidage est plus haut que la recherche : on descend les boutons de la
        // carte juste en dessous, chaque fois que sa taille ou sa visibilite change
        guidancePanel.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> placeControls());
    }

    private void placeControls() {
        float gap = 10 * getResources().getDisplayMetrics().density;
        float overlap = guidancePanel.getVisibility() == View.VISIBLE
                ? guidancePanel.getBottom() + gap - mapControls.getTop() : 0;
        mapControls.setTranslationY(Math.max(0, overlap));
    }

    /** "Y aller" : guide l'utilisateur jusqu'a ce lieu */
    private void startGuidance(Place place) {
        withLocationPermission(() -> {
            setWantsLocation(true);
            destination = place;
            guidance = null;
            routeRequest++;          // une reponse en attente pour un autre lieu sera ignoree
            routePending = false;
            selected = null;         // la fiche se ferme, le marqueur du lieu reste en avant
            js("CampusMap.select(" + place.id + ", false)");
            js("CampusMap.clearRoute()");
            hideKeyboard();

            guideDestination.setText(getString(R.string.nav_to, getString(place.name))
                    .toUpperCase(Locale.getDefault()));
            guideArrow.setImageResource(R.drawable.ic_route);
            guideArrow.setRotation(0);
            guideInstruction.setText(lastLocation != null ? R.string.nav_computing : R.string.nav_waiting);
            guideDetail.setText("");
            guideMinutes.setText("");
            guideInfo.setText("");
            refreshOverlays();

            startLocationUpdates();
            // position deja connue : on calcule tout de suite, sinon a la premiere position
            if (lastLocation != null) {
                requestRoute(lastLocation.getLatitude(), lastLocation.getLongitude());
            }
        });
    }

    private void stopGuidance() {
        destination = null;
        guidance = null;
        routeRequest++;
        routePending = false;
        js("CampusMap.clearRoute()");
        js("CampusMap.select(" + (selected != null ? selected.id : "null") + ", false)");
        refreshOverlays();
    }

    /**
     * Calcule l'itineraire sur routeThread (le reseau est interdit sur le thread principal),
     * puis revient sur le thread principal avec runOnUiThread.
     * runOnUiThread plutot que handler.post : showToast vide les messages du handler.
     */
    private void requestRoute(double lat, double lng) {
        if (destination == null || routePending) return;
        routePending = true;
        lastRouteTime = SystemClock.elapsedRealtime();
        int request = ++routeRequest;
        double toLat = destination.lat;
        double toLng = destination.lng;
        routeThread.execute(() -> {
            Route route;
            try {
                route = RouteClient.walking(lat, lng, toLat, toLng);
            } catch (Exception e) {
                // pas de reseau, serveur en panne, aucun chemin : on guidera a vol d'oiseau
                Log.w(TAG, "itineraire indisponible", e);
                route = null;
            }
            Route result = route;
            runOnUiThread(() -> onRouteReady(request, result));
        });
    }

    private void onRouteReady(int request, Route route) {
        // reponse perimee (guidage arrete ou autre lieu choisi) ou ecran deja detruit
        if (request != routeRequest || destination == null || isDestroyed()) return;
        routePending = false;
        boolean first = guidance == null;
        if (route == null && (first || !guidance.isDirect())) showToast(getString(R.string.nav_direct), 3500);
        guidance = new Guidance(route, destination.lat, destination.lng);
        if (lastLocation != null) {
            if (first && !guidance.isDirect()) {
                // au depart, la carte cadre tout le trajet
                js("CampusMap.showRoute(" + toJson(guidance.points()) + ", true)");
            }
            onGuidancePosition(lastLocation.getLatitude(), lastLocation.getLongitude());
        }
    }

    /** nouvelle position pendant un guidage : avancement sur l'itineraire et panneau a jour */
    private void onGuidancePosition(double lat, double lng) {
        if (destination == null) return;
        if (guidance == null) {
            // premiere position apres "Y aller" : on calcule l'itineraire
            guideInstruction.setText(R.string.nav_computing);
            requestRoute(lat, lng);
            return;
        }

        Guidance.Progress p = guidance.update(lat, lng);
        if (p.arrived) {
            Place reached = destination;
            stopGuidance();
            showToast(getString(R.string.nav_arrived, getString(reached.name)), 4000);
            return;
        }
        js("CampusMap.showRoute(" + toJson(p.remaining) + ", false)");

        // minutes restantes et heure d'arrivee, recalculees a chaque position
        long seconds = Math.round(p.remainingMeters / WALK_SPEED);
        guideMinutes.setText(getString(R.string.nav_minutes, Math.max(1, Math.round(seconds / 60.0))));
        String arrival = DateFormat.getTimeFormat(this).format(new Date(System.currentTimeMillis() + seconds * 1000));
        guideInfo.setText(getString(R.string.nav_info,
                TrackingState.formatDistance(this, rounded(p.remainingMeters)), arrival));

        // la phrase et la fleche ; ic_arrow pointe vers la droite, d'ou le -90 degres
        if (guidance.isDirect()) {
            guideArrow.setImageResource(R.drawable.ic_arrow);
            guideArrow.setRotation(p.bearing - 90);
            guideInstruction.setText(getString(R.string.nav_head, Instructions.compass(this, p.bearing)));
            guideDetail.setText(R.string.nav_direct_short);
        } else if ("arrive".equals(p.next.type)) {
            guideArrow.setImageResource(R.drawable.ic_pin);
            guideArrow.setRotation(0);
            guideInstruction.setText(Instructions.text(this, p.next));
            guideDetail.setText(getString(R.string.nav_in, TrackingState.formatDistance(this, rounded(p.toNextMeters))));
        } else {
            guideArrow.setImageResource(R.drawable.ic_arrow);
            guideArrow.setRotation(Instructions.arrowAngle(p.next) - 90);
            guideInstruction.setText(Instructions.text(this, p.next));
            guideDetail.setText(getString(R.string.nav_in, TrackingState.formatDistance(this, rounded(p.toNextMeters))));
        }

        // l'utilisateur s'est ecarte de l'itineraire, ou on est a vol d'oiseau depuis un moment :
        // on redemande un itineraire depuis sa position (au plus une fois toutes les 15 ou 30 s)
        long since = SystemClock.elapsedRealtime() - lastRouteTime;
        if ((p.offRouteMeters > Guidance.OFF_ROUTE_METERS && since > REROUTE_DELAY_MS)
                || (guidance.isDirect() && since > RETRY_ROUTE_MS)) {
            requestRoute(lat, lng);
        }
    }

    /** arrondi a 5 m (10 m au-dela de 100 m) : le chiffre ne tremble pas a chaque position */
    private static double rounded(double meters) {
        double step = meters < 100 ? 5 : 10;
        return Math.round(meters / step) * step;
    }

    /** [[lat, lng], ...] pour map.js */
    private static String toJson(List<double[]> points) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < points.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append('[').append(coords(points.get(i)[0], points.get(i)[1])).append(']');
        }
        return sb.append(']').toString();
    }

    /* ====================================================================================
     * Hors connexion
     * ==================================================================================== */

    private void updateOfflineBanner() {
        findViewById(R.id.offline_banner).setVisibility(isOnline() ? View.GONE : View.VISIBLE);
    }

    /**
     * @return true si le reseau actif annonce un acces Internet (permission ACCESS_NETWORK_STATE).
     *         Ne garantit pas que les serveurs de tuiles repondent : map.js previent aussi par
     *         onTilesError.
     */
    private boolean isOnline() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        Network network = cm.getActiveNetwork();
        if (network == null) return false;
        NetworkCapabilities caps = cm.getNetworkCapabilities(network);
        return caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
    }
}
