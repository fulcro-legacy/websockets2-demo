(ns wsfix.client
  (:require [fulcro.client :as fc]
            [wsfix.networking :as wn]
            [wsfix.ui.root :as root]
            [fulcro.client.mutations :refer [defmutation]]
            [fulcro.client.primitives :as prim]))

(defonce ws-net (atom nil))
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

(defn ^:export init []
  (reset! ws-net (wn/make-channel-client "/chsk"
                   :state-callback state-callback
                   :global-error-callback (fn [& args]
                                            (apply println "Network error " args))))
  (reset! app (fc/new-fulcro-client
                :networking {:remote @ws-net}
                :started-callback (fn [app]
                                    (wn/install-push-handlers @ws-net app))))
  (start))
