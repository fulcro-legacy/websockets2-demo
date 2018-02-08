(ns wsfix.server
  (:require
    [fulcro.easy-server :refer [make-fulcro-server]]
    [fulcro.websockets.protocols :refer [WSListener WSNet add-listener remove-listener client-added client-dropped]]
    [taoensso.sente.server-adapters.http-kit :as hk]
    [org.httpkit.server :as http-kit]
    [fulcro.websockets.components.channel-server :as cs]
    ; MUST require these, or you won't get them installed.
    [wsfix.api.read]
    [clojure.pprint :refer [pprint]]
    [wsfix.api.mutations]
    [fulcro.server :refer [defmutation]]
    [com.stuartsierra.component :as component]
    [taoensso.timbre :as timbre]
    [taoensso.sente :as sente]
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
    [clojure.java.io :as io]))

(defn make-event-handler [{:keys [send-fn listeners connected-uids parser] :as channel-server}]
  (fn [event]
    (let [env {:channel-server channel-server
               :push           send-fn
               :connected-uids (:all connected-uids)
               :parser         parser
               :sente-message  event}
          {:keys [?reply-fn id uid client-id ?data]} event]
      (case id
        :chsk/uidport-open (doseq [l ^WSListener @listeners] (client-added l channel-server uid))
        :chsk/uidport-close (doseq [l ^WSListener @listeners] (client-dropped l channel-server uid))
        :fulcro.client/API (let [result (server/handle-api-request parser env ?data)]
                             (println "Request: " ?data)
                             (println "Response: " result)
                             (if ?reply-fn
                               (?reply-fn result)
                               (println "ERROR: Reply function missing on API call!")))
        (println "Server got unrecognized event: " id uid client-id)))))

(defrecord ChannelServer [parser server-options ring-ajax-post ring-ajax-get-or-ws-handshake ch-recv send-fn connected-uids stop-fn listeners]
  WSNet
  (add-listener [this listener]
    (swap! listeners conj listener))
  (remove-listener [this listener]
    (swap! listeners disj listener))
  (push [this cid verb edn]
    (send-fn cid [:api/server-push {:topic verb :msg edn}]))

  component/Lifecycle
  (start [this]
    (println "Starting Sente Socket server")
    (let [chsk-server (sente/make-channel-socket-server! (hk/get-sch-adapter) (merge {:packer (tp/make-packer {})} server-options))
          {:keys [ch-recv send-fn connected-uids
                  ajax-post-fn ajax-get-or-ws-handshake-fn]} chsk-server
          result      (assoc this
                        :ring-ajax-post ajax-post-fn
                        :ring-ajax-get-or-ws-handshake ajax-get-or-ws-handshake-fn
                        :ch-rech ch-recv
                        :send-fn send-fn
                        :listeners (atom #{})
                        :connected-uids connected-uids)
          stop        (sente/start-server-chsk-router! ch-recv (make-event-handler result))]
      (println "Started Sente event loop.")
      (assoc result :stop-fn stop)))
  (stop [this]
    (when stop-fn
      (println "Stopping Sente.")
      (stop-fn))
    (println "Stopped Sente.")
    (assoc this :stop-fn nil :ch-recv nil :send-fn nil)))

(defn make-channel-server
  "Build a channel server component with the given API parser and sente socket server options (see sente docs).
  NOTE: If you supply a packer, you'll need to make sure tempids are supported (this is done by default, but if you override it, it is up to you.
  The default user id mapping is to use the internally generated UUID of the client. Use sente's `:user-id-fn` option
  to override this.

    (let [env {:channel-server channel-server
               :push           send-fn
               :connected-uids (:all connected-uids)
               :parser         parser
               :sente-message  event}

  Anything injected as a dependency of this component is added to your parser environment (in addition to the parser
  itself).

  Thus, if you'd like some other component (like a database) to be there, simply do this:

  (component/using (make-channel-server parser {})
    [:sql-database :sessions])

  and when the system starts it will inject those components into this one, and this one will be your parser env.

  Additionally, the parser environment will include:
    :channel-server The channel server component
    :push           A function that can send push messages to any connected client of this server
    :connected-uids A set of client UIDs that are connected to this server
    :parser         The parser you gave this function
    :sente-message  The raw sente event, which include the raw ring request. "
  [parser sente-socket-server-options]
  (map->ChannelServer {:server-options (merge {:user-id-fn (fn [r] (:client-id r))} sente-socket-server-options)
                       :parser         parser}))

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

(defn wrap-api [handler {:keys [ring-ajax-post ring-ajax-get-or-ws-handshake] :as channel-server}]
  (fn [{:keys [request-method uri] :as req}]
    (let [is-ws? (= "/chsk" uri)]
      (if is-ws?
        (case request-method
          :get (ring-ajax-get-or-ws-handshake req)
          :post (ring-ajax-post req))
        (handler req)))))

(defrecord Middleware [ring-stack channel-server]
  component/Lifecycle
  (start [this]
    (assoc this :ring-stack
                (-> (not-found-handler)
                  (wrap-api channel-server)
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
    [:channel-server]))

(defrecord WebServer [config middleware stop-fn]
  component/Lifecycle
  (start [this]
    (let [port (get-in config [:value :port] 0)
          [port stop-fn] (let [stop-fn (http-kit/run-server (:ring-stack middleware) {:port port})]
                           [(:local-port (meta stop-fn)) (fn [] (stop-fn :timeout 100))])
          uri  (format "http://localhost:%s/" port)]
      (println "Web server running at `%s`" uri)
      (assoc this :stop-fn stop-fn)))
  (stop [this]
    (when stop-fn
      (stop-fn))
    this))

(defn make-server []
  (component/using
    (map->WebServer {})
    [:middleware :config]))

(defmutation op/die! [_]
  (action [env]
    (throw (ex-info "Dead!" {:reason "You suck!"}))))

(defmutation op/long-running-thing [{:keys [n]}]
  (action [env]
    (println "Running for a long time...." n)
    (Thread/sleep 5000)
    (println "Done Running")
    {:status n}))

(defrecord ChannelListener [channel-server]
  WSListener
  (client-dropped [this ws-net cid]
    (println "Client disconnected " cid))
  (client-added [this ws-net cid]
    (println "Client connected " cid))

  component/Lifecycle
  (start [component]
    (add-listener channel-server component)
    component)
  (stop [component]
    (remove-listener channel-server component)
    component))

(defn make-channel-listener []
  (component/using
    (map->ChannelListener {})
    [:channel-server]))

(defn valid-id? [client-id] true)

(defn build-server
  [{:keys [config] :or {config "config/dev.edn"}}]
  (component/system-map
    :config (server/new-config config)
    :middleware (make-middleware)
    :channel-server (make-channel-server (server/fulcro-parser) {})
    :channel-listener (make-channel-listener)
    :web-server (make-server)))
