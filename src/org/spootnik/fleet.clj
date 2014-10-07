(ns org.spootnik.fleet
  (:require [org.spootnik.fleet.config  :as config]
            [org.spootnik.fleet.service :as service]
            [org.spootnik.fleet.engine  :as engine]
            [org.spootnik.fleet.http    :as http]
            [clojure.tools.logging      :refer [info]]
            [clojure.tools.cli          :refer [cli]])
  (:gen-class))

(defn get-cli
  [args]
  (try
    (cli args
         ["-h" "--help" "Show help" :default false :flag true]
         ["-f" "--path" "Configuration path" :default "/etc/fleet.yml"])
    (catch Exception e
      (binding [*out* *err*]
        (println "Could not parse arguments: " (.getMessage e))
        (System/exit 1)))))

(defn -main
  [& args]
  (let [[{:keys [path help]} args banner] (get-cli args)]
    (when help
      (println banner)
      (System/exit 0))
    (let [cfg    (config/init path)
          engine (engine/reactor (:keepalive cfg))]
      (info "Creating request processing engine")
      (engine/set-transport! engine (:transport cfg))
      (info "Starting PubSub transport")
      (service/start! (:transport cfg))
      (info "Starting HTTP service")
      (http/start-http (:scenarios cfg) engine (:http cfg)))))
