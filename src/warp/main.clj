(ns warp.main
  (:gen-class)
  (:require [com.stuartsierra.component :as component]
            [warp.config                :refer [load-path default-logging]]
            [warp.archive               :refer [make-archive]]
            [warp.engine                :refer [make-engine]]
            [warp.api                   :refer [make-api]]
            [warp.transport             :refer [make-transport]]
            [warp.watcher               :refer [make-watcher]]
            [warp.mux                   :refer [make-mux]]
            [signal.handler             :refer [with-handler]]
            [unilog.config              :refer [start-logging!]]
            [spootnik.uncaught          :refer [uncaught]]
            [clojure.tools.logging      :refer [info warn]]
            [clojure.tools.cli          :refer [cli]]))

(set! *warn-on-reflection* true)

(defn get-cli
  [args]
  (try
    (cli args
         ["-h" "--help" "Show help" :default false :flag true]
         ["-f" "--path" "Configuration file path" :default nil])
    (catch Exception e
      (binding [*out* *err*]
        (println "Could not parse arguments: " (.getMessage e)))
      (System/exit 1))))

(defn config->system
  [path]
  (try
    (let [config (load-path path)]

      (start-logging! (merge default-logging (:logging config)))

      (-> (component/system-map :api       (make-api (:api config))
                                :watcher   (make-watcher (:scenarios config))
                                :mux       (make-mux)
                                :engine    (make-engine (:keepalive config))
                                :transport (make-transport (:transport config))
                                :archive   (make-archive))
          (component/system-using {:api       [:engine :archive :watcher]
                                   :engine    [:transport :archive :watcher :mux]
                                   :transport [:mux]})))))

(uncaught e (warn e "uncaught exception"))

(defn -main
  [& args]
  (let [[{:keys [path help]} args banner] (get-cli args)]

    (when help
      (println banner)
      (System/exit 0))

    (let [system (atom (config->system path))]

      (with-handler :term
        (info "Caught SIGTERM, quitting")
        (component/stop-system @system)
        (System/exit 0))

      (with-handler :hup
        (info "Caught SIGHUP, reloading")
        (swap! system (comp component/start-system
                            component/stop-system)))

      (info "ready to start the system")
      (swap! system component/start-system)))
  nil)
