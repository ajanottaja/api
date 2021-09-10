(ns ajanottaja.db.json
  (:require [ajanottaja.db.pg-object :refer [pgobject->clj]]
            [camel-snake-kebab.core :as csk]
            [jsonista.core :as json]))

;; Create a json mapper with memoized key decode function for better perf
(def mapper (json/object-mapper {:decode-key-fn (memoize csk/->kebab-case-keyword)}))
(def ->json json/write-value-as-string)
(def <-json #(json/read-value % mapper))

(defmethod pgobject->clj :jsonb
  [^org.postgresql.util.PGobject x]
  (some->> x
           .getValue
           <-json))

(defmethod pgobject->clj :jsonb
  [^org.postgresql.util.PGobject x]
  (some->> x
           .getValue
           <-json))
