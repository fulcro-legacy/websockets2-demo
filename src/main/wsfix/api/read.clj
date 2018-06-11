(ns wsfix.api.read
  (:require
    [fulcro.server :refer [defquery-entity defquery-root]]
    [taoensso.timbre :as timbre]))

;; Server queries can go here
(defquery-root :SOMETHING
  (value [env params]
    {:x 1}))
