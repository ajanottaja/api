(ns ajanottaja.db.date
  "Date support based on java.time.Duration and tick."
  (:require [next.jdbc
             [result-set :as rs]]))

(extend-protocol rs/ReadableColumn
  java.sql.Date
  (read-column-by-label [^java.sql.Date v _]     (.toLocalDate v))
  (read-column-by-index [^java.sql.Date v _2 _3] (.toLocalDate v))
  java.sql.Timestamp
  (read-column-by-label [^java.sql.Timestamp v _]     (.toInstant v))
  (read-column-by-index [^java.sql.Timestamp v _2 _3] (.toInstant v)))
