(ns wsfix.networking
  (:require-macros [cljs.core.async.macros :refer (go go-loop)])
  (:require [cognitect.transit :as ct]
            [taoensso.sente :as sente :refer (cb-success?)]
            [fulcro.client.network :refer [FulcroNetwork]]
            [fulcro.client.logging :as log]
            [fulcro.websockets.transit-packer :as tp]
            [fulcro.client.primitives :as prim]))

(defn make-event-handler [push-handler]
  (fn [{:keys [id ?data] :as event}]
    (case id
      :api/server-push (when push-handler (push-handler ?data))
      (log/debug "Unsupported message " id))))

(defrecord Websockets [channel-socket push-handler url host state-callback global-error-callback transit-handlers req-params stop app]
  FulcroNetwork
  (send [this edn ok err]
    (let [{:keys [send-fn]} @channel-socket]
      (send-fn [:fulcro.client/API edn] 30000
        (fn process-response [resp]
          (if (cb-success? resp)
            (let [{:keys [status body]} resp]
              (if (= 200 status) (ok body) (err body)))
            (do
              (case resp
                :chsk/closed (println "Connection closed...")
                :chsk/error (println "Connection error...")
                :chsk/timeout (println "Connection timeout...")
                (println "Unknown resp: " resp))
              ; retry...probably don't need a back-off, but YMMV
              (js/setTimeout #(fulcro.client.network/send this edn ok err) 1000)))))))
  (start [this]
    (let [{:keys [ch-recv state] :as cs} (sente/make-channel-socket! url ; path on server
                                           {:packer         (tp/make-packer transit-handlers)
                                            :host           host
                                            :type           :ws ; e/o #{:auto :ajax :ws}
                                            :params         req-params
                                            :wrap-recv-evs? false})
          message-received (make-event-handler push-handler)]
      (cond
        (fn? state-callback) (add-watch state ::state-callback (fn [a k o n]
                                                                 (state-callback o n)))
        (instance? Atom state-callback) (add-watch state ::state-callback (fn [a k o n]
                                                                            (@state-callback o n))))
      (reset! channel-socket cs)
      (sente/start-chsk-router! ch-recv message-received)
      (log/debug "Remember to install the push handlers!")
      this)))

(defn make-websocket-networking
  "Creates a websocket-based networking component for use as a Fulcro remote.

  Params:
  - `url` - The url to handle websocket traffic on. (ex. \"/chsk\")
  - `push-handler` - A function (fn [{:keys [topic msg]}] ...) that can handle a push message.
                     The topic is the server push verb, and the message will be the EDN sent.
  - `host` (Optional) - server that is hosting the websocket server
  - `global-error-callback` (Optional) - Analagous to the global error callback in fulcro client.
  - `req-params` (Optional) - Params to be attached to the initial request.
  - `state-callback` (Optional) - Callback that runs when the websocket state of the websocket changes.
      The function takes an old state parameter and a new state parameter (arity 2 function).
      `state-callback` can be either a function, or an atom containing a function.
  - `transit-handlers` (Optional) - Expects a map with `:read` and/or `:write` key containing a map of transit handlers,
  "
  [url & {:keys [global-error-callback push-handler host req-params state-callback transit-handlers]}]
  (map->Websockets {:channel-socket        (atom nil)
                    :url                   url
                    :push-handler          push-handler
                    :host                  host
                    :state-callback        state-callback
                    :global-error-callback global-error-callback
                    :transit-handlers      transit-handlers
                    :app                   (atom nil)
                    :stop                  (atom nil)
                    :req-params            req-params}))
