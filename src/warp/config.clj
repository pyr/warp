(ns warp.config
  (:require [com.stuartsierra.component :as component]
            [clojure.edn                :as edn]
            [clojure.tools.logging      :refer [error info debug]]))

(def default-logging
  {:pattern "%p [%d] %t - %c - %m%n"
   :external false
   :console true
   :files  []
   :level  "info"})

(defn load-path
  [path]
  (-> (or path "/etc/warp/controller.clj")
      slurp
      edn/read-string))
