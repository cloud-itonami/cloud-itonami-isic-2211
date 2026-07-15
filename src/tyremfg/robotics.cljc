(ns tyremfg.robotics
  "Robot-executed bead-wire pull-out/anchorage verification -- the
  concrete, actor-level realization of ADR-2607011000's robotics
  premise (every cloud-itonami vertical is designed on the premise that
  a robot performs the physical-domain work; an independent governor
  gates any action before it ever reaches hardware) and
  ADR-2607142800's fleet-wide robotics-process-simulation pattern
  (established by `cloud-itonami-isic-2910`'s `automotive.robotics`),
  applied to THIS vertical per ADR-2607999700: this actor's own
  `:verified?`/`:registered?` batch gate (`tyremfg.registry/batch-
  ready?`) is a purely SELF-REPORTED checklist boolean -- nothing in
  this actor previously re-derived a batch's own build quality from a
  real engineering simulation. This ns closes that gap ADDITIVELY,
  never replacing `batch-ready?`/`shipment-quantity-exceeded?`: a
  genuine time-stepped `physics-2d` rigid-body simulation of a
  BEAD-WIRE PULL-OUT / BEAD-UNSEATING-RESISTANCE TEST -- a real
  tyre-industry QA procedure (bead-wire anchorage/retention is safety-
  critical: the bead bundle is what keeps an inflated tyre seated on
  its rim under load, and FMVSS 109/139 (US) / UN Regulation No. 30
  (EU) both require bead-unseating-resistance qualification). A
  representative coupon -- a section of bead-seat rubber with its
  bead-wire bundle still anchored -- is clamped into a pull-fixture jaw
  that pulls the bead wire away from the bead seat at a controlled
  rate, and the peak retention force at the moment the bead wire
  breaks free is compared against a real minimum required anchorage
  force.

  Like `autoparts.robotics`'s weld-joint/fastener proof-load pull test
  (the direct template for this ns) and unlike a genuine approach/
  contact event, this vertical has no design-library sibling repo
  either, so the physics module lives DIRECTLY in this ns and takes a
  real pinned git-coordinate dependency on `kotoba-lang/physics-2d`
  alone (see `deps.edn`).

  HONEST REINTERPRETATION TECHNIQUE (identical in spirit to
  `autoparts.robotics`'s own disclosed technique -- copied here because
  the underlying physical situation is the SAME shape: a joint/bond
  being pulled apart, not two bodies approaching and colliding):
  `physics-2d`'s `world-step` ONLY natively resolves bodies that are
  APPROACHING/colliding -- it has no notion of a body SEPARATING under
  tension, so there is no direct way to simulate 'pull the bead wire
  out of its seat until it breaks free' with this engine's
  collision-only impulse resolver. This ns reframes the SAME physical
  event as an approach instead: `:jaw` (the pull-fixture jaw gripping
  the bead-wire bundle) starts right beside `:fixture` (a static body
  anchoring the bead-seat coupon's own rubber-compound side) and moves
  steadily AWAY from it at a real, controlled pull-test rate -- but a
  THIRD, static `:limit-boundary` body is placed exactly
  `travel-to-unseat-m` (the bead seat's own real compliance/give
  distance before the bead wire breaks free of its rubber anchorage)
  beyond the jaw's start. As the jaw travels, it is really the
  BEAD-WIRE BOND running out of give -- `physics-2d` only knows how to
  render that as the jaw's leading face reaching the limit-boundary's
  near face, at which point its native inelastic (restitution 0)
  collision resolution zeroes the jaw's velocity in a SINGLE tick --
  exactly the 'anchorage holds, then suddenly gives way' event a real
  bead-wire pull-out test exhibits at peak retention load. The peak
  deceleration read off that tick, times the batch's own recorded
  effective participating mass (`:bead-mass-kg` -- the moving pull-
  fixture jaw + the locally-engaged bead-wire-bundle/rubber material),
  is `:sim-bead-pullout-force-n` (Newtons) -- REAL, derived from the
  actual simulated trajectory, never invented.

  Disclosed engineering priors (this ns's own, not measured facts --
  same discipline as `autoparts.robotics`'s constants):

  - `pull-speed-mps` is a deliberately chosen, disclosed ANALOG pull
    rate -- fast enough for `physics-2d`'s single-tick boxcar-collision
    model to produce a physically meaningful impulse -- NOT a literal
    transcription of a real bead-unseating-resistance test rig's actual
    (much slower, quasi-static, ram-driven) crosshead speed. Running
    this SAME single-tick 'boxcar' technique at a genuinely slow
    quasi-static rate would derive a physically negligible reading
    (peak-decel = pull-speed^2 / travel-to-unseat scales with the
    SQUARE of speed, so a slow rate is the wrong physical regime for a
    discrete-collision technique) -- the identical disclosed limitation
    `autoparts.robotics/test-speed-mps` and `deviceassembly.robotics/
    insertion-speed-mps` both state for their own boxcar-technique rate
    choices.
  - `travel-to-unseat-m` is a representative low-single-digit-
    millimeter bead-seat rubber-compound compliance/give distance
    before the bead-wire bundle pulls free of its anchorage -- a real,
    disclosed order of magnitude for this failure mode, analogous to
    `autoparts.robotics/travel-to-failure-m`'s spot-weld button-pull-out
    displacement prior.
  - `initial-grip-slack-m` is a small, real, disclosed test-fixture
    grip-seating/alignment slack the jaw travels BEFORE the bead-wire
    bond itself begins to bear load -- present only so the simulated
    trajectory captures a real pre-load approach phase, not just the
    single stopping tick (mirrors `autoparts.robotics/initial-grip-
    slack-m`).
  - `min-bead-pullout-force-n` is a newly-defined, clearly-disclosed
    real-world floor (the SAME allowance ADR-2607152000 established for
    `autoparts.robotics/min-proof-load-n`, applied here) -- a plausible
    minimum acceptable bead-wire-bundle pull-out/anchorage retention
    force for a passenger/light-truck-class tyre bead construction,
    low-single-digit-to-mid kN range, NOT a literal transcription of
    one specific named standard's exact figure (real bead-unseating-
    resistance requirements vary by tyre class/rim diameter and are set
    by FMVSS 109/139 / UN R30 test procedures this ns does not attempt
    to reproduce exactly).
  - Jaw/fixture/limit-boundary AABB half-extents are FIXED test-rig
    constants, like `deviceassembly.robotics`'s plug/receptacle
    geometry -- this vertical has no CAD/BREP bridge (unlike
    `autoparts.robotics`'s ADR-2607160000 envelope-solid pipeline), so
    there is no per-batch real geometry to derive them from; sizing
    them off nothing real would be dishonest, so they stay disclosed
    fixture-scale placeholders that never change the force reading (see
    below).

  Like `autoparts.robotics`'s weld-joint pull test, `:jaw` collides
  against a mass-0 (immovable) `:limit-boundary`, so `:sim-peak-decel-
  mps2` is PROVABLY MASS-INVARIANT (the impulse that fully arrests the
  jaw's velocity in one tick depends only on the approach velocity, not
  the jaw's own mass -- mass cancels algebraically in `physics-2d`'s
  `resolve-contact`). Consequently the batch's own recorded
  `:bead-mass-kg` DOES directly scale `:sim-bead-pullout-force-n`
  (force = mass x deceleration) -- intentional, not an oversight: a
  real load-cell retention-force reading legitimately depends on the
  physical scale of the coupon/fixture under test, not an accident of
  chosen units.

  `bead-pullout-force-out-of-tolerance?` independently re-derives the
  batch's OWN recorded `:sim-bead-pullout-force-n` against
  `min-bead-pullout-force-n`, never from the mission's self-reported
  result -- the SAME 'ground truth, not self-report' discipline
  `tyremfg.registry/shipment-quantity-exceeded?` established for
  shipment quantity. `tyremfg.governor`'s `robotics-simulation-
  violations` calls this ns's independent recheck, never the stored
  :passed? value, before any `:coordinate-shipment` proposal may
  commit -- ADDITIVE alongside (never replacing) the pre-existing
  `batch-not-verified-violations`/`shipment-quantity-exceeded-
  violations` checks, an UNRELATED real-world QA domain (bead-wire
  anchorage strength, not batch registration/quantity).

  Pure data + pure functions -- no real robot I/O, no network.
  `physics-2d/world-step` is itself a pure, fixed-timestep integrator
  (no wall-clock/IO), so this stays exactly as offline/deterministic as
  every other sibling namespace in this actor -- tests and the demo run
  without a network.

  Honest scope (mirrors `autoparts.robotics`/`deviceassembly.
  robotics`): this DOES model a real time-stepped `physics-2d`
  rigid-body trajectory for the bead-wire pull-out event. It does NOT
  model: bead-wire/rubber-compound material stiffness (`physics-2d` has
  no force-deflection/spring model at all -- the anchorage's 'give' is
  encoded purely as a travel DISTANCE, not a compliance curve), 3D
  geometry (2D projection only, same disclosed limit as every sibling),
  a real load-cell/DAQ connection, or a real robot controller -- still
  simulation, not control, the same 'policy, not control' boundary
  `kotoba.robotics`'s docstring already establishes. This ns explicitly
  does NOT attempt tyre burst/inflation-pressure testing -- that is a
  pressure-vessel/membrane physics problem `physics-2d`'s rigid-body
  AABB engine is a genuinely bad fit for (ADR-2607999700 Decision), so
  it is not modeled here at all, honestly, rather than forced."
  (:require [kotoba.robotics :as robotics]
            [physics-2d :as p2d]))

;; ---------------------------------------------------------------------------
;; Platform shims (mirrors physics-2d's own private sqrt*/abs*/signum* style
;; and `autoparts.robotics`'s/`deviceassembly.robotics`'s identical shims,
;; keeping this ns portable .cljc -- a raw Math/ceil + Math/abs would be
;; JVM-only and break a ClojureScript consumer).
;; ---------------------------------------------------------------------------

(defn- abs* [x] (if (neg? x) (- x) x))

(defn- ceil* [x]
  #?(:clj  (Math/ceil (double x))
     :cljs (js/Math.ceil x)))

(def mission-actions
  "The three-step bead-inspection-cell verification mission every batch
  walks through before `:coordinate-shipment` is proposable for it. All
  :sense/:actuate at :none/:low safety -- coupon-level QA sensing and a
  bounded pull-test cycle on a stationary bead-seat coupon, not the
  outbound-shipment coordination that `:coordinate-shipment` itself
  proposes (governed separately, see `tyremfg.governor`)."
  [{:step :bead-wire-ultrasonic-scan     :kind :sense   :safety :none}
   {:step :bead-coupon-fixture-clamp     :kind :actuate :safety :low}
   {:step :bead-wire-pullout-force-test  :kind :actuate :safety :low}])

;; ---------------------- real bead-wire pull-out physics constants ----------

(def ^:const pull-speed-mps
  "Controlled jaw pull-rate (m/s) -- see ns docstring: a deliberately
  chosen, disclosed ANALOG rate for this single-tick 'boxcar'
  collision technique, not a literal quasi-static crosshead mm/min
  transcription of a real bead-unseating-resistance test rig."
  1.6)

(def ^:const travel-to-unseat-m
  "The bead-wire bundle's own real anchorage compliance/give distance
  (m) before it pulls free of the bead-seat rubber -- see ns
  docstring: a representative low-single-digit-millimeter prior."
  0.0025)

(def ^:const initial-grip-slack-m
  "Test-fixture grip-seating/alignment slack (m) the jaw travels before
  the bead-wire anchorage itself begins to bear load -- present only
  so the trajectory captures a real pre-load approach phase, mirroring
  `autoparts.robotics/initial-grip-slack-m`."
  0.0008)

(def ^:const jaw-half-w-m
  "Jaw AABB half-width along the pull axis (m) -- a small, fixed
  pull-fixture jaw footprint gripping the bead-wire-bundle coupon, not
  a per-batch CAD input (this ns has no CAD/BREP pipeline, unlike
  `autoparts.robotics`'s ADR-2607160000 envelope-solid bridge)."
  0.015)

(def ^:const jaw-half-h-m 0.02)

(def ^:const fixture-half-w-m
  "Bead-seat-coupon-side fixture AABB half-width (m) -- static anchor,
  never actually collides with anything (the jaw moves AWAY from it),
  present purely as a real Body2D so the simulated world honestly
  contains both sides of the bond being pulled apart."
  0.015)

(def ^:const fixture-half-h-m 0.02)

(def ^:const limit-boundary-half-w-m
  "Virtual limit-boundary AABB half-width (m) -- the 'end of tether'
  wall the jaw's approach is reframed against; see ns docstring. This
  body has no physical counterpart at all (it is a pure math device
  standing in for the bead-wire bond running out of give)."
  0.01)

(def ^:const limit-boundary-half-h-m 0.02)

(def ^:const settle-ticks
  "Extra ticks appended after the jaw is expected to reach the
  limit-boundary, so the trajectory also captures post-contact
  settling. `physics-2d`'s positional correction removes 80% of any
  remaining overlap per tick (`resolve-contact`'s `0.8` factor), so
  residual overlap after `settle-ticks` further ticks is `0.2^settle-
  ticks` of whatever it was at first contact -- 15 ticks converges to
  ~3e-11 (same rationale/constant as `autoparts.robotics`'s /
  `deviceassembly.robotics`'s `settle-ticks`, a genuine physics-2d
  engine property, not re-derived here)."
  15)

(def ^:const min-bead-pullout-force-n
  "Real, disclosed minimum acceptable bead-wire-bundle pull-out/
  anchorage retention force (N) for a passenger/light-truck-class tyre
  bead construction -- see ns docstring. 5000 N (5 kN) sits in the
  plausible low-single-digit-to-mid-kN range for this class of
  anchorage; a newly-defined bound, not a literal transcription of one
  specific named standard's number (the SAME allowance ADR-2607152000
  established for `autoparts.robotics/min-proof-load-n`)."
  5000.0)

;; ------------------------------ real simulation ------------------------------

(defn run-bead-pullout-test
  "Time-steps a REAL `physics-2d` world for the bead-wire pull-out/
  bead-unseating-resistance test and returns:

    {:trajectory [{:tick :position :velocity} ...]   ; jaw body only
     :sim-peak-decel-mps2 n :sim-bead-pullout-force-n n
     :ticks n :dt n :pull-speed-mps n :travel-to-unseat-m n}

  `bead-mass-kg` is the batch's own recorded effective participating
  mass of the moving pull-fixture jaw + locally-engaged bead-wire-
  bundle/rubber material (see ns docstring's 'effective participating
  mass' framing, the same shape `autoparts.robotics/run-pull-test`'s
  `:joint-mass-kg` and `deviceassembly.robotics/run-connector-mating-
  test`'s `plug-mass-kg` use).
  opts (all optional, for tuning/testing): `:pull-speed-mps`,
  `:travel-to-unseat-m`, `:initial-grip-slack-m`, `:dt` overrides (each
  defaults to this ns's own constant of the same name).

  `:sim-peak-decel-mps2` is the PEAK magnitude of tick-to-tick velocity
  change (along the pull axis) divided by `dt` -- derived from the
  actual simulated velocity trajectory, not invented; PROVABLY
  MASS-INVARIANT (the `:limit-boundary` is immovable, mass 0, so mass
  cancels algebraically in `physics-2d`'s `resolve-contact` -- see ns
  docstring). `:sim-bead-pullout-force-n` is `:sim-peak-decel-mps2 *
  bead-mass-kg` (Newtons) -- see ns docstring for why mass legitimately
  scales this reading."
  [bead-mass-kg & [{v-opt :pull-speed-mps travel-opt :travel-to-unseat-m
                     slack-opt :initial-grip-slack-m dt-opt :dt}]]
  (let [v      (double (or v-opt pull-speed-mps))
        travel (double (or travel-opt travel-to-unseat-m))
        slack  (double (or slack-opt initial-grip-slack-m))
        dt     (double (or dt-opt (/ travel v)))
        fixture-x 0.0
        jaw-x0 (+ fixture-x fixture-half-w-m jaw-half-w-m)
        limit-boundary-x (+ jaw-x0 slack travel jaw-half-w-m limit-boundary-half-w-m)
        approach-m (+ slack travel)
        ticks (long (+ settle-ticks (long (ceil* (/ approach-m (* v dt))))))
        fixture (p2d/make-body {:position [fixture-x 0.0]
                                 :velocity [0.0 0.0]
                                 :mass 0.0
                                 :restitution 0.0
                                 :friction 0.0
                                 :collider (p2d/make-aabb-collider fixture-half-w-m fixture-half-h-m)
                                 :user-data :fixture})
        jaw (p2d/make-body {:position [jaw-x0 0.0]
                             :velocity [v 0.0]
                             :mass (double bead-mass-kg)
                             :restitution 0.0
                             :friction 0.0
                             :collider (p2d/make-aabb-collider jaw-half-w-m jaw-half-h-m)
                             :user-data :jaw})
        limit-boundary (p2d/make-body {:position [limit-boundary-x 0.0]
                                        :velocity [0.0 0.0]
                                        :mass 0.0
                                        :restitution 0.0
                                        :friction 0.0
                                        :collider (p2d/make-aabb-collider limit-boundary-half-w-m limit-boundary-half-h-m)
                                        :user-data :limit-boundary})
        w0 (p2d/world-new [0.0 0.0])
        [w1 _fixture-id] (p2d/world-add w0 fixture)
        [w2 jaw-id] (p2d/world-add w1 jaw)
        [w3 _limit-id] (p2d/world-add w2 limit-boundary)
        worlds (reductions (fn [w _] (p2d/world-step w dt)) w3 (range ticks))
        trajectory (mapv (fn [tick world]
                            (let [b (nth (:bodies world) jaw-id)]
                              {:tick tick :position (:position b) :velocity (:velocity b)}))
                          (range (count worlds)) worlds)
        vxs (mapv (comp first :velocity) trajectory)
        peak-decel-mps2 (->> (map (fn [va vb] (abs* (/ (- vb va) dt))) vxs (rest vxs))
                              (reduce max 0.0))]
    {:trajectory trajectory
     :sim-peak-decel-mps2 peak-decel-mps2
     :sim-bead-pullout-force-n (* peak-decel-mps2 (double bead-mass-kg))
     :ticks (count trajectory)
     :dt dt
     :pull-speed-mps v
     :travel-to-unseat-m travel}))

(defn bead-pullout-telemetry-for
  "Runs the REAL `run-bead-pullout-test` `physics-2d` simulation for
  `batch`'s own recorded `:bead-mass-kg` and returns the actual
  simulated trajectory telemetry: `{:sim-bead-pullout-force-n n
  :sim-peak-decel-mps2 n :ticks n :dt n :pull-speed-mps n
  :travel-to-unseat-m n}`. Pure, deterministic -- the same
  `:bead-mass-kg` always reproduces the same telemetry."
  [batch]
  (select-keys (run-bead-pullout-test (:bead-mass-kg batch))
               [:sim-bead-pullout-force-n :sim-peak-decel-mps2 :ticks :dt
                :pull-speed-mps :travel-to-unseat-m]))

(defn bead-pullout-force-out-of-tolerance?
  "Ground-truth check: does `batch`'s own recorded
  `:sim-bead-pullout-force-n` (the REAL `run-bead-pullout-test`
  trajectory telemetry already on file for this batch -- see
  `bead-pullout-telemetry-for`) fall below `min-bead-pullout-force-n`?
  Needs no mission run -- its inputs are permanent fields already on
  the batch, the same shape `tyremfg.registry/shipment-quantity-
  exceeded?` uses for shipped quantity. Missing telemetry (`nil`) is
  never silently treated as a violation."
  [{:keys [sim-bead-pullout-force-n]}]
  (and (number? sim-bead-pullout-force-n)
       (< sim-bead-pullout-force-n min-bead-pullout-force-n)))

(defn simulate-bead-pullout-cell
  "Run the robot bead-wire pull-out verification mission for `batch-id`
  (`batch` is the full batch record, incl. `:bead-mass-kg`). Actually
  runs the REAL engine: `bead-pullout-telemetry-for` -- the actual
  `physics-2d`-stepped bead-wire pull-out trajectory
  (`:sim-bead-pullout-force-n`/`:sim-peak-decel-mps2`).

  Returns {:mission .. :actions [{:action .. :proof ..} ..] :passed?
  bool :sim-bead-pullout-force-n n :sim-peak-decel-mps2 n}.
  Deterministic: :passed? is derived from the batch's OWN recorded
  `:bead-mass-kg` via the REAL simulated trajectory
  (`bead-pullout-force-out-of-tolerance?`), never invented or
  randomized -- `kotoba.robotics` mandates no network/IO, and a
  repeatable simulation is what makes the governor's independent
  recheck meaningful."
  [batch-id batch]
  (let [telemetry (bead-pullout-telemetry-for batch)
        out-of-range? (bead-pullout-force-out-of-tolerance? (merge batch telemetry))
        reading (if out-of-range? :out-of-tolerance :nominal)
        mission (robotics/mission (str "mission-" batch-id "-bead-pullout-verify")
                                   :robot/bead-pullout-cell-1
                                   :bead-anchorage-verification
                                   :boundaries {:station "end-of-line-bead-inspection-cell"}
                                   :max-steps (count mission-actions))
        actions (mapv (fn [{:keys [step kind safety]}]
                        (let [a (robotics/action (str (:mission/id mission) "-" (name step))
                                                  (:mission/id mission) kind safety
                                                  :params {:step step :batch-id batch-id})]
                          {:action a
                           :proof (robotics/telemetry-proof (:mission/id mission) step reading
                                                             :provenance :simulated)}))
                      mission-actions)]
    {:mission mission
     :actions actions
     :passed? (not out-of-range?)
     :sim-bead-pullout-force-n (:sim-bead-pullout-force-n telemetry)
     :sim-peak-decel-mps2 (:sim-peak-decel-mps2 telemetry)}))

(defn simulation-out-of-tolerance?
  "Independent ground-truth recheck for the governor: does `batch`'s
  OWN current, on-file real `physics-2d`-simulated bead-wire pull-out
  telemetry (`:sim-bead-pullout-force-n`) fall out of tolerance right
  now? Ignores whatever :passed? verdict a prior mission run stored --
  identical in spirit to `tyremfg.registry/shipment-quantity-
  exceeded?`'s refusal to trust a proposal's self-report."
  [batch]
  (bead-pullout-force-out-of-tolerance? batch))
