(ns wsfix.client
  (:require [fulcro.client :as fc]
            [fulcro.websockets.networking :refer [push-received]]
            [fulcro.websockets :as fw]
            wsfix.api.mutations
            [wsfix.transit.othertempid :as ot :refer [OtherTempId]]
            [wsfix.ui.root :as root]
            [fulcro.logging :as log]
            [wsfix.transit.handlers :as custom-handlers]
            [fulcro.client.mutations :refer [defmutation]]
            [fulcro.client.primitives :as prim]))

(defonce app (atom nil))

(defn mount []
  (reset! app (fc/mount @app root/Root "app")))

(defn start []
  (mount))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Hook up logic for dealing with server push and network state changes (e.g. offline?)
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defmutation set-network-status
  "Mutation: Set the network status indicator in app state to up? (true/false)"
  [{:keys [up?]}]
  (action [{:keys [state]}]
    (swap! state assoc :ui/network-connected? up?)))

(defn state-callback
  "Function given to websocket network to watch the network connection status. Calls
  transact to copy that state into the client app state. "
  [_ {:keys [open?] :as new-state}]
  (when-let [reconciler (some-> app deref :reconciler)]
    (prim/transact! reconciler `[(set-network-status {:up? ~open?})])))

; hook new support into legacy push received multimethod.
(defn push-handler
  "Callback for websocket push messages. For this sample, we just hook it into
  the legacy support, which is what you might do if you were porting...otherwise,
  use whatever you want to handle the messages."
  [msg] (push-received @app msg))

(defmutation time-changed
  "A top-level mutation meant to update time. Used from push received for the broadcast server time."
  [{:keys [time]}]
  (action [{:keys [state]}]
    (swap! state assoc :ui/current-time time)))

; The signature of the legacy push received is to receive the app and a topic/msg map.
(defmethod push-received :time-change [{:keys [reconciler]} {:keys [msg]}]
  (prim/transact! reconciler `[(time-changed ~msg)]))

; Verify our custom transit-handlers are working correctly.
(defmethod push-received :othertempid-ping [env {:keys [msg]}]
  (when-not (= OtherTempId (type (:othertempid msg)))
    (log/error "othertempid type not encoded correctly" (:othertempid msg))))

(defn ^:export init []
  (reset! app (fc/new-fulcro-client
                ; replace the default remote with websockets
               :networking {:remote (fw/make-websocket-networking
                                     "/socket"
                                     :req-params {:trustworthy true}
                                     :transit-handlers {:read  custom-handlers/read
                                                        :write custom-handlers/write}
                                     :auto-retry? true
                                     :push-handler push-handler
                                     :state-callback state-callback
                                     :global-error-callback (fn [& args]
                                                              (apply println "Network error " args)))}))
  (start))
