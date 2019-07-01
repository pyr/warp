(ns warp.watcher
  (:import java.io.File)
  (:require [com.stuartsierra.component :as com]
            [clojure.edn                :as edn]
            [clojure.java.io            :as io]
            [watch.man                  :refer [watch! ->path close]]
            [warp.parser                :refer [load-unit]]
            [clojure.tools.logging      :refer [info]]))

(defprotocol ScenarioStore
  (by-id [this id])
  (all-scenarios [this]))

(defn extract-id
  [{:keys [type path]}]
  (and (= type :path)
       (when-let [[_ id] (re-find #"(?i)^([^.].*)\.(clojure|edn|clj|warp|wrp)$" (str path))]
         (keyword id))))

(defn load-dir
  [db dir]
  (info "loading dir" dir)
  (let [dir (io/file dir)]
    (doseq [^File file (file-seq dir)
            :when (.isFile file)
            :let [path (.relativize (.toPath dir) (.toPath file))]]
      (when-let [id (extract-id {:type :path :path path})]
        (when-not (re-find #"/" (name id))
          (info "found scenario: " (name id))
          (swap! db assoc id (assoc (load-unit file) :id id)))))))

(defn on-change-fn
  [dir db]
  (fn [{:keys [type path types] :as ev}]
    (info "filesystem event: " (pr-str ev))
    (when-let [id (extract-id ev)]
      (case (last (remove #{:overflow} (:types ev)))
        :delete (swap! db dissoc id)
        :modify (swap! db assoc id (load-unit (->path dir path)))
        :create (swap! db assoc id (load-unit (->path dir path)))))))

(defrecord Watcher [server db directory]
  ScenarioStore
  (by-id [this id]
    (get @db (keyword id)))
  (all-scenarios [this]
    (vals @db))
  com/Lifecycle
  (start [this]
    (let [internal-db (atom {})
          f           (on-change-fn directory internal-db)]
      (load-dir internal-db directory)
      (assoc this :server (watch! directory f) :db internal-db)))
  (stop [this]
    (close server)
    (assoc this :db nil :server nil)))


(defn make-watcher
  [scenario-dir]
  (map->Watcher {:directory scenario-dir}))
