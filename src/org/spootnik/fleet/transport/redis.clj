(ns org.spootnik.fleet.transport.redis
  (:require [clojure.core.async           :refer [>!! chan pub sub unsub-all]]
            [org.spootnik.fleet.codec     :as codec]
            [org.spootnik.fleet.transport :as transport]
            [org.spootnik.fleet.service   :as service]))

(defn subscriber
  [ch codec]
  (proxy [redis.clients.jedis.JedisPubSub] []
    (onSubscribe [^String chan subscription-count])
    (onPSubscribe [^String chan subscription-count])
    (onPUnsubscribe [^String chan subscription-count])
    (onUnsubscribe [^String chan subscription-count])
    (onMessage [^String chan ^String msg])
    (onPMessage [^String pattern ^String chan ^String msg]
      (>!! ch {:chan chan :msg (codec/decode codec msg)}))))

(defn rpool
  [{:keys [host port timeout max-active max-idle max-wait]
    :or {max-active 5
         max-wait   200
         timeout    200
         host       "localhost"
         port       6379}}]
  (-> (doto (redis.clients.jedis.JedisPoolConfig.)
        (.setMaxActive max-active)
        (.setMaxIdle (or max-idle max-active))
        (.setTestOnBorrow true)
        (.setMaxWait max-wait))
      (redis.clients.jedis.JedisPool. host port timeout)))

(defn rpublish
  [pool chan msg]
  (let [client (.getResource pool)]
    (try
      (.publish client chan msg)
      (finally (.returnResource pool client)))))

(defn redis-transport
  [{:keys [inbuf] :or {inbuf 10}} codec]

  (let [ch   (chan inbuf)
        pool (rpool {})
        pub  (pub ch :chan)
        ptrn "fleet:*"]

    (reify
      service/Service
      (start! [this]
        (future
          (let [redis (.getResource pool)]
            (try
              (.psubscribe redis (subscriber ch codec)
                           (into-array String [ptrn]))
              (finally (.returnResource pool redis))))))

      transport/Transport
      (publish [this chan msg]
        (let [client (.getResource pool)]
          (try
            (.publish client chan (codec/encode codec msg))
            (finally (.returnResource pool client)))))
      (subscribe [this chans]
        (mapv (partial sub pub) chans (repeatedly #(chan inbuf))))
      (unsubscribe [this chans]
        (doseq [chan chans]
          (unsub-all pub chan))))))
