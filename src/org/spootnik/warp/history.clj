(ns org.spootnik.warp.history
  (:require [org.spootnik.warp.api :as api]))

(def history (atom {}))

(def defaults
  {:hosts {}
   :total_acks 0
   :starting_hosts 0
   :total_done 0})

(defn fetch
  [store script_name]
  (let [scenario (api/get! store script_name)]
    (assoc (get @history script_name defaults)
      :script_name script_name
      :script (-> scenario :script))))

(defn process
  [payload {:keys [msg type]}]
  (if (= type :stop)
    payload
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
                   (update-in [:total_done] inc)))))))

(defn update
  [script_name msg]
  (swap! history update-in [script_name] process msg))
