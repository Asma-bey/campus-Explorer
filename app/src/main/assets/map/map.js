/*
 * Carte de Campus Explorer avec Leaflet.
 *
 * Deux sens de communication avec Android :
 *  - Android -> JavaScript : MapActivity appelle webView.evaluateJavascript("CampusMap.xxx(...)")
 *  - JavaScript -> Android : l'objet "Android" est injecte par addJavascriptInterface ;
 *    ses methodes annotees @JavascriptInterface (MapActivity.MapBridge) sont appelees ici.
 */
(function () {
    "use strict";

    // icones des categories : les memes traces que les vector drawables de l'appli
    var ICONS = {
        buildings: '<path d="M4 21V5l8-3 8 3v16M8 8h2M14 8h2M8 12h2M14 12h2M8 16h2M14 16h2M2 21h20"/>',
        library: '<path d="M4 4c3-1 6 0 8 2v15c-2-2-5-3-8-2ZM20 4c-3-1-6 0-8 2v15c2-2 5-3 8-2Z"/>',
        food: '<path d="M7 3v8M4 3v5a3 3 0 0 0 6 0V3M7 11v10M16 3v18M16 3c4 2 5 7 0 10"/>',
        services: '<circle cx="9" cy="8" r="3"/><path d="M3 20c0-4 2-7 6-7s6 3 6 7M16 4a3 3 0 0 1 0 6M17 13c3 .5 4 3 4 6"/>'
    };

    var map;
    var places = [];          // [{id, name, text, cat, lat, lng}] ; text = nom + details en minuscules
    var markers = {};         // id -> L.marker
    var selectedId = null;
    var userMarker = null;
    var userPosition = null;
    var trackUnder = null;    // trait noir large sous le trace
    var trackLine = null;     // trait jaune du trace
    var trackPoints = [];     // points retenus par le service : [[lat, lng], ...]
    var tracking = false;     // un suivi est en cours
    var following = false;    // la carte suit l'utilisateur (mode navigation)

    // centre de Cergy-Pontoise : la vue d'ensemble cadre les sites proches, pas Versailles
    var CERGY = L.latLng(49.0367, 2.0775);
    var NEAR_CERGY_M = 8000;

    function bridge() {
        // absent si la page est ouverte dans un navigateur ordinaire (pour tester)
        return window.Android || null;
    }

    function markerIcon(place, selected) {
        var size = selected ? 44 : 36;
        // la pointe de la goutte est sous le centre, a demi-diagonale : size * racine(2) / 2
        var tip = size / 2 + size * Math.SQRT2 / 2;
        return L.divIcon({
            className: "place-marker cat-" + place.cat + (selected ? " selected" : ""),
            html: '<div class="pin"><svg width="15" height="15" viewBox="0 0 24 24" fill="none" '
                + 'stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">'
                + ICONS[place.cat] + "</svg></div>",
            iconSize: [size, size],
            iconAnchor: [size / 2, tip]
        });
    }

    /** distanceTo de Leaflet : distance en metres sur la sphere terrestre (formule de haversine) */
    function isNearCergy(place) {
        return CERGY.distanceTo([place.lat, place.lng]) < NEAR_CERGY_M;
    }

    /** cadre ces lieux, en laissant de la place pour la recherche, les filtres et les boutons */
    function fit(list) {
        if (!list.length) return;
        var bounds = L.latLngBounds(list.map(function (p) { return [p.lat, p.lng]; }));
        map.fitBounds(bounds, { paddingTopLeft: [24, 150], paddingBottomRight: [70, 170], maxZoom: 16 });
    }

    /** le trace + un dernier segment jusqu'au point bleu, pour que la ligne colle a l'utilisateur */
    function drawTrack() {
        var points = trackPoints.slice();
        if (tracking && userPosition && points.length) points.push(userPosition);
        if (!points.length) {
            if (trackLine) {
                map.removeLayer(trackLine);
                map.removeLayer(trackUnder);
                trackLine = null;
                trackUnder = null;
            }
            return;
        }
        // deux polylignes superposees (noire large dessous, jaune fine dessus) : un trait jaune
        // borde de noir, lisible sur toutes les couleurs de la carte. On les cree une fois, puis
        // on change seulement leurs points (setLatLngs), sans recreer de calque a chaque position
        if (!trackLine) {
            trackUnder = L.polyline(points, { color: "#090909", weight: 7, opacity: 0.9 }).addTo(map);
            trackLine = L.polyline(points, { color: "#ffe500", weight: 4 }).addTo(map);
        } else {
            trackUnder.setLatLngs(points);
            trackLine.setLatLngs(points);
        }
    }

    function init() {
        map = L.map("map", {
            zoomControl: false,          // nos boutons + et - sont des vues Android
            attributionControl: true
        }).setView([49.0367, 2.0775], 15);

        // fond de carte : images de 256 x 256 px decoupees par niveau de zoom z et colonne/ligne
        // x, y ; Leaflet ne telecharge que celles de la zone visible
        var tiles = L.tileLayer("https://tile.openstreetmap.org/{z}/{x}/{y}.png", {
            maxZoom: 19,
            className: "osm-tiles",
            attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>'
        });
        var tileErrorSent = false;
        tiles.on("tileerror", function () {
            // pas de reseau : on previent Android une seule fois, la carte garde ses marqueurs
            if (!tileErrorSent && bridge()) {
                tileErrorSent = true;
                bridge().onTilesError();
            }
        });
        tiles.on("tileload", function () {
            if (tileErrorSent && bridge()) {
                tileErrorSent = false;
                bridge().onTilesLoaded();
            }
        });
        tiles.addTo(map);
        map.attributionControl.setPrefix(false);

        // l'utilisateur deplace la carte du doigt : on arrete de la recentrer sur lui
        map.on("dragstart", function () { following = false; });

        if (bridge()) bridge().onMapReady();
    }

    window.CampusMap = {
        /** remplace tous les lieux (liste JSON envoyee par Android) */
        setPlaces: function (list) {
            Object.keys(markers).forEach(function (id) { map.removeLayer(markers[id]); });
            markers = {};
            places = list;
            places.forEach(function (place) {
                var m = L.marker([place.lat, place.lng], {
                    icon: markerIcon(place, place.id === selectedId),
                    title: place.name,
                    riseOnHover: true
                });
                m.on("click", function () {
                    if (bridge()) bridge().onPlaceClicked(place.id);
                });
                markers[place.id] = m;
                m.addTo(map);
            });
        },

        /** n'affiche que les lieux de la categorie ("all" pour tout) dont le nom ou les details contiennent query */
        filter: function (category, query) {
            // minuscules et sans accents, comme le "text" prepare par Android :
            // NFD separe "ê" en "e" + accent combinant, et l'expression retire ces accents
            // (plage Unicode U+0300 a U+036F)
            var q = (query || "").normalize("NFD").replace(/[̀-ͯ]/g, "").toLowerCase();
            var visible = 0;
            places.forEach(function (place) {
                var show = (category === "all" || place.cat === category)
                    && (place.text || place.name.toLowerCase()).indexOf(q) !== -1;
                var m = markers[place.id];
                if (show) {
                    visible++;
                    if (!map.hasLayer(m)) m.addTo(map);
                } else if (map.hasLayer(m)) {
                    map.removeLayer(m);
                }
            });
            // une recherche ou une categorie : on cadre les lieux trouves (sauf pendant un suivi)
            if (!following && (q || category !== "all")) {
                var shown = places.filter(function (place) { return map.hasLayer(markers[place.id]); });
                // pour une categorie, les lieux proches suffisent ; une recherche peut viser Versailles
                var near = shown.filter(isNearCergy);
                fit(q || !near.length ? shown : near);
            }
            return visible;
        },

        /** met un lieu en avant (marqueur plus gros) ; id null pour aucun */
        select: function (id, pan) {
            selectedId = id;
            places.forEach(function (place) {
                var m = markers[place.id];
                m.setIcon(markerIcon(place, place.id === id));
                m.setZIndexOffset(place.id === id ? 1000 : 0);
                if (place.id === id && pan) {
                    map.flyTo([place.lat, place.lng], Math.max(map.getZoom(), 16), { duration: 0.6 });
                }
            });
        },

        /** cadre tous les lieux, en laissant de la place pour la recherche, les filtres et les boutons */
        fitPlaces: function () {
            fit(places.filter(isNearCergy));
        },

        zoomIn: function () { map.zoomIn(); },
        zoomOut: function () { map.zoomOut(); },

        /**
         * Point bleu de l'utilisateur ; center = true pour recentrer la carte dessus.
         * bearing : direction du deplacement en degres (0 = nord), absente ou -1 a l'arret.
         */
        showUser: function (lat, lng, center, bearing) {
            userPosition = [lat, lng];
            if (!userMarker) {
                userMarker = L.marker(userPosition, {
                    icon: L.divIcon({
                        className: "user-location",
                        html: '<div class="halo"><div class="arrow"></div><div class="dot"></div></div>',
                        iconSize: [64, 64],
                        iconAnchor: [32, 32]
                    }),
                    interactive: false,
                    keyboard: false
                }).addTo(map);
            } else {
                userMarker.setLatLng(userPosition);
            }
            // la fleche indique la direction de la marche, comme dans Google Maps
            var arrow = userMarker.getElement() && userMarker.getElement().querySelector(".arrow");
            if (arrow) {
                var moving = typeof bearing === "number" && bearing >= 0;
                arrow.style.display = moving ? "block" : "none";
                if (moving) arrow.style.transform = "rotate(" + bearing + "deg)";
            }
            if (center) {
                map.flyTo(userPosition, Math.max(map.getZoom(), 17), { duration: 0.6 });
            } else if (following) {
                // la camera accompagne l'utilisateur en douceur
                map.panTo(userPosition, { animate: true, duration: 0.8 });
            }
            if (tracking) drawTrack();
        },

        /** recentre la carte sur l'utilisateur et la fait suivre ses deplacements */
        follow: function () {
            following = true;
            if (userPosition) map.flyTo(userPosition, Math.max(map.getZoom(), 17), { duration: 0.6 });
        },

        /** debut (true) ou fin (false) d'un suivi : la carte suit l'utilisateur pendant le suivi */
        setTracking: function (on) {
            tracking = on;
            following = on;
            drawTrack();
        },

        hideUser: function () {
            if (userMarker) {
                map.removeLayer(userMarker);
                userMarker = null;
            }
            userPosition = null;
        },

        /** trace du parcours en cours : liste de [lat, lng] */
        setTrack: function (points) {
            trackPoints = points;
            drawTrack();
        },

        addTrackPoint: function (lat, lng) {
            trackPoints.push([lat, lng]);
            drawTrack();
        },

        clearTrack: function () {
            trackPoints = [];
            drawTrack();
        }
    };

    init();
})();
