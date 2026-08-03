package com.nexaflow.domain.models

data class Profile(
    val id: String,
    val name: String,
    val description: String,
    val icon: String,
    val color: Long,
    val active: Boolean,
    val automationIds: List<String>,
    val createdAt: Long
)
