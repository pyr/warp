(ns warp.ssl
  "Clojure glue code to interact with the horrible JVM SSL code"
  (:require [clojure.java.io :as io])
  (:import io.netty.handler.ssl.ClientAuth
           io.netty.handler.ssl.SslContextBuilder))

(defn server-context
  "Build an SSL client context for netty"
  [{:keys [pkey cert ca-cert]}]
  (-> (SslContextBuilder/forServer (io/file cert) (io/file pkey))
      (.trustManager (io/file ca-cert))
      (.clientAuth ClientAuth/REQUIRE)
      (.build)))
