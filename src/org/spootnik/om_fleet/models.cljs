(ns org.spootnik.om-fleet.models
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [ajax.core                   :refer [GET POST PUT DELETE]]
            [cljs.core.async             :refer [chan <! >! pub sub put! close!]]
            [org.spootnik.om-fleet.utils :refer [pretty-match redirect]]))

(def base-url "https://control.internal.exoscale.ch")
;(def base-url "")

(defprotocol Resource
  (resource [this])
  (refresh [this])
  (fetch [this id])
  (execute [this script profile]))

(defn replace-match
  [value]
  (assoc value "match" (pretty-match (value "match"))))

(defn ansi
  [value]
  (when-not (nil? value)
    (js/ansi_up.ansi_to_html (js/ansi_up.escape_for_html value))))

(defn ansi-colors
  [[hostname data]]
  [hostname (map (fn [d] (-> d
                             (assoc "stderr" (ansi (d "stderr")))
                             (assoc "stdout" (ansi (d "stdout"))))) data)])

(defn add-scripts
  [scripts [host steps]]
    (let [steps (vec steps)
          steps (map-indexed (fn [index item]
                               (assoc item "script" (scripts index nil)))
                             steps)]
      [host steps]))

(defn event-source [url]
  (let [source (new js/EventSource url)
        channel (chan)]
    (.addEventListener source "message"
                       (fn [e] (put! channel (js->clj (js/JSON.parse (.-data e))))))
    {:source source
     :channel channel}))

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
                                                  (seq (response "profiles")))))
                                  
                                  )))})
      (GET (str base-url "/scenarios/" scenario "/history")
           {:handler (fn [response]
                       (let [hosts (response "hosts")
                             scripts (response "script")]
                         (swap! app assoc-in [:history scenario]
                                (assoc response "hostsv"
                                       (->> (mapv ansi-colors hosts)
                                            (mapv (partial add-scripts scripts)))))))}))
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
      (let [url (str base-url "/scenarios/" script_name "/executions")
            url (if (= profile :default) url (str url "?profile=" (name profile)))
            {:keys [source channel]} (event-source url)]
        (go (loop [{:strs [type] :as event} (<! channel)]
              (condp = type
                "ack"
                (do
                  (swap! app update-in [:history script_name "total_acks"] inc)
                  (when (= (get-in event ["msg" "status"]) "starting")
                    (swap! app update-in [:history script_name "starting_hosts"] inc)))

                "resp"
                (do
                  (let [status (get-in event ["msg" "output" "status"])]
                    (when (or (= status "finished")
                              (= status "failure"))
                      (do
                        (swap! app update-in [:history script_name "total_done"] inc)
                        (if (>= (get-in app [:history script_name "total_done"])
                                (get-in app [:history script_name "starting_hosts"]))
                          (swap! app assoc-in [:history script_name :done] true)))))
                  (let [host (get-in event ["msg" "host"])
                        output (get-in event ["msg" "output"])
                        output (assoc output "stdout" (ansi (output "stdout")))
                        output (assoc output "stderr" (ansi (output "stderr")))]
                    (swap! app update-in
                           [:history script_name "hosts" host] (comp vec conj) output))
                  (let [hosts (get-in @app [:history script_name "hosts"])]
                    (swap! app assoc-in [:history script_name "hostsv"]
                           (map (partial add-scripts (scenario "script")) hosts))))

                "stop"
                (do
                  (.close source)
                  (close! channel)))

              (if-not (= "stop" type)
                (recur (<! channel)))))))))

(defn listen
  [ps handler]
  (let [channel (chan)
        resource (resource handler)]
    (sub ps resource channel)
    
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
