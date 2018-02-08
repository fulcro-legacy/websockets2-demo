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

(defn make-event-handler
  "Builds a sente event handler that connects the websockets support up to the parser via the
  :fulcro.client/API event, and also handles notifying listeners that clients connected and dropped."
  [{:keys [send-fn listeners parser] :as websockets}]
  (fn [event]
    (let [env (merge {:push          send-fn
                      :websockets    websockets
                      :sente-message event}
                (dissoc websockets :server-options :ring-ajax-get-or-ws-handshake :ring-ajax-post
                  :ch-recv :send-fn :stop-fn :listeners))
          {:keys [?reply-fn id uid ?data]} event]
      (case id
        :chsk/uidport-open (doseq [l ^WSListener @listeners] (client-added l websockets uid))
        :chsk/uidport-close (doseq [l ^WSListener @listeners] (client-dropped l websockets uid))
        :fulcro.client/API (let [result (server/handle-api-request parser env ?data)]
                             (println "Request: " ?data)
                             (println "Response: " result)
                             (if ?reply-fn
                               (?reply-fn result)
                               (println "ERROR: Reply function missing on API call!")))
        (do :nothing-by-default)))))

(defrecord Websockets [parser server-adapter server-options ring-ajax-post ring-ajax-get-or-ws-handshake ch-recv send-fn connected-uids stop-fn listeners]
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
    (let [chsk-server (sente/make-channel-socket-server! server-adapter (merge {:packer (tp/make-packer {})} server-options))
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

(defn make-websockets
  "Build a web sockets component with the given API parser and sente socket server options (see sente docs).
  NOTE: If you supply a packer, you'll need to make sure tempids are supported (this is done by default, but if you override it, it is up to you.
  The default user id mapping is to use the internally generated UUID of the client. Use sente's `:user-id-fn` option
  to override this.

  Anything injected as a dependency of this component is added to your parser environment (in addition to the parser
  itself).

  Thus, if you'd like some other component (like a database) to be there, simply do this:

  (component/using (make-websockets parser {})
    [:sql-database :sessions])

  and when the system starts it will inject those components into this one, and this one will be your parser env.

  Additionally, the parser environment will include:
    :websockets The channel server component itself
    :push           A function that can send push messages to any connected client of this server. (just a shortcut to send-fn in websockets)
    :parser         The parser you gave this function
    :sente-message  The raw sente event.

  The websockets component must be joined into a real network server via a ring stack. This implementation assumes http-kit.

  If you don't supply a server adapter, it defaults to http-kit.
  "
  ([parser]
   (make-websockets parser nil {}))
  ([parser http-server-adapter sente-socket-server-options]
   (map->Websockets {:server-options (merge {:user-id-fn (fn [r] (:client-id r))} sente-socket-server-options)
                     :server-adapter (or http-server-adapter (hk/get-sch-adapter))
                     :parser         parser})))

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

(defn wrap-api
  "Add API support to a Ring middleware chain. The websockets argument is an initialized Websockets component."
  [handler {:keys [ring-ajax-post ring-ajax-get-or-ws-handshake] :as websockets}]
  (fn [{:keys [request-method uri] :as req}]
    (let [is-ws? (= "/chsk" uri)]
      (if is-ws?
        (case request-method
          :get (ring-ajax-get-or-ws-handshake req)
          :post (ring-ajax-post req))
        (handler req)))))

(defrecord Middleware [ring-stack websockets]
  component/Lifecycle
  (start [this]
    (assoc this :ring-stack
                (-> (not-found-handler)
                  (wrap-api websockets)
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

(defmutation op/die! [_]
  (action [env]
    (throw (ex-info "Dead!" {:reason "You suck!"}))))

(defmutation op/long-running-thing [{:keys [n]}]
  (action [env]
    (println "Running for a long time...." n)
    (Thread/sleep 5000)
    (println "Done Running")
    {:status n}))

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

(defn build-server
  [{:keys [config] :or {config "config/dev.edn"}}]
  (component/system-map
    :config (server/new-config config)
    :middleware (make-middleware)
    :websockets (make-websockets (server/fulcro-parser))
    :channel-listener (make-channel-listener)
    :web-server (make-server)))
