(ns org.spootnik.om-warp.utils
  (:import goog.History)
  (:require [org.spootnik.om-warp.ansi :refer [highlight]]))

(defn json-stringify
  [value]
  (js/JSON.stringify (clj->js value)))

(defn redirect
  [history location]
  (.setToken history location))

(defn pretty-match
  [match]
  (cond
    (nil? match) ""
    (= match "all") "all"
    (match "host") (str "host = " (match "host"))
    (match "fact") (str "facts[" (match "fact") "] = " (match "value"))
    (match "not") (str "!(" (pretty-match (match "not")) ")")
    (match "and") (str "(" (clojure.string/join " && " (map pretty-match (match "and"))) ")")
    (match "or") (str "(" (clojure.string/join " || " (map pretty-match (match "or"))) ")")))

(defn ansi-colors
  [[hostname data]]
  [hostname (map (fn [d] (-> d
                             (assoc "stderr" (highlight (d "stderr"))
                                    "stdout" (highlight (d "stdout"))))) data)])

(defn add-scripts
  [scripts [host steps]]
  (let [steps (vec steps)
        steps (map-indexed (fn [index item]
                             (assoc item "script" (scripts index nil)))
                           steps)]
    [host steps]))
