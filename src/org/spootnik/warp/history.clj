(ns org.spootnik.warp.history
  (:require [org.spootnik.warp.api :as api]
            [clojure.tools.logging  :refer [info]]))  

(def done (atom {}))
(def in-progress (atom {}))

(def defaults
  {:hosts {}
   :total_acks 0
   :starting_hosts 0
   :total_done 0})

(defn fetch
  [store script_name]
  (let [scenario (api/get! store script_name)]
    {:done (get @done script_name [])
     :in-progress (get @in-progress script_name {})
     :script_name script_name
     :script (-> scenario :script)}))

(defn process
  [payload {:keys [msg type]}]
  (let [host    (:host msg)
        id      (-> msg :id)
        payload (or payload defaults)
        payload (if (not= id (:id payload)) defaults payload)
        payload (assoc payload :id id)
        output  (or (get-in payload [:hosts host]) [])]
    (cond

     (= type :ack)
     (-> payload
         (update-in [:total_acks] inc)
         (cond-> (= (:status msg) "starting")
                 (update-in [:starting_hosts] inc)))

     (= type :resp)
     (-> payload
         (assoc-in [:hosts host] (conj output (:output msg)))
         (cond-> (#{"finished" "failure"} (-> msg :output :status))
                 (update-in [:total_done] inc))))))

(defn update
  [script_name {:keys [type] :as msg}]
  (info "update" script_name type msg)
  (if (= type :stop)
    (let [id (:id msg)
          completed (get-in @in-progress [script_name id])]
      (info "completed" completed)
      (when completed
        (swap! done update-in [script_name]
               (comp (partial take 5) conj)
               completed))
      (swap! in-progress update-in [script_name] dissoc id)
      (info "in-progress" @in-progress))
    (do
      (swap! in-progress update-in [script_name (get-in msg [:msg :id])] process msg)
      (info "progress" @in-progress))))
