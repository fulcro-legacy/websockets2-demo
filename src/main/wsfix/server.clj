(ns wsfix.server
  (:require
    [fulcro.easy-server :refer [make-fulcro-server]]
    [fulcro.websockets.protocols :as wp :refer [WSListener WSNet add-listener remove-listener client-added client-dropped]]
    [taoensso.sente.server-adapters.http-kit :as hk]
    [org.httpkit.server :as http-kit]
    ; MUST require these, or you won't get them installed.
    wsfix.api.read
    wsfix.api.mutations
    [fulcro.server :refer [defmutation]]
    [com.stuartsierra.component :as component]
    [fulcro.websockets.transit-packer :as tp]
    [ring.middleware.params :refer [wrap-params]]
    [ring.middleware.keyword-params :refer [wrap-keyword-params]]
    [ring.middleware.content-type :refer [wrap-content-type]]
    [ring.middleware.gzip :refer [wrap-gzip]]
    [ring.middleware.not-modified :refer [wrap-not-modified]]
    [ring.middleware.resource :refer [wrap-resource]]
    [ring.util.response :as rsp :refer [response file-response resource-response]]
    [fulcro.server :as server]
    [fulcro.easy-server :refer [index]]
    [fulcro.websockets :as fw]
    [clojure.java.io :as io]
    [fulcro.easy-server :as easy])
  (:import (java.util Date)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Example of a hand-built websocket server
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn not-found-handler []
  (fn [req]
    {:status  404
     :headers {"Content-Type" "text/html"}
     :body    (io/file (io/resource "public/not-found.html"))}))

(defn wrap-root [handler]
  (fn [{:keys [uri] :as req}]
    (if (= "/" uri)
      (index req)
      (handler req))))

(defn wrap-verify-sente-params [handler]
  (fn [{:keys [uri] :as req}]
    (when (= "/chsk" uri)
      ;; Verify our :req-params from the client are here.
      (if (clojure.string/includes? (:query-string req)
                                    "trustworthy=true")
        (handler req)
        {:status 401}))))

(defrecord Middleware [ring-stack websockets]
  component/Lifecycle
  (start [this]
    (assoc this :ring-stack
           (-> (not-found-handler)
               (fw/wrap-api websockets)
               (wrap-verify-sente-params)
               (server/wrap-transit-params)
               (server/wrap-transit-response)
               (wrap-keyword-params)
               (wrap-params)
               (wrap-resource "public")
               (wrap-root)
               (wrap-content-type)
               (wrap-not-modified)
               (wrap-gzip))))
  (stop [this]))

(defn make-middleware []
  (component/using (map->Middleware {})
    [:websockets]))

(defrecord WebServer [config middleware stop-fn]
  component/Lifecycle
  (start [this]
    (let [port (get-in config [:value :port] 0)
          [port stop-fn] (let [stop-fn (http-kit/run-server (:ring-stack middleware) {:port port})]
                           [(:local-port (meta stop-fn)) (fn [] (stop-fn :timeout 100))])
          uri  (format "http://localhost:%s/" port)]
      (println "Web server running at " uri)
      (assoc this :stop-fn stop-fn)))
  (stop [this]
    (when stop-fn
      (stop-fn))
    this))

(defn make-server []
  (component/using
    (map->WebServer {})
    [:middleware :config]))

(defrecord ChannelListener [websockets]
  WSListener
  (client-dropped [this ws-net cid]
    (println "Client disconnected " cid))
  (client-added [this ws-net cid]
    (println "Client connected " cid))

  component/Lifecycle
  (start [component]
    (add-listener websockets component)
    component)
  (stop [component]
    (remove-listener websockets component)
    component))

(defn make-channel-listener []
  (component/using
    (map->ChannelListener {})
    [:websockets]))

(defn valid-id? [client-id] true)

(defrecord Broadcaster [websockets ^Thread thread]
  component/Lifecycle
  (start [this]
    (let [t (new Thread (fn []
                          (Thread/sleep 1000)
                          (let [cids (some-> websockets :connected-uids deref :any)]
                            (doseq [cid cids]
                              (wp/push websockets cid :time-change {:time (Date.)})))
                          (recur)))]
      (.start t)
      (assoc this :thread t)))
  (stop [this] (.stop thread)))

(defn make-broadcaster []
  (component/using
    (map->Broadcaster {})
    [:websockets]))

(defn build-server
  [{:keys [config] :or {config "config/dev.edn"}}]
  (component/system-map
    :config (server/new-config config)
    :middleware (make-middleware)
    :websockets (fw/make-websockets (server/fulcro-parser))
    :channel-listener (make-channel-listener)
    :broadcaster (make-broadcaster)
    :web-server (make-server)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Example using easy server
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn build-easy-server [path]
  (easy/make-fulcro-server
    :config-path path
    :components {:websockets       (fw/make-websockets (server/fulcro-parser))
                 :channel-listener (make-channel-listener)
                 :broadcaster      (make-broadcaster)
                 :adapter          (fw/make-easy-server-adapter)}))
