(ns warp.parser
  (:require [clojure.string :as str]
            [clojure.spec.alpha :as s]
            [clojure.edn :as edn]
            [warp.dsl :as dsl]))

(defn get-extension
  [path]
  (when-let [[_ extension] (re-matches #"^.*\.([a-zA-Z]+)*$" path)]
    (let [kw        (keyword (str/lower-case extension))
          shortcuts {:clojure :edn :clj :edn :wrp :warp}]
      (shortcuts kw kw))))

(defn load-error
  [path]
  (throw (ex-info "unknown extension for path" {:path path})))

(defmulti load-config  get-extension)
(defmethod load-config :edn     [in] (edn/read-string (slurp in)))
(defmethod load-config :warp    [in] (dsl/load-scenario in))
(defmethod load-config :default [in] (load-error in))

(defn validate!
  [input]
  (let [conformed (s/conform ::scenario input)]
    (when (= ::s/invalid conformed)
      (throw (ex-info "invalid input" (s/explain-data ::scenario input))))
    input))

(defn load-unit
  [path]
  (validate! (load-config (str path))))

(def known-action? #{"stop" "start" "status" "restart" "reload"})

(s/def ::name string?)
(s/def ::timeout pos-int?)

(s/def ::clauses (s/coll-of ::matcher))
(s/def ::host string?)
(s/def ::fact (s/or :string string? :keyword keyword?))
(s/def ::value string?)

(defmulti matcher-type  :type)
(defmethod matcher-type :all  [_] map?)
(defmethod matcher-type :none [_] map?)
(defmethod matcher-type :not  [_] (s/keys :req-un [::clause]))
(defmethod matcher-type :and  [_] (s/keys :req-un [::clauses]))
(defmethod matcher-type :or   [_] (s/keys :req-un [::clauses]))
(defmethod matcher-type :host [_] (s/keys :req-un [::host]))
(defmethod matcher-type :fact [_] (s/keys :req-un [::fact ::value]))

(s/def :matcher/type    #{:all :none :not :or :and :fact :host})
(s/def ::matcher (s/multi-spec matcher-type :matcher/type))
(s/def ::clause ::matcher)

(s/def ::service string?)
(s/def ::interpol-val (s/and string? (partial re-matches #"\{\{[0-9*]\}\}")))
(s/def ::known-action (s/and string? known-action?))
(s/def ::action (s/or :known ::known-action :interpol ::interpol-val))
(s/def ::seconds pos-int?)

(s/def ::shell string?)
(s/def ::exits (s/coll-of int?))
(s/def ::cwd string?)

(defmulti  cmd-type :type)
(defmethod cmd-type :shell   [_] (s/keys :req-un [::shell] :opt-un [::exits ::cwd]))
(defmethod cmd-type :ping    [_] map?)
(defmethod cmd-type :sleep   [_] (s/keys :req-un [::seconds]))
(defmethod cmd-type :service [_] (s/keys :req-un [::service ::action]))

(s/def :cmd/type #{:shell :service :sleep :ping})

(s/def ::profiles (s/map-of keyword? ::matcher))

(s/def ::command (s/multi-spec cmd-type :cmd/type))
(s/def ::commands (s/coll-of ::command))
(s/def ::scenario (s/keys :req-un [::name ::matcher ::commands]
                          :opt-un [::timeout ::profiles]))
