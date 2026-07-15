# cloud-itonami-isic-2211: Manufacture of rubber tyres and tubes

Open Business Blueprint for **ISIC 2211**: manufacture of rubber tyres and tubes — an autonomous "actor" (LLM advisor behind an independent Governor, langgraph-clj StateGraph, append-only audit ledger) that coordinates back-office **tyre-plant operations**: production-batch data logging (tyre-category/load-index/quantity/defect-rate), building/curing-line maintenance scheduling, safety-concern flagging, and outbound tyre shipment coordination.

This repository designs a forkable OSS business for tyre-plant
operations: run by a qualified operator so a plant keeps its own
operating records instead of renting a closed SaaS.

## Scope: plant operations coordination, not tyre-line control

ISIC 2211 covers the **manufacturing plant** that compounds rubber,
builds "green" (uncured) tyres, cures/vulcanizes them under heat and
pressure, and finishes/inspects the resulting pneumatic and solid
tyres and inner tubes. This actor coordinates the back-office record
keeping around that plant — it never touches the tyre-building/
curing/vulcanization equipment directly, and it is never the DOT (US)
/ ECE (EU) tyre-safety certification authority.

## What this actor does

Proposes **plant operations coordination**, not equipment operation:
- `:log-production-batch` — tyre-build/cure batch, size/load-index, output-quality data logging (administrative, not an operational decision)
- `:schedule-maintenance` — building/curing-equipment maintenance scheduling proposal
- `:flag-safety-concern` — surface a curing-defect/DOT-compliance/equipment-safety concern (always escalates)
- `:coordinate-shipment` — outbound tyre shipment coordination proposal

## What this actor does NOT do

**CRITICAL SCOPE BOUNDARY — this is a safety-critical domain**
(building/curing/vulcanization line equipment, high-temperature/
high-pressure curing hazard, DOT/ECE tyre-safety certification,
public road-safety consequence):

- Does NOT control tyre-building or curing/vulcanization-line equipment directly
- Does NOT make plant-safety or certification decisions (that's the plant supervisor's / certification body's exclusive human/institutional authority)
- Does NOT actuate the building/curing/vulcanization line (human plant supervisor decides)
- Does NOT self-issue a DOT/ECE tyre-safety certification mark (the accredited certification body's exclusive authority — a PERMANENT, unconditional block)
- ONLY proposes/coordinates operations back-office; all actuation and certification requires explicit human/institutional authority
- Safety-concern flagging ALWAYS escalates — never auto-decided, no confidence threshold or phase below escalation

## Architecture

Classic governed-actor pattern (`tyremfg.operation/build`, a langgraph-clj StateGraph):
1. **`tyremfg.advisor`** (sealed intelligence node, `TyreAdvisor`): proposes decisions only, never commits
2. **`tyremfg.governor`** (independent, `Tyre Plant Operations Governor`): validates against domain rules, re-derived from `tyremfg.registry`'s pure functions and `tyremfg.store`'s SSoT -- never trusts the advisor's own self-report
   - HARD invariants (always `:hold`, no override):
     - Plant/batch record must be independently verified/registered (`:verified?` AND `:registered?`) before any action is taken against it (equipment before maintenance scheduling, batch before shipment coordination)
     - The request's own `:effect` must be `:propose` (never a direct-write bypass)
     - `:op` must be in the closed four-op allowlist
     - The proposal's own `:effect` must be one of the four propose-shaped effects (no direct building/curing-line-equipment control)
     - Directly actuating the building/curing/vulcanization line (`:actuate-line? true`) is a PERMANENT, unconditional block
     - Self-issuing a DOT/ECE tyre-safety certification (`:issue-certification? true`, any op) is a PERMANENT, unconditional block
     - A shipment may not push a batch's own recorded shipped quantity past its own logged production quantity (independently recomputed)
     - No double-scheduling the same maintenance record
     - No fabricated `:tyre-category` value on a production-batch patch
     - No physically implausible `:load-index` value on a production-batch patch
     - No physically implausible `:defect-rate-percent` value on a production-batch patch
     - A batch's own recorded bead-wire pull-out/anchorage force must clear its real minimum retention floor before any shipment coordination (ADR-2607999700, independently re-derived from a REAL `physics-2d` simulation — see Robotics below; ADDITIVE alongside, never a replacement for, the verified/registered gate)
   - ESCALATE (always human sign-off, overridable by a human):
     - `:flag-safety-concern` always escalates, regardless of confidence
     - Low-confidence proposals
3. **`tyremfg.phase`** (Phase 0->3 rollout): `:schedule-maintenance`/`:flag-safety-concern`/`:coordinate-shipment` are NEVER in any phase's `:auto` set (permanent, matching the governor's own posture); only `:log-production-batch` may auto-commit at phase 3 when clean
4. **`tyremfg.store`** (append-only audit ledger + SSoT): a single `MemStore` backend behind a `Store` protocol (see ns docstring for why a second Datomic-backed backend is out of scope for this build)
5. **`tyremfg.robotics`** (ADR-2607999700): a real, time-stepped `physics-2d` rigid-body simulation of a bead-wire pull-out/bead-unseating-resistance test — closes the gap that `:verified?`/`:registered?` were purely self-reported checklist booleans with no engineering-simulation backing. `tyremfg.governor` independently re-derives the batch's own recorded `:sim-bead-pullout-force-n` against a real minimum retention floor before `:coordinate-shipment` may commit, never trusting the mission's own stored `:passed?` verdict. Honest scope: this models a real pull-apart event (reframed as an approach against a virtual limit-boundary, since `physics-2d` only natively resolves approaching/colliding bodies — see ns docstring); it does NOT attempt tyre burst/inflation-pressure testing, a pressure-vessel/membrane physics problem this rigid-body engine is a genuinely bad fit for.

## Development

```bash
# Run tests (top-level deps.edn already pins langgraph+langchain local/root)
clojure -M:test

# Run tests via the workspace :dev override alias (equivalent, kept for sibling-repo parity)
clojure -M:dev:test

# Run the demo
clojure -M:dev:run

# Lint
clojure -M:lint
```

## Status

`:implemented` — `governor.cljc`/`store.cljc`/`advisor.cljc`/`registry.cljc`/`robotics.cljc` + `deps.edn` complete the module set; tests green, demo runnable, langgraph-clj integration verified.

## License

AGPL-3.0-or-later
