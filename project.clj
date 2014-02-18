(defproject org.spootnik/fleet "0.5.0"
  :description "distributed command execution"
  :url "https://github.com/pyr/fleet"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :main org.spootnik.fleet
  :dependencies [[org.clojure/clojure           "1.5.1"]
                 [org.clojure/core.async        "0.1.267.0-0d7780-alpha"]
                 [org.clojure/tools.logging     "0.2.6"]
                 [org.clojure/tools.cli         "0.3.1"]
                 [org.clojure/data.json         "0.2.3"]
                 [org.clojure/data.codec        "0.1.0"]
                 [clj-yaml                      "0.4.0"]
                 [compojure                     "1.1.6"]
                 [http-kit                      "2.1.16"]
                 [ring/ring-json                "0.2.0"]
                 [redis.clients/jedis           "2.1.0"]
                 [org.slf4j/slf4j-log4j12       "1.6.6"]
                 [org.bouncycastle/bcprov-jdk16 "1.46"]
                 [log4j/log4j                   "1.2.17"
                  :exclusions [javax.mail/mail
                               javax.jms/jms
                               com.sun.jdmk/jmxtools
                               com.sun.jmx/jmxri]]])
