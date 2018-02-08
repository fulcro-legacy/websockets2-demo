(ns wsfix.ui.root
  (:require
    [fulcro.client.mutations :as m]
    [fulcro.client.data-fetch :as df]
    translations.es                                         ; preload translations by requiring their namespace. See Makefile for extraction/generation
    [fulcro.client.dom :as dom]
    [wsfix.api.mutations :as api]
    [fulcro.client.primitives :as prim :refer [defsc]]
    [fulcro.i18n :refer [tr trf]]))

(defsc Status [this {:keys [:status] :as props}]
  {:query [:status]
   :ident [:status/by-id :status]}
  (dom/div nil (str "Status " status)))

(m/defmutation op/long-running-thing [_]
  (action [{:keys [state]}]
    (swap! state update :a inc))
  (remote [{:keys [ast state]}]
    (-> ast
      (m/returning @state Status)
      (m/with-target [:status]))))

(m/defmutation op/die! [_]
  (remote [env] true))

(def ui-status (prim/factory Status))

(defsc Root [this {:keys [a ui/network-connected? status]}]
  {:query         [:a :ui/network-connected? {:status (prim/get-query Status)}]
   :initial-state {:a 1}}
  (dom/div nil
    (dom/div nil (if network-connected? "Online" "Offline"))
    (dom/button #js {:onClick (fn []
                                (prim/transact! this `[(op/die!)]))} "Die!!!")
    (dom/button #js {:onClick (fn []
                                (prim/transact! this `[(op/long-running-thing {:n ~a})]))} "Bam!!!")
    (when status
      (ui-status status))))
