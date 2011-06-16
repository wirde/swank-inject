# Injecting a Clojure REPL in an unmodified Java application 

A tool which lets you "inject" a Clojure REPL into a running Java application with remote debugging enabled. You are also able to specify one or more classes, one instance of each of those classes (preferably singletons) will be bound to the symbol:

     swank-inject.aot/*ctx*

Uses Java Debug Interface (JDI) and a URLClassLoader to inject the code (http://download.oracle.com/javase/6/docs/jdk/api/jpda/jdi/index.html)

## Building

Built with https://github.com/technomancy/leiningen

On OSX it is enough to do 

     > lein uberjar

(because the com.sun.jdi package is a part of the normal runtime)

On other platforms those classes are generally found in tools.jar. To build you need to make sure that the tools.jar path in project.clj is correct for your system. For the build to work a new feature of leiningen is needed (:extra-classpath-dirs) - meaning version 1.6.0-SNAPSHOT of leiningen is required.

## Sample Usage

In the samples below showing how to use swank-inject you will also need to add a path to tools.jar to the classpath on platforms where com.sun.jdi is not included in the normal runtime.

Show options:

     > java -cp swank-inject-0.5.0-SNAPSHOT-standalone.jar com.wirde.inject.Main -h
       Remote Swank injector.Options
           --host <arg>       The hostname of the (remote) process                           
           --port <arg>       The portnumber of the process with remote debugging enabled    
           --urls <arg>       Comma separated list of URLs to jar-files used while injecting 
           --instances <arg>  Comma separated list of classes to locate instances for        
           --injectee <arg>   Injectee class (default com.wirde.inject.ReplInjectee)

Start application in remote debug mode using JVM switches:
-Xdebug -Xrunjdwp:transport=dt_socket,address=7777,server=y,suspend=n 

Start injector:

      > java -cp swank-inject-0.5.0-SNAPSHOT-standalone.jar com.wirde.inject.Main -host localhost -port 7777 -urls file:///home/wirde/swank-inject-0.5.0-SNAPSHOT-standalone.jar -instances com.wirde.MySingleton

Connect to the REPL using telnet (port 4711)

Your instances will be available in a sequence bound to 

      swank-inject.aot/*ctx*

## Injecting a Swank server

At the moment injecting a Swank repl does not work completely (the injecting process never exits). To try it out anyway, use the following option:
-injectee com.wirde.inject.SwankInjectee

## Injecting other code

By providing an implementation of com.wirde.inject.Injectee using the "-injectee" option (must be available in one of the jars pointed to by -urls) you can inject arbitrary code.

## Limitations

* Probably only works for HotSpot...
* Expects classes passed to be singletons, if not then the first found instance will be used.
* Will currently not work reliably if the class is loaded in multiple classloaders.
* Using Swank as the injected REPL does not work well for some reason. For now I am using clojure.contrib.server-socket/create-repl-server as the default.

## Sample run using the Spring sample application "swf-booking-mvc" 

Slightly contrived example of how injecting a Clojure REPL can be useful to explore and poke at an existing application. Using one of the Spring Web Flow Reference Applicatons: (http://www.springsource.org/webflow-samples)

### Inject REPL

Inject a handle to the Spring ApplicationContext from which Spring beans can be reached later on.

    > java -cp swank-inject-0.5.0-SNAPSHOT-standalone.jar com.wirde.inject.Main -host localhost -port 7777 -urls file:///home/wirde/temp/inject-test/swank-inject-0.5.0-SNAPSHOT-standalone.jar -instances org.springframework.web.context.support.XmlWebApplicationContext

### Connect using telnet

    ;;First get at the bookingService handle
    clojure.core=> (def app-ctx (first swank-inject.aot/*ctx*))
    #'clojure.core/app-ctx
    clojure.core=> (def bookingService (.getBean app-ctx "bookingService"))
    #'clojure.core/bookingService
    clojure.core=> bookingService
    #<$Proxy19 org.springframework.webflow.samples.booking.JpaBookingService@3853cb28>

    ;;Set up a wide search
    clojure.core=> (def search (doto (org.springframework.webflow.samples.booking.SearchCriteria.) (.setSearchString "") (.setPageSize 100)))
    #'clojure.core/search

    ;;Find all hotels in Eau Claire with price lower than 100
    clojure.core=> (filter #(and (= "Eau Claire" (.getCity %)) (< (.getPrice %) 100)) (.findHotels bookingService search))
    (#<Hotel Hotel(Sea Horse Inn,2106 N Clairemont Ave,Eau Claire,54703)> #<Hotel Hotel(Super 8 Eau Claire Campus Area,1151 W Macarthur Ave,Eau Claire,54701)>)

## TODO

* Handle singletons being loaded in multiple classloaders. How to select which branch to follow?
* Alternatively start a Swank REPL
