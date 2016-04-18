(ns warp.client.router
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:require [goog.events            :as events]
            [goog.history.EventType :as EventType]
            [cljs.core.async        :as a]
            [warp.client.state      :refer [app]]
            [bidi.bidi              :refer [match-route]])
  (:import goog.History))

(defonce route-events
  (a/chan 10))

(def router
  (atom {:routes nil :handlers nil}))

(defn set-router!
  [routes handlers]
  (reset! router {:routes routes :handlers handlers}))

(defn route-update
  [event]
  (let [token  (.-token event)]
    (a/put! route-events token)))

(defonce history
  (doto (History.)
    (events/listen EventType/NAVIGATE route-update)
    (.setEnabled true)))

(defn route-dispatcher
  []
  (go-loop [location (a/<! route-events)]
    (when-let [routes (:routes @router)]
      (swap! app assoc :route (match-route routes location)))
    (recur (a/<! route-events)))
  (let [{:keys [handler route-params]} (:route @app)
        component                      (or (get (:handlers @router) handler)
                                           (get (:handlers @router) :default))]
      (println "got handler:" handler ", and params:" route-params)
      (if route-params [component route-params] [component])))

(defn redirect
  [location]
  (.setToken history location))
