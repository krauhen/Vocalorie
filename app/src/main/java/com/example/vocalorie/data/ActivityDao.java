package com.example.vocalorie.data;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface ActivityDao {
    @Query("SELECT * FROM activities ORDER BY createdAtEpochMillis DESC")
    List<ActivityEntity> getAll();

    @Query("SELECT * FROM activities WHERE id = :id")
    ActivityEntity getById(long id);

    @Insert
    long insert(ActivityEntity activity);

    @Update
    int update(ActivityEntity activity);

    @Delete
    int delete(ActivityEntity activity);

    @Query("DELETE FROM activities WHERE id = :id")
    int deleteById(long id);
}
