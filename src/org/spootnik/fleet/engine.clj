(ns org.spootnik.fleet.engine
  (:require [clojure.core.async :refer [chan pub sub >!! <!!
                                        alts!! timeout close!]]
            [org.spootnik.fleet.transport :as transport]))

(def ack-timeout 2000)

(defprotocol Engine
  (set-transport! [this transport])
  (request [this payload out]))

(defn valid?
  [{:keys [status] :as ack}]
  (= status "starting"))

(defn get-acks
  [input output]
  (loop [acks nil]
    (let [timeout       (timeout ack-timeout)
          [payload src] (alts!! [input timeout])]
      (if (= timeout src)
        (do (>!! output {:type :acks :acks acks})
            (filter valid? acks))
        (recur (conj acks (:msg payload)))))))

(defn get-resps
  [input output timeout max]
  (loop [got 0]
    (let [[payload src] (alts!! [input timeout])
          got           (inc got)]
      (>!! output (if (= timeout src) {:type :timeout} (:msg payload)))
      (if (and (not= timeout src) (< got max))
        (recur got)
        (do (close! output))))))

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
      (request [this payload out]
        (let [id                (str (java.util.UUID/randomUUID))
              payload           (assoc payload :id id)
              [pubchan & chans] (chan-names id)
              [ack-ch resp-ch]  (transport/subscribe @transport chans)]
          (transport/publish @transport pubchan payload)
          (let [acks (get-acks ack-ch out)]
            (get-resps resp-ch out (timeout (:timeout payload)) (count acks))
            (transport/unsubscribe @transport chans)))))))
