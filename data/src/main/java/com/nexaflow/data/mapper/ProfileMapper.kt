package com.nexaflow.data.mapper

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.nexaflow.core.database.ProfileEntity
import com.nexaflow.domain.models.Profile

private val gson = Gson()

fun ProfileEntity.toDomain(): Profile {
    val type = object : TypeToken<List<String>>() {}.type
    val ids: List<String> = gson.fromJson(automationIdsJson, type) ?: emptyList()
    return Profile(
        id = id,
        name = name,
        description = description,
        icon = icon,
        color = color,
        active = active,
        automationIds = ids,
        createdAt = createdAt
    )
}

fun Profile.toEntity(): ProfileEntity {
    val type = object : TypeToken<List<String>>() {}.type
    return ProfileEntity(
        id = id,
        name = name,
        description = description,
        icon = icon,
        color = color,
        active = active,
        automationIdsJson = gson.toJson(automationIds, type),
        createdAt = createdAt
    )
}
