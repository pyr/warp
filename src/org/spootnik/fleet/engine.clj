(ns org.spootnik.fleet.engine
  (:require [clojure.core.async           :refer [chan >! <! alts! go] :as a]
            [org.spootnik.fleet.transport :as transport]))

(def ack-timeout 2000)

(defprotocol Engine
  (set-transport! [this transport])
  (request [this payload out]))

(def valid?
  (comp (partial = "starting") :status :msg))

(def finished?
  (comp #{"finished" "failure" "timeout"} :status :output :msg))

(defn siphon!
  ([input output type timeout pred]
     (go
       (loop [got 0]
         (let [[payload src] (alts! [input timeout])]
           (if-not (= timeout src)
             (do
               (>! output {:type type :msg (:msg payload)})
               (recur (if (pred payload) (inc got) got)))
             (do (a/close! output) got))))))
  ([input output type timeout]
     (siphon! input output type timeout identity)))

(defn chan-names
  [id]
  [(str "fleetreq:" id) (str "fleet:ack:" id) (str "fleet:res:" id)])

(defn reactor
  []
  (let [transport    (atom nil)]
    (reify
      Engine
      (set-transport! [this new-transport]
        (reset! transport new-transport))
      (request [this payload sink]
        (let [id                (str (java.util.UUID/randomUUID))
              payload           (assoc payload :id id)
              [pubchan & chans] (chan-names id)
              [ack-ch resp-ch]  (transport/subscribe @transport chans)]
          (transport/publish @transport pubchan payload)
          (go
             (let [resps   (chan 10)
                   acks    (chan 10)
                   timeout (a/timeout ack-timeout)
                   need    (siphon! ack-ch acks :ack timeout valid?)]
               (a/pipe acks sink false)
               (siphon! resp-ch resps :resp (a/timeout (:timeout payload)))
               (go
                 (loop [payload (<! resps)
                        need    (<! need)]
                   (when (and (pos? need) payload)
                     (>! sink payload)
                     (recur (<! resps)
                            (if (finished? payload) (dec need) need))))
                 (a/close! sink)))))))))
