(ns org.spootnik.fleet.transport)

(defprotocol Transport
  (publish [this chan msg])
  (subscribe [this chans])
  (unsubscribe [this chan]))
