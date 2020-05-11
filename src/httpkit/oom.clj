(ns httpkit.oom
  (:require [clj-http.client :as http]
            [clojure.java.io :as io]
            [org.httpkit.server :as server])
  (:gen-class))

(def server
  (atom nil))

(defn urandom
  [n]
  (with-open [in (io/input-stream (io/file "/dev/urandom"))]
    (let [buf (byte-array n)
          _ (.read in buf)]
      buf)))

(defn router
  [{:keys [uri query-string] :as request}]
  (case uri
    "/urandom" {:status 200
                :headers {"Content-Type" "text/plain"}
                :body (->> query-string
                           (re-find #"\d+")
                           Integer/parseInt
                           urandom)}
    "/urandom/random" {:status 200
                       :headers {"Content-Type" "text/plain"}
                       :body (urandom (+ 500 (rand-int 500)))}
    "/increaser" {:status 200
                  :headers {"Content-Type" "text/plain"}
                  :body (let [i (->> query-string
                                     (re-find #"\d+")
                                     Integer/parseInt)
                              data (urandom (* i 100000))]
                          data)}
    {:status 404}))

(defn wrap-log-requests
  [handler]
  (fn [{:keys [request-method server-name server-port uri] :as request}]
    (println (format "%-5s %12s:%s%s" request-method server-name server-port uri))
    (handler request)))

(def handler
  #'router)

(defn start-server
  []
  (when-not @server
    (reset! server (server/run-server #'handler {:port 8080
                                                 :thread 4}))))

(defn stop-server
  []
  (when @server
    (@server)
    (reset! server nil)))

(comment
  (do
    (stop-server)
    (start-server)))
