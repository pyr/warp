(ns warp.client.utils
  (:import goog.History)
  (:require [warp.client.ansi :refer [highlight]]
            [clojure.string   :refer [join split]]))

(defn json-stringify
  [value]
  (js/JSON.stringify (clj->js value)))

(defn paren
  ([prefix content]
   (str prefix "(" content ")"))
  ([content]
   (paren "" content)))

(defn first-line
  [s]
  (let [[line] (split s #"\n")]
    line))

(defn pretty-match
  [{:keys [type host fact value clause clauses] :as matcher}]
  (println "pretty matching:" type " => " (pr-str matcher))
  (case type
    "all" "all"
    "none" "none"
    "host" (str "host = " host)
    "fact" (str "facts[" fact "] =" value)
    "not"  (paren "!" (pretty-match clause))
    "and"  (paren (join " && " (map pretty-match clauses)))
    "or"   (paren (join " || " (map pretty-match clauses)))
    ""))

(defn highlight-datum
  [datum]
  (assoc datum
         "stderr" (highlight (get datum "stderr"))
         "stdout" (highlight (get datum "stdout"))))

(defn ansi-colors
  [[hostname data]]
  [hostname (map highlight-datum data)])

(defn add-scripts
  [scripts [host steps]]
  [host
   (map-indexed (fn [index item] (assoc item "script" (scripts index nil)))
                (vec steps))])
