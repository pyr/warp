(ns org.spootnik.om-warp.models
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [ajax.core                  :refer [GET POST PUT DELETE]]
            [cljs.core.async            :refer [chan <! >! pub sub put! close!]]
            [org.spootnik.om-warp.utils :refer [pretty-match redirect]]
            [org.spootnik.om-warp.ansi  :refer [highlight]]))

(def base-url "")

(defprotocol Resource
  (resource [this])
  (history [this])
  (refresh [this])
  (fetch [this id])
  (execute [this script profile])
  (stream [this]))

(defn replace-match
  [value]
  (assoc value "match" (pretty-match (value "match"))))

(defn ansi-colors
  [[hostname data]]
  [hostname (map (fn [d] (-> d
                             (assoc "stderr" (highlight (d "stderr")))
                             (assoc "stdout" (highlight (d "stdout"))))) data)])

(defn add-scripts
  [scripts [host steps]]
    (let [steps (vec steps)
          steps (map-indexed (fn [index item]
                               (assoc item "script" (scripts index nil)))
                             steps)]
      [host steps]))

(defn event-source [url]
  (js/console.log "making event source to" (pr-str url))
  (let [source (new js/EventSource url)
        channel (chan)]
    (.addEventListener source "message"
                       (fn [e] (put! channel (js->clj (js/JSON.parse (.-data e))))))
    (.addEventListener source "error"
                       (fn [e]
                         (js/console.log (pr-str e))))
    {:source source
     :channel channel}))

(defn transform-host
  [scripts {:strs [hosts] :as result}]
  (assoc result "hostsv"
         (->> (mapv ansi-colors hosts)
              (mapv (partial add-scripts scripts)))))

(defn Scenarios
  [app]
  (reify
    Resource
    (resource
      [this]
      :scenarios)
    (refresh [this]
      (GET (str base-url "/scenarios")
           {:handler (fn [response]
                       (swap! app assoc
                              :scenarios
                              (map (fn [[n data]]
                                     [n (-> data
                                            (replace-match)
                                            (assoc "timeout" (/ (data "timeout")
                                                                1000))
                                            (assoc
                                              "profiles"
                                              (into {}
                                                    (map (fn [[k v]]
                                                           [k (replace-match v)])
                                                         (seq (data "profiles"))))))])
                                   (->> response
                                        (seq)
                                        (sort-by #(first %))))))}))
    (history [this]
      (GET (str base-url "/history")
           {:handler (fn [response]
                       (swap! app assoc :done (response "done"))
                       (swap! app assoc :in-progress (response "in-progress")))}))
    (fetch [this scenario]
      (GET (str base-url "/scenarios/" scenario)
           {:handler (fn [response]
                       (swap! app assoc :scenario
                              (-> response
                                  (replace-match)
                                  (assoc "profiles"
                                    (sort-by #(first %)
                                             (map (fn [[n data]]
                                                    [n (replace-match data)])
                                                  (seq (response "profiles"))))))))})
      (GET (str base-url "/scenarios/" scenario "/history")
           {:handler (fn [response]
                       (let [scripts (response "script")
                             done (map (partial transform-host scripts) (response "done"))

                             in-progress (map (fn [[k v]] [k (transform-host scripts v)]) (seq (response "in-progress")))
                             in-progress (into {} in-progress)
                             response (assoc response
                                             "done" done
                                             "in-progress" in-progress)]
                         (swap! app assoc-in [:history scenario] response)))}))
    (execute [this {:strs [script_name script] :as scenario} profile]
      (swap! app assoc-in [:history script_name] {:done false
                                                  "id" ""
                                                  "script" script
                                                  "script_name" script_name
                                                  "hosts" {}
                                                  "total_acks" 0
                                                  "starting_hosts" 0
                                                  "total_done" 0})
      (swap! app assoc :scenario scenario)
      (redirect (:h @app) (str "/scenarios/" script_name))
      (GET (str base-url "/scenarios/" script_name "/executions?stream=false"))
      ;(let [url (str base-url "/scenarios/" script_name "/executions")
      ;      url (if (= profile :default) url (str url "?profile=" (name profile)))
      ;      {:keys [source channel]} (event-source url)]
      ;  (go (loop [{:strs [type] :as event} (<! channel)]
      ;        (condp = type
      ;          "ack"
      ;          (do
      ;            (swap! app update-in [:history script_name "total_acks"] inc)
      ;            (when (= (get-in event ["msg" "status"]) "starting")
      ;              (swap! app update-in [:history script_name "starting_hosts"] inc)))

      ;          "resp"
      ;          (do
      ;            (let [status (get-in event ["msg" "output" "status"])]
      ;              (when (or (= status "finished")
      ;                        (= status "failure"))
      ;                (do
      ;                  (swap! app update-in [:history script_name "total_done"] inc)
      ;                  (if (>= (get-in app [:history script_name "total_done"])
      ;                          (get-in app [:history script_name "starting_hosts"]))
      ;                    (swap! app assoc-in [:history script_name :done] true)))))
      ;            (let [host (get-in event ["msg" "host"])
      ;                  output (get-in event ["msg" "output"])
      ;                  output (assoc output "stdout" (highlight (output "stdout")))
      ;                  output (assoc output "stderr" (highlight (output "stderr")))]
      ;              (swap! app update-in
      ;                     [:history script_name "hosts" host] (comp vec conj) output))
      ;            (let [hosts (get-in @app [:history script_name "hosts"])]
      ;              (swap! app assoc-in [:history script_name "hostsv"]
      ;                     (map (partial add-scripts (scenario "script")) hosts))))

      ;          "stop"
      ;          (do
      ;            (.close source)
      ;            (close! channel)))

      ;        (if-not (= "stop" type)
      ;          (recur (<! channel))))))
      )
    (stream [this]
      (let [url (str base-url "/events")
            {:keys [source channel]} (event-source url)]
        (go (loop [{:strs [type id] :as event} (<! channel)]
              (js/console.log "stream" (pr-str event))
              (recur (<! channel))))))))

(defn listen
  [ps handler]
  (let [channel (chan)
        resource (resource handler)]
    (sub ps resource channel)
    (stream handler)
    (history handler)
    
    (go (loop [msg (<! channel)]
          (js/console.log "resource msg" (pr-str msg))
          (case (:action msg) 
            :refresh (refresh handler)
            :get (fetch handler (:id msg))
            :execute (execute handler (:script msg) (:profile msg)))
          (recur (<! channel))))))

(defn start-sync!
  [app models]
  (let [channel (:sync @app)
        ps (pub channel :resource)
        handlers (map #(% app) models)]
    (mapv (partial listen ps) handlers)
    channel))
