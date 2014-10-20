(ns org.spootnik.warp.codec
  (:require [clojure.data.json :as json]))

(defprotocol Codec
  (encode [this payload])
  (decode [this payload]))

(defn edn-codec
  [config]
  (reify
    Codec
    (encode [this payload]
      (pr-str payload))
    (decode [this payload]
      (read-string payload))))

(defn json-codec
  [config]
  (reify
    Codec
    (encode [this payload]
      (json/write-str payload))
    (decode [this payload]
      (json/read-str payload :key-fn keyword))))
