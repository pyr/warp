(ns org.spootnik.om-warp.router
  (:require [goog.events :as events]
            [goog.history.EventType :as EventType]
            [secretary.core :as secretary])
  (:import goog.History))

(defn init!
  [routes app]
  (doseq [[id {:keys [route tab]}] routes]
    (secretary/add-route! route
      (fn [params] (swap! app assoc :router {:view id
                                             :tab tab
                                             :route-params params}))))

  (let [h (History.)]
    (goog.events/listen h EventType/NAVIGATE #(secretary/dispatch! (.-token %)))
    (doto h (.setEnabled true))
    (swap! app assoc :h h)))
