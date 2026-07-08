package com.example.vocalorie.data;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

@Database(entities = {MealEntity.class}, version = 4, exportSchema = false)
public abstract class VocalorieDatabase extends RoomDatabase {
    public abstract MealDao mealDao();

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
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4).build();
                instance = current;
            }
            return current;
        }
    }
}
