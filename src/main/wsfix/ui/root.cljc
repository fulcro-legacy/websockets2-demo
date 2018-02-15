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

(defsc Root [this {:keys [a ui/network-connected? status ui/current-time]}]
  {:query         [:a :ui/network-connected? :ui/current-time {:status (prim/get-query Status)}]
   :initial-state {:a 1}}
  (dom/div nil
    (dom/div nil (if network-connected? "Online" "Offline"))
    (dom/p nil (str current-time))
    (dom/button #js {:onClick (fn []
                                (prim/transact! this `[(op/die! {:othertempid ~(ot/othertempid)})]))}
      "Die!!!")
    (dom/button #js {:onClick (fn []
                                (prim/transact! this `[(op/long-running-thing {:n ~a
                                                                               :othertempid ~(ot/othertempid)})]))}
      "Bam!!!")
    (when status
      (ui-status status))))
