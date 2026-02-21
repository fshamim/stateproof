#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SAMPLE_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
GEN_DIR="$SAMPLE_DIR/composeApp/src/desktopTest/kotlin/generated/stateproof"

if [[ ! -d "$GEN_DIR" ]]; then
  echo "No generated test directory found at $GEN_DIR"
  exit 0
fi

while IFS= read -r -d '' file; do
  if grep -q "TODO: Implement test - create state machine and fire events" "$file"; then
    tmp_file="$(mktemp)"

    awk '
    BEGIN { skipping = 0 }

    {
      if (skipping == 1) {
        if ($0 ~ /\/\/ assertEquals\(expectedTransitions, sm.getTransitionLog\(\)\)/) {
          skipping = 0
        }
        next
      }

      if ($0 ~ /\/\/ TODO: Implement test - create state machine and fire events/) {
        match($0, /^[[:space:]]*/)
        indent = substr($0, RSTART, RLENGTH)
        print indent "val runtime = createTaskProofRuntimeForIntrospection()"
        print indent "val sm = runtime.stateMachine"
        print indent "for (transition in expectedTransitions) {"
        print indent "    sm.onEvent(eventForTransition(sm.currentState, transition, runtime))"
        print indent "}"
        print indent "sm.awaitIdle()"
        print indent "assertGeneratedTransitions(expectedTransitions, sm.getTransitionLog())"
        print indent "runtime.close()"
        skipping = 1
        next
      }

      print $0
    }
    ' "$file" > "$tmp_file"

    mv "$tmp_file" "$file"
  fi

  if grep -q "val sm = createTaskProofStateMachineForIntrospection()" "$file"; then
    perl -0pi -e 's/val sm = createTaskProofStateMachineForIntrospection\(\)/val runtime = createTaskProofRuntimeForIntrospection()\n        val sm = runtime.stateMachine/g' "$file"
    perl -0pi -e 's/eventForTransition\(sm.currentState, transition\)/eventForTransition(sm.currentState, transition, runtime)/g' "$file"
    perl -0pi -e 's/\bsm\.close\(\)/runtime.close()/g' "$file"
  fi

  echo "Hydrated $file"
done < <(find "$GEN_DIR" -type f -name '*.kt' -print0)
