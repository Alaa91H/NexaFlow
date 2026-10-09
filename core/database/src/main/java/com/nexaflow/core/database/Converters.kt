package com.nexaflow.core.database

import androidx.room.TypeConverter
import com.nexaflow.domain.models.Action
import com.nexaflow.domain.models.Constraint
import com.nexaflow.domain.models.MaintenanceProfile
import com.nexaflow.domain.models.Trigger
import com.nexaflow.domain.workflow.TriggerExpressionDefinitionV2
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * Room [TypeConverter]s for the JSON columns (triggers / actions / constraints
 * configs). Uses kotlinx.serialization — compile-time, R8-safe, and consistent
 * with BackupManager and ExecutionRecordMapper. [ignoreUnknownKeys] keeps rows
 * written by older app versions parseable when new optional fields are added.
 */
class Converters {

    private val json = Json { ignoreUnknownKeys = true }

    @TypeConverter
    fun fromConstraintList(value: List<Constraint>): String =
        json.encodeToString(ListSerializer(Constraint.serializer()), value)

    @TypeConverter
    fun toConstraintList(value: String): List<Constraint> =
        json.decodeFromString(ListSerializer(Constraint.serializer()), value)

    @TypeConverter
    fun fromTriggerList(value: List<Trigger>): String =
        json.encodeToString(ListSerializer(Trigger.serializer()), value)

    @TypeConverter
    fun toTriggerList(value: String): List<Trigger> =
        json.decodeFromString(ListSerializer(Trigger.serializer()), value)

    @TypeConverter
    fun fromMaintenanceProfile(value: MaintenanceProfile?): String? =
        value?.let { json.encodeToString(MaintenanceProfile.serializer(), it) }

    @TypeConverter
    fun toMaintenanceProfile(value: String?): MaintenanceProfile? =
        value?.let { json.decodeFromString(MaintenanceProfile.serializer(), it) }

    @TypeConverter
    fun fromTriggerExpressionV2(value: TriggerExpressionDefinitionV2?): String? =
        value?.let { json.encodeToString(TriggerExpressionDefinitionV2.serializer(), it) }

    @TypeConverter
    fun toTriggerExpressionV2(value: String?): TriggerExpressionDefinitionV2? =
        value?.let {
            runCatching { json.decodeFromString(TriggerExpressionDefinitionV2.serializer(), it) }
                .getOrElse { TriggerExpressionDefinitionV2.invalidSentinel() }
        }

    @TypeConverter
    fun fromActionList(value: List<Action>): String =
        json.encodeToString(ListSerializer(Action.serializer()), value)

    @TypeConverter
    fun toActionList(value: String): List<Action> =
        json.decodeFromString(ListSerializer(Action.serializer()), value)
}
