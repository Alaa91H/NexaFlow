package com.nexaflow.core.database

import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.nexaflow.domain.models.Action
import com.nexaflow.domain.models.Condition
import com.nexaflow.domain.models.Trigger

class Converters {
    private val gson = Gson()

    @TypeConverter
    fun fromTriggerList(value: List<Trigger>): String {
        val type = object : TypeToken<List<Trigger>>() {}.type
        return gson.toJson(value, type)
    }

    @TypeConverter
    fun toTriggerList(value: String): List<Trigger> {
        val type = object : TypeToken<List<Trigger>>() {}.type
        return gson.fromJson(value, type)
    }

    @TypeConverter
    fun fromConditionList(value: List<Condition>): String {
        val type = object : TypeToken<List<Condition>>() {}.type
        return gson.toJson(value, type)
    }

    @TypeConverter
    fun toConditionList(value: String): List<Condition> {
        val type = object : TypeToken<List<Condition>>() {}.type
        return gson.fromJson(value, type)
    }

    @TypeConverter
    fun fromActionList(value: List<Action>): String {
        val type = object : TypeToken<List<Action>>() {}.type
        return gson.toJson(value, type)
    }

    @TypeConverter
    fun toActionList(value: String): List<Action> {
        val type = object : TypeToken<List<Action>>() {}.type
        return gson.fromJson(value, type)
    }
}
