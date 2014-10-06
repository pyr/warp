(ns org.spootnik.fleet.config
  (:require [clj-yaml.core :as yaml]))

(def default-logging
  "Logging can be bypassed if a log4j configuration is provided to the underlying JVM"
  {:use "org.spootnik.fleet.logging/start-logging"
   :pattern "%p [%d] %t - %c - %m%n"
   :external false
   :console true
   :files  []
   :level  "info"
   :overrides {:io.pithos "debug"}})

(defn find-ns-var
  "Find a symbol in a namespace"
  [s]
  (try
    (let [n (namespace (symbol s))]
      (require (symbol n))
      (find-var (symbol s)))
    (catch Exception _
      nil)))

(defn instantiate
  "Find a symbol pointing to a function of a single argument and
   call it"
  [class config args]
  (if-let [f (find-ns-var class)]
    (apply f config args)
    (throw (ex-info (str "no such namespace: " class) {}))))

(defn get-instance
  "For dependency injected configuration elements, find build fn
   and call it"
  [{:keys [use] :as config} & args]
  (let [sym          (-> use name symbol)
        clean-config (dissoc config :use)]
    (instantiate sym clean-config args)))

(defn read-config
  [path]
  (-> path slurp yaml/parse-string))

(defn init
  [path]
  (let [config    (read-config path)
        logging   (get-instance (merge default-logging (:logging config)))
        codec     (get-instance (:codec config))
        signer    (get-instance (:security config))
        transport (get-instance (:transport config) codec signer)
        scenarios (get-instance (:scenarios config))]
    (assoc config
      :transport transport
      :codec     codec
      :scenarios scenarios
      :http      (:http config))))
