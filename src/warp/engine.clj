(ns warp.engine
  (:require [com.stuartsierra.component :as com]
            [clojure.string             :as str]
            [manifold.stream            :as stream]
            [manifold.deferred          :as d]
            [warp.archive               :as arc]
            [warp.transport             :as transport]
            [warp.execution             :as x]
            [warp.watcher               :as watcher]
            [warp.scenario              :as scenario]
            [warp.archive               :as archive]
            [unilog.context             :refer [with-context]]
            [clojure.tools.logging      :refer [debug info warn error]]))

(defprotocol ExecutionListener
  (publish-state [this state])
  (publish-event [this event]))

(defn random-uuid
  []
  (str (com.datastax.driver.core.utils.UUIDs/timeBased)))

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
  [archive executions mux]
  (stream/consume
   (fn [payload]
     (when (some? payload)
       (let [event (update payload :opcode (fnil keyword :none))]
         (when-not (= :pong (:opcode event))
           (process-event archive executions event)))))
   mux))

(defn run-keepalive
  [keepalive transport]
  (when keepalive
    (future
      (loop []
        (Thread/sleep keepalive)
        (try
          (transport/broadcast transport {:opcode "ping" :sequence (random-uuid)})
          (catch Exception e
            (error e "broadcast error during ping")))
        (recur)))))

(defrecord Engine [keepalive keepalive-thread executions
                   transport archive watcher mux]
  com/Lifecycle
  (start [this]
    (let [executions (atom {})]
      (process-events archive executions mux)
      (assoc this
             :executions executions
             :keepalive-thread (run-keepalive keepalive transport))))
  (stop [this]
    (future-cancel keepalive-thread)
    (assoc this :executions nil :keepalive-thread nil)))

(defn build-context
  [scenario id {:keys [profile matchargs args]}]
  (cond-> {:scenario (str scenario)
           :id       (str id)}
    (some? profile)   (assoc :profile (str profile))
    (some? matchargs) (assoc :to-args (str/join "," (map str matchargs)))
    (some? args)      (assoc :with-args (str/join "," (map str args)))))


(defn new-execution
  [engine scenario-id listener args]
  (let [id          (random-uuid)
        scenario    (scenario/fetch engine scenario-id args)
        timeout     (* 1000 (or (:timeout scenario) 5))
        ack-timeout (* 1000 (or (:ack-timeout scenario) 1))
        executions  (:executions engine)
        execution   (x/make-execution id scenario listener)]

    (with-context (build-context scenario-id id args)
      (info "launching execution"))

    (swap! executions assoc id execution)


    (stream/put! (:mux engine) {:opcode :init :sequence id})

    (d/chain (d/future (Thread/sleep ack-timeout))
             (fn [_]
               (stream/put! (:mux engine)
                            {:opcode :ack-timeout :sequence id})))

    (d/chain (d/future (Thread/sleep timeout))
             (fn [_]
               (stream/put! (:mux engine)
                            {:opcode :timeout :sequence id})))
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
