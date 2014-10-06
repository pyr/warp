(defproject org.spootnik/fleet "0.5.0"
  :description "distributed command execution"
  :url "https://github.com/pyr/fleet"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :aot :all
  :main org.spootnik.fleet
  :dependencies [[org.clojure/clojure           "1.6.0"]
                 [org.clojure/core.async        "0.1.346.0-17112a-alpha"]
                 [org.clojure/tools.logging     "0.3.1"]
                 [org.clojure/tools.cli         "0.3.1"]
                 [org.clojure/data.json         "0.2.3"]
                 [org.clojure/data.codec        "0.1.0"]
                 [clj-yaml                      "0.4.0"]
                 [compojure                     "1.1.6"]
                 [http-kit                      "2.1.16"]
                 [ring/ring-json                "0.2.0"]
                 [redis.clients/jedis           "2.1.0"]
                 [org.slf4j/slf4j-log4j12       "1.7.7"]
                 [log4j/apache-log4j-extras     "1.2.17"]
                 [commons-logging               "1.2"]
                 [net.logstash.log4j/jsonevent-layout "1.7"]
                 [org.bouncycastle/bcprov-jdk16 "1.46"]
                 [log4j/log4j                   "1.2.17"
                  :exclusions [javax.mail/mail
                               javax.jms/jms
                               com.sun.jdmk/jmxtools
                               com.sun.jmx/jmxri]]])
