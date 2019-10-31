(defproject warp "0.7.2-SNAPSHOT"
  :main warp.main
  :plugins [[lein-cljsbuild "1.1.7"]
            [lein-ancient   "0.6.15"]]
  :dependencies [[org.clojure/clojure                      "1.10.1"]
                 [org.clojure/tools.logging                "0.4.0"]
                 [org.clojure/tools.cli                    "0.3.5"]
                 [org.clojure/core.async                   "0.4.500"]
                 [com.stuartsierra/component               "0.4.0"]
                 [spootnik/unilog                          "0.7.25"]
                 [spootnik/uncaught                        "0.5.5"]
                 [spootnik/signal                          "0.2.2"]
                 [spootnik/watchman                        "0.3.7"]
                 [spootnik/stevedore                       "0.9.1"]
                 [aleph                                    "0.4.6"]
                 [gloss                                    "0.2.6"]
                 [bidi                                     "2.1.6"]
                 [cheshire                                 "5.8.1"]
                 [org.clojure/clojurescript                "1.10.520"]
                 [reagent                                  "0.8.1"]
                 [cljs-http                                "0.1.46"]
                 [ring/ring-core                           "1.7.1"]
                 [io.netty/netty-all                       "4.1.37.Final"]
                 [io.netty/netty-tcnative                  "2.0.25.Final"]
                 [org.javassist/javassist                  "3.25.0-GA"]
                 [io.netty/netty-tcnative-boringssl-static "2.0.25.Final"]
                 [cc.qbits/alia                            "4.3.1"]
                 ]
  :pedantic? :warn
  :deploy-repositories [["releases" :clojars] ["snapshots" :clojars]]
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
