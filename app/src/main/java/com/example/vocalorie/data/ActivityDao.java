package com.example.vocalorie.data;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface ActivityDao {
    @Query("SELECT * FROM activities ORDER BY createdAtEpochMillis DESC")
    List<ActivityEntity> getAll();

    @Insert
    long insert(ActivityEntity activity);

    @Update
    int update(ActivityEntity activity);

    @Query("DELETE FROM activities WHERE id = :id")
    int deleteById(long id);
}
