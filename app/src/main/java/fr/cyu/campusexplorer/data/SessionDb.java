package fr.cyu.campusexplorer.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * Historique des parcours dans une base SQLite (meme construction que EventDbHelper au TP2).
 * Une ligne = le resume d'une session de suivi : debut, duree, distance.
 * Les positions elles-memes ne sont pas gardees : la vie privee reste simple.
 */
public class SessionDb extends SQLiteOpenHelper {

    private static final String DB_NAME = "sessions.db";
    private static final int DB_VERSION = 1;

    static final String TABLE = "SESSIONS";
    static final String ID = "ID";
    static final String START = "START_TIME";
    static final String DURATION = "DURATION_S";
    static final String DISTANCE = "DISTANCE_M";

    /** un parcours termine */
    public static class Session {
        public final long id;
        /** debut, en millisecondes depuis 1970 (System.currentTimeMillis) */
        public final long startTime;
        public final long durationSeconds;
        public final double distanceMeters;

        Session(long id, long startTime, long durationSeconds, double distanceMeters) {
            this.id = id;
            this.startTime = startTime;
            this.durationSeconds = durationSeconds;
            this.distanceMeters = distanceMeters;
        }
    }

    /**
     * Ne cree rien tout de suite : la base (fichier /data/data/fr.cyu.campusexplorer/databases/
     * sessions.db) est ouverte au premier getWritableDatabase() / getReadableDatabase().
     */
    public SessionDb(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    /** Appele par SQLiteOpenHelper une seule fois, quand le fichier de la base n'existe pas encore. */
    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + TABLE + " ("
                + ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + START + " INTEGER NOT NULL, "
                + DURATION + " INTEGER NOT NULL, "
                + DISTANCE + " REAL NOT NULL)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // version 1 seulement pour l'instant ; une version 2 ferait un ALTER TABLE pour garder l'historique
    }

    /**
     * Enregistre le resume d'un parcours (appele par TrackingService.finishSession).
     * Equivalent SQL : INSERT INTO SESSIONS (START_TIME, DURATION_S, DISTANCE_M) VALUES (?, ?, ?)
     *
     * @param startTime       debut, en millisecondes depuis 1970
     * @param durationSeconds duree du parcours
     * @param distanceMeters  distance parcourue
     * @return l'ID de la nouvelle ligne, ou -1 en cas d'erreur
     */
    public long insert(long startTime, long durationSeconds, double distanceMeters) {
        ContentValues row = new ContentValues();
        row.put(START, startTime);
        row.put(DURATION, durationSeconds);
        row.put(DISTANCE, distanceMeters);
        return getWritableDatabase().insert(TABLE, null, row);
    }

    /**
     * les "limit" derniers parcours, du plus recent au plus ancien.
     * Equivalent SQL : SELECT * FROM SESSIONS ORDER BY START_TIME DESC LIMIT ?
     * Le try-with-resources ferme le Cursor meme en cas d'erreur.
     *
     * @param limit nombre maximum de lignes (5 dans SavedActivity)
     */
    public List<Session> latest(int limit) {
        List<Session> sessions = new ArrayList<>();
        try (Cursor c = getReadableDatabase().query(TABLE, null, null, null, null, null,
                START + " DESC", String.valueOf(limit))) {
            while (c.moveToNext()) {
                sessions.add(new Session(
                        c.getLong(c.getColumnIndexOrThrow(ID)),
                        c.getLong(c.getColumnIndexOrThrow(START)),
                        c.getLong(c.getColumnIndexOrThrow(DURATION)),
                        c.getDouble(c.getColumnIndexOrThrow(DISTANCE))));
            }
        }
        return sessions;
    }
}
