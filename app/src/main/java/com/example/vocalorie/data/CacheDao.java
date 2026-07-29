package com.example.vocalorie.data;

import androidx.annotation.Nullable;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

/**
 * Access to the dedicated reuse caches (meal-key cache and item-name cache). Both caches upsert
 * with last-saved-wins semantics (one row per key) and are never used to modify meal history.
 *
 * <p>Lookups are keyed, not scans: {@link #findMeal(String)} is a primary-key point lookup and
 * {@link #findItems(List)} asks only for the names an estimate actually needs, so lookup cost does
 * not grow with cache size. The {@code getAll*} reads exist for the full-database backup only.
 */
@Dao
public interface CacheDao {
    @Query("SELECT * FROM cached_meals")
    List<CachedMealEntity> getAllMeals();

    @Query("SELECT * FROM cached_items")
    List<CachedItemEntity> getAllItems();

    /**
     * The single cached meal stored under {@code normalizedKey}, or {@code null} when nothing is
     * cached for it. Annotated {@code @Nullable} so Kotlin callers see {@code CachedMealEntity?}
     * rather than a platform type that silently allows a null dereference.
     */
    @Nullable
    @Query("SELECT * FROM cached_meals WHERE normalizedKey = :normalizedKey")
    CachedMealEntity findMeal(String normalizedKey);

    /** Only the cached items whose normalized name is in {@code normalizedNames}. */
    @Query("SELECT * FROM cached_items WHERE normalizedName IN (:normalizedNames)")
    List<CachedItemEntity> findItems(List<String> normalizedNames);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsertMeal(CachedMealEntity meal);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsertItems(List<CachedItemEntity> items);
}
