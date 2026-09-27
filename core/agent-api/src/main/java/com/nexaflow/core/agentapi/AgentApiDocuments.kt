package com.nexaflow.core.agentapi

/**
 * Versioned machine-readable REST contracts.
 *
 * The same payloads are checked into docs/api for non-running clients.
 */
object AgentApiDocuments {
    val taskSchemaJson: String = """
        {
          "${'$'}schema": "https://json-schema.org/draft/2020-12/schema",
          "${'$'}id": "https://nexaflow.local/schemas/task-v1.json",
          "title": "NexaFlow AgentTaskDraftV1",
          "type": "object",
          "additionalProperties": false,
          "required": ["name"],
          "properties": {
            "schemaVersion": {"type": "integer", "const": 1},
            "name": {"type": "string", "minLength": 1},
            "description": {"type": "string"},
            "icon": {"type": "string"},
            "iconColor": {"type": "integer"},
            "backgroundColor": {"type": "integer"},
            "category": {"type": "string"},
            "priority": {"type": "integer"},
            "enabled": {"type": "boolean", "default": false},
            "showToastOnToggle": {"type": "boolean"},
            "triggers": {
              "type": "array",
              "maxItems": 64,
              "items": {"${'$'}ref": "#/${'$'}defs/trigger"}
            },
            "triggerMatch": {"type": "string", "enum": ["ANY", "ALL"]},
            "actions": {
              "type": "array",
              "maxItems": 256,
              "items": {"${'$'}ref": "#/${'$'}defs/action"}
            },
            "constraints": {
              "type": "array",
              "items": {"${'$'}ref": "#/${'$'}defs/constraint"}
            },
            "exitActions": {
              "type": "array",
              "maxItems": 256,
              "items": {"${'$'}ref": "#/${'$'}defs/action"}
            },
            "revertOnExit": {"type": "boolean"},
            "cooldownSeconds": {"type": "integer", "minimum": 0},
            "maintenance": {"type": ["object", "null"]}
          },
          "${'$'}defs": {
            "config": {
              "type": "object",
              "maxProperties": 64,
              "propertyNames": {"minLength": 1},
              "additionalProperties": {"type": "string", "maxLength": 8192}
            },
            "trigger": {
              "type": "object",
              "additionalProperties": false,
              "required": ["type"],
              "properties": {
                "type": {"type": "string"},
                "config": {"${'$'}ref": "#/${'$'}defs/config"}
              }
            },
            "constraint": {
              "type": "object",
              "additionalProperties": false,
              "required": ["type"],
              "properties": {
                "type": {"type": "string"},
                "config": {"${'$'}ref": "#/${'$'}defs/config"}
              }
            },
            "endBehavior": {
              "type": "object",
              "additionalProperties": false,
              "properties": {
                "mode": {"type": "string", "enum": ["LEAVE", "REVERT", "SET_VALUE", "RERUN"]},
                "config": {"${'$'}ref": "#/${'$'}defs/config"}
              }
            },
            "action": {
              "type": "object",
              "additionalProperties": false,
              "required": ["type"],
              "properties": {
                "type": {"type": "string"},
                "config": {"${'$'}ref": "#/${'$'}defs/config"},
                "endBehavior": {
                  "oneOf": [
                    {"${'$'}ref": "#/${'$'}defs/endBehavior"},
                    {"type": "null"}
                  ]
                }
              }
            }
          }
        }
    """.trimIndent()

    val openApiJson: String = """
        {
          "openapi": "3.1.0",
          "info": {
            "title": "NexaFlow Agent API",
            "version": "1.0.0",
            "description": "Loopback-only authenticated control API for NexaFlow agents."
          },
          "servers": [{"url": "http://127.0.0.1:8766/api/v1"}],
          "components": {
            "securitySchemes": {
              "bearerAuth": {"type": "http", "scheme": "bearer"}
            }
          },
          "security": [{"bearerAuth": []}],
          "paths": {
            "/auth/pair/complete": {
              "post": {
                "security": [],
                "summary": "Consume one user-created pairing challenge and receive a permanent refresh credential"
              }
            },
            "/auth/session": {
              "post": {
                "security": [],
                "summary": "Exchange a permanent refresh credential for a short-lived access session"
              }
            },
            "/status": {"get": {"summary": "Read API and agent-access status"}},
            "/capabilities": {"get": {"summary": "Read live device capability and privilege observations"}},
            "/catalog": {"get": {"summary": "Read canonical trigger/action/constraint schemas"}},
            "/tasks": {
              "get": {"summary": "List tasks"},
              "post": {
                "summary": "Create a task",
                "parameters": [
                  {"name": "Idempotency-Key", "in": "header", "required": true, "schema": {"type": "string"}}
                ]
              }
            },
            "/tasks/{id}": {
              "get": {"summary": "Get a task"},
              "patch": {"summary": "Update a task using If-Match and Idempotency-Key"},
              "delete": {"summary": "Delete a task using If-Match and Idempotency-Key"}
            },
            "/tasks/{id}/enable": {"post": {"summary": "Enable a task using If-Match and Idempotency-Key"}},
            "/tasks/{id}/disable": {"post": {"summary": "Disable a task using If-Match and Idempotency-Key"}},
            "/tasks/{id}/run": {
              "post": {
                "summary": "Run a task exactly once for one idempotency key and task revision",
                "parameters": [
                  {"name": "Idempotency-Key", "in": "header", "required": true, "schema": {"type": "string"}},
                  {"name": "If-Match", "in": "header", "required": true, "schema": {"type": "string"}}
                ]
              }
            },
            "/validate": {"post": {"summary": "Validate and dry-run a task draft"}},
            "/simulate": {"post": {"summary": "Return a no-side-effect execution simulation"}},
            "/schedules/preview": {"post": {"summary": "Preview next schedule occurrences"}},
            "/history": {"get": {"summary": "Read bounded execution history"}},
            "/audit": {"get": {"summary": "Read bounded redacted agent audit history"}},
            "/a2a": {"post": {"summary": "A2A JSON-RPC: message/send and tasks/get over the same command service"}},
            "/.well-known/agent-card.json": {"get": {"security": [], "summary": "A2A agent card with skill inventory"}},
            "/openapi.json": {"get": {"security": [], "summary": "OpenAPI 3.1 document"}},
            "/schemas/task-v1.json": {"get": {"security": [], "summary": "Agent task JSON Schema"}}
          }
        }
    """.trimIndent()
}
