(ns org.spootnik.fleet.api
  "Minimal file base keyvalue database for scenarios"
  (:require [clojure.java.io :as io]
            [clojure.string  :as str]
            [clj-yaml.core   :as yaml]))


(defn interpol
    [input args]
    (let [clean   #(str/replace % #"(\{\{|\}\})" "")
          args    (assoc (into {} (map-indexed #(vector (str %1) %2) args))
                    "*" (str/join " " args))
          extract (fn [k] (get args (clean k) ""))]
      (str/replace input #"\{\{[0-9*]+\}\}" extract)))

(defn prepare-command
  [args command]
  (cond
   (string? command)
   (interpol command args)

   (and (:shell command) (not (:literal command)))
   (update-in command [:shell] interpol args)

   :else
   command))

(defn prepare-match
  [args match]
  (if (string? match)
    (interpol match args)
    (let [has-key? (or (some-> match keys set)
                     (constantly false))]
      (cond
       (has-key? :not)  {:not (prepare-match (:not match) args)}
       (has-key? :fact) {:fact (:fact match)
                         :value (interpol (:value match) args)}
       (has-key? :or)   {:or (map (partial prepare-match args)
                                  (:or match))}
       (has-key? :and)  {:and (map (partial prepare-match args)
                                   (:and match))}
       (has-key? :host) {:host (interpol (:host match) args)}))))

(defn prepare
  [scenario profile matchargs args]
  (let [{:keys [script match] :as scenario}
        (merge scenario (get-in scenario [:profiles profile]))]
    (assoc scenario
      :match (prepare-match match matchargs)
      :script (map (partial prepare-command args) script))))

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
