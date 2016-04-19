(ns warp.transport
  (:require [com.stuartsierra.component :as com]
            [cheshire.core              :as json]
            [warp.mux                   :as mux]
            [net.tcp                    :as tcp]
            [net.ssl                    :as ssl]
            [net.ty.channel             :as channel]
            [net.ty.pipeline            :as pipeline]
            [clojure.tools.logging      :refer [info error]]))

(defn warp-adapter
  [mux group]
  (reify
    pipeline/HandlerAdapter
    (is-sharable? [this]
      true)
    (capabilities [this]
      #{:channel-active :channel-read})
    (channel-active [this ctx]
      (channel/add-to-group group (channel/channel ctx)))
    (channel-read [this ctx msg]
      (try
        (let [payload (json/parse-string msg true)]
          (mux/input mux payload))
        (catch Exception e
          (error e "read failure"))))))

(defn pipeline
  [mux group ssl]
  [(ssl/handler-fn (ssl/server-context ssl))
   (pipeline/length-field-based-frame-decoder)
   (pipeline/length-field-prepender)
   pipeline/string-decoder
   pipeline/string-encoder
   (pipeline/read-timeout-handler 60)
   (pipeline/make-handler-adapter (warp-adapter mux group))])

(defn bootstrap
  [mux group ssl]
  {:child-options {:so-keepalive           true
                   :connect-timeout-millis 1000}
   :handler       (pipeline/channel-initializer (pipeline mux group ssl))})

(defrecord Transport [host port ssl server group mux]
  com/Lifecycle
  (start [this]
    (let [group (channel/channel-group "clients")]
      (assoc this
             :group  group
             :server (tcp/server (bootstrap mux group ssl)
                                 (or host "localhost")
                                 (or port 1337)))))
  (stop [this]
    (when group
      (.close group))
    (when server
      (server))
    (assoc this :group nil :server nil)))

(defn broadcast
  [transport msg]
  (channel/write-and-flush! (:group transport) (json/generate-string msg)))

(defn make-transport
  [options]
  (map->Transport options))
