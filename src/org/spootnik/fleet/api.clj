(ns org.spootnik.fleet.api
  "Minimal file base keyvalue database for scenarios"
  (:require [clojure.java.io :as io]))

(defprotocol ScenarioStore
  (upsert! [this scenario])
  (save! [this])
  (all! [this])
  (load! [this])
  (get! [this script_name]))

(defn standard-scenario-store
  [{:keys [path]}]
  (let [scenarios (atom {})]
    (reify
      ScenarioStore
      (upsert! [this {:keys [script_name] :as scenario}]
        (swap! scenarios assoc script_name scenario))
      (save! [this]
        (doseq [[script_name scenario] @scenarios]
          (spit (str path "/" script_name) (pr-str scenario))))
      (all! [this]
        @scenarios)
      (load! [this]
        (let [dir (io/file path)]
          (doseq [script_file (.listFiles dir)
                  :let [scenario (-> script_file slurp read-string)]]
            (upsert! this scenario))))
      (get! [this script_name]
        (or
         (get @scenarios script_name)
         (throw (ex-info "no such scenario" {:status 404})))))))
