package com.example.vocalorie.data;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface MealDao {
    @Query("SELECT * FROM meals ORDER BY createdAtEpochMillis DESC")
    List<MealEntity> getAll();

    @Query("SELECT * FROM meals WHERE id = :id")
    MealEntity getById(long id);

    @Insert
    long insert(MealEntity meal);

    @Update
    int update(MealEntity meal);

    @Delete
    int delete(MealEntity meal);

    @Query("DELETE FROM meals WHERE id = :id")
    int deleteById(long id);
}
