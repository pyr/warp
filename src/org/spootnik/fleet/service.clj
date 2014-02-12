(ns org.spootnik.fleet.service)

(defprotocol Service
  (start! [this])
  (stop! [this])
  (reload! [this]))
