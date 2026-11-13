package fr.cyu.campusexplorer.navigation;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Demande un itineraire A PIED au serveur de routage OSRM de l'association FOSSGIS
 * (routing.openstreetmap.de, profil "foot"), calcule sur les chemins d'OpenStreetMap.
 *
 * Attention : walking() fait un appel reseau bloquant. Android interdit le reseau sur le
 * thread principal (NetworkOnMainThreadException) : MapActivity l'appelle depuis un thread
 * a part (ExecutorService) et revient sur le thread principal avec runOnUiThread.
 */
public final class RouteClient {

    /*
     * API OSRM : /route/v1/{profil}/{lng},{lat};{lng},{lat}. Sur ce serveur, c'est le chemin
     * "routed-foot" qui choisit la marche ; le mot "driving" est seulement impose par l'API.
     * Attention a l'ordre : OSRM attend la LONGITUDE avant la latitude.
     */
    private static final String SERVER = "https://routing.openstreetmap.de/routed-foot/route/v1/driving/";
    /** la politique d'utilisation du serveur demande un User-Agent qui identifie l'application */
    private static final String USER_AGENT = "CampusExplorer/1.0 (projet etudiant CY Cergy)";
    private static final int TIMEOUT_MS = 8000;

    private RouteClient() {
    }

    /**
     * @return l'itineraire a pied entre les deux points
     * @throws IOException   pas de reseau, serveur injoignable ou aucun chemin trouve
     * @throws JSONException reponse illisible
     */
    public static Route walking(double fromLat, double fromLng, double toLat, double toLng)
            throws IOException, JSONException {
        // Locale.US : un point decimal, sinon "49,03" casserait l'adresse
        String url = SERVER + String.format(Locale.US, "%.6f,%.6f;%.6f,%.6f", fromLng, fromLat, toLng, toLat)
                + "?overview=false&steps=true&geometries=geojson";
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(TIMEOUT_MS);
        connection.setReadTimeout(TIMEOUT_MS);
        connection.setRequestProperty("User-Agent", USER_AGENT);
        try {
            int code = connection.getResponseCode();
            if (code != HttpURLConnection.HTTP_OK) throw new IOException("HTTP " + code);
            try (InputStream in = connection.getInputStream()) {
                return parse(readAll(in));
            }
        } finally {
            connection.disconnect();
        }
    }

    /** InputStream.readAllBytes n'existe qu'a partir d'Android 13 : on lit par blocs */
    private static String readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int n;
        while ((n = in.read(buffer)) != -1) out.write(buffer, 0, n);
        return out.toString(StandardCharsets.UTF_8.name());
    }

    /*
     * Reponse OSRM (extrait) :
     * {"code":"Ok","routes":[{"legs":[{"steps":[
     *     {"geometry":{"coordinates":[[2.0698,49.0390],[2.0697,49.0388],...]},
     *      "maneuver":{"type":"turn","modifier":"left","bearing_after":209,...},
     *      "name":"Rue X", ...}, ...]}]}]}
     * Chaque etape a sa propre ligne, qui commence au point de sa manoeuvre. On les met bout a
     * bout pour obtenir toute la ligne, en notant l'indice ou commence chaque etape.
     */
    static Route parse(String json) throws IOException, JSONException {
        JSONObject root = new JSONObject(json);
        if (!"Ok".equals(root.optString("code"))) {
            throw new IOException("OSRM : " + root.optString("code"));
        }
        JSONArray jsonSteps = root.getJSONArray("routes").getJSONObject(0)
                .getJSONArray("legs").getJSONObject(0).getJSONArray("steps");

        List<double[]> points = new ArrayList<>();
        List<Route.Step> steps = new ArrayList<>();
        for (int i = 0; i < jsonSteps.length(); i++) {
            JSONObject step = jsonSteps.getJSONObject(i);
            JSONArray coords = step.getJSONObject("geometry").getJSONArray("coordinates");
            // le premier point d'une etape est le dernier de la precedente : on ne le double pas
            int stepStart = points.isEmpty() ? 0 : points.size() - 1;
            for (int j = points.isEmpty() ? 0 : 1; j < coords.length(); j++) {
                JSONArray c = coords.getJSONArray(j);
                points.add(new double[]{c.getDouble(1), c.getDouble(0)});   // [lng, lat] -> {lat, lng}
            }
            JSONObject maneuver = step.getJSONObject("maneuver");
            steps.add(new Route.Step(
                    maneuver.optString("type"),
                    maneuver.optString("modifier"),
                    step.optString("name"),
                    maneuver.optInt("bearing_after"),
                    stepStart));
        }
        if (points.size() < 2) throw new IOException("itineraire vide");
        return new Route(points, steps);
    }
}
