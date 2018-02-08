(ns wsfix.client
  (:require [fulcro.client :as fc]
            [wsfix.networking :as wn]
            [fulcro.websockets.networking :as ws]
            [wsfix.ui.root :as root]
            [fulcro.client.mutations :refer [defmutation]]
            [fulcro.client.primitives :as prim]))

(defonce app (atom nil))

(defn mount []
  (reset! app (fc/mount @app root/Root "app")))

(defn start []
  (mount))

(defmutation set-network-status [{:keys [up?]}]
  (action [{:keys [state]}]
    (swap! state assoc :ui/network-connected? up?)))

(defn state-callback [_ {:keys [open?] :as new-state}]
  (when-let [reconciler (some-> app deref :reconciler)]
    (prim/transact! reconciler `[(set-network-status {:up? ~open?})])))

(defn push-handler [msg] (ws/push-received @app msg))

(defmutation time-changed [{:keys [time]}]
  (action [{:keys [state]}]
    (swap! state assoc :ui/current-time time)))

(defmethod ws/push-received :time-change [{:keys [reconciler]} {:keys [msg]}]
  (prim/transact! reconciler `[(time-changed ~msg)]))

(defn ^:export init []
  (reset! app (fc/new-fulcro-client
                :networking {:remote (wn/make-websocket-networking "/chsk"
                                       :push-handler push-handler
                                       :state-callback state-callback
                                       :global-error-callback (fn [& args]
                                                                (apply println "Network error " args)))}))
  (start))
