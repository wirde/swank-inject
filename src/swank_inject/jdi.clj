(ns swank-inject.jdi
  (:require [clojure.string :as str])
  (:import [com.sun.jdi Bootstrap VirtualMachineManager])
  (:import [com.sun.tools.jdi SocketAttachingConnector])
  (:import [com.sun.jdi.event BreakpointEvent]))

(def *finalizer-thread* "Finalizer")
(def *timeout* 10000)

;;TODO: pre-conditions

(defn attach-to-vm [host port]
  (let [connectors (.attachingConnectors (Bootstrap/virtualMachineManager))
	socket-conn (first (filter #(instance? SocketAttachingConnector %) connectors))
	args (.defaultArguments socket-conn)]
    (.setValue (.get args "port") port)
    (.setValue (.get args "hostname") host)
    (.setValue (.get args "timeout") *timeout*)
    (.attach socket-conn args)))

;;Invoking methods on mirrored instances requires a reference to a thread which has been suspended by an event which
;;occurred in that thread.
;;[http://download.oracle.com/javase/6/docs/jdk/api/jpda/jdi/com/sun/jdi/ObjectReference.html#invokeMethod(com.sun.jdi.ThreadReference, com.sun.jdi.Method, java.util.List, int)]
;;Calling ThreadReference.suspend() does *not* work. This hack will get you such a thread (at least for HotSpot 1.6)
;;without knowledge of the running code. It works by putting a breakpoint in the code run by the finalizer thread and
;;then interrupting that thread. It will stay suspended until thread.resume() or vm.dispose() is called.
(defn suspend-finalizer-thread [vm]
  {:pre [(not (nil? vm))]
   :post [(.isAtBreakpoint %)]}
  (let [thread (first (filter #(.equals *finalizer-thread* (.name %)) (.allThreads vm)))]
    (.suspend thread)
    (let [breakpoint (.createBreakpointRequest (.eventRequestManager vm) (.location (.frame thread 1)))]
      (.enable breakpoint)
      (.resume thread)
      (.interrupt thread)
      ;;TODO: we could get other events here...
      (if (.isEmpty (.remove (.eventQueue vm) *timeout*))
	(throw (RuntimeException. "Got no breakpoint event")))
      (.disable breakpoint))
    thread))

(defn locate-class [vm clazz]
  {:pre [(not (nil? vm))]}
  ;;TODO: Handle the same class loaded in different classloaders.
  (first (.classesByName vm clazz)))

(defn invoke-method [thread instance name signature args]
  (.invokeMethod instance
		 thread
		 (.concreteMethodByName (.referenceType instance) name signature)
		 args
		 0))

(defn new-instance [thread class-name signature args]
  ;;TODO: load class if not already loaded
  (let [clazz (locate-class (.virtualMachine thread) class-name)]
    (.newInstance clazz
		  thread
		  (.concreteMethodByName clazz "<init>" signature)
		  args
		  0)))

(defn create-array [thread type members]
  (let [arr (.newInstance (locate-class (.virtualMachine thread) (str type "[]")) (count members))]
    (.setValues arr members)
    arr))

(defn to-value-list [vm args]
  {:pre [(not (nil? vm))]}
  (map #(.mirrorOf vm %) args))

;;TODO: Deal with multiple classloaders
(defn find-first-instance [vm class-name]
  {:pre [(not (nil? vm))]
   :post [(not (nil? %))]}
  (first (.instances (locate-class vm class-name) 1)))

(defn load-class [thread classloader class-name]
  (invoke-method thread
		 classloader
		 "loadClass"
		 "(Ljava/lang/String;Z)Ljava/lang/Class;"
		 (to-value-list (.virtualMachine thread) (list class-name true))))

(defn get-context-classloader [thread]
  (invoke-method
   thread
   thread
   "getContextClassLoader"
   "()Ljava/lang/ClassLoader;"
   '()))

(defn set-context-classloader [thread classloader]
  (invoke-method thread
		 thread
		 "setContextClassLoader"
		 "(Ljava/lang/ClassLoader;)V"
		 (list classloader)))

(defn create-url-classloader [thread urls parent]
  (let [vm (.virtualMachine thread)]
    (new-instance thread
		  "java.net.URLClassLoader"
		  "([Ljava/net/URL;Ljava/lang/ClassLoader;)V"
		  (list (create-array
			 thread
			 "java.net.URL" 
			 (map #(new-instance
				thread
				"java.net.URL"
				"(Ljava/lang/String;)V"
				(to-value-list vm (list %)))
			      urls))
			parent))))

;;true if classloader is cl1 is a grandchild (or identical to) cl2
;;false if the classloader hieararchies are disjoint or if they both use the bootstrap classloader
(defn descendant? [thread cl1 cl2]
  (if (= cl1 cl2)
    true
    (if (nil? cl1)
      false
      (recur thread (invoke-method thread
				   cl1
				   "getParent"
				   "()Ljava/lang/ClassLoader;"
				   '())
	     cl2))))

(defn find-instance-with-lowest-common-classloader
  ([thread instance1 instance2]
     (let [cl1 (.classLoader (.referenceType instance1))
	   cl2 (.classLoader (.referenceType instance2))]
       (if (descendant? thread cl1 cl2)
	 instance1
	 (if (descendant? thread cl2 cl1)
	   instance2
	   nil))))
  ([thread instances]
     (if (nil? instances)
       nil
       (if (nil? (rest instances))
	 (first instances)
	 (reduce #(find-instance-with-lowest-common-classloader thread %1 %2) instances)))))

(defn inject-bootstrapper [thread urls injectee instances]
  (let [vm (.virtualMachine thread)
	prev-context-classloader (get-context-classloader thread)
	;;If the classloader hierarchy is disjoint, then the bootstrap classloader will be used.
	url-classloader (create-url-classloader thread urls (.classLoader (.referenceType (find-instance-with-lowest-common-classloader thread instances))))]
    (set-context-classloader thread url-classloader)
    
    (let [bootstrapper (invoke-method
			thread
			(load-class thread
				    url-classloader
				    "com.wirde.inject.Injecter")
			"newInstance"
			"()Ljava/lang/Object;"
			'())
	  remote-args (new-instance thread
				    "java.util.ArrayList"
				    "()V"
				    '())]
      (reduce
       #(do
	  (invoke-method thread
			 %1
			 "add"
			 "(Ljava/lang/Object;)Z"
			 (list %2))
	  %1)
       remote-args
       instances)

      (let [result 
	    (invoke-method thread
			   bootstrapper
			   "inject"
			   "(Lcom/wirde/inject/Injectee;Ljava/util/List;)Ljava/lang/Object;"
			   (list
			    (invoke-method thread
					   (load-class thread
						       url-classloader
						       injectee)
					   
					   "newInstance"
					   "()Ljava/lang/Object;"
					   '())
			    remote-args))]
	(println "after inject")      
	(set-context-classloader thread prev-context-classloader)
	(println "done in inject-boot")
	result))))