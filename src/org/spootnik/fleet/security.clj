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
  (let [pem (.readObject (PEMReader. (io/reader ca-priv)))
        private (cond
                 (instance? java.security.KeyPair pem)
                 (.getPrivate pem)

                 :else
                 (throw (ex-info "dunno how to get private key"
                                 {:pem pem :path ca-priv})))]
    (reify AuthStore
      (verify [this host input sig]
        (let [path (format "%s/%s.%s" certdir host suffix)
              pem (.readObject (PEMReader. (io/reader path)))
              public (cond
                      (instance? java.security.PublicKey pem)
                      pem

                      (instance? java.security.cert.X509Certificate pem)
                      (.getPublicKey pem)

                      :else
                      (throw (ex-info "dunno how to get public key"
                                      {:pem pem :path path})))]
          (->
           (doto (Signature/getInstance "SHA256withRSA")
             (.initVerify public)
             (.update (.getBytes input)))
           (.verify (-> sig .getBytes b64/decode)))))
      (sign [this input]
        (String.
         (->
          (doto (Signature/getInstance "SHA256withRSA")
            (.initSign private)
            (.update (.getBytes input)))
          (.sign)
          (b64/encode))
         "UTF-8")))))
