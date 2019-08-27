(ns warp.archive
  (:require [com.stuartsierra.component :as com]))

(defprotocol Archive
  (record [this scenario id event])
  (all-replays [this])
  (replays [this scenario])
  (replay [this id]))

(defn set-conj
  [v e]
  (if (nil? v) #{e} (conj v e)))

(defrecord MemoryArchive [db]
  com/Lifecycle
  (start [this]
    (assoc this :db (atom {})))
  (stop [this]
    (assoc this :db nil))
  Archive
  (record [this scenario id execution]
    (swap! db #(-> %
                   (assoc-in  [:replays id] (dissoc execution :listener))
                   (update-in [:by-scenario (keyword scenario)] set-conj id))))
  (replays [this scenario]
    (vec (get-in @db [:by-scenario (keyword scenario)])))
  (replay [this id]
    (get-in @db [:replays id]))
  (all-replays [this]
    (vals (:replays @db))))

(defn make-archive
  []
  (map->MemoryArchive {}))
