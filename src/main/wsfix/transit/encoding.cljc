(ns wsfix.transit.encoding
  #?(:clj
     (:refer-clojure :exclude [ref]))
  (:require [cognitect.transit :as t]
    #?(:cljs [com.cognitect.transit :as ct])
            [wsfix.transit.othertempid :as othertempid #?@(:cljs [:refer [OtherTempId]])])
  #?(:clj
     (:import [com.cognitect.transit
               TransitFactory WriteHandler ReadHandler]
              [wsfix.transit.othertempid OtherTempId])))

#?(:cljs
   (deftype OtherTempIdHandler []
     Object
     (tag [_ _] "wsfix/othertempid")
     (rep [_ r] (. r -id))
     (stringRep [_ _] nil)))

#?(:clj
   (deftype OtherTempIdHandler []
     WriteHandler
     (tag [_ _] "wsfix/othertempid")
     (rep [_ r] (. ^OtherTempId r -id))
     (stringRep [_ r] (. ^OtherTempId r -id))
     (getVerboseHandler [_] nil)))

#?(:cljs
   (defn writer
     ([]
      (writer {}))
     ([opts]
      (t/writer :json
        (assoc-in opts [:handlers OtherTempId] (OtherTempIdHandler.))))))

#?(:clj
   (defn writer
     ([out]
      (writer out {}))
     ([out opts]
      (t/writer out :json
        (assoc-in opts [:handlers OtherTempId] (OtherTempIdHandler.))))))

#?(:cljs
   (defn reader
     ([]
      (reader {}))
     ([opts]
      (t/reader :json
        (assoc-in opts
          [:handlers "wsfix/othertempid"]
          (fn [id] (othertempid/othertempid id)))))))

#?(:clj
   (defn reader
     ([in]
      (reader in {}))
     ([in opts]
      (t/reader in :json
        (assoc-in opts
          [:handlers "wsfix/othertempid"]
          (reify
            ReadHandler
            (fromRep [_ id] (OtherTempId. id))))))))

(defn serializable?
  "Checks to see that the value in question can be serialized by the default wsfix writer."
  [v]
  #?(:clj  (try
             (.write (writer (java.io.ByteArrayOutputStream.)) v)
             true
             (catch Exception e false))
     :cljs (try
             (.write (writer) v)
             true
             (catch :default e false))))

(comment
  ;; cljs
  (t/read (reader) (t/write (writer) (othertempid/othertempid)))

  ;; clj
  (import '[java.io ByteArrayOutputStream ByteArrayInputStream])

  (def baos (ByteArrayOutputStream. 4096))
  (def w (writer baos))
  (t/write w (OtherTempId. (java.util.UUID/randomUUID)))
  (.toString baos)

  (def in (ByteArrayInputStream. (.toByteArray baos)))
  (def r (reader in))
  (t/read r)
  )
