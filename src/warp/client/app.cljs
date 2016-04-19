(ns warp.client.app
  (:require [warp.client.views  :as views]
            [warp.client.router :refer [set-router! route-dispatcher]]
            [warp.client.models :refer [start-sync!]]
            [reagent.core       :refer [render]]))


(enable-console-print!)

(defn ^:export run
  "Our entrypoint, renders the main component in a predefined location"
  []
  (set-router!
   ["/" {""                :scenario-list
         ["scenario/" :id] :scenario-detail
         "replay"          {["/" :id "/" :host] :client-detail
                            ["/" :id]           :replay-detail}}]
   {:scenario-list   views/scenario-list
    :scenario-detail views/scenario-detail
    :replay-detail   views/replay-detail
    :client-detail   views/client-detail
    :default         views/scenario-list})
  (start-sync!)
  (render [route-dispatcher] (js/document.getElementById "app")))
