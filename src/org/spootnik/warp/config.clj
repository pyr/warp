(ns org.spootnik.warp.config
  (:require [clj-yaml.core          :as yaml]
            [org.spootnik.logconfig :refer [start-logging!]]))

(defn defaults
  [key config]
  (let [defaults {:logging
                  {:pattern "%p [%d] %t - %c - %m%n"
                   :external false
                   :console true
                   :files  []
                   :level  "info"
                   :overrides {:org.spootnik.warp "debug"}}
                  :codec
                  {:use "org.spootnik.warp.codec/json-codec"}
                  :transport
                  {:use "org.spootnik.warp.transport.redis/redis-transport"}
                  :security
                  {:use "org.spootnik.warp.security/rsa-store"}
                  :scenarios
                  {:use "org.spootnik.warp.api/standard-scenario-store"}}]
    (merge (get defaults key) (get config key))))

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
        logging   (start-logging! (defaults :logging config))
        codec     (get-instance  (defaults :codec config))
        signer    (get-instance (defaults :security config))
        transport (get-instance (defaults :transport config) codec signer)
        scenarios (get-instance (defaults :scenarios config))]
    (assoc config
      :transport transport
      :codec     codec
      :scenarios scenarios
      :http      (:http config))))
