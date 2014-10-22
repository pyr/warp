(defproject org.spootnik/warp "0.5.0"
  :description "distributed command execution"
  :url "https://github.com/pyr/warp"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :aot :all
  :main org.spootnik.warp
  :plugins [[lein-cljsbuild "1.0.4-20140402.162426-2"]]
  :cljsbuild {
    :builds [{:id "om-warp"
              :source-paths ["src/org/spootnik/om_warp"]
              :compiler {
                :output-to "resources/public/warp/app.js"
                :output-dir "resources/public/warp"
                :optimizations :none
                :source-map true}}]}
  :dependencies [[org.clojure/clojure           "1.6.0"]
                 [org.clojure/core.async        "0.1.346.0-17112a-alpha"]
                 [org.clojure/tools.logging     "0.3.1"]
                 [org.clojure/tools.cli         "0.3.1"]
                 [org.clojure/data.json         "0.2.5"]
                 [org.clojure/data.codec        "0.1.0"]
                 [clj-yaml                      "0.4.0"]
                 [compojure                     "1.2.0"]
                 [cc.qbits/jet                  "0.5.0-beta2"]
                 [ring/ring-json                "0.3.1"]
                 [redis.clients/jedis           "2.6.0"]
                 [org.bouncycastle/bcprov-jdk16 "1.46"]
                 [jumblerg/ring.middleware.cors "1.0.1"]
                 [org.spootnik/logconfig        "0.7.2"]

                 [org.clojure/clojurescript     "0.0-2356"]
                 [om                            "0.7.1"]
                 [secretary                     "1.2.1-20140627.190529-1"]
                 [racehub/om-bootstrap          "0.3.0"
                  :exclusions [org.clojure/clojure]]
                 [prismatic/om-tools            "0.3.2"
                  :exclusions [org.clojure/clojure]]
                 [cljs-ajax                     "0.3.3"]
                 [com.ninjudd/eventual          "0.3.0"]])
