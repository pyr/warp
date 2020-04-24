(ns warp.execution
  (:require [clojure.tools.logging :as log]))


(defrecord Event     [host opcode sequence step output])

(defrecord Client    [host state steps index max])

(defrecord Execution [id scenario state accepted refused
                      total clients listener can-close?])

(defn make-client
  [host max]
  (map->Client {:host  host
                :state :running
                :index 0
                :max   max
                :steps []}))

(defn make-execution
  [id scenario listener]
  (map->Execution {:id         id
                   :scenario   scenario
                   :state      :running
                   :accepted   0
                   :refused    0
                   :total      0
                   :listener   listener
                   :clients    {}
                   :can-close? false}))

(defn close
  [client]
  (assoc client :state :closed))

(defn sanitize-state
  [execution]
  (when (some? execution)
    (let [can-close? (:can-close? execution)
          clients    (:clients execution)
          closed?    (comp (partial = :closed) :state)
          open?      (complement closed?)]
      (if (and can-close? (open? execution) (every? closed? (vals clients)))
        (assoc execution :state :closed)
        execution))))

(defmulti augment (fn [something event] (class something)))

(defmethod augment Client
  [{:keys [state index max] :as client} {:keys [step output]}]
  (when-not (= step index)
    (throw (ex-info (str "augment: invalid step index"
                         (pr-str {:step step :index index}))
                    {:step step :index index})))
  (when (>= step max)
    (throw (ex-info "augment: step index out of bounds" {})))

  (-> client
      (update :steps conj output)
      (update :index inc)
      (cond-> (not (:success output)) close)))

(defmethod augment Execution
  [{:keys [id scenario state] :as execution} {:keys [host opcode sequence step] :as event}]
  (log/debug "transition:"  state "=>" opcode)
  (sanitize-state
   (let [max (count (:commands scenario))]
     (cond
       (not (= id sequence))
       (do
         (log/error "augment: invalid event sequence")
         execution)

       (= state :close)
       ;; This will return nil
       (log/error "augment: input while execution closed" {})

       (= opcode :init)
       execution

       (= opcode :ack-timeout)
       (assoc execution :can-close? true)

       (= opcode :timeout)
       (-> execution
           (update :clients (fn [cs] (zipmap (keys cs) (mapv close (vals cs)))))
           (assoc :state :closed))

       (= opcode :command-start)
       (-> execution
           (update :accepted inc)
           (update :total inc)
           (assoc-in [:clients host] (make-client host max)))

       (contains? #{:command-deny :command-denied} opcode)
       (-> execution
           (update :refused inc)
           (update :total inc))

       (= opcode :command-end)
       (update-in execution [:clients host] close)

       :command-step
       (if (get-in execution [:clients host])
         (update-in execution [:clients host] augment event)
         (do (log/error "augment: invalid client in event")
             execution))

       :else
       (log/error "augment: invalid event or state")))))

(defmethod augment nil
  [_ event]
  (log/info "augment: stray event: " (pr-str event))
  nil)

(comment
  (let [events [{:opcode :command-start :sequence 0 :host :a}
                {:opcode :command-deny :sequence 0 :host :b}
                {:opcode :command-start :sequence 0 :host :c}
                {:opcode :command-step :sequence 0 :step 0 :host :c :output {:success :false :exit-code 1 :output "nope"}}
                {:opcode :command-step :sequence 0 :step 0 :host :a :output {:success :true :exit-code 0 :output "yup"}}
                {:opcode :command-step :sequence 0 :step 1 :host :a :output {:success :true :exit-code 0 :output "yup"}}
                {:opcode :timeout :sequence 0}
                {:opcode :command-step :sequence 0 :step 2 :host :a :output {:success :true :exit-code 0 :output "yup"}}]]
    (reduce augment (make-execution 0 {:commands [:ping :service :shell]}) events))
  )
