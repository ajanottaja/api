(ns ajanottaja.db.pg-object
  (:require [next.jdbc.prepare :as p]
            [next.jdbc.result-set :as rs])
  (:import [java.sql PreparedStatement]
           [clojure.lang PersistentArrayMap]
           [org.postgresql.util PGobject]))

(defmulti pgobject->clj
  "Convert returned PGobject to Clojure value.
   Dispatches on the type of pg object to figure out which conversion method
   to use.
   Based on https://github.com/dcj/coerce/blob/develop/src/coerce/jdbc/pg.clj"
  #(keyword (when % (.getType ^org.postgresql.util.PGobject %))))


(extend-protocol rs/ReadableColumn
  ;; Convert Postgres objects to some clojure value.
  org.postgresql.util.PGobject
  (read-column-by-label [^org.postgresql.util.PGobject v _]
    (pgobject->clj v))
  (read-column-by-index [^org.postgresql.util.PGobject v _2 _3]
    (pgobject->clj v)))

(defmulti map->pgobject
  "Convert clj edn map values to postgres value. Dispatches on the metadata
   of the clojure object to figure out which method to use."
  #(-> % meta :type))

(extend-protocol p/SettableParameter
  ;; Convert Clojure edn maps to some postgres value
  PersistentArrayMap
  (set-parameter [^PersistentArrayMap v ^PreparedStatement ps ^long i]
    (let [meta      (.getParameterMetaData ps)
          type-name (.getParameterTypeName meta i)]
      (.setObject ps i (doto (PGobject.)
                         (.setType type-name)
                         (.setValue (map->pgobject v)))))))