(ns org.spootnik.om-warp.app
  (:require [om.core :as om :include-macros true]
            [om-tools.core :refer-macros [defcomponent]]
            [cljs.core.async :refer [chan]]
            [ajax.core :refer [GET]]
            [org.spootnik.om-warp.views :as views]
            [org.spootnik.om-warp.router :as router]
            [org.spootnik.om-warp.models :as models]))

(enable-console-print!)

(def routes {:index {:handler views/index
                     :route "/"
                     :tab nil}
             :scenarios {:handler views/scenarios-list
                         :route "/scenarios"
                         :tab :scenarios}
             :scenario {:handler views/scenario-detail
                        :route "/scenarios/:scenario"
                        :tab :scenarios}
             :host-history {:handler views/scenario-host-history
                            :route "/scenarios/:scenario/:run/:host"
                            :tab :scenarios}})

(def sync-chan (chan))

(def app-state (atom {:sync sync-chan
                      :scenarios []
                      :h nil
                      :history {}
                      :scenario nil
                      :results nil
                      :router nil}))

(models/start-sync! app-state [models/Scenarios])
(router/init! routes app-state)

(defcomponent dispatcher [app owner]
  (render
    [this]
    (let [view (get-in app [:router :view])
          handler (get-in routes [view :handler])]
      (om/build handler app))))

(om/root dispatcher app-state
         {:target (. js/document (getElementById "app"))
          :shared {:sync sync-chan}})

(om/root views/nav app-state
         {:target (. js/document (getElementById "nav"))})
