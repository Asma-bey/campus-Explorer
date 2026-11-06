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
    var places = [];          // [{id, name, cat, lat, lng}]
    var markers = {};         // id -> L.marker
    var selectedId = null;
    var userMarker = null;
    var userPosition = null;
    var trackUnder = null;    // trait noir large sous le trace
    var trackLine = null;     // trait jaune du trace

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

    function init() {
        map = L.map("map", {
            zoomControl: false,          // nos boutons + et - sont des vues Android
            attributionControl: true
        }).setView([49.0367, 2.0775], 15);

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

        /** n'affiche que les lieux de la categorie ("all" pour tout) dont le nom contient query */
        filter: function (category, query) {
            var q = (query || "").toLowerCase();
            var visible = 0;
            places.forEach(function (place) {
                var show = (category === "all" || place.cat === category)
                    && place.name.toLowerCase().indexOf(q) !== -1;
                var m = markers[place.id];
                if (show) {
                    visible++;
                    if (!map.hasLayer(m)) m.addTo(map);
                } else if (map.hasLayer(m)) {
                    map.removeLayer(m);
                }
            });
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
            if (!places.length) return;
            var bounds = L.latLngBounds(places.map(function (p) { return [p.lat, p.lng]; }));
            map.fitBounds(bounds, { paddingTopLeft: [24, 150], paddingBottomRight: [70, 170], maxZoom: 16 });
        },

        zoomIn: function () { map.zoomIn(); },
        zoomOut: function () { map.zoomOut(); },

        /** point bleu de l'utilisateur ; center = true pour recentrer la carte dessus */
        showUser: function (lat, lng, center) {
            userPosition = [lat, lng];
            if (!userMarker) {
                userMarker = L.marker(userPosition, {
                    icon: L.divIcon({
                        className: "user-location",
                        html: '<div class="halo"><div class="dot"></div></div>',
                        iconSize: [64, 64],
                        iconAnchor: [32, 32]
                    }),
                    interactive: false,
                    keyboard: false
                }).addTo(map);
            } else {
                userMarker.setLatLng(userPosition);
            }
            if (center) map.flyTo(userPosition, Math.max(map.getZoom(), 17), { duration: 0.6 });
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
            if (!trackLine) {
                trackUnder = L.polyline(points, { color: "#090909", weight: 7, opacity: 0.9 }).addTo(map);
                trackLine = L.polyline(points, { color: "#ffe500", weight: 4 }).addTo(map);
            } else {
                trackUnder.setLatLngs(points);
                trackLine.setLatLngs(points);
            }
        },

        addTrackPoint: function (lat, lng) {
            if (!trackLine) {
                this.setTrack([[lat, lng]]);
            } else {
                trackUnder.addLatLng([lat, lng]);
                trackLine.addLatLng([lat, lng]);
            }
        },

        clearTrack: function () {
            if (trackLine) {
                map.removeLayer(trackLine);
                map.removeLayer(trackUnder);
                trackLine = null;
                trackUnder = null;
            }
        }
    };

    init();
})();
