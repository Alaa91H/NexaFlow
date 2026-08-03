package com.nexaflow.domain.usecases

import com.nexaflow.domain.repositories.AutomationRepository
import javax.inject.Inject

class GetAutomationsUseCase @Inject constructor(
    private val repository: AutomationRepository
) {
    operator fun invoke() = repository.getAutomations()
}
