# CY Campus Explorer

Asma BACHIR BEY and Patrick-Léon GANDONOU, L3 Informatique, CY Cergy Paris Université  
Mobile Computing, project

Campus Explorer helps students find their way around the CY Cergy campus. It shows a map with the
main places (teaching sites, university library, university restaurant, student house), displays
the user's position, and can record a walk as a tracking session that keeps running in the
background.

Java, XML layouts, Android Studio. minSdk 26 (Android 8.0), targetSdk 36. The map is OpenStreetMap,
displayed with Leaflet.js inside a WebView.

| Home | Map | Place details | Tracking | Saved places |
|---|---|---|---|---|
| ![Home](docs/screenshots/01_home.jpg) | ![Map](docs/screenshots/02_map.jpg) | ![Details](docs/screenshots/03_place_details.jpg) | ![Tracking](docs/screenshots/05_tracking.jpg) | ![Saved](docs/screenshots/07_saved.jpg) |

## Running the project

1. Open the folder in Android Studio (**File > Open**) and let Gradle sync.
2. Run on a phone or an emulator with Internet access (map tiles).
3. On the emulator, set a position in Cergy: **⋯ > Location**, search "Cergy", **Set location**.
   To simulate a walk, change the position a few times while tracking is running.

## Screens and Activities

| Activity | Screen | Main elements |
|---|---|---|
| `MainActivity` | Home (launcher) | categories, three places to discover, buttons to the map and to saved places |
| `MapActivity` | Campus map | Leaflet map in a WebView, search, category filters, place sheet, location, start/stop tracking |
| `SavedActivity` | Saved places and history | places saved with the bookmark button, last tracking sessions |

A bottom bar links the three screens. Navigation uses explicit Intents with
`FLAG_ACTIVITY_REORDER_TO_FRONT`, so each Activity exists once in the back stack. The identifier of
a place (`EXTRA_PLACE_ID`) or a category (`EXTRA_CATEGORY`) travels as an Intent extra;
`MapActivity` reads it in `onCreate` or, when it already exists, in `onNewIntent`.

## Course concepts

| Concept | Where |
|---|---|
| Activities, explicit Intents, extras | `BaseActivity.openTab`, `MainActivity.openMap`, `MapActivity.handleIntent` |
| Activity lifecycle | `BaseActivity` logs every callback (Logcat tag `Lifecycle`). `MapActivity.onResume` starts the on-screen location updates and registers the tracking receiver; `onPause` stops and unregisters them |
| Background service | `TrackingService`: foreground service of type `location`, persistent notification with a STOP action, independent of the map screen |
| Sensor | GPS (`LocationManager.GPS_PROVIDER`, network as a complement indoors) |
| Location and maps | runtime permissions with `ActivityResultContracts`, Leaflet + OpenStreetMap tiles in a `WebView` |
| Local data | saved places in `SharedPreferences` (`SavedPlaces`), session summaries in SQLite (`SessionDb`) |

## The map: Leaflet in a WebView

The page `app/src/main/assets/map/index.html` loads Leaflet (bundled in `assets/map/leaflet`, no
CDN) and `map.js`. It is served by `WebViewAssetLoader` at
`https://appassets.androidplatform.net/assets/map/index.html`: with a `file://` URL the page has no
web origin, and the OpenStreetMap tile servers reject requests without a Referer (HTTP 403). The
WebView user agent also identifies the app, as the OpenStreetMap tile usage policy requires.

Java and JavaScript talk both ways:

- Java → JavaScript: `webView.evaluateJavascript("CampusMap.select(3, true)")`;
- JavaScript → Java: `Android.onPlaceClicked(id)`, through `addJavascriptInterface`. These
  callbacks run on a WebView thread, so they use `runOnUiThread` before touching any view.

The search text is passed through `JSONObject.quote` so that whatever the user types cannot break
or inject JavaScript.

## Location and tracking

- Location permission is requested only when the user needs it ("Afficher ma position" or
  "Démarrer le suivi"), after a dialog that explains why. If it is denied, the map still shows the
  campus places and nothing crashes.
- **On-screen location** (the blue dot) is active only between `onResume` and `onPause` of
  `MapActivity`.
- **Tracking** is done by `TrackingService`, started by the user with `startForegroundService`. It
  calls `startForeground` with type `FOREGROUND_SERVICE_TYPE_LOCATION`, shows an ongoing
  notification (chronometer, distance, STOP action), and receives GPS updates while the screen is
  off or another app is open. Positions with an accuracy worse than 35 m are ignored so that they
  do not inflate the distance.
- When the session stops (from the map or from the notification), the service saves the summary in
  SQLite, removes the location updates, removes the notification and stops itself. It never runs
  without an active session (`START_NOT_STICKY`).
- On Android 13+, the notification permission is requested before the first session.

Tested on the emulator (API 37): permission dialog, denied permission, position display, a
simulated walk of 660 m with the app in the background (`MapActivity.onStop`, service still in
the foreground), stop from the notification, session in the history, map without network.

## Design

The interface follows the Figma prototype (`design/`): the same colors (`res/values/colors.xml`
copies the CSS variables), the Inter font (bundled in `res/font`, OFL licence), the icons
(converted to vector drawables from the prototype's SVG paths), and the same screens and states.
Text sizes are about 1.3 times those of the prototype, which was drawn small; Android recommends
at least 11sp for readable text. The OpenStreetMap tiles get a CSS filter to look closer to the
muted map of the prototype.

The places' coordinates come from OpenStreetMap (Nominatim and Overpass, November 2026). The four
places of the prototype come first; three more places complete the map.

## Files

| File | Role |
|---|---|
| `BaseActivity.java` | lifecycle logs, bottom bar, system bar insets |
| `MainActivity.java`, `activity_main.xml` | home screen |
| `MapActivity.java`, `activity_map.xml` | map screen, permissions, on-screen location |
| `SavedActivity.java`, `activity_saved.xml` | saved places and history |
| `Dialogs.java`, `dialog_confirm.xml` | dialogs of the prototype |
| `tracking/TrackingService.java` | foreground tracking service and its notification |
| `tracking/TrackingState.java` | state of the running session, shared with the screens |
| `data/Place.java`, `data/Places.java` | predefined places |
| `data/SavedPlaces.java` | saved places (SharedPreferences) |
| `data/SessionDb.java` | session history (SQLite) |
| `assets/map/` | Leaflet page, styles and script |
| `design/` | Figma Make prototype (source code and `.make` file) |

## Limits

- Map tiles need Internet. Without it, a message is shown and already cached tiles and all places
  stay available.
- Tracking stops if Android kills the app's process; the session in progress is then lost.
- Sessions store only a summary (start, duration, distance), not the route itself.
