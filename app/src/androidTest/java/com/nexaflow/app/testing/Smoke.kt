package com.nexaflow.app.testing

/**
 * Marks the instrumentation tests that must pass on every pull request
 * (NFA-02). The emulator lane filters with
 * `-Pandroid.testInstrumentationRunnerArguments.annotation=com.nexaflow.app.testing.Smoke`,
 * so a class-level annotation selects its whole suite while a method-level one
 * narrows the selection further.
 *
 * The set stays deliberately small: it covers the scheduler/trigger path, the
 * workflow interpreter and durable execution checkpoints — the regressions
 * that break real users fastest — and must stay stable enough to gate PRs.
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
annotation class Smoke
