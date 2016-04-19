(ns warp.scenario
  (:require [clojure.string :as str]
            [warp.watcher   :as watcher]
            [clojure.tools.logging :refer [info]]))

(defn ensure-sequential
  [x]
  (if (or (nil? x) (sequential? x)) x [x]))

(defn interpol
  ([input args not-found]
   (when (string? input)
     (let [clean   #(str/replace % #"(\{\{|\}\})" "")
           args    (assoc (into {} (map-indexed #(vector (str %1) %2)
                                                (ensure-sequential args)))
                          "*" (str/join " " args))
           extract (fn [k] (or (get args (clean k))
                               (if (fn? not-found)
                                 (not-found k)
                                 not-found)))]
       (str/replace input #"\{\{[0-9*]+\}\}" extract))))
  ([input args]
   (interpol input args "")))

(defn prepare-command
  [{:keys [literal type] :as cmd} args]
  (if (nil? cmd)
    (throw (ex-info "invalid command for scenario" {}))
    (case type
      :ping    cmd
      :sleep   cmd
      :service (if literal
                 cmd
                 (-> cmd
                     (update :service interpol args "NOSERVICE")
                     (update :action interpol args "status")))
      :shell   (if literal
                 cmd
                 (-> cmd
                     (update :shell interpol args)
                     (update :cwd interpol args)))
      (throw (ex-info "invalid command type" {:command cmd})))))

(defn prepare-matcher
  [{:keys [type] :as matcher} args]
  (if (nil? matcher)
    {:type "none"}
    (case type
      :all  matcher
      :none matcher
      :host (update matcher :host #(interpol % args ""))
      :fact (update matcher :value #(interpol % args ""))
      :not  (update matcher :clause  #(prepare-matcher % args))
      :or   (update matcher :clauses #(mapv prepare-matcher % (repeat args)))
      :and  (update matcher :clauses #(mapv prepare-matcher % (repeat args)))
      (throw (ex-info "invalid matcher" {:matcher matcher :status 400})))))

(defn prepare-raw
  [scenario profile matchargs args]
  (let [profiles (:profiles scenario)]
    (-> scenario
        (cond-> profile (assoc :matcher
                               (or (get profiles (keyword profile))
                                   (throw (ex-info "invalid profile" {})))))
        (update :matcher  #(prepare-matcher % matchargs))
        (update :commands #(map prepare-command % (repeat args)))
        (update :commands vec)
        (dissoc :profiles))))

(defn fetch
  [{:keys [watcher]} id {:keys [profile matchargs args]}]
  (info "preparing for: " (pr-str {:id id
                                   :profile profile
                                   :matchargs matchargs
                                   :args args}))
  (let [scenario (prepare-raw (watcher/by-id watcher id) profile matchargs args)]
    (info "got scenario: " (pr-str scenario))
    scenario))
