(ns warp.engine
  (:require [com.stuartsierra.component :as com]
            [clojure.core.async         :as a]
            [warp.archive               :as arc]
            [warp.transport             :as transport]
            [warp.mux                   :as mux]
            [warp.execution             :as x]
            [warp.watcher               :as watcher]
            [warp.scenario              :as scenario]
            [warp.archive               :as archive]
            [clojure.tools.logging      :refer [debug info]]))

(defprotocol ExecutionListener
  (publish-state [this state])
  (publish-event [this event]))

(defn random-uuid
  []
  (str (java.util.UUID/randomUUID)))

(def progress-command?
  #{:init :command-start :command-deny :command-step :command-end
    :ack-timeout :timeout})

(defn process-event
  [archive executions {:keys [opcode sequence] :as event}]

  (when-not (progress-command? opcode)
    (throw (ex-info "invalid event received" {:event event})))

  (debug "processing: " (pr-str event))

  (let [world     (swap! executions update sequence x/augment event)
        execution (get world sequence)]
    (when-let [listener (:listener execution)]
      (publish-event listener event)
      (publish-state listener (dissoc execution :listener)))
    (when (= (:state execution) :closed)
      (archive/record archive
                      (get-in execution [:scenario :id])
                      (:id execution)
                      execution)
      (swap! executions dissoc sequence))))

(defn process-events
  [archive executions in]
  (a/go
    (loop [payload (a/<! in)]
      (when-not (nil? payload)
        (let [event (update payload :opcode (fnil keyword :none))]
          (when-not (= :pong (:opcode event))
            (process-event archive executions event))
          (recur (a/<! in)))))))

(defn run-keepalive
  [keepalive transport]
  (when keepalive
    (future
      (loop []
        (Thread/sleep keepalive)
        (info "ticking")
        (transport/broadcast transport {:opcode   "ping"
                                        :sequence (random-uuid)})
        (recur)))))

(defrecord Engine [keepalive keepalive-thread executions
                   transport archive watcher mux]
  com/Lifecycle
  (start [this]
    (let [executions (atom {})]
      (process-events archive executions (:in mux))
      (assoc this
             :executions executions
             :keepalive-thread (run-keepalive keepalive transport))))
  (stop [this]
    (future-cancel keepalive-thread)
    (assoc this :executions nil :keepalive-thread nil)))

(defn new-execution
  [engine scenario-id listener args]
  (let [id          (random-uuid)
        scenario    (scenario/fetch engine scenario-id args)
        timeout     (* 1000 (or (:timeout scenario) 5))
        ack-timeout (* 1000 (or (:ack-timeout scenario) 1))
        executions  (:executions engine)
        execution   (x/make-execution id scenario listener)]
    (swap! executions assoc id execution)
    (a/go
      (a/>! (get-in engine [:mux :in]) {:opcode :init :sequence id})
      (a/<! (a/timeout ack-timeout))
      (a/>! (get-in engine [:mux :in]) {:opcode :ack-timeout :sequence id}))
    (a/go
      (a/<! (a/timeout timeout))
      (a/>! (get-in engine [:mux :in]) {:opcode :timeout :sequence id}))
    (when listener
      (publish-state listener (dissoc execution :listener)))
    (transport/broadcast (:transport engine)
                         {:opcode  "script"
                          :sequence id
                          :scenario scenario})
    execution))

(defn find-execution
  [{:keys [executions archive]} scenario id]
  (or (get @executions id)
      (arc/replay archive id)
      (throw (ex-info "execution not found" {}))))

(defn make-engine
  [keepalive]
  (map->Engine {:keepalive  (* 1000 (or keepalive 30))}))
