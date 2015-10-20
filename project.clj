(defproject org.spootnik/warp "0.5.0"
  :description "distributed command execution"
  :url "https://github.com/pyr/warp"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :aot :all
  :main org.spootnik.warp
  :plugins [[lein-cljsbuild "1.1.0"]]
  :clean-targets ^{:protect false} [:target-path
                                    "resources/public/warp"]
  :prep-tasks ["compile" ["cljsbuild" "once"]]
  :cljsbuild {
    :builds [{:id "om-warp"
              :source-paths ["src/org/spootnik/om_warp"]
              :compiler {:output-to "resources/public/warp/app.js"
                         :output-dir "resources/public/warp"
                         :asset-path "/warp"
                         :optimizations :whitespace
                         :pretty-print false}
              :main org.spootnik.om_warp.app}]}
  :dependencies [[org.clojure/clojure           "1.7.0"]
                 [org.clojure/core.async        "0.1.346.0-17112a-alpha"]
                 [org.clojure/tools.logging     "0.3.1"]
                 [org.clojure/tools.cli         "0.3.1"]
                 [org.clojure/data.codec        "0.1.0"]
                 [clj-yaml                      "0.4.0"]
                 [compojure                     "1.4.0"]
                 [cc.qbits/jet                  "0.6.4"]
                 [ring/ring-json                "0.4.0"]
                 [redis.clients/jedis           "2.6.2"]
                 [org.bouncycastle/bcprov-jdk16 "1.46"]
                 [jumblerg/ring.middleware.cors "1.0.1"]
                 [org.spootnik/logconfig        "0.7.3"]

                 [org.clojure/clojurescript     "1.7.145"]
                 [org.omcljs/om                 "0.9.0"]
                 [secretary                     "1.2.3"]
                 [racehub/om-bootstrap          "0.5.3"
                  :exclusions [org.clojure/clojure]]
                 [prismatic/om-tools            "0.3.12"
                  :exclusions [org.clojure/clojure prismatic/schema]]
                 [cljs-ajax                     "0.5.0"
                  :exclusions [commons-codec]]])
