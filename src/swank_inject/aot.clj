
(ns swank-inject.aot
  (:gen-class
   :name com.wirde.inject.Main)
  (:use [swank-inject.jdi])
  (:require [clojure.contrib.command-line :as command-line])
  (:require [clojure.string :as str])
  (:require [swank.swank :as swank])
  (:require [clojure.contrib.server-socket :as ss])
  (:import [com.sun.tools.jdi SocketAttachingConnector])
  (:import [com.sun.tools.jdi ProcessAttachingConnector])
  (:use [clojure.main :only (repl)]))

(def *ctx* nil)

(gen-interface
 :name com.wirde.inject.Injectee
 :methods [[inject [java.util.List] Object]])

(gen-class
 :name com.wirde.inject.Injecter
 :prefix "injecter-"
 :methods [[inject [com.wirde.inject.Injectee java.util.List] Object]])

(gen-class
 :name com.wirde.inject.SwankInjectee
 :implements [com.wirde.inject.Injectee]
 :prefix "swank-")

(gen-class
 :name com.wirde.inject.ReplInjectee
 :implements [com.wirde.inject.Injectee]
 :prefix "repl-server-")

(defn- print-remote-stacktrace [thread ex]
  (println "Got remote Exception")
  (binding [swank-inject.jdi/thread thread]
    (println (.value ((remote-method-handle ex
					    "toString"
					    "()Ljava/lang/String;")
		      '())))				    
    (doall
     (map (fn [e] (println "    at"
			   (.value ((remote-method-handle e
							  "toString"
							  "()Ljava/lang/String;")
				    '()))))
	  (.getValues ((remote-method-handle ex
					     "getStackTrace"
					     "()[Ljava/lang/StackTraceElement;")
		       '()))))))

;;TODO:
;;Better error message
(defn -main [& args]
  (command-line/with-command-line args
    "Remote Swank injector."
    [[host "The hostname of the (remote) process"]
     [port "The portnumber of the process with remote debugging enabled"]
     [urls "Comma separated list of URLs to jar-files used while injecting"]
     [instances "Comma separated list of classes to locate instances for"]
     [injectee "Injectee class (default com.wirde.inject.ReplInjectee)"]
     remaining]
    (if (or (nil? host) (nil? port) (nil? urls) (nil? instances))
      (println "Host, port, urls and instance class names must be specified using -host <arg> -port <arg> -url <arg> -instances <arg>")
      (if (not (empty? remaining))
	(println "Unknown arguments: " remaining)
	(let [vm (attach-to-vm SocketAttachingConnector {"hostname" host "port" port})
	      thread (suspend-finalizer-thread vm)]
	  (try
	    (println (inject-bootstrapper
		      thread
		      (str/split urls (java.util.regex.Pattern/compile ","))
		      (if (nil? injectee)
			"com.wirde.inject.ReplInjectee"
			injectee)
		      (remove nil? (map #(find-first-instance vm %)
					(str/split instances (java.util.regex.Pattern/compile ","))))))
	    (catch com.sun.jdi.InvocationException e		
	      (print-remote-stacktrace thread (.exception e)))
	    (catch Exception e
	      (let [cause (.getCause e)]
		(if (instance? com.sun.jdi.InvocationException cause)
		  (print-remote-stacktrace thread (.exception cause))
		  (.printStackTrace e))))
	    (finally (.dispose vm)))))))
  (shutdown-agents)
  (println "Done"))

(defn injecter-inject [this injectee args]
  (.inject injectee args)
  (.println System/out "done injecting"))

;;Copied from clojure-contrib since the original is private
(defn- socket-repl [ins outs]
  (binding [*in* (clojure.lang.LineNumberingPushbackReader. (java.io.InputStreamReader. ins))
            *out* (java.io.OutputStreamWriter. outs)
            *err* (java.io.PrintWriter. #^java.io.OutputStream outs true)]
    (repl)))

(defn repl-server-inject [this args]
  (.println System/out "Starting REPL")
  (.println System/out *ctx*)
  (binding [*ctx* (seq args)]
    (ss/create-server 4711 (bound-fn* socket-repl)))
  (.println System/out "Started REPL"))

(defn swank-inject [this args]
;  (binding [user/*ctx* (seq args)]
; would prefer to use dynamic binding here, but swank starts its own thread and I can't use bound-fn*
  (def *ctx* (seq args))
  (.println System/out "Starting Swank")
  (.println System/out *ctx*)
  (swank/start-repl)
  (.println System/out "Started Swank"))
