(ns warp.client.models
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:require [cljs.core.async   :as a]
            [cljs-http.client  :as http]
            [warp.client.state :refer [app]]))

(defonce input (a/chan 100 (map :body)))

(defn event-type
  [event]
  (let [has-key?  (set (keys event))
        has-keys? (partial every? has-key?)]
    (cond (has-key?  :scenarios)           :scenario-list
          (has-key?  :execution)           :scenario-replay
          (has-keys? [:scenario :replays]) :scenario-details)))

(defmulti handle-event  event-type)

(defmethod handle-event :scenario-list
  [{:keys [scenarios]}]
  (swap! app assoc :scenarios (vec scenarios)))

(defmethod handle-event :scenario-replay
  [{:keys [execution]}]
  (let [id (:id execution)]
    (swap! app assoc-in [:executions id] execution)))

(defmethod handle-event :scenario-details
  [event]
  (println "event: " (pr-str event))
  (let [id (get-in event [:scenario :id])]
    (println "scenario id:" id)
    (swap! app assoc-in [:scenario id] event)))

(defmethod handle-event :default
  [event]
  (println "unknown event type:" (pr-str event)))

(defn start-sync!
  []
  (go-loop [event (a/<! input)]
    (if event
      (do
        (try
          (handle-event event)
          (catch :default e
            (println "event error:" (pr-str e))))
        (recur (a/<! input)))
      (println "input channel closed for events (this is weird)."))))

(defn pipe-get
  [& fragments]
  (let [ch (a/chan 1)]
    (a/pipe ch input false)
    (http/get (apply str fragments) {:channel ch})))

(defn refresh-scenarios
  []
  (pipe-get "api/scenarios"))

(defn refresh-scenario
  [id]
  (println "refreshing scenario:" id)
  (pipe-get "api/scenarios/" id))

(defn refresh-replay
  [id]
  (println "refreshing replay:" id)
  (pipe-get "api/replays/" id))
