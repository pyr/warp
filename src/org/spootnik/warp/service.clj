(ns org.spootnik.warp.service)

(defprotocol Service
  (start! [this])
  (stop! [this])
  (reload! [this]))
