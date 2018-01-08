(defproject warp "0.7.1-SNAPSHOT"
  :main warp.main
  :plugins [[lein-cljsbuild "1.1.7"]
            [lein-ancient   "0.6.15"]]
  :dependencies [[org.clojure/clojure           "1.9.0"]
                 [org.clojure/tools.logging     "0.4.0"]
                 [org.clojure/tools.cli         "0.3.5"]
                 [org.clojure/core.async        "0.3.465"]
                 [com.stuartsierra/component    "0.3.2"]
                 [spootnik/unilog               "0.7.22"]
                 [spootnik/uncaught             "0.5.5"]
                 [spootnik/signal               "0.2.2"]
                 [spootnik/watchman             "0.3.6"]
                 [spootnik/net                  "0.3.3-beta16"]
                 [spootnik/stevedore            "0.9.1"]
                 [exoscale/clj-yaml             "0.5.6"]
                 [bidi                          "2.1.2"]
                 [cheshire                      "5.8.0"]
                 [org.javassist/javassist       "3.20.0-GA"]
                 [org.clojure/clojurescript     "1.9.946"]
                 [reagent                       "0.8.0-alpha2"]
                 [cljs-http                     "0.1.44"]]
  :clean-targets ^{:protect false} [:target-path "resources/public/warp"]
  :cljsbuild {:builds {:app {:source-paths ["src/warp/client"]
                             :compiler {:output-to     "resources/public/warp/app.js"
                                        :output-dir    "resources/public/warp"
                                        :asset-path    "/warp"
                                        :optimizations :whitespace
                                        :pretty-print  false}}}}
  :profiles {:dev     {:cljsbuild {:builds {:app {:compiler {}}}}}
             :uberjar {:omit-source true
                       :aot         :all
                       :prep-tasks ["compile" ["cljsbuild" "once"]]
                       :cljsbuild {:jar true
                                   :builds {:app {:compiler {:optimizations :advanced
                                                             :pretty-print  false}}}}}})
