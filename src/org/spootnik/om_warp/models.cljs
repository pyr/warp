(ns org.spootnik.om-warp.models
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [ajax.core                  :refer [GET POST PUT DELETE]]
            [cljs.core.async            :refer [chan <! >! pub sub put! close!]]
            [org.spootnik.om-warp.utils :refer [pretty-match redirect]]))

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

(def defaults
  {"hosts" {}
   "total_acks" 0
   "starting_hosts" 0
   "total_done" 0})

(defn process
  [payload {:strs [msg type host id]} app]
  (let [host (get msg "host")
        id (or id (msg "id") (get-in msg ["msg" "id"]))
        payload (or payload defaults)
        payload (assoc payload "id" id)
        output (or (get-in payload ["hosts" host]) [])]

    (cond
      (= type "start")
      defaults

      (= type "ack")
      (-> payload
          (update-in ["total_acks"] inc)
          (cond-> (= (msg "status") "starting")
                  (update-in ["starting_hosts"] inc)))

      (= type "resp")
      (-> payload
          (assoc-in ["hosts" host] (conj output (msg "output")))
          (cond-> (#{"finished" "failure"} (get-in msg ["output" "status"]))
            (update-in ["total_done"] inc))))))

(defn handle-event
  [app script-name {:strs [type id] :as msg}]
  (if (= type "stop")
    (let [completed (get-in @app [:in-progress script-name id])]
      (when completed
        (swap! app update-in [:done script-name]
               (comp (partial take 5) conj)
               completed))
      (swap! app update-in [:in-progress script-name] dissoc id))
    (let [id (or id (msg "id") (get-in msg ["msg" "id"]))]
      (swap! app update-in [:in-progress script-name id] process msg app))))

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
                       (swap! app assoc
                              :done (response "done")
                              :in-progress (response "in-progress")))}))
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
                                                  (seq (response "profiles"))))))))}))

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
      (GET (str base-url "/scenarios/" script_name "/executions?stream=false")))

    (stream [this]
      (let [url (str base-url "/events")
            {:keys [source channel]} (event-source url)]
        (go (loop [{:strs [type id scenario] :as event} (<! channel)]
              (js/console.log "stream" (pr-str event))
              (let [id (or id (get-in event ["msg" "id"]))
                    script-name (if (= type "start")
                                  (do
                                    (swap! app assoc-in [:runs id] scenario)
                                    scenario)
                                  (get-in @app [:runs id]))]
                (when (not= type "keepalive")
                  (handle-event app script-name event)))
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
