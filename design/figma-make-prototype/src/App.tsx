import { useEffect, useMemo, useState } from "react";

type Screen = "home" | "map" | "saved";
type Category = "Tout" | "Bâtiments" | "Bibliothèque" | "Restauration" | "Services";

type Place = {
  id: number;
  name: string;
  category: Exclude<Category, "Tout">;
  shortCategory: string;
  description: string;
  className: string;
};

const places: Place[] = [
  {
    id: 1,
    name: "Bibliothèque universitaire",
    category: "Bibliothèque",
    shortCategory: "Bibliothèque",
    description: "Un espace dédié au travail, à la lecture et à l'étude.",
    className: "marker-library",
  },
  {
    id: 2,
    name: "Restaurant universitaire",
    category: "Restauration",
    shortCategory: "Restauration",
    description: "Une pause conviviale pour déjeuner entre deux cours.",
    className: "marker-food",
  },
  {
    id: 3,
    name: "Bâtiment d'enseignement",
    category: "Bâtiments",
    shortCategory: "Bâtiment",
    description: "Salles de cours et espaces pédagogiques du campus.",
    className: "marker-building",
  },
  {
    id: 4,
    name: "Maison des étudiants",
    category: "Services",
    shortCategory: "Service étudiant",
    description: "Informations, accompagnement et vie associative.",
    className: "marker-service",
  },
];

function Icon({
  name,
  size = 22,
}: {
  name:
    | "home"
    | "map"
    | "bookmark"
    | "pin"
    | "search"
    | "locate"
    | "building"
    | "book"
    | "food"
    | "users"
    | "arrow"
    | "close"
    | "route"
    | "trash"
    | "clock"
    | "check";
  size?: number;
}) {
  const paths: Record<string, React.ReactNode> = {
    home: (
      <>
        <path d="m3 11 9-8 9 8" />
        <path d="M5 10v11h14V10M9 21v-7h6v7" />
      </>
    ),
    map: (
      <>
        <path d="m3 6 6-3 6 3 6-3v15l-6 3-6-3-6 3Z" />
        <path d="M9 3v15M15 6v15" />
      </>
    ),
    bookmark: <path d="M6 3h12v18l-6-4-6 4Z" />,
    pin: (
      <>
        <path d="M20 10c0 5-8 11-8 11S4 15 4 10a8 8 0 1 1 16 0Z" />
        <circle cx="12" cy="10" r="2.4" />
      </>
    ),
    search: (
      <>
        <circle cx="10.5" cy="10.5" r="6.5" />
        <path d="m16 16 5 5" />
      </>
    ),
    locate: (
      <>
        <circle cx="12" cy="12" r="4" />
        <path d="M12 2v3M12 19v3M2 12h3M19 12h3" />
      </>
    ),
    building: (
      <>
        <path d="M4 21V5l8-3 8 3v16M8 8h2M14 8h2M8 12h2M14 12h2M8 16h2M14 16h2M2 21h20" />
      </>
    ),
    book: (
      <>
        <path d="M4 4c3-1 6 0 8 2v15c-2-2-5-3-8-2ZM20 4c-3-1-6 0-8 2v15c2-2 5-3 8-2Z" />
      </>
    ),
    food: (
      <>
        <path d="M7 3v8M4 3v5a3 3 0 0 0 6 0V3M7 11v10M16 3v18M16 3c4 2 5 7 0 10" />
      </>
    ),
    users: (
      <>
        <circle cx="9" cy="8" r="3" />
        <path d="M3 20c0-4 2-7 6-7s6 3 6 7M16 4a3 3 0 0 1 0 6M17 13c3 .5 4 3 4 6" />
      </>
    ),
    arrow: (
      <>
        <path d="M5 12h14M14 7l5 5-5 5" />
      </>
    ),
    close: (
      <>
        <path d="m5 5 14 14M19 5 5 19" />
      </>
    ),
    route: (
      <>
        <circle cx="6" cy="18" r="2" />
        <circle cx="18" cy="6" r="2" />
        <path d="M8 18h3a3 3 0 0 0 0-6 3 3 0 0 1 0-6h5" />
      </>
    ),
    trash: (
      <>
        <path d="M4 7h16M9 3h6l1 4M7 7l1 14h8l1-14M10 11v6M14 11v6" />
      </>
    ),
    clock: (
      <>
        <circle cx="12" cy="12" r="9" />
        <path d="M12 7v5l3 2" />
      </>
    ),
    check: <path d="m5 12 4 4L19 6" />,
  };
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.8"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      {paths[name]}
    </svg>
  );
}

function Slashes({ color = "yellow" }: { color?: "yellow" | "cyan" | "green" }) {
  return (
    <span className={`slashes ${color}`} aria-hidden="true">
      <i />
      <i />
      <i />
    </span>
  );
}

function Logo() {
  return (
    <div className="logo">
      <span className="logo-pin">
        <Icon name="pin" size={19} />
      </span>
      <span>
        <strong>CY</strong> Campus Explorer
      </span>
    </div>
  );
}

function BottomNav({ active, onChange }: { active: Screen; onChange: (screen: Screen) => void }) {
  return (
    <nav className="bottom-nav" aria-label="Navigation principale">
      {[
        ["home", "home", "Accueil"],
        ["map", "map", "Carte"],
        ["saved", "bookmark", "Enregistrés"],
      ].map(([screen, icon, label]) => (
        <button
          key={screen}
          className={active === screen ? "active" : ""}
          onClick={() => onChange(screen as Screen)}
        >
          <Icon name={icon as "home" | "map" | "bookmark"} size={21} />
          <span>{label}</span>
        </button>
      ))}
    </nav>
  );
}

function StatusBar() {
  return (
    <div className="status-bar">
      <span>9:41</span>
      <div>
        <span className="signal" />
        <span className="wifi">◒</span>
        <span className="battery" />
      </div>
    </div>
  );
}

function HomeScreen({
  navigate,
  selectPlace,
}: {
  navigate: (screen: Screen) => void;
  selectPlace: (place: Place) => void;
}) {
  const categories = [
    ["building", "Bâtiments", "cyan"],
    ["book", "Bibliothèque", "yellow"],
    ["food", "Restauration", "green"],
    ["users", "Services étudiants", "cyan"],
  ] as const;
  return (
    <div className="screen home-screen">
      <StatusBar />
      <section className="home-hero">
        <div className="hero-top">
          <Logo />
          <span className="mini-badge">
            CAMPUS
            <br />
            CERGY
          </span>
        </div>
        <div className="hero-orbit">
          <span />
          <Icon name="pin" size={42} />
        </div>
        <Slashes color="green" />
        <h1>
          Explorez votre
          <br />
          campus
        </h1>
        <p>
          Découvrez les lieux essentiels de
          <br />
          CY Cergy Paris Université.
        </p>
        <button className="primary-button" onClick={() => navigate("map")}>
          <Icon name="map" size={20} /> Explorer la carte <Icon name="arrow" size={19} />
        </button>
        <div className="hero-curve" />
      </section>

      <section className="home-content">
        <div className="section-title">
          <Slashes color="cyan" />
          <h2>Que recherchez-vous ?</h2>
        </div>
        <div className="category-grid">
          {categories.map(([icon, label, color]) => (
            <button key={label} onClick={() => navigate("map")}>
              <span className={`category-icon ${color}`}>
                <Icon name={icon} size={24} />
              </span>
              <strong>{label}</strong>
              <Icon name="arrow" size={16} />
            </button>
          ))}
        </div>

        <div className="discovery-head">
          <div className="section-title">
            <Slashes color="yellow" />
            <h2>À découvrir</h2>
          </div>
          <button onClick={() => navigate("map")}>Tout voir</button>
        </div>
        <div className="discovery-list">
          {places.slice(0, 3).map((place, index) => (
            <button key={place.id} onClick={() => selectPlace(place)} className="discovery-card">
              <span className={`place-number n${index + 1}`}>0{index + 1}</span>
              <span className="place-copy">
                <small>{place.shortCategory}</small>
                <strong>{place.name}</strong>
                <span>{place.description}</span>
              </span>
              <span className="circle-arrow">
                <Icon name="arrow" size={16} />
              </span>
            </button>
          ))}
        </div>
        <button className="outline-button" onClick={() => navigate("saved")}>
          <Icon name="bookmark" size={18} /> Mes lieux enregistrés
        </button>
      </section>
    </div>
  );
}

function CampusMap({
  placesToShow,
  selected,
  onSelect,
  located,
  zoom,
}: {
  placesToShow: Place[];
  selected: Place | null;
  onSelect: (place: Place) => void;
  located: boolean;
  zoom: number;
}) {
  return (
    <div className="campus-map" style={{ transform: `scale(${zoom})` }}>
      <div className="park park-one">PARC</div>
      <div className="park park-two" />
      <div className="river" />
      <div className="road road-a" />
      <div className="road road-b" />
      <div className="path path-a" />
      <div className="path path-b" />
      <div className="building b1">
        <span>BÂT. A</span>
      </div>
      <div className="building b2">
        <span>BIBLIOTHÈQUE</span>
      </div>
      <div className="building b3">
        <span>
          MAISON
          <br />
          ÉTUDIANTE
        </span>
      </div>
      <div className="building b4">
        <span>RU</span>
      </div>
      <span className="map-label label-a">Boulevard du Port</span>
      <span className="map-label label-b">Avenue du Parc</span>
      {placesToShow.map((place) => (
        <button
          key={place.id}
          aria-label={place.name}
          onClick={() => onSelect(place)}
          className={`map-marker p${place.id} ${place.className} ${selected?.id === place.id ? "selected" : ""}`}
        >
          <Icon
            name={
              place.category === "Bibliothèque"
                ? "book"
                : place.category === "Restauration"
                  ? "food"
                  : place.category === "Bâtiments"
                    ? "building"
                    : "users"
            }
            size={14}
          />
        </button>
      ))}
      {located && (
        <div className="user-location">
          <span />
        </div>
      )}
      <div className="map-credit">© OpenStreetMap</div>
    </div>
  );
}

function MapScreen({
  favorites,
  toggleFavorite,
  initialPlace,
  onInitialConsumed,
  onTrackingChange,
  tracking,
  seconds,
}: {
  favorites: number[];
  toggleFavorite: (id: number) => void;
  initialPlace: Place | null;
  onInitialConsumed: () => void;
  onTrackingChange: (value: boolean) => void;
  tracking: boolean;
  seconds: number;
}) {
  const [category, setCategory] = useState<Category>("Tout");
  const [query, setQuery] = useState("");
  const [selected, setSelected] = useState<Place | null>(initialPlace);
  const [located, setLocated] = useState(false);
  const [dialog, setDialog] = useState<"permission" | "stop" | null>(null);
  const [toast, setToast] = useState("");
  const [zoom, setZoom] = useState(1);
  const [expanded, setExpanded] = useState(false);

  useEffect(() => {
    if (initialPlace) {
      setSelected(initialPlace);
      setExpanded(false);
      onInitialConsumed();
    }
  }, [initialPlace, onInitialConsumed]);

  const filteredPlaces = useMemo(
    () =>
      places.filter(
        (place) =>
          (category === "Tout" || place.category === category) &&
          place.name.toLowerCase().includes(query.toLowerCase()),
      ),
    [category, query],
  );

  const startTracking = () => {
    if (!located) setDialog("permission");
    else onTrackingChange(true);
  };

  const approveLocation = () => {
    setLocated(true);
    setDialog(null);
    onTrackingChange(true);
  };

  const stopTracking = () => {
    onTrackingChange(false);
    setDialog(null);
    setToast("Parcours terminé.");
    window.setTimeout(() => setToast(""), 2600);
  };

  const elapsed = `${String(Math.floor(seconds / 60)).padStart(2, "0")}:${String(seconds % 60).padStart(2, "0")}`;

  return (
    <div className="screen map-screen">
      <StatusBar />
      <div className="map-topbar">
        <Logo />
        <span className={`location-status ${located ? "available" : ""}`}>
          <i />
          {tracking
            ? "Suivi en cours"
            : located
              ? "Position disponible"
              : "Localisation désactivée"}
        </span>
      </div>
      <div className="map-stage">
        <CampusMap
          placesToShow={filteredPlaces}
          selected={selected}
          onSelect={(place) => {
            setSelected(place);
            setExpanded(false);
          }}
          located={located}
          zoom={zoom}
        />
        <div className="map-search">
          <Icon name="search" size={19} />
          <input
            value={query}
            onChange={(event) => setQuery(event.target.value)}
            placeholder="Rechercher un lieu..."
          />
          {query && (
            <button onClick={() => setQuery("")}>
              <Icon name="close" size={16} />
            </button>
          )}
        </div>
        <div className="filter-chips">
          {(["Tout", "Bâtiments", "Bibliothèque", "Restauration", "Services"] as Category[]).map(
            (item) => (
              <button
                key={item}
                onClick={() => setCategory(item)}
                className={category === item ? "active" : ""}
              >
                {item}
              </button>
            ),
          )}
        </div>
        <div className="map-controls">
          <button onClick={() => setLocated(true)} aria-label="Afficher ma position">
            <Icon name="locate" size={21} />
          </button>
          <button
            onClick={() => setZoom((value) => Math.min(1.28, value + 0.14))}
            aria-label="Zoomer"
          >
            +
          </button>
          <button
            onClick={() => setZoom((value) => Math.max(0.86, value - 0.14))}
            aria-label="Dézoomer"
          >
            −
          </button>
        </div>

        {!located && !selected && !tracking && (
          <div className="location-prompt">
            <span>
              <Icon name="locate" size={18} />
            </span>
            <p>Activez la localisation pour afficher votre position.</p>
            <button onClick={() => setDialog("permission")}>Afficher ma position</button>
          </div>
        )}

        {tracking && (
          <div className="tracking-panel">
            <div className="tracking-head">
              <span className="tracking-icon">
                <Icon name="route" size={19} />
              </span>
              <div>
                <small>
                  <i /> SUIVI EN COURS
                </small>
                <strong>{elapsed}</strong>
              </div>
              <div className="distance">
                <small>DISTANCE</small>
                <strong>{(seconds * 0.0014).toFixed(2)} km</strong>
              </div>
            </div>
            <button onClick={() => setDialog("stop")}>Arrêter le suivi</button>
          </div>
        )}

        {selected && !tracking && (
          <div className={`place-sheet ${expanded ? "expanded" : ""}`}>
            <div className="sheet-handle" />
            <button className="sheet-close" onClick={() => setSelected(null)}>
              <Icon name="close" size={17} />
            </button>
            <div className="sheet-category">
              <span className={selected.className}>
                <Icon name="pin" size={17} />
              </span>
              <small>{selected.shortCategory}</small>
            </div>
            <h2>{selected.name}</h2>
            <p>{selected.description}</p>
            {expanded && (
              <div className="place-detail-copy">
                <h3>À propos de ce lieu</h3>
                <p>
                  Retrouvez ce point d'intérêt sur la carte du campus. Enregistrez-le pour y accéder
                  rapidement depuis vos lieux favoris.
                </p>
              </div>
            )}
            <div className="sheet-actions">
              <button className="details-button" onClick={() => setExpanded((value) => !value)}>
                {expanded ? "Réduire" : "Voir les détails"} <Icon name="arrow" size={17} />
              </button>
              <button
                className={`save-button ${favorites.includes(selected.id) ? "saved" : ""}`}
                onClick={() => {
                  toggleFavorite(selected.id);
                  setToast(favorites.includes(selected.id) ? "Lieu retiré." : "Lieu enregistré.");
                  window.setTimeout(() => setToast(""), 2200);
                }}
                aria-label="Enregistrer ce lieu"
              >
                <Icon name="bookmark" size={19} />
              </button>
            </div>
          </div>
        )}

        {!tracking && !selected && (
          <button className="start-tracking" onClick={startTracking}>
            <Icon name="route" size={19} /> Démarrer le suivi
          </button>
        )}
      </div>

      {dialog && (
        <div className="dialog-backdrop">
          <div className="dialog">
            <span className={`dialog-icon ${dialog === "stop" ? "stop" : ""}`}>
              <Icon name={dialog === "stop" ? "route" : "locate"} size={25} />
            </span>
            <h2>{dialog === "stop" ? "Arrêter le suivi ?" : "Votre position"}</h2>
            <p>
              {dialog === "stop"
                ? "Voulez-vous terminer votre parcours actuel ?"
                : "Votre position est nécessaire pour enregistrer votre parcours sur le campus."}
            </p>
            <button
              className="dialog-primary"
              onClick={dialog === "stop" ? stopTracking : approveLocation}
            >
              {dialog === "stop" ? "Arrêter" : "Continuer"}
            </button>
            <button className="dialog-secondary" onClick={() => setDialog(null)}>
              {dialog === "stop" ? "Continuer le suivi" : "Pas maintenant"}
            </button>
          </div>
        </div>
      )}
      {toast && (
        <div className="toast">
          <Icon name="check" size={16} />
          {toast}
        </div>
      )}
    </div>
  );
}

function SavedScreen({
  favorites,
  toggleFavorite,
  openPlace,
  tracking,
  navigate,
}: {
  favorites: number[];
  toggleFavorite: (id: number) => void;
  openPlace: (place: Place) => void;
  tracking: boolean;
  navigate: (screen: Screen) => void;
}) {
  const savedPlaces = places.filter((place) => favorites.includes(place.id));
  return (
    <div className="screen saved-screen">
      <StatusBar />
      <header className="saved-header">
        <Logo />
        <div className="section-title">
          <Slashes color="yellow" />
          <h1>
            Mes lieux
            <br />
            enregistrés
          </h1>
        </div>
        <p>
          Retrouvez vos endroits préférés
          <br />
          et vos derniers parcours.
        </p>
        <div className="header-shape" />
      </header>
      <div className="saved-content">
        <div className="saved-section-head">
          <h2>Mes lieux</h2>
          <span>
            {savedPlaces.length} {savedPlaces.length > 1 ? "lieux" : "lieu"}
          </span>
        </div>
        {savedPlaces.length ? (
          <div className="saved-list">
            {savedPlaces.map((place, index) => (
              <article key={place.id}>
                <span className={`saved-place-icon c${index % 3}`}>
                  <Icon name="pin" size={20} />
                </span>
                <button className="saved-place-copy" onClick={() => openPlace(place)}>
                  <small>{place.shortCategory}</small>
                  <strong>{place.name}</strong>
                  <span>
                    Voir sur la carte <Icon name="arrow" size={14} />
                  </span>
                </button>
                <button
                  className="remove-button"
                  onClick={() => toggleFavorite(place.id)}
                  aria-label={`Retirer ${place.name}`}
                >
                  <Icon name="trash" size={17} />
                </button>
              </article>
            ))}
          </div>
        ) : (
          <div className="empty-state">
            <span>
              <Icon name="bookmark" size={30} />
            </span>
            <h3>Aucun lieu enregistré</h3>
            <p>Enregistrez les endroits qui vous intéressent pour les retrouver facilement.</p>
            <button onClick={() => navigate("map")}>Explorer la carte</button>
          </div>
        )}

        <div className="saved-section-head history-head">
          <h2>Mes derniers parcours</h2>
        </div>
        <article className="history-card">
          <span className="route-icon">
            <Icon name="route" size={21} />
          </span>
          <div>
            <small>06 NOVEMBRE 2026 · DÉMONSTRATION</small>
            <strong>Parcours du campus</strong>
            <p>
              <span>
                <Icon name="clock" size={13} />
                12 min
              </span>
              <span>Distance selon les données enregistrées</span>
            </p>
          </div>
          <Icon name="arrow" size={17} />
        </article>
        {tracking && (
          <div className="active-session">
            <i />
            <span>
              <strong>Suivi en cours</strong>Votre parcours est enregistré en arrière-plan.
            </span>
          </div>
        )}
      </div>
    </div>
  );
}

type NotificationView = "compact" | "shade" | "lock";

function TrackingNotification({
  seconds,
  view,
  setView,
  onStop,
}: {
  seconds: number;
  view: NotificationView;
  setView: (view: NotificationView) => void;
  onStop: () => void;
}) {
  const elapsed = `${String(Math.floor(seconds / 60)).padStart(2, "0")}:${String(seconds % 60).padStart(2, "0")}`;

  const notificationCard = (compact = false) => (
    <div className={`system-notification-card ${compact ? "compact" : ""}`}>
      <span className="system-app-icon">
        <Icon name="pin" size={compact ? 14 : 17} />
      </span>
      <div className="system-notification-copy">
        <small>CY Campus Explorer · maintenant</small>
        <strong>Suivi de localisation actif</strong>
        <p>
          {compact
            ? "Parcours en cours d'enregistrement"
            : "Votre parcours sur le campus est en cours d'enregistrement."}
        </p>
        {!compact && (
          <span className="tracking-duration">
            <i /> Enregistrement en cours · {elapsed}
          </span>
        )}
      </div>
      <button
        className="notification-stop"
        onClick={(event) => {
          event.stopPropagation();
          onStop();
        }}
      >
        ARRÊTER
      </button>
    </div>
  );

  if (view === "compact") {
    return (
      <div
        className="compact-notification-wrap"
        onClick={() => setView("shade")}
        onKeyDown={(event) => {
          if (event.key === "Enter" || event.key === " ") setView("shade");
        }}
        role="button"
        tabIndex={0}
        aria-label="Ouvrir la notification de suivi"
      >
        {notificationCard(true)}
      </div>
    );
  }

  if (view === "lock") {
    return (
      <div className="android-lock-screen">
        <div className="lock-wallpaper" />
        <div className="lock-status">
          <span>◉</span>
          <span>⌁</span>
          <span>▰</span>
        </div>
        <button className="unlock-back" onClick={() => setView("shade")}>
          Retour
        </button>
        <div className="lock-clock">
          <span>09:41</span>
          <p>Vendredi 6 novembre</p>
        </div>
        <div className="lock-notification">{notificationCard(false)}</div>
        <div className="lock-footer">
          <span>⌁</span>
          <i />
          <span>◎</span>
        </div>
      </div>
    );
  }

  return (
    <div className="notification-shade">
      <div className="shade-status">
        <strong>09:41</strong>
        <div>
          <span>◉</span>
          <span>⌁</span>
          <span>▰</span>
        </div>
      </div>
      <div className="shade-heading">
        <div>
          <strong>Ven. 6 nov.</strong>
          <span> · 18°C</span>
        </div>
        <button
          onClick={() => setView("lock")}
          aria-label="Voir la notification sur l'écran verrouillé"
        >
          ▣
        </button>
      </div>
      <div className="quick-settings">
        <span className="quick-active">
          ⌁<small>Internet</small>
        </span>
        <span className="quick-active">
          ᛒ<small>Bluetooth</small>
        </span>
        <span>
          ◐<small>Ne pas déranger</small>
        </span>
        <span>
          ▣<small>Rotation auto</small>
        </span>
      </div>
      <div className="brightness">
        <span>☀</span>
        <i>
          <b />
        </i>
        <button>⌄</button>
      </div>
      <div className="notifications-label">
        <span>Notifications</span>
        <button onClick={() => setView("compact")}>FERMER</button>
      </div>
      {notificationCard(false)}
      <p className="persistent-note">
        <Icon name="pin" size={12} /> Cette notification ne peut pas être supprimée pendant le
        suivi.
      </p>
      <div className="shade-handle" />
    </div>
  );
}

export default function App() {
  const [screen, setScreen] = useState<Screen>("home");
  const [favorites, setFavorites] = useState<number[]>(() => {
    try {
      const stored = localStorage.getItem("cy-saved-places");
      return stored ? JSON.parse(stored) : [1, 2];
    } catch {
      return [1, 2];
    }
  });
  const [initialPlace, setInitialPlace] = useState<Place | null>(null);
  const [tracking, setTracking] = useState(false);
  const [trackingSeconds, setTrackingSeconds] = useState(0);
  const [notificationView, setNotificationView] = useState<NotificationView>("compact");
  const [systemToast, setSystemToast] = useState("");

  useEffect(() => {
    localStorage.setItem("cy-saved-places", JSON.stringify(favorites));
  }, [favorites]);

  useEffect(() => {
    if (!tracking) return;
    const timer = window.setInterval(() => setTrackingSeconds((value) => value + 1), 1000);
    return () => window.clearInterval(timer);
  }, [tracking]);

  const toggleFavorite = (id: number) => {
    setFavorites((current) =>
      current.includes(id) ? current.filter((item) => item !== id) : [...current, id],
    );
  };

  const openPlace = (place: Place) => {
    setInitialPlace(place);
    setScreen("map");
  };

  const handleTrackingChange = (value: boolean) => {
    setTracking(value);
    if (value) {
      setNotificationView("compact");
    } else {
      setTrackingSeconds(0);
    }
  };

  const stopFromNotification = () => {
    setTracking(false);
    setTrackingSeconds(0);
    setNotificationView("compact");
    setSystemToast("Suivi arrêté.");
    window.setTimeout(() => setSystemToast(""), 2500);
  };

  return (
    <main className="app-canvas">
      <div className="desktop-intro" aria-hidden="true">
        <Logo />
        <div>
          <Slashes color="yellow" />
          <h2>
            Votre campus,
            <br />
            <span>à portée de main.</span>
          </h2>
          <p>
            Explorez. Enregistrez.
            <br />
            Suivez votre parcours.
          </p>
        </div>
        <span className="desktop-note">PROJET MOBILE · CY CERGY PARIS UNIVERSITÉ</span>
      </div>
      <div className="phone-shell">
        <div className="phone-content">
          {screen === "home" && <HomeScreen navigate={setScreen} selectPlace={openPlace} />}
          {screen === "map" && (
            <MapScreen
              favorites={favorites}
              toggleFavorite={toggleFavorite}
              initialPlace={initialPlace}
              onInitialConsumed={() => setInitialPlace(null)}
              tracking={tracking}
              seconds={trackingSeconds}
              onTrackingChange={handleTrackingChange}
            />
          )}
          {screen === "saved" && (
            <SavedScreen
              favorites={favorites}
              toggleFavorite={toggleFavorite}
              openPlace={openPlace}
              tracking={tracking}
              navigate={setScreen}
            />
          )}
        </div>
        <BottomNav active={screen} onChange={setScreen} />
        {tracking && (
          <TrackingNotification
            seconds={trackingSeconds}
            view={notificationView}
            setView={setNotificationView}
            onStop={stopFromNotification}
          />
        )}
        {systemToast && (
          <div className="system-toast">
            <Icon name="check" size={16} />
            {systemToast}
          </div>
        )}
      </div>
    </main>
  );
}
