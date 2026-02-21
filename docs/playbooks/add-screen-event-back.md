# Playbook: Add Screen + Event + Back Path

## Goal

Extend an existing StateProof state machine with a new screen flow using the KMP route-only architecture.

## Checklist

1. Add route state
   - add route-only state object in `AppState` (no data payload)
2. Add view events
   - add `AppEvent` for machine-routed behavior
   - add `LocalUiEvent` only for local/view-model behavior
3. Add transitions
   - forward path(s)
   - back path(s)
   - guarded alternatives for data-dependent behavior
4. Add/adjust reactive state-data container
   - add `MutableStateFlow` field(s) for screen/business data
   - expose read-only flow(s) through view-model state
5. Side-effect boundaries
   - use repositories for IO/business side effects
   - avoid direct DB/service calls in UI
6. In-place updates
   - use `doNotTransition()` when route should remain unchanged
7. Navigation mapping
   - map new route state to screen/destination
   - handle stale selection/invalid detail route fallback
8. Sync and validate
   - run sync/diagram/viewer tasks
   - inspect generated tests for `STATEPROOF:MANUAL_REQUIRED`
   - implement manual-required tests with shared helper strategy

## Quality gates

1. Every non-terminal state has explicit inbound/outbound transitions.
2. Back behavior is explicit and deterministic.
3. Guarded logic is declarative in transition metadata.
4. Generated expected transitions remain untouched by manual edits.
