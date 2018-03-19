(ns wsfix.api.mutations
  (:require
    [taoensso.timbre :as timbre]
    [fulcro.server :refer [defmutation]]
    [wsfix.transit.othertempid :as othertempid])
  (:import [wsfix.transit.othertempid OtherTempId]))

(defn tempid-check! [othertempid]
  (when-not (= OtherTempId (type othertempid))
    (throw (ex-info "othertempid type not encoded correctly"
                    {:othertempid othertempid}))))

(defmutation op/die! [{:keys [othertempid]}]
  (action [env]
    (tempid-check! othertempid)
    (throw (ex-info "Dead!" {:reason "You suck!"}))))

(defmutation op/long-running-thing [{:keys [n othertempid]}]
  (action [env]
    (tempid-check! othertempid)
    (println "Running for a long time...." n)
    (Thread/sleep 2000)
    (println "Done Running")
    {:status n}))

