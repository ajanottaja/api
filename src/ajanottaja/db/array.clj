(ns ajanottaja.db.array
  "PgArray support"
  (:require [ajanottaja.db.pg-object :refer [pgobject->clj]]
            [next.jdbc
             [result-set :as rs]
             [prepare :as p]]
            [tick.core :as t])
  (:import [java.sql PreparedStatement Array]
           [org.postgresql.jdbc PgArray]))


(defn array->seq
  "Convert a Postgres array into a clojure seq"
  [^Array a]
  (->> (.getArray a)
       (filter (comp not nil?))
       ;; Optimize by getting subtype and then mapping with type specific function?
       (map pgobject->clj)))

(extend-protocol rs/ReadableColumn
  Array
  (read-column-by-label [^Array v _] (array->seq v))
  (read-column-by-index [^Array v _ _] (array->seq v)))