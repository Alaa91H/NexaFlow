package com.nexaflow.domain.catalog

/**
 * Small domain-only schema DSL shared by trigger and action catalogs.
 * Keeping it separate prevents the canonical inventory from becoming another
 * monolithic source of UI/editor logic.
 */
internal fun thresholdSchema(
        default: String,
        min: Double? = null,
        max: Double? = null,
        direction: String = "ABOVE"
    ): NodeConfigurationSchema = schema(
        decimalField("threshold", default = default, min = min, max = max),
        enumField("direction", "ABOVE", "BELOW", default = direction)
    )

internal fun schema(vararg fields: NodeConfigField): NodeConfigurationSchema =
        NodeConfigurationSchema(fields = fields.toList(), acceptsUnknownKeys = true)

internal fun stringField(
        key: String,
        required: Boolean = false,
        default: String? = null,
        expressionCapable: Boolean = false
    ) = NodeConfigField(
        key = key,
        valueType = NodeConfigValueType.STRING,
        required = required,
        defaultValue = default,
        expressionCapable = expressionCapable
    )

internal fun packageField(key: String, required: Boolean = false) = NodeConfigField(
        key = key,
        valueType = NodeConfigValueType.PACKAGE,
        required = required
    )

internal fun urlField(
        key: String,
        required: Boolean = false,
        expressionCapable: Boolean = false
    ) = NodeConfigField(
        key = key,
        valueType = NodeConfigValueType.URL,
        required = required,
        expressionCapable = expressionCapable
    )

internal fun secretField(key: String, required: Boolean = false) = NodeConfigField(
        key = key,
        valueType = NodeConfigValueType.SECRET,
        required = required,
        sensitive = true
    )

internal fun jsonField(key: String, required: Boolean = false) = NodeConfigField(
        key = key,
        valueType = NodeConfigValueType.JSON,
        required = required
    )

internal fun coordinateField(
        key: String,
        required: Boolean = false,
        expressionCapable: Boolean = false
    ) = NodeConfigField(
        key = key,
        valueType = NodeConfigValueType.COORDINATE,
        required = required,
        expressionCapable = expressionCapable
    )

internal fun dateField(key: String) = NodeConfigField(
        key = key,
        valueType = NodeConfigValueType.DATE
    )

internal fun timeField(key: String, default: String? = null) = NodeConfigField(
        key = key,
        valueType = NodeConfigValueType.TIME,
        defaultValue = default
    )

internal fun durationField(
        key: String,
        default: String? = null,
        min: Double? = null,
        max: Double? = null,
        expressionCapable: Boolean = false
    ) = NodeConfigField(
        key = key,
        valueType = NodeConfigValueType.DURATION_SECONDS,
        defaultValue = default,
        minValue = min,
        maxValue = max,
        expressionCapable = expressionCapable
    )

internal fun integerField(
        key: String,
        required: Boolean = false,
        default: String? = null,
        min: Double? = null,
        max: Double? = null,
        expressionCapable: Boolean = false
    ) = NodeConfigField(
        key = key,
        valueType = NodeConfigValueType.INTEGER,
        required = required,
        defaultValue = default,
        minValue = min,
        maxValue = max,
        expressionCapable = expressionCapable
    )

internal fun decimalField(
        key: String,
        default: String? = null,
        min: Double? = null,
        max: Double? = null,
        expressionCapable: Boolean = false
    ) = NodeConfigField(
        key = key,
        valueType = NodeConfigValueType.DECIMAL,
        defaultValue = default,
        minValue = min,
        maxValue = max,
        expressionCapable = expressionCapable
    )

internal fun booleanField(key: String, default: String? = null) = NodeConfigField(
        key = key,
        valueType = NodeConfigValueType.BOOLEAN,
        defaultValue = default
    )

internal fun enumField(
        key: String,
        vararg values: String,
        default: String? = null
    ) = NodeConfigField(
        key = key,
        valueType = NodeConfigValueType.ENUM,
        defaultValue = default,
        allowedValues = values.toList()
    )


