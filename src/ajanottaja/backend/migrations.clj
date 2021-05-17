(ns ajanottaja.backend.migrations
  (:require [migratus.core :as migratus]))


(defn config
  [datasource]
  {:store :database
   :migration-dir "migrations/"
   :parent-migration-dir "resources/clj"
   :db {:datasource datasource}})

(defn migrate!
  [datasource]
  (migratus/migrate (config datasource)))

(defn rollback!
  [datasource]
  (migratus/rollback (config datasource)))

(comment
  (migratus/create (config nil) "setup")
  (migratus/create (config nil) "accounts")
  (migratus/create (config nil) "workinterval")
  (migratus/create (config nil) "workday")
  (migrate! @ajanottaja.db/datasource)
  (rollback! @ajanottaja.db/datasource))

