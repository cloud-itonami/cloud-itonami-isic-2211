(ns tyremfg.robotics-test
  "Direct tests of `tyremfg.robotics`'s REAL, ADR-2607999700
  time-stepped `physics-2d` bead-wire pull-out/bead-unseating-
  resistance simulation -- proving `:sim-bead-pullout-force-n` is
  actually DERIVED from the simulated trajectory (changes sensibly
  with `bead-mass-kg`/`pull-speed-mps`, is deterministic/repeatable,
  and the peak deceleration is mass-invariant against the immovable
  limit-boundary), the same shape this fleet's other `*-robotics-test`
  suites (`autoparts.robotics-test`, `deviceassembly.robotics-test`)
  use to prove a physics check isn't invented or randomized --
  alongside the UNCHANGED pre-existing self-reported `:verified?`/
  `:registered?` batch checklist, proving this ADR is purely
  additive (see `tyremfg.governor-contract-test` for the governor-level
  HARD-hold wiring)."
  (:require [clojure.test :refer [deftest is testing]]
            [tyremfg.robotics :as robotics]))

(defn- abs* [x] (if (neg? x) (- x) x))
(defn- approx= [a b eps] (< (abs* (double (- a b))) eps))

(deftest bead-pullout-test-runs-a-real-trajectory
  (testing "run-bead-pullout-test returns a non-trivial, tick-by-tick trajectory -- not a single invented number"
    (let [{:keys [trajectory ticks dt pull-speed-mps travel-to-unseat-m]} (robotics/run-bead-pullout-test 7.2)]
      (is (> ticks 1) "more than one simulated tick")
      (is (= ticks (count trajectory)))
      (is (pos? dt))
      (is (= robotics/pull-speed-mps pull-speed-mps))
      (is (= robotics/travel-to-unseat-m travel-to-unseat-m))
      (testing "the jaw starts moving at the full pull speed"
        (is (= pull-speed-mps (first (:velocity (first trajectory))))))
      (testing "the jaw's velocity actually drops to (near) zero once the bead wire pulls free -- a real collision was resolved, not skipped"
        (is (< (abs* (first (:velocity (last trajectory)))) 1.0e-6))))))

(deftest bead-pullout-force-scales-with-bead-mass
  (testing "F = m*a: a heavier bead-mass-kg input yields a proportionally larger peak pull-out force, off the SAME simulated deceleration -- proves the reading is derived, not a fixed/invented constant"
    (let [light (robotics/run-bead-pullout-test 3.0)
          heavy (robotics/run-bead-pullout-test 6.0)]
      (is (< (:sim-bead-pullout-force-n light) (:sim-bead-pullout-force-n heavy)))
      (is (approx= (* 2.0 (:sim-bead-pullout-force-n light)) (:sim-bead-pullout-force-n heavy) 1.0e-6)
          "force doubles (within floating-point tolerance) with mass -- same peak deceleration, per ns docstring's mass-invariance disclosure")
      (testing "peak deceleration itself is mass-invariant (the limit-boundary is immovable, mass cancels algebraically)"
        (is (approx= (:sim-peak-decel-mps2 light) (:sim-peak-decel-mps2 heavy) 1.0e-9))))))

(deftest bead-pullout-force-scales-with-pull-speed
  (testing "a faster controlled pull-speed-mps yields a larger peak force off the SAME bead mass -- a second independent axis the reading actually tracks"
    (let [slow (robotics/run-bead-pullout-test 5.0 {:pull-speed-mps 0.8})
          fast (robotics/run-bead-pullout-test 5.0 {:pull-speed-mps 2.5})]
      (is (< (:sim-bead-pullout-force-n slow) (:sim-bead-pullout-force-n fast))))))

(deftest bead-pullout-simulation-is-deterministic
  (testing "the same bead-mass-kg always reproduces the same telemetry -- no wall-clock/IO/randomness"
    (let [a (robotics/run-bead-pullout-test 6.5)
          b (robotics/run-bead-pullout-test 6.5)]
      (is (= (dissoc a :trajectory) (dissoc b :trajectory)))
      (is (= a b)))))

(deftest bead-pullout-telemetry-for-reads-the-batchs-own-mass
  (testing "bead-pullout-telemetry-for runs the real simulation off :bead-mass-kg, not a hand-typed double"
    (let [light-batch {:bead-mass-kg 3.0}
          heavy-batch {:bead-mass-kg 9.0}
          light-telemetry (robotics/bead-pullout-telemetry-for light-batch)
          heavy-telemetry (robotics/bead-pullout-telemetry-for heavy-batch)]
      (is (= (:sim-bead-pullout-force-n light-telemetry)
             (:sim-bead-pullout-force-n (robotics/run-bead-pullout-test 3.0))))
      (is (< (:sim-bead-pullout-force-n light-telemetry) (:sim-bead-pullout-force-n heavy-telemetry))))))

(deftest bead-pullout-force-out-of-tolerance-thresholds-on-the-real-floor
  (testing "a batch whose real simulated peak pull-out force is at/over the floor is in-tolerance; under it is out-of-tolerance"
    (is (false? (robotics/bead-pullout-force-out-of-tolerance? {:sim-bead-pullout-force-n (+ robotics/min-bead-pullout-force-n 1.0)})))
    (is (true? (robotics/bead-pullout-force-out-of-tolerance? {:sim-bead-pullout-force-n (- robotics/min-bead-pullout-force-n 1.0)})))
    (is (false? (robotics/bead-pullout-force-out-of-tolerance? {:sim-bead-pullout-force-n nil}))
        "missing telemetry is never silently treated as a violation")))

(deftest simulate-bead-pullout-cell-mission-shape
  (testing "simulate-bead-pullout-cell walks the real 3-step mission and folds the real telemetry's verdict into :passed?"
    (let [clean {:bead-mass-kg 7.2}
          weak {:bead-mass-kg 0.4}
          clean-run (robotics/simulate-bead-pullout-cell "batch-clean" clean)
          weak-run (robotics/simulate-bead-pullout-cell "batch-weak" weak)]
      (is (true? (:passed? clean-run)))
      (is (false? (:passed? weak-run)))
      (is (= 3 (count (:actions clean-run))))
      (is (= 3 (count robotics/mission-actions)))
      (is (pos? (:sim-bead-pullout-force-n clean-run)))
      (is (= (:sim-bead-pullout-force-n weak-run)
             (:sim-bead-pullout-force-n (robotics/bead-pullout-telemetry-for weak)))
          "the mission's reported force is the SAME real simulated reading, not a re-derived/invented one"))))

(deftest simulation-out-of-tolerance-mirrors-bead-pullout-force-check
  (testing "the governor-facing independent recheck delegates to bead-pullout-force-out-of-tolerance? unchanged"
    (let [weak-batch (merge {:bead-mass-kg 0.4} (robotics/bead-pullout-telemetry-for {:bead-mass-kg 0.4}))
          strong-batch (merge {:bead-mass-kg 7.2} (robotics/bead-pullout-telemetry-for {:bead-mass-kg 7.2}))]
      (is (true? (robotics/simulation-out-of-tolerance? weak-batch)))
      (is (false? (robotics/simulation-out-of-tolerance? strong-batch))))))

(deftest independent-recheck-catches-a-too-light-bead-mass-even-when-self-reported-verified
  (testing "the SAME 'ground truth, not self-report' discipline this fleet's lot-5/device-unit-6 misclassified-record pattern establishes: a batch can claim :robotics-sim-verified? true, but the real re-run simulation still catches an implausibly light bead-mass-kg"
    (let [batch {:bead-mass-kg 0.3 :robotics-sim-verified? true}
          rechecked (merge batch (robotics/bead-pullout-telemetry-for batch))]
      (is (true? (:robotics-sim-verified? rechecked)) "the self-reported flag still (falsely) claims verified")
      (is (true? (robotics/simulation-out-of-tolerance? rechecked))
          "but the independent, real re-simulation catches it anyway"))))
