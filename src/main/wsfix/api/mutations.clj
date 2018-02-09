(ns wsfix.api.mutations
  (:require
    [taoensso.timbre :as timbre]
    [fulcro.server :refer [defmutation]]))

(defmutation op/die! [_]
  (action [env]
    (throw (ex-info "Dead!" {:reason "You suck!"}))))

(defmutation op/long-running-thing [{:keys [n]}]
  (action [env]
    (println "Running for a long time...." n)
    (Thread/sleep 5000)
    (println "Done Running")
    {:status n}))

