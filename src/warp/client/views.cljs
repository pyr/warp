(ns warp.client.views
  (:require [clojure.string     :as str]
            [warp.client.models :as m]
            [warp.client.layout :refer [h4 h3 panel table-striped link-to
                                        code console tr]]
            [warp.client.utils  :refer [pretty-match first-line]]
            [warp.client.state  :refer [app]]))

(defn scenario-tr
  [{:keys [name ack-timeout timeout matcher]}]
  (tr (link-to (str "#/scenario/" name) name)
      (str "ack: " ack-timeout ", timeout:" timeout)
      (pretty-match matcher)))

(defn scenario-list
  []
  (m/refresh-scenarios)
  (fn []
    (panel
     "Scenarios"
     (table-striped
      ["Scenario" "Timeouts" "Default Matcher"]
      (for [scenario (sort-by :name (:scenarios @app))]
        ^{:key (:name scenario)} [scenario-tr scenario])))))

(defmulti scenario-cmd-summary :type)

(defmethod scenario-cmd-summary "shell"
  [{:keys [shell]}]
  [:span
   [:span {:class "label label-primary"} "shell"]
   (code (first-line shell))])

(defmethod scenario-cmd-summary "service"
  [{:keys [action service]}]
  [:span
   [:span {:class "label label-primary"} "service"]
   (code "service" service action)])

(defmethod scenario-cmd-summary "ping"
  [_]
  [:span
   [:span {:class "label label-primary"} "ping"]])

(defmethod scenario-cmd-summary "sleep"
  [{:keys [seconds]}]
  [:span
   [:span {:class "label label-primary"} "sleep"]
   (code "sleep" seconds)])

(defmethod scenario-cmd-summary :default
  [{:keys [type]}]
  [:span {:class "label label-primary"} type])

(defmulti scenario-cmd :type)

(defmethod scenario-cmd "shell"
  [{:keys [cwd exits shell literal]}]
  (tr "script" (console shell)))

(defmethod scenario-cmd "service"
  [{:keys [service action]}]
  (tr "service" (console "service " service " " action)))

(defmethod scenario-cmd "ping"
  [_]
  (tr "ping" ""))

(defmethod scenario-cmd "sleep"
  [{:keys [seconds]}]
  (tr "sleep" seconds))

(defn scenario-cmd-list
  [cmds]
  (for [[i cmd] (map-indexed vector cmds)]
    ^{:key (str "cmd-" i)} [scenario-cmd cmd]))


(defn scenario-profile
  [[id profile]]
  (tr (name id) (code (pretty-match (:matcher profile)))))

(defn scenario-profile-list
  [profiles]
  (for [[i profile] (map-indexed vector profiles)]
    ^{:key (str "profile-" i)} [scenario-profile profile]))

(defn scenario-description
  [{:keys [matcher profiles commands id]}]
  (println "building scenario description")
  (panel
   [:span (link-to "#/scenarios" "Scenarios") " / " id]
   [:div
    (h4 "Commands")
    (table-striped ["Type" "Description"] (scenario-cmd-list commands))
    (h4 "Profiles")
    (table-striped ["Name" "Matcher"] (scenario-profile-list profiles))]))

(defn scenario-replays
  [replays]
  (panel
   "Last runs"
   [:ul
    (for [[i r] (map-indexed vector replays)]
      ^{:key (str "replay-" i)} [:li [:a {:href (str "#/replay/" r)} r]])]))

(defn scenario-detail
  [{:keys [id] :as params}]
  (m/refresh-scenario id)
  (fn []
    (let [{:keys [scenario replays]} (get-in @app [:scenario id])]
      [:div
       [scenario-description scenario]
       (when (seq replays)
         [scenario-replays replays])])))

(defn success-output
  [success?]
  (if success?
    [:span {:class "label label-success"} "success"]
    [:span {:class "label label-danger"}  "failure"]))

(defn sanitize-date
  [datetime]
  (str/replace datetime #"T" " "))

(defn scenario-output-summary
  [{:keys [replay host started steps]}]
  (apply tr (concat [(link-to (str "#/replay/" replay "/" host) host)]
                    [(sanitize-date started)]
                    (for [{:keys [success index]} steps]
                      ^{:key (str "step-" index)}
                      (success-output success))
                    [(success-output (every? :success steps))])))

(defn replay-summary
  [{:keys [clients accepted refused total scenario id started]}]
  (panel
   [:span "Run:" (code id) " @ " started]
   (table-striped
    (vec
     (concat ["Host"]
             ["Started"]
             (for [[i cmd] (map-indexed vector (:commands scenario))]
               ^{:key (str "cmd-" i)} (scenario-cmd-summary cmd))
             ["Completion"]))
    (for [[host client] clients]
      ^{:key host} [scenario-output-summary (assoc client :replay id)]))))

(defn replay-detail
  [{:keys [id]}]
  (m/refresh-replay id)
  (fn []
    (let [execution (get-in @app [:executions id])]
      [:div
       [scenario-description (:scenario execution)]
       [replay-summary execution]])))

(defn client-step-output
  [{:keys [output] :as cmd}]
  (println "cmd step:" (pr-str cmd))
  [:div
   [:h4 (:type cmd) [:span {:style #js {"float" "right"}} (success-output (:success output))]]
   [:div
    [:pre {:class "console" :dangerouslySetInnerHTML #js {:__html (:output output)}}]
    [:p [:span {:class "text-muted"} (str "process returned: " (:exit output))]]]])

(defn client-detail
  [{:keys [id host]}]
  (m/refresh-replay id)
  (fn []
    (let [execution (get-in @app [:executions id])
          s-id      (get-in execution [:scenario :id])
          commands  (get-in execution [:scenario :commands])
          output    (get-in execution [:clients (keyword host)])]
      (println "commands: " commands)
      (println "execution:" output)
      (panel
       [:span
        (link-to "#/scenarios" "Scenarios")
        " / "
        (link-to (str "#/scenario/" s-id) s-id)
        " / "
        (link-to (str "#/replay/" id) id)
        " / "
        host]
       [:div
        (for [[cmd [i output]] (partition 2 (interleave commands (map-indexed vector (:steps output))))]
          ^{:key (str "step-" i)} [client-step-output (assoc cmd :output output)])]))))
