# NexaFlow dashboard, automation options, and AI settings design

## User-approved outcome

The home screen should keep the existing floating New Task action and move the
Ask NexaFlow entry point beside it as a second floating action. In the
automation builder, advanced trigger and action choices should each expand and
collapse when their header is tapped.

AI configuration should be organized within the AI-specific settings screen
using three top-level tabs: **Agents**, **Logs**, and **Add agent**. Logs in this
screen mean agent activity (provider verification and agent use/status events),
not conversation transcripts. The add flow should provide ready-to-use
presets for OpenAI, Claude, Gemini, and OpenCode, ask the user for their API
key, discover available models where supported, offer model selection and a
simple ready-made capability/quality level, verify provider connectivity, and
retain a manual/custom provider route so more providers can be added later.
Adding a provider should make it available in Agents.

The general Settings area should have a separate activity/history destination
for automation execution history, blocked-call events, and NexaFlow SMS events.
SMS history is limited to messages/events handled by NexaFlow automations; it
must not read the device's full SMS inbox or add `READ_SMS` permission.

## Structure and behavior

### Dashboard and automation builder

- Replace the dashboard's large Ask NexaFlow card with a floating action beside
  New Task, keeping both actions visible, labeled accessibly, and usable with
  RTL layout and system insets.
- Keep advanced trigger options and advanced action options independently
  controlled. Their section headers remain interactive in both collapsed and
  expanded states and expose the current state accessibly.

### AI-specific settings

- Use three top tabs: Agents, Logs, Add agent. Keep provider configuration and
  AI activity inside this AI-specific screen.
- Agents lists configured profiles, identifies the selected/default agent,
  and provides existing edit, verify, select, and remove behavior.
- Logs lists AI agent activity with useful event type, provider/agent, time,
  status, and error details when available. It is an event log, not a chat
  transcript store.
- Add agent presents presets for OpenAI, Claude, Gemini, and OpenCode, plus a
  custom OpenAI-compatible/manual provider option. Presets supply protocol and
  endpoint defaults; credentials remain user supplied and stored using the
  existing secure credential handling. Model selection is a picker backed by
  provider model discovery when available. If discovery is unsupported or
  fails, allow a manual model identifier. Offer a small set of understandable
  quality/latency levels with documented mapping to provider parameters.
- Verify checks the selected provider configuration and returns actionable
  success/failure feedback before or after saving. Provider definitions must
  remain catalog/adapter driven so adding providers does not require redesigning
  the screen.

### General activity/history

- Add a dedicated Settings destination that groups existing automation
  executions, blocked-call events, and NexaFlow SMS events, with clear
  navigation/filters appropriate to each data type.
- Capture SMS activity only at NexaFlow's own send/receive/processing points.
  Persist bounded event metadata and delivery/result status; never persist
  message contents unless existing product behavior already does so and the
  event schema explicitly requires it. No inbox permission is introduced.

## Data, compatibility, and security

- Preserve existing provider profiles and credentials through migration-free
  UI restructuring where feasible; do not silently replace a user's model or
  selected profile.
- Use current persistence and encryption paths for provider secrets.
- Keep AI activity and SMS event logs bounded and clearable; avoid API keys,
  message bodies, or other secrets in logs.
- Keep routing for execution and blocked-call histories behind their current
  repositories/view models. Add a focused SMS event store/repository through
  existing data-layer conventions rather than granting broad SMS access.

## Acceptance criteria

1. Dashboard presents both floating actions and no longer shows the old Ask
   NexaFlow card.
2. Advanced trigger and action sections independently expand and collapse by
   tapping their section control.
3. AI settings has the three approved tabs; configured agents are listed, and
   provider activity is distinct from chat transcripts.
4. OpenAI, Claude, Gemini, OpenCode, and custom/manual provider flows can be
   configured with a user API key; users can discover/select models when the
   provider supports discovery, use manual model IDs as fallback, select a
   ready-made level, and verify connectivity.
5. A separate general activity/history screen surfaces automation execution,
   blocked-call, and NexaFlow-only SMS events.
6. No `READ_SMS` permission is added; NexaFlow SMS events are recorded only
   from NexaFlow-owned automation handling paths.
7. Existing profiles, history screens, and deep links continue to work or
   migrate to the new destinations without data loss.

## Out of scope

- Persisting AI chat transcripts in the Logs tab.
- Reading, importing, or indexing the device-wide SMS inbox.
- Expanding SMS event logging beyond messages handled by NexaFlow.
- Building provider-specific UI forks for each new provider.

## Assumptions to validate during implementation

- Provider model discovery and health checks vary by protocol; each adapter
  reports unsupported discovery explicitly and the UI offers manual fallback.
- Existing AI and blocked-call logs may have different retention/storage
  mechanisms; the unified destination can compose existing screens rather than
  force all data into one database table.
- The new SMS history records event metadata/status only, not message bodies.
