package com.littlelock.app.data.local;

import androidx.room.TypeConverter;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

public class TypeConverters {

    @TypeConverter
    public static Long fromLocalDateTime(LocalDateTime value) {
        return value == null ? null : value.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    @TypeConverter
    public static LocalDateTime toLocalDateTime(Long millis) {
        return millis == null ? null : LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneId.systemDefault());
    }
}
