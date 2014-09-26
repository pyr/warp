(ns org.spootnik.fleet.security
  (:import [java.security                   Signature Security KeyPair]
           [org.bouncycastle.jce.provider   BouncyCastleProvider]
           [org.bouncycastle.openssl        PEMReader])
  (:require [clojure.data.codec.base64 :as b64]
            [clojure.java.io           :as io]))

(defprotocol AuthStore
  (sign [this input])
  (verify [this host input sig]))

(defn rsa-store
  [{:keys [ca-priv certdir suffix]}]
  (Security/addProvider (BouncyCastleProvider.))
  (let [keypair (.readObject (PEMReader. (io/reader ca-priv)))]
    (reify AuthStore
      (verify [this host input sig]
        (let [path (format "%s/%s.%s" certdir host suffix)
              cert (.readObject (PEMReader. (io/reader path)))]
          (->
           (doto (Signature/getInstance "SHA256withRSA")
             (.initVerify (.getPublic cert))
             (.update (.getBytes input)))
           (.verify (-> sig .getBytes b64/decode)))))
      (sign [this input]
        (String.
         (->
          (doto (Signature/getInstance "SHA256withRSA")
            (.initSign (.getPrivate keypair))
            (.update (.getBytes input)))
          (.sign)
          (b64/encode))
         "UTF-8")))))
