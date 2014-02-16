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

(comment (defn finished?
           [resp]
           (println "looking at: " resp)
           (#{"finished" "failure"} (-> resp :output :status))))

(defn siphon!
  ([input output type timeout]
     (go
       (loop []
         (let [[payload src] (alts! [input timeout])]
           (if-not (= timeout src)
             (do
               (>! output {:type type :msg (:msg payload)})
               (recur))
             (a/close! output)))))))

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
            (let [acks   (chan 10)
                  resps  (chan 10)
                  need   (atom 0)]
              (siphon! ack-ch acks :ack (a/timeout ack-timeout))
              (siphon! resp-ch resps :resp (a/timeout (:timeout payload)))
              (go
                (loop [chans         [acks resps]]
                  (let [[payload src] (alts! chans)]
                    (if (= src acks)
                      (if payload
                        (do
                          (>! sink payload)
                          (println "got ack payload: " payload)
                          (when (valid? payload) (swap! need inc))
                          (recur [acks resps]))
                        (recur [resps]))
                      (if payload
                        (do
                          (println "got resp payload: " payload)
                          (>! sink payload)
                          (if-not (and (finished? payload)
                                       (not (pos? (swap! need dec))))
                            (recur chans)
                            (a/close! sink)))
                        (a/close! sink)))))))))))))
