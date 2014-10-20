(ns org.spootnik.warp.transport)

(defprotocol Transport
  (publish [this chan msg])
  (subscribe [this chans])
  (unsubscribe [this chan]))
