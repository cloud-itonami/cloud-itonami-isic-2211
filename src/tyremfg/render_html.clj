(ns tyremfg.render-html
  "Build-time HTML renderer for `docs/samples/operator-console.html`.

  Closes flagship checklist item 2 (com-junkawasaki/root ADR-2607189300,
  Wave5 rollout template: `90-docs/business/cloud-itonami-flagship-
  generator-template.edn`). This repo previously had NO demo page and
  no generator at all. This namespace drives the REAL actor stack
  (`tyremfg.operation` -> `tyremfg.governor` -> `tyremfg.store`) through
  a scenario adapted from this repo's own `tyremfg.sim` demo driver
  (`clojure -M:dev:run`, confirmed by running it directly to use ids
  that DO match `tyremfg.store/sample-data!`'s real seed data --
  batch-001/002/003, builder-001, curing-press-002, mnt-1/2/3, ship-1/
  2/3 -- and to produce the exact dispositions/violation rules quoted
  below; unlike `cloud-itonami-isic-851`'s known-bad `schoolops.sim`,
  this repo's own sim driver was safe to mine directly rather than
  author from scratch), trimmed to a representative subset (one full
  auto-commit + three escalate->approve lifecycles, and four distinct
  HARD-hold reasons that never reach a human) and rendered
  deterministically -- no invented numbers, no timestamps in the page
  content, byte-identical across reruns against the same seed (verify
  by diffing two consecutive runs before shipping).

  Usage: `clojure -M:dev:render-html [out-file]`
  (default `docs/samples/operator-console.html`)."
  (:require [clojure.string :as str]
            [tyremfg.store :as store]
            [tyremfg.operation :as op]
            [langgraph.graph :as g]))

;; ----------------------------- harness (unchanged across every repo
;; in this cluster -- do not rewrite, only copy) -----------------------

(def ^:private operator
  {:actor-id "op-1" :actor-role :plant-operations-coordinator :phase 3})

(defn- exec! [actor tid request]
  (g/run* actor {:request request :context operator} {:thread-id tid}))

(defn- approve! [actor tid]
  (g/run* actor {:approval {:status :approved :by "op-1"}}
          {:thread-id tid :resume? true}))

(defn run-demo!
  "Runs a fresh seeded store through a scenario mixing every disposition
  this actor can reach:

  Clean paths:
    - batch-001 clears a production-batch intake patch (governor-clean,
      high confidence, phase-3 `:auto` -- auto-commits, no human).
    - mnt-1 schedules maintenance against builder-001 (verified +
      registered tyre-building machine -- governor-clean, but
      `:schedule-maintenance` is never in any phase's `:auto` set, so
      it ALWAYS escalates -- approved).
    - concern-1 flags a safety concern on builder-001 (ALWAYS
      high-stakes per `governor/high-stakes`, regardless of confidence
      -- escalates, approved).
    - ship-1 coordinates a 500-unit shipment against batch-001
      (verified + registered, well within its own 5000-unit logged
      quantity and 1000 already shipped -- governor-clean but
      `:coordinate-shipment` is also never auto-eligible -- escalates,
      approved).

  HARD-hold paths (never reach a human, each a DISTINCT real governor
  rule from `tyremfg.governor`):
    - mnt-2 tries to schedule maintenance against curing-press-002,
      whose own record is `:verified? false :registered? false` ->
      `:equipment-not-verified`.
    - ship-2 tries to coordinate a shipment against batch-003, whose
      own record is `:verified? false :registered? false` ->
      `:batch-not-verified` (also trips `:robotics-simulation-missing`,
      since an unverified batch never ran the bead-pullout mission
      either -- both real, both shown in this scenario's own ledger
      basis).
    - ship-3 tries to coordinate 100 more units against batch-002,
      whose own record already has 750.0 of its 800.0-unit logged
      quantity shipped (750.0 + 100.0 > 800.0) ->
      `:shipment-quantity-exceeded`.
    - mnt-3 tries to schedule maintenance against builder-001 (itself
      verified + registered) but the proposal's own `:value` declares
      `:actuate-line? true` -> `:line-actuate-blocked`, a PERMANENT
      scope boundary no phase or human approval can ever override.

  Returns the resulting store -- every field `render` below reads is
  real governor/store output, not a hand-typed copy."
  []
  (let [db (-> (store/mem-store) (store/sample-data!))
        actor (op/build db)]

    (exec! actor "t1-batch"
           {:op :log-production-batch :effect :propose :subject "batch-001"
            :patch {:tyre-category :passenger :last-assessed "2026-07-14"}})

    (exec! actor "t2-mnt"
           {:op :schedule-maintenance :effect :propose :subject "mnt-1"
            :value {:equipment-id "builder-001" :maintenance-type :drum-inspection
                    :scheduled-date "2026-08-01" :actuate-line? false}})
    (approve! actor "t2-mnt")

    (exec! actor "t3-safety"
           {:op :flag-safety-concern :effect :propose :subject "concern-1"
            :value {:equipment-id "builder-001" :severity :moderate
                    :description "成型ドラムの異音、加硫プレスの温度異常兆候"}})
    (approve! actor "t3-safety")

    (exec! actor "t4-ship"
           {:op :coordinate-shipment :effect :propose :subject "ship-1"
            :value {:batch-id "batch-001" :units 500.0
                    :destination "buyer-yard-north"}})
    (approve! actor "t4-ship")

    (exec! actor "t5-mnt-unverified"
           {:op :schedule-maintenance :effect :propose :subject "mnt-2"
            :value {:equipment-id "curing-press-002" :maintenance-type :mold-inspection
                    :scheduled-date "2026-08-01" :actuate-line? false}})

    (exec! actor "t6-ship-unverified"
           {:op :coordinate-shipment :effect :propose :subject "ship-2"
            :value {:batch-id "batch-003" :units 100.0
                    :destination "buyer-yard-south"}})

    (exec! actor "t7-ship-over-quantity"
           {:op :coordinate-shipment :effect :propose :subject "ship-3"
            :value {:batch-id "batch-002" :units 100.0
                    :destination "buyer-yard-east"}})

    (exec! actor "t8-mnt-actuate"
           {:op :schedule-maintenance :effect :propose :subject "mnt-3"
            :value {:equipment-id "builder-001" :maintenance-type :force-run
                    :scheduled-date "2026-09-01" :actuate-line? true}})
    db))

;; ----------------------------- rendering -----------------------------

(defn- esc [v]
  (-> (str v)
      (str/replace "&" "&amp;")
      (str/replace "<" "&lt;")
      (str/replace ">" "&gt;")))

(defn- last-fact-for [ledger subject-id]
  (last (filter #(= (:subject %) subject-id) ledger)))

(defn- status-cell [ledger subject-id]
  (let [f (last-fact-for ledger subject-id)]
    (cond
      (nil? f) "<span class=\"muted\">no activity</span>"
      (= :committed (:t f)) "<span class=\"ok\">committed</span>"
      (= :approval-granted (:t f)) "<span class=\"ok\">approved &amp; committed</span>"
      (= :governor-hold (:t f))
      (let [rule (-> f :violations first :rule)]
        (str "<span class=\"critical\">HARD hold &middot; " (esc (name (or rule :unknown))) "</span>"))
      (= :approval-requested (:t f)) "<span class=\"warn\">awaiting approval</span>"
      :else "<span class=\"muted\">in progress</span>")))

(defn- batch-row [ledger {:keys [id tyre-category tyre-size load-index
                                  quantity-units defect-rate-percent
                                  verified? registered? shipped-units]}]
  ;; `:log-production-batch` is the one op in this domain whose own
  ;; `:subject` IS the batch-id itself (schedule-maintenance/coordinate-
  ;; shipment key ledger facts on their own maintenance-id/shipment-id
  ;; instead), so `status-cell` genuinely reflects this batch's own last
  ;; intake-op outcome here (batch-001 shows "committed" from this
  ;; scenario's t1-batch call; batch-002/batch-003 correctly show "no
  ;; activity" -- neither was ever the subject of a :log-production-batch
  ;; call in this scenario).
  (format "        <tr><td>%s</td><td>%s</td><td>%s</td><td>%s</td><td>%s / %s</td><td>%s%%</td><td>%s</td><td>%s</td><td>%s</td></tr>"
          (esc id) (esc (name (or tyre-category :n-a))) (esc tyre-size) (esc load-index)
          (esc shipped-units) (esc quantity-units) (esc defect-rate-percent)
          (if verified? "<span class=\"ok\">yes</span>" "<span class=\"err\">no</span>")
          (if registered? "<span class=\"ok\">yes</span>" "<span class=\"err\">no</span>")
          (status-cell ledger id)))

(defn- equipment-row [{:keys [id kind verified? registered?]}]
  ;; No "last op status" column here (unlike the batch/property rows in
  ;; sibling templates): this domain's ledger facts key `:subject` on
  ;; the maintenance-id/shipment-id/concern-id, never on the
  ;; equipment-id itself, so a per-equipment ledger lookup would always
  ;; read "no activity" regardless of scenario -- true but uninformative,
  ;; so it is omitted rather than shown as a column that can never say
  ;; anything else.
  (format "        <tr><td>%s</td><td>%s</td><td>%s</td><td>%s</td></tr>"
          (esc id) (esc (name (or kind :n-a)))
          (if verified? "<span class=\"ok\">yes</span>" "<span class=\"err\">no</span>")
          (if registered? "<span class=\"ok\">yes</span>" "<span class=\"err\">no</span>")))

(defn- ledger-row [{:keys [t op subject disposition basis]}]
  (format "        <tr><td>%s</td><td><code>%s</code></td><td>%s</td><td>%s</td></tr>"
          (esc (name t)) (esc (name (or op :n-a))) (esc subject)
          (esc (or (some->> basis (map name) (str/join ", ")) (some-> disposition name) ""))))

(def ^:private action-gate-rows
  ;; Static description of this actor's own op contract (README `Ops`
  ;; table, `tyremfg.governor`/`tyremfg.phase`) -- documentation of
  ;; fixed behavior, not runtime telemetry, so it is legitimately
  ;; hand-described rather than derived from a live run.
  ["        <tr><td><code>:log-production-batch</code></td><td><span class=\"ok\">auto-commit when clean, phase-3 &middot; tyre-category/load-index/defect-rate independently validated</span></td></tr>"
   "        <tr><td><code>:schedule-maintenance</code></td><td><span class=\"warn\">ALWAYS human approval (never auto-eligible) &middot; equipment verified/registered independently re-checked &middot; double-schedule blocked &middot; line actuation PERMANENTLY blocked</span></td></tr>"
   "        <tr><td><code>:flag-safety-concern</code></td><td><span class=\"warn\">ALWAYS human approval (high-stakes, regardless of confidence)</span></td></tr>"
   "        <tr><td><code>:coordinate-shipment</code></td><td><span class=\"warn\">ALWAYS human approval &middot; batch verified/registered independently re-checked &middot; shipment quantity independently recomputed &middot; robot bead-wire pull-out simulation independently re-verified</span></td></tr>"])

(defn render
  "Renders the full operator-console.html document from a store `db`
  that has already run `run-demo!` (or any other real scenario)."
  [db]
  (let [ledger (vec (store/ledger db))
        batches (store/all-batches db)
        equipment (store/all-equipment db)
        batch-rows (str/join "\n" (map (partial batch-row ledger) batches))
        equipment-rows (str/join "\n" (map equipment-row equipment))
        ledger-rows (str/join "\n" (map ledger-row ledger))]
    (str
     "<html><head><meta charset=\"utf-8\"><title>cloud-itonami-isic-2211 &middot; rubber tyre &amp; tube manufacturing</title><style>\n"
     "table { width: 100%; border-collapse: collapse; font-size: 14px; }\n"
     ".ok { color: #137a3f; }\n"
     "body { font-family: system-ui,-apple-system,sans-serif; margin: 0; color: #1a1a1a; background: #fafafa; }\n"
     "header.bar { display: flex; align-items: center; gap: 12px; padding: 12px 20px; background: #fff; border-bottom: 1px solid #e5e5e5; }\n"
     "th, td { text-align: left; padding: 8px 10px; border-bottom: 1px solid #f0f0f0; }\n"
     "h2 { margin-top: 0; font-size: 15px; }\n"
     ".warn { color: #b25c00; background: #fff8e1; padding: 2px 6px; border-radius: 4px; }\n"
     "main { max-width: 1080px; margin: 24px auto; padding: 0 20px; }\n"
     "header.bar h1 { font-size: 18px; margin: 0; font-weight: 600; }\n"
     ".muted { color: #888; font-size: 13px; }\n"
     ".critical { color: #fff; background: #b3261e; padding: 2px 6px; border-radius: 4px; font-weight: 600; }\n"
     ".card { background: #fff; border: 1px solid #e5e5e5; border-radius: 8px; padding: 16px; margin-bottom: 16px; }\n"
     ".err { color: #b3261e; background: #fbe9e7; padding: 2px 6px; border-radius: 4px; }\n"
     "th { font-weight: 600; color: #555; font-size: 12px; text-transform: uppercase; letter-spacing: 0.04em; }\n"
     "header.bar .badge { margin-left: auto; font-size: 12px; color: #666; }\n"
     "code { font-size: 12px; background: #f4f4f4; padding: 1px 4px; border-radius: 3px; }\n"
     "</style></head><body>\n"
     "<header class=\"bar\">\n"
     "  <h1>Rubber tyre &amp; tube manufacturing (ISIC 2211) — Operator Console</h1>\n"
     "  <span class=\"badge\">read-only sample · governor-gated · building/curing-line actuation always blocked</span>\n"
     "</header>\n"
     "<main>\n"
     "  <section class=\"card\">\n"
     "    <h2>Production batches</h2>\n"
     "    <p class=\"muted\">Demo snapshot — build-time-generated from <code>tyremfg.store</code> via <code>tyremfg.render-html</code> (<code>clojure -M:dev:render-html</code>), regenerated nightly. Shipped/Quantity and Verified/Registered columns are ground truth the governor independently re-derives -- never trusted from a proposal's own report.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Batch</th><th>Category</th><th>Size</th><th>Load idx</th><th>Shipped / Qty (units)</th><th>Defect rate</th><th>Verified</th><th>Registered</th><th>Last op status</th></tr></thead>\n"
     "      <tbody>\n"
     batch-rows "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"
     "  <section class=\"card\">\n"
     "    <h2>Building / curing-line equipment</h2>\n"
     "    <table>\n"
     "      <thead><tr><th>Equipment</th><th>Kind</th><th>Verified</th><th>Registered</th></tr></thead>\n"
     "      <tbody>\n"
     equipment-rows "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"
     "  <section class=\"card\">\n"
     "    <h2>Action gate (Tyre Plant Operations Governor)</h2>\n"
     "    <p class=\"muted\">HARD holds cannot be overridden by any phase or human approval. Line actuation and self-issued DOT/ECE certification are permanently blocked, unconditionally.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Op</th><th>Gate</th></tr></thead>\n"
     "      <tbody>\n"
     (str/join "\n" action-gate-rows) "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"
     "  <section class=\"card\">\n"
     "    <h2>Audit ledger (this run)</h2>\n"
     "    <p class=\"muted\">Append-only decision-fact log — every proposal, hold and commit this scenario produced.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Fact</th><th>Op</th><th>Subject</th><th>Basis</th></tr></thead>\n"
     "      <tbody>\n"
     ledger-rows "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"
     "</main>\n"
     "</body></html>\n")))

(defn -main [& args]
  (let [out (or (first args) "docs/samples/operator-console.html")
        db (run-demo!)
        html (render db)]
    (spit out html)
    (println "wrote" out "(" (count (store/ledger db)) "ledger facts,"
             (count (store/maintenance-history db)) "maintenance drafts,"
             (count (store/shipment-history db)) "shipment drafts )")))
