(ns warp.transport
  "
  A TCP server for warp. Funnels all client messages in a single
  input stream. Clients are subscribed to an event bus which is
  used to broadcast messages.
  "
  (:require [com.stuartsierra.component :as component]
            [cheshire.core              :as json]
            [manifold.stream            :as stream]
            [manifold.bus               :as bus]
            [manifold.deferred          :as d]
            [aleph.tcp                  :as tcp]
            [gloss.core                 :as gloss]
            [gloss.io                   :as io]
            [warp.ssl                   :as ssl]
            [warp.pipeline              :as pipeline]
            [clojure.tools.logging      :refer [info warn error]]))

(defn encode
  [x]
  (try
    (json/generate-string x)
    (catch Exception e
      (error e "JSON encode"))))

(defn decode
  [x]
  (try
    (json/parse-string (String. x "UTF-8") true)
    (catch Exception e
      (error e "JSON decode"))))

(defn make-handler
  [bus mux]
  (let [decoder (comp (map decode) (remove nil?))]
    (fn [duplex info]
      (d/future
        (try
          (warn "incoming client" (:remote-addr info))
          (stream/on-closed duplex #(warn "lost client" (:remote-addr info)))
          (stream/connect (stream/transform decoder 10 duplex)
                          mux
                          {:downstream? false})
          (stream/connect (bus/subscribe bus :out) duplex)
          (catch Exception e
            (error e "warp/tcp handler error")))))))

(defn make-pipeline
  [initial-pipeline]
  (-> initial-pipeline
      (.addBefore "handler"   "timeout"    (pipeline/read-timeout 30))
      (.addBefore "timeout"   "length-in"  (pipeline/length-decoder))
      (.addBefore "length-in" "length-out" (pipeline/length-encoder))))

(defrecord Transport [host port ssl server group mux clients]
  component/Lifecycle
  (start [this]
    (let [encoder (comp (map encode) (remove nil?))
          bus     (bus/event-bus #(stream/stream* {:buffer-size 10
                                                   :xform       encoder}))
          options (cond-> {:host               (or host "localhost")
                           :port               (or port 1337)
                           :epoll?             true
                           :pipeline-transform make-pipeline}
                    (some? ssl)
                    (assoc :ssl-context (ssl/server-context ssl)))
          server  (tcp/start-server (make-handler bus mux) options)]
      (assoc this :bus bus :server server)))
  (stop [this]
    (when (some? server)
      (.close server))
    (assoc this :bus nil :server nil)))

(defn broadcast
  [{:keys [bus]} msg]
  (bus/publish! bus :out msg))

(defn make-mux
  []
  (stream/stream* {:buffer-size 2048
                   :permanent?  true}))

(defn make-transport
  [options]
  (map->Transport options))
