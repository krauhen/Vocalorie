package com.example.vocalorie.data;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

@Database(entities = {MealEntity.class, ActivityEntity.class}, version = 7, exportSchema = false)
public abstract class VocalorieDatabase extends RoomDatabase {
    public abstract MealDao mealDao();
    public abstract ActivityDao activityDao();

    private static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE meals ADD COLUMN saturatedFatG REAL");
            database.execSQL("ALTER TABLE meals ADD COLUMN sugarG REAL");
            database.execSQL("ALTER TABLE meals ADD COLUMN saltG REAL");
            database.execSQL("ALTER TABLE meals ADD COLUMN source TEXT");
        }
    };

    private static final Migration MIGRATION_2_3 = new Migration(2, 3) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE meals ADD COLUMN amountGml REAL");
        }
    };

    private static final Migration MIGRATION_3_4 = new Migration(3, 4) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE meals ADD COLUMN title TEXT NOT NULL DEFAULT ''");
        }
    };

    public static final Migration MIGRATION_4_5 = new Migration(4, 5) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            // Create new table without source column
            database.execSQL(
                "CREATE TABLE meals_new (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "createdAtEpochMillis INTEGER NOT NULL, " +
                    "title TEXT NOT NULL, " +
                    "query TEXT NOT NULL, " +
                    "itemsJson TEXT NOT NULL, " +
                    "caloriesKcal REAL, " +
                    "amountGml REAL, " +
                    "proteinG REAL, " +
                    "carbsG REAL, " +
                    "fatG REAL, " +
                    "saturatedFatG REAL, " +
                    "sugarG REAL, " +
                    "saltG REAL, " +
                    "assumptionsText TEXT NOT NULL, " +
                    "warningsText TEXT NOT NULL, " +
                    "confidence TEXT NOT NULL, " +
                    "needsHumanReview INTEGER NOT NULL" +
                ")"
            );
            // Copy data from old table (excluding source column)
            database.execSQL(
                "INSERT INTO meals_new (" +
                    "id, createdAtEpochMillis, title, query, itemsJson, " +
                    "caloriesKcal, amountGml, proteinG, carbsG, fatG, " +
                    "saturatedFatG, sugarG, saltG, assumptionsText, " +
                    "warningsText, confidence, needsHumanReview" +
                ") SELECT " +
                    "id, createdAtEpochMillis, title, query, itemsJson, " +
                    "caloriesKcal, amountGml, proteinG, carbsG, fatG, " +
                    "saturatedFatG, sugarG, saltG, assumptionsText, " +
                    "warningsText, confidence, needsHumanReview " +
                "FROM meals"
            );
            // Drop old table
            database.execSQL("DROP TABLE meals");
            // Rename new table
            database.execSQL("ALTER TABLE meals_new RENAME TO meals");
        }
    };

    public static final Migration MIGRATION_5_6 = new Migration(5, 6) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL(
                "CREATE TABLE activities (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "createdAtEpochMillis INTEGER NOT NULL, " +
                    "type TEXT NOT NULL, " +
                    "title TEXT NOT NULL, " +
                    "description TEXT NOT NULL, " +
                    "caloriesBurnedKcal REAL NOT NULL, " +
                    "durationMinutes INTEGER NOT NULL" +
                ")"
            );
        }
    };

    public static final Migration MIGRATION_6_7 = new Migration(6, 7) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE activities ADD COLUMN stepsCount INTEGER");
        }
    };

    private static volatile VocalorieDatabase instance;

    public static VocalorieDatabase get(Context context) {
        VocalorieDatabase current = instance;
        if (current != null) {
            return current;
        }
        synchronized (VocalorieDatabase.class) {
            current = instance;
            if (current == null) {
                current = Room.databaseBuilder(
                        context.getApplicationContext(),
                        VocalorieDatabase.class,
                        "vocalorie.db"
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7).build();
                instance = current;
            }
            return current;
        }
    }
}
