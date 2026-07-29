package com.example.vocalorie.data;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface MealDao {
    @Query("SELECT * FROM meals ORDER BY createdAtEpochMillis DESC")
    List<MealEntity> getAll();

    /**
     * Projection for totals-only readers (day totals, statistics): the eight persisted total
     * columns plus identity, timestamp, title and category, and deliberately not
     * {@code itemsJson} — so a stats reload never decodes per-item JSON it does not use.
     */
    @Query(
        "SELECT id, createdAtEpochMillis, title, category, "
            + "caloriesKcal, amountGml, proteinG, carbsG, fatG, saturatedFatG, sugarG, saltG "
            + "FROM meals ORDER BY createdAtEpochMillis DESC"
    )
    List<MealSummary> getAllSummaries();

    @Insert
    long insert(MealEntity meal);

    @Update
    int update(MealEntity meal);

    @Query("DELETE FROM meals WHERE id = :id")
    int deleteById(long id);
}
