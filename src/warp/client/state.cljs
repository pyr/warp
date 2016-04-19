(ns warp.client.state
  (:require [reagent.core :as r]))

(def app
  "This is where we will keep all state"
  (r/atom {:route     {:handler :scenario-list}
           :scenario  {}
           :scenarios []}))
