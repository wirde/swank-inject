(ns swank-inject.test
  (:use [swank-inject.jdi]))

(comment
  (let [vm (attach-to-vm "localhost" 7777)
	thread (suspend-finalizer-thread vm)]
    (try
      (print (inject-bootstrapper
	      thread
	      '("file:///Users/wirde/Documents/workspaces/other/swank-inject/swank-inject-1.0.0-SNAPSHOT-standalone.jar")
	      "com.wirde.inject.SwankInjectee"
	      '("MyTest")))
	     
      (catch Exception e (invoke-method
			  thread
			  (.exception e)
			  "printStackTrace"
			  "()V"
			  '()))
      (finally (.dispose vm))
      ))
  )

(comment
  (def vm (attach-to-vm "localhost" 7777))
  (def thread (suspend-finalizer-thread vm))


  (print (find-first-instance vm "MyTest"))
  
(def url (new-instance
	  thread
	  "java.net.URL"
	  "(Ljava/lang/String;)V"
	  (to-value-list vm '("file:///Users/wirde/Documents/workspaces/other/swank-inject/swank-inject-1.0.0-SNAPSHOT-standalone.jar"))))

(def url2 (new-instance
	  thread
	  "java.net.URL"
	  "(Ljava/lang/String;)V"
	  (to-value-list vm '("file:///Users/wirde/Documents/workspaces/other/swank-inject/test.jar"))))


(def arr (create-array thread "java.net.URL" (list url)))

(def url-classloader (new-instance
	 thread
	 "java.net.URLClassLoader"
	 "([Ljava/net/URL;)V"
	 (list arr)))

(invoke-method
 thread
 thread
 "setContextClassLoader"
 "(Ljava/lang/ClassLoader;)V"
 (list cl))

(def bootstrapper (invoke-method
		   thread
		   cl
		   "loadClass"
		   "(Ljava/lang/String;Z)Ljava/lang/Class;"
		   (to-value-list vm '("com.wirde.inject.Bootstrapper" true))))
;;		   (to-value-list vm '("Test" true))))


(try
(def boot-inst (invoke-method
	 thread
	 bootstrapper
	 "newInstance"
	 "()Ljava/lang/Object;"
	 '()))
(catch Exception e (invoke-method
		    thread
		    (.exception e)
		    "printStackTrace"
		    "()V"
		    '())))
)