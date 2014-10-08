(ns org.spootnik.fleet.engine
  (:require [clojure.core.async           :refer [chan >! <! alts! go] :as a]
            [clojure.tools.logging        :refer [info error]]
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
  [keepalive]
  (let [transport    (atom nil)
        subscription (atom nil)]
    (reify
      Engine
      (set-transport! [this new-transport]
        (let [close-ch (chan)]
          (reset! transport new-transport)
          (when @subscription
            (a/put! @subscription :close)
            (a/close! @subscription))
          (reset! subscription close-ch)
          (when (and keepalive (pos? keepalive))
            (info "found keepalive, starting loop")
            (go
              (loop []
                (let [tm      (a/timeout keepalive)
                      [_ sig] (alts! [close-ch tm])]
                  (when (= tm sig)
                    ;; every keepalive interval
                    ;; send a dummy script on the wire to
                    ;; keep machines alive
                    (info "pinging clients")
                    (try
                      (request this
                               {:script_name "ping"
                                :script ["ping"]
                                :id (str (java.util.UUID/randomUUID))
                                :timeout 1000}
                               (chan (a/dropping-buffer 0)))
                      (catch Exception e
                        (error e "cannot send keepalive")))
                    (recur))))))))
      (request [this payload sink]
        (let [id                (str (java.util.UUID/randomUUID))
              payload           (assoc payload :id id)
              [pubchan & chans] (chan-names id)
              [ack-ch resp-ch]  (transport/subscribe @transport chans)]
          (info "emitting new request with id " id)
          (transport/publish @transport pubchan payload)
          (go
             (let [resps   (chan (a/dropping-buffer 200))
                   acks    (chan (a/dropping-buffer 200))
                   timeout (a/timeout ack-timeout)
                   need    (siphon! ack-ch acks :ack timeout valid?)]
               (a/pipe acks sink false)
               (siphon! resp-ch resps :resp (a/timeout (:timeout payload)))

               (let [need (<! need)]
                 (info "waiting for" (str need) "replies")
                 (loop [payload (<! resps)
                        need    need]
                   (when (and (pos? need) payload)
                     (>! sink payload)
                     (recur (<! resps)
                            (if (finished? payload) (dec need) need)))))
               (>! sink {:type :stop})
               (a/close! sink))))))))
