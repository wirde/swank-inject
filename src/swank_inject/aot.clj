
(ns swank-inject.aot
  (:gen-class
   :name com.wirde.inject.Main)
  (:use [swank-inject.jdi])
  (:require [clojure.contrib.command-line :as command-line])
  (:require [clojure.string :as str])
  (:require [swank.swank :as swank])
  (:require [swank.util])
  )

(gen-interface
 :name com.wirde.inject.Injectee
 :methods [[inject [java.util.List] Object]]) ;;void?

(gen-class
 :name com.wirde.inject.Injecter
 :prefix "injecter-"
 :methods [[inject [com.wirde.inject.Injectee java.util.List] Object]]) ;;void?

(gen-class
 :name com.wirde.inject.SwankInjectee
 :implements [com.wirde.inject.Injectee]
 :prefix "swank-")

;;TODO:
;;Better error message
(defn -main [& args]
  (command-line/with-command-line args
    "Remote Swank injector."
    [[host "The hostname of the (remote) process"]
     [port "The portnumber of the process with remote debugging enabled"]
     [url "URL to swank-inject jar-file"]
     [instances "Comma separated list of classes to locate instances for"] 
     remaining]
    (if (or (nil? host) (nil? port) (nil? url))
      (do
	(println "Host, port and url must be specified using -host <arg> -port <arg> -url <arg>")
	)
      (if (not (empty? remaining))
	(println "Unknown arguments: " remaining)
	(do
	  (println "host: " host)
	  (println "port: " port)
	  (println "remaining: " remaining)
	  (let [vm (attach-to-vm host port)
		thread (suspend-finalizer-thread vm)]
	    (try
	      (println (inject-bootstrapper
		      thread
		      (list url)
		      "com.wirde.inject.SwankInjectee"
		      (map #(find-first-instance vm %) (str/split instances (java.util.regex.Pattern/compile ",")))))
	      
	      (catch Exception e (invoke-method
				  thread
				  (.exception e)
				  "printStackTrace"
				  "()V"
				  '()))
	      (finally (.dispose vm)))
	    )))))
  (shutdown-agents)
  (println "Done")
  )

(defn injecter-inject [this injectee args]
  (.println System/out injectee)
  (.println System/out args)
  (.inject injectee args)
  (.println System/out "done injecting")
  "foo")



;;TODO: Start in new thread (daemon?)
(defn swank-inject [this args]
;  (binding [user/*ctx* (seq args)]
  ;TODO: want to use bindings, but swank starts it's own thread...
  (def *ctx* (seq args))
  (.println System/out "Starting swank")
  (.println System/out *ctx*)
  (swank/start-repl)
  (.println System/out "Started swank")
  "Starting Swank")
