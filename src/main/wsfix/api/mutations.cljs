(ns wsfix.api.mutations
  (:require
    [fulcro.client.mutations :as m :refer [defmutation]]
    [wsfix.ui.root :refer [Status]]
    [fulcro.client.logging :as log]))

(defmutation op/long-running-thing [_]
  (action [{:keys [state]}]
    (swap! state update :a inc))
  (remote [{:keys [ast state]}]
    (-> ast
      (m/returning @state Status)
      (m/with-target [:status]))))

(defmutation op/die! [_]
  (remote [env] true))


