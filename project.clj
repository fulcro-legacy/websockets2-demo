(defproject wsfix "0.1.0-SNAPSHOT"
  :description "My Cool Project"
  :license {:name "MIT" :url "https://opensource.org/licenses/MIT"}
  :min-lein-version "2.7.0"

  :dependencies [[org.clojure/clojure "1.9.0"]
                 [thheller/shadow-cljs "2.3.35"]
                 [org.clojure/core.async "0.4.474"]
                 [fulcrologic/fulcro "2.5.8-SNAPSHOT"]
                 [http-kit "2.2.0"]
                 [ring/ring-core "1.6.3" :exclusions [commons-codec]]
                 [bk/ring-gzip "0.2.1"]
                 [bidi "2.1.3"]
                 [com.taoensso/sente "1.12.0"]]

  :uberjar-name "wsfix.jar"

  :source-paths ["src/main"]

  :profiles {:uberjar    {:main           wsfix.server-main
                          :aot            :all
                          :jar-exclusions [#"public/js/test" #"public/js/cards" #"public/cards.html"]
                          :prep-tasks     ["clean" ["clean"]
                                           "compile" ["with-profile" "cljs" "run" "-m" "shadow.cljs.devtools.cli" "release" "main"]]}
             :production {}
             :cljs       {:source-paths ["src/main" "src/test" "src/cards"]
                          :dependencies [[binaryage/devtools "0.9.8"]
                                         [fulcrologic/fulcro-inspect "2.0.0" :exclusions [fulcrologic/fulcro-css]]
                                         [devcards "0.2.4" :exclusions [cljsjs/react cljsjs/react-dom]]]}
             :dev        {:source-paths ["src/dev" "src/main"]

                          :dependencies [[org.clojure/tools.namespace "0.3.0-alpha4"]
                                         [org.clojure/tools.nrepl "0.2.13"]
                                         [com.cemerick/piggieback "0.2.2"]]
                          :repl-options {:init-ns          user
                                         :nrepl-middleware [cemerick.piggieback/wrap-cljs-repl]}}})
