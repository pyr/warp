(ns org.spootnik.om-fleet.utils
  (:import goog.History))

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
