# Capability comparison and roadmap

Status date: 2026-09-17. This is a development roadmap, not a statement that unimplemented work is complete.

## Automate comparison

[Automate](https://llamalab.com/automate/) advertises more than 400 blocks and publishes a [block reference](https://llamalab.com/automate/doc/block/index.html). NexaFlow currently has 56 trigger enum entries and 176 action enum entries, with multiple operations inside some entries. Those quantities are not directly interchangeable. There is no independent benchmark establishing NexaFlow as better in every dimension.

| Dimension | Current evidence | Work required before a superiority claim |
| --- | --- | --- |
| Catalog breadth | Generated enum/picker coverage; eight new data actions and eight added sensor modes | Map equivalent user outcomes across the complete competitor catalog; implement missing outcomes with real tests |
| Customization | Data operations, sensor comparisons, HTTP headers/methods/retries, existing per-action editors | Audit every existing editor against handler inputs and runtime validation; close per-option gaps |
| Security | Bounded ingress, revocable capabilities, HTTPS/DNS policy, release manifest audit | Independent review, device permission-gate tests and malicious-provider testing |
| Reliability | Unit/integration suites and CI gates | Long-running device tests across Android versions, OEM background restrictions and process death |
| Usability | Compose builder, history, details and capability hints | Comparative task-completion studies, accessibility and localization review |
| Integrations | Android controls, plugin paths and HTTP | Verify each advertised integration end to end; broaden files/media/cloud capabilities where gaps are measured |
| Distribution | Signed tag-release pipeline and English changelog | Confirm every candidate's exact SHA, artifact identity and green CI before publication |

## Next acceptance milestones

1. Produce an outcome-based comparison matrix, including unavailable, restricted and unsupported states rather than raw counts.
2. Complete a schema-driven audit of all existing configurations: defaults, valid ranges, empty states, errors, permissions, outputs and exit semantics.
3. Add missing capabilities only with production routing, full editor support and integration tests.
4. Run an Android/OEM device matrix, including API 26, current Android, denied permissions and absent hardware. Record actual devices and results.
5. Complete translations for new configuration surfaces and validate RTL/accessibility layouts.
6. Measure background delivery, cancellation, resource budgets and recovery under sustained operation.

These milestones remain open. This release must not be described as completing the entire competitive roadmap.
