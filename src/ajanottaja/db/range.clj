(ns ajanottaja.db.range
  "Range support inspired by the nice work of dcj (Don Jackson) on Clojurians slack.
   https://clojurians.slack.com/archives/C1Q164V29/p1620584219175100
   https://github.com/dcj/coerce/blob/develop/src/coerce/jdbc/pg.clj
   "
  (:require [ajanottaja.db.pg-object :refer [pgobject->clj map->pgobject]]
            [cambium.core :as log]
            [clojure.string :as string]
            [next.jdbc.prepare :as p]
            [tick.alpha.api :as t])
  (:import [clojure.lang PersistentArrayMap]
           [java.sql PreparedStatement]
           [java.time Instant]
           [org.postgresql.util PGobject]))


(defn ^:private parse-range
  [^String s]
  (let [[start end] (-> (subs s 1 (dec (count s)))
                        (string/replace #"\"" "")
                        (string/split #","))]
    [(str (first s))
     start
     end
     (str (last s))]))

(defn ^:private pgobject->interval
  "Converts Postgres range string into tstzrange or tsrange depending
   on which date parse function f is passed as argument. Attaches :type
   :interval metadata to result for consistency with input type."
  [f s]
  (let [[_ start end _] (parse-range s)]
    (-> (zipmap [:beginning :end]
                (map #(some-> % (string/replace #" " "T") f)
                     [start end]))
        (with-meta {:type :interval}))))


;; Convert interval edn map to pg parameter
(defmethod map->pgobject :interval
  [^PersistentArrayMap v]
  (tap> v)
  (str "["
       (if (= (:beginning v) Instant/MIN) "" (:beginning v))
       ","
       (if (= (:end v) Instant/MAX) "" (:end v)) ")"))

;; Read PG tstzrange and tsrange
(defmethod pgobject->clj :tstzrange
  [^org.postgresql.util.PGobject x]
  (tap> x)
  (some->> x
           .getValue
           (pgobject->interval (comp t/instant t/zoned-date-time))))


(defmethod pgobject->clj :tsrange
  [^org.postgresql.util.PGobject x]
  (some->> x
           .getValue
           (pgobject->interval (comp t/instant t/date-time))))


(comment
  ;; Working with threeten extra intervals
  (.getStart (Interval/ALL))

  ;; Constructing dates
  (t/zoned-date-time "2014-01-01T00:00:00+01")

  ;; Parsing Postgres tstzrange values
  (parse-range "[\"2014-01-01 00:00:00+01\",\"2015-01-01 00:00:00+01\"]")
  (parse-range "[\"2021-05-14 12:36:42.740282+02\",)")

  (pgobject->interval (comp t/instant t/zoned-date-time) "[\"2014-01-01 00:00:00+01\",\"2015-01-01 00:00:00+01\"]")
  (pgobject->interval (comp t/instant t/zoned-date-time) "[\"2021-05-14 12:36:42.740282+02\",)"))