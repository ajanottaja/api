(ns ajanottaja.migrations
  (:require [ajanottaja.db :as db]
            [cambium.core :as log]
            [migratus.core :as migratus]
            [mount.lite :refer [defstate] :as mount]))


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

#_:clj-kondo/ignore
(defstate migrations
  :start (migrate! @db/datasource)
  :stop  (log/info "Spinning down migrations"))


(comment
  (migratus/create (config nil) "setup")
  (migratus/create (config nil) "accounts")
  (migratus/create (config nil) "workinterval")
  (migratus/create (config nil) "workday")
  (migrate! @ajanottaja.db/datasource)
  (rollback! @ajanottaja.db/datasource))

