(ns wsfix.transit.handlers
  (:refer-clojure :exclude [read])
  (:require [wsfix.transit.encoding :as ot]
            [taoensso.sente.packers.transit :as st]
            [wsfix.transit.othertempid :as othertempid #?@(:cljs [:refer [OtherTempId]])])
  #?(:clj (:import [com.cognitect.transit ReadHandler]
                   [wsfix.transit.othertempid OtherTempId])))

(def write {OtherTempId (ot/->OtherTempIdHandler)})
(def read {"wsfix/othertempid" #?(:clj (reify
                                         ReadHandler
                                         (fromRep [_ id] (OtherTempId. id)))
                                  :cljs (fn [id] (othertempid/othertempid id)))})
