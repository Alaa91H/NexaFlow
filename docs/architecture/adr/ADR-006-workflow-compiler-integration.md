# ADR-006: Workflow Compiler Integration

**Status:** Accepted  
**Date:** 2026-09-28

## Context

`WorkflowDocumentCompiler` already compiles a versioned pure document into the
runtime `WorkflowNode` graph. Introducing another compiler/interpreter would
split control-flow safety and make behavior diverge.

## Decision

Extend the existing compilation boundary with explicit pure phases:

```
parse/adapter → normalize → type-check → semantic-check
→ capability preflight → execution planning → WorkflowNode runtime graph
```

`WorkflowDocumentCompiler` remains the execution-boundary compiler. Canonical
leaf compilation is added to it (or a pure front-end invoked by it), while
control-flow nodes continue using the existing runtime model.

Compilation has no side effects. Named functions/predicates require registered
typed implementations; unresolved values and unsupported constructs fail closed.

## Invariants

- There is one runtime interpreter.
- Compiler output cannot bypass execution budgets/cancellation.
- Validation happens before the first side effect.
- Typed canonical errors identify the failing node/field.
- Legacy `AutomationWorkflowMapper` remains a compatibility adapter until
  ADR-015 retirement criteria are met.

## Consequences

The project gains language-like type/semantic checks without discarding mature
workflow flow-control and safety mechanisms.
