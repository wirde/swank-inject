(ns swank-inject.aot
  (:gen-class
   :name com.wirde.inject.Main)
  (:use [swank-inject.jdi])
  (:use [clojure.contrib.command-line])
  (:require [swank.swank :as swank])
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
;;Allow multiple classnames
(defn -main [& args]
  (with-command-line args
    "Remote Swank injector."
    [[host "The hostname of the (remote) process"]
     [port "The portnumber of the process with remote debugging enabled"]
     [url "URL to swank-inject jar-file"]
     ;;TODO: Allow multiple classnames
     [instances "Comma separated list of classes to locate instances for"] 
     remaining]
    (if (or (nil? host) (nil? port) (nil? url))
      (println "Host, port and url must be specified using -host <arg> -port <arg> -url <arg>")
      (if (not (empty? remaining))
	(println "Unknown arguments: " remaining)
	(do
	  (println "host: " host)
	  (println "port: " port)
	  (println "remaining: " remaining)
	  (let [vm (attach-to-vm host port)
		thread (suspend-finalizer-thread vm)]
;;	    (try
	      (print (inject-bootstrapper
		      thread
		      (list url)
		      "com.wirde.inject.SwankInjectee"
		      (map #(find-first-instance vm %) (list instances))))
	      
	  ;;    (catch Exception e (invoke-method
	;;			  thread
	;;			  (.exception e)
	;;			  "printStackTrace"
	;;			  "()V"
	;;			  '()))
	  ;;    (finally (.dispose vm)))
	  ))))))

(defn injecter-inject [this injectee args]
  (.println System/out injectee)
  (.println System/out args)
  (.inject injectee args))

(defn swank-inject [this args]
  (def *ctx* args)
  (swank/start-repl)
  "Starting Swank")
