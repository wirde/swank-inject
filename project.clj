(defproject swank-inject "0.5.0-SNAPSHOT"
  :description "Inject clojure code into a running application with remote debugging enabled"
  :dependencies [[org.clojure/clojure "1.2.0"]
                 [org.clojure/clojure-contrib "1.2.0"]
		 [swank-clojure "1.2.1"]]
  :aot [swank-inject.aot]
  :repl-init-script "src/swank_inject/test.clj")
