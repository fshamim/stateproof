# Playbook: Add Screen + Event + Back Path

## Goal

Extend an existing StateProof state machine with one new screen flow.

## Checklist

1. Add state
   - new screen state in sealed state hierarchy
2. Add events
   - user-intent events (enter, primary action, back, error)
3. Add transitions
   - forward path(s)
   - back path(s)
   - guarded alternatives for data-dependent behavior
4. Add side-effect metadata
   - declare emitted events for side effects that branch behavior
5. Navigation mapping
   - map new state to composable destination
6. Sync and validate
   - run sync/diagram/viewer tasks
   - verify expected test growth and transitions

## Quality gates

- every new state has at least one inbound and outbound transition (except terminal states)
- back behavior is explicit
- no hidden transition logic in side effects
