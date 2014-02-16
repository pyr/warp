(ns org.spootnik.fleet.api
  "Minimal file base keyvalue database for scenarios"
  (:require [clojure.java.io :as io]
            [clj-yaml.core   :as yaml]))

(defprotocol ScenarioStore
  (upsert! [this scenario])
  (save! [this])
  (all! [this])
  (load! [this])
  (delete! [this script_name])
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
          (spit (str path "/" script_name) (yaml/generate-string scenario))))
      (all! [this]
        @scenarios)
      (load! [this]
        (let [dir (io/file path)]
          (doseq [script_file (.listFiles dir)
                  :let [scenario (-> script_file slurp yaml/parse-string)]]
            (upsert! this scenario))))
      (delete! [this script_name]
        (try
          (.delete (io/file (str path "/" script_name)))
          (catch Exception e))
        (swap! scenarios dissoc script_name))
      (get! [this script_name]
        (or
         (get @scenarios script_name)
         (throw (ex-info "no such scenario" {:status 404})))))))
