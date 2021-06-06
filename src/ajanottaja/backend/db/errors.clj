(ns ajanottaja.backend.db.errors
  (:require [ajanottaja.shared.failjure :as f])
  (:import [java.sql SQLException]))

(def default-error
  "Generic error map for unspecified sql/postgres errors"
  {:error :unrecognized
   :message "Unrecognized postgresql error"})

(def errors
  {:23505 {:error :duplicate-key
           :message "Violation of unique constraint"}})

(defn ->error
  "Given SQLException returns error map"
  [^SQLException e]
  (->> e
       .getSQLState
       keyword
       (#(get errors % default-error))))