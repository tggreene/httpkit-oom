(ns httpkit.oom-test
  (:require [clj-http.client :as http]
            [clojure.java.io :as io]
            [clojure.test :refer :all]
            [httpkit.oom :refer :all]
            [org.httpkit.server :as server]))

(defn free-port
  ([] (free-port 1))
  ([attempt]
   (when (< attempt 3)
     (or
      (try
        (let [socket (java.net.ServerSocket. 0)
              port (.getLocalPort socket)]
          (.close socket)
          port)
        (catch Exception _)
        (finally))
      (free-port (inc attempt))))))

(def ^:dynamic *port* nil)

(defmacro with-server
  [handler & body]
  `(let [port# (free-port)
         server# (server/run-server ~handler {:port port#})]
    (try
      (with-bindings {#'*port* port#}
        ~@body)
      (catch Exception e#
        (prn e#))
      (finally
        (server#)))))

(def mebibyte 1048576)

(def direct-memory-limit
  (if (re-matches #"1\.8.+" (System/getProperty "java.version"))
    (sun.misc.VM/maxDirectMemory)
    ;; if we can't introspect maxDirectMemory use 500MiB as a default
    (* mebibyte 500)))

;; httpkit will try and write any size of object into a socket the nio.Socket
;; implementation will create a direct memory backed buffer to hold this
;; object in

(deftest instant-memory-exhaustion
  (with-server (fn [_]
                 {:status 200
                  :headers {"Content-Type" "application/octet-stream"}
                  :body (clojure.java.io/input-stream (byte-array (inc direct-memory-limit)))})
    (let [{:keys [body status]}
          (http/get (str "http://localhost:" *port*)
                    {:socket-timeout 5000
                     :connection-timeout 5000
                     :throw-exceptions false})]
      (is (= 500 status))
      (is (= "Direct buffer memory" body)))))

(def memory-increment (* 10 mebibyte))

;; Here we can see the behaviour of the nio thread local cache, the cache
;; retains references to every direct buffer allocated (in this case in 10MB
;; increments). No GC will be triggered by this process alone as they don't
;; impact the jvm heap.
;;
;; The only current mitigation against this is to use
;; -XX:maxCachedBufferSize=x

(deftest incremental-memory-exhaustion
  (with-server (let [state (atom 0)]
                 (fn [_]
                   (swap! state inc)
                   {:status 200
                    :headers {"Content-Type" "application/octet-stream"}
                    :body (clojure.java.io/input-stream (byte-array (* @state memory-increment)))}))
    (let [{:keys [body status]}
          (loop [try 1]

            (let [{:keys [status] :as response}
                  (http/get (str "http://localhost:" *port*)
                            {:socket-timeout 5000
                             :connection-timeout 5000
                             :throw-exceptions false})]
              (if (and (= 200 status) (< try 100))
                (recur (inc try))
                response)))]
      (is (= 500 status))
      (is (= "Direct buffer memory" body)))))
