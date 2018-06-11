(ns wsfix.ui.root
  (:require
    [fulcro.client.data-fetch :as df]
    [fulcro.client.dom :as dom]
    [wsfix.transit.othertempid :as ot]
    [fulcro.client.primitives :as prim :refer [defsc]]))

(defsc Status [this {:keys [:status] :as props}]
  {:query [:status]
   :ident [:status/by-id :status]}
  (dom/div nil (str "Status " status)))

(def ui-status (prim/factory Status))

(defsc Root [this {:keys [a ui/network-connected? status SOMETHING ui/current-time]}]
  {:query         [:a :ui/network-connected? :ui/current-time :SOMETHING {:status (prim/get-query Status)}]
   :initial-state {:a 1}}
  (dom/div
    (dom/div (if network-connected? "Online" "Offline"))
    (dom/p (str SOMETHING))
    (dom/p (str current-time))
    (dom/button {:onClick (fn []
                            (prim/transact! this `[(op/die! {:othertempid ~(ot/othertempid)})]))}
      "Run operation that will throw (will not be auto-retried)")
    (dom/button {:onClick (fn []
                            (prim/transact! this `[(op/long-running-thing {:n           ~a
                                                                           :othertempid ~(ot/othertempid)})]))}
      "Run long-running thing.")
    (when status
      (ui-status status))))
