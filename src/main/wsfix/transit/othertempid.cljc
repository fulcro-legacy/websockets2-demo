(ns wsfix.transit.othertempid
  #?(:clj (:import [java.io Writer])))

;; =============================================================================
;; ClojureScript

#?(:cljs
   (deftype OtherTempId [^:mutable id ^:mutable __hash]
     Object
     (toString [this]
       (pr-str this))
     IEquiv
     (-equiv [this other]
       (and (instance? OtherTempId other)
         (= (. this -id) (. other -id))))
     IHash
     (-hash [this]
       (when (nil? __hash)
         (set! __hash (hash id)))
       __hash)
     IPrintWithWriter
     (-pr-writer [_ writer _]
       (write-all writer "#wsfix/othertempid[\"" id "\"]"))))

#?(:cljs
   (defn othertempid
     ([]
      (othertempid (random-uuid)))
     ([id]
      (OtherTempId. id nil))))

;; =============================================================================
;; Clojure

#?(:clj
   (defrecord OtherTempId [id]
     Object
     (toString [this]
       (pr-str this))))

#?(:clj
   (defmethod print-method OtherTempId [^OtherTempId x ^Writer writer]
     (.write writer (str "#wsfix/othertempid[\"" (.id x) "\"]"))))

#?(:clj
   (defn othertempid
     ([]
      (othertempid (java.util.UUID/randomUUID)))
     ([uuid]
      (OtherTempId. uuid))))

(defn othertempid?
  #?(:cljs {:tag boolean})
  [x]
  (instance? OtherTempId x))
