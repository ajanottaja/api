(ns ajanottaja.backend.db.interval
  "PGInterval support based on java.time.Duration and tick."
  (:require [next.jdbc
             [result-set :as rs]
             [prepare :as p]]
            [tick.alpha.api :as t])
  (:import [java.sql PreparedStatement]
           [org.postgresql.util PGInterval]))


(defn duration->pg-interval
  "Takes a Dudration instance and converts it into a PGInterval
   instance where the interval is created as a number of seconds."
  [^java.time.Duration duration]
  (doto (PGInterval.)
    (.setSeconds (.getSeconds duration))))

(defn pg-interval->duration
  "Takes a PGInterval instance and converts it into a Duration
   instance. Ignore sub-second units."
  [^org.postgresql.util.PGInterval interval]
  (println interval)
  (-> (t/new-duration 0 :seconds)
      (.plusSeconds (.getSeconds interval))
      (.plusMinutes (.getMinutes interval))
      (.plusHours (.getHours interval))
      (.plusDays (.getDays interval))))


(extend-protocol p/SettableParameter
  ;; Convert durations to PGIntervals before inserting into db
  java.time.Duration
  (set-parameter [^java.time.Duration v ^PreparedStatement statement ^long index]
    (.setObject statement index (duration->pg-interval v))))

(extend-protocol rs/ReadableColumn
  ;; Convert PGIntervals back to durations
  org.postgresql.util.PGInterval
  (read-column-by-label [^org.postgresql.util.PGInterval v _]
    (pg-interval->duration v))
  (read-column-by-index [^org.postgresql.util.PGInterval v _2 _3]
    (pg-interval->duration v)))