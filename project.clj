(defproject ex-presentation "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [org.clojure/core.async "0.1.338.0-5c5012-alpha"]
                 [http-kit "2.1.16"]
                 [org.clojure/clojurescript "0.0-2322"]]
  :main ^:skip-aot ex-presentation.core
  :target-path "target/%s"
  :profiles {:dev {:plugins [[com.cemerick/austin "0.1.5"]
                             [lein-cljsbuild "1.0.3"]]
                   :cljsbuild {:builds [{:source-paths ["src-cljs"]
                                         :compiler {:output-to "app.js"
                                                    :optimizations :simple
                                                    :pretty-print true}}]}}
             :uberjar {:aot :all}})
