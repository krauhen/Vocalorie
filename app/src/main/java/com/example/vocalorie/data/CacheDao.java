package com.example.vocalorie.data;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

/**
 * Access to the dedicated reuse caches (meal-key cache and item-name cache). Both caches upsert
 * with last-saved-wins semantics (one row per key) and are never used to modify meal history.
 */
@Dao
public interface CacheDao {
    @Query("SELECT * FROM cached_meals")
    List<CachedMealEntity> getAllMeals();

    @Query("SELECT * FROM cached_items")
    List<CachedItemEntity> getAllItems();

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsertMeal(CachedMealEntity meal);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsertItems(List<CachedItemEntity> items);
}
