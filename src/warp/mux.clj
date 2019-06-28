(ns warp.mux
  (:require [com.stuartsierra.component :as com]
            [clojure.core.async         :as a]))

(defrecord Mux [in]
  com/Lifecycle
  (start [this]
    (assoc this :in (a/chan 2000)))
  (stop [this]
    (a/close! in)
    (assoc this :in nil)))

(defn input
  [mux msg]
  (a/put! (:in mux) msg))

(defn make-mux
  []
  (map->Mux {}))
