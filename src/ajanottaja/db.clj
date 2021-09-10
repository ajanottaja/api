(ns ajanottaja.db
  (:require [ajanottaja.db.range]
            [ajanottaja.db.interval]
            [ajanottaja.db.date]
            [ajanottaja.db.array]
            [ajanottaja.db.json]
            [ajanottaja.db.pg-object :as pg-object]
            [ajanottaja.db.errors :as db-errors]
            [ajanottaja.config :as config]
            [ajanottaja.failjure :as f]
            [cambium.core :as log]
            [mount.lite :refer (defstate) :as mount]
            [next.jdbc :as jdbc]
            [next.jdbc.sql :as sql]
            [next.jdbc.connection :as connection]
            [next.jdbc.result-set :as rs]
            [next.jdbc.prepare :as p]
            [next.jdbc.date-time :refer [read-as-local read-as-instant]]
            [next.jdbc.types :as types]
            [hikari-cp.core :as hk]
            [honey.sql :as hsql]
            [honey.sql.helpers :as helpers]
            [camel-snake-kebab.core :as csk]
            [tick.core :as tick])
  (:import [org.postgresql.util PGInterval PGobject]
           [java.sql PreparedStatement SQLException]
           [java.time Duration]))


;; Call this so all dates are read as local dates
(hsql/format-expr (tick.core/today))

(defn- contains-formatter [_f args]
  (let [[sql & params] (hsql/format-expr-list args)]
    (into [(str (first sql) " @> " (second sql))]
          (flatten params))))

(hsql/register-fn! :at> contains-formatter)

(hsql/register-op! (keyword "@>"))

(def jdbc-opts
  {:builder-fn rs/as-unqualified-kebab-maps
   :column-fn csk/->Camel_Snake_Case_String
   :table-fn csk/->Camel_Snake_Case_String})

(defn try-insert!
  "Insert some record into "
  [datasource table data]
  (log/debug {:table table} "Inserting data into database")
  (try (sql/insert! @datasource table data jdbc-opts)
       (catch SQLException e (f/fail {:error (ex-message e)
                                      :code (.getSQLState e)
                                      :stacktrace (.getStackTrace e)}))
       (catch Exception e (f/fail {:error (ex-message e)
                                   :stacktrace (.getStackTrace e)}))))


(defn query!
  "Run a query with default parameters"
  [datasource query]
  (log/debug {:query query} "Run database query")
  (try (sql/query @datasource query jdbc-opts)
       (catch SQLException e (tap> e) (f/fail "Failed to run sql query"
                                     (db-errors/->error e)))
       (catch Exception e (tap> e) (f/fail "Failed to run sql query"
                                  db-errors/default-error))))

  
  (comment
    (f/if-let-ok? [test (f/try* (throw (SQLException. "Testing")))]
                  (println "ok")
                  (.getMessage test)))

(defn dbspec
  "Create a dbspec options map based on provided config. Expects :db-user, :db-password,
  :db-name, :db-host, and :db-port to be provided."
  [db-config]
  {:adapter "postgresql"
   :username (:user db-config)
   :password (:password db-config)
   :database-name   (:name db-config)
   :server-name     (:host db-config)
   :port-number     (:port db-config)})

(defn start-datasource!
  [config]
  (log/info "Starting Hikari database connection pool")
  (let [ds (hk/make-datasource (dbspec (:db config)))]
    ds))

(defn stop-datasource!
  [datasource]
  (log/info "Closing Hikari database connection pool")
  (.close datasource))

#_:clj-kondo/ignore
(defstate datasource
  :start (start-datasource! @config/config)
  :stop (stop-datasource! @datasource))

(comment
  ;; Duration to PGInterval conversion
  (duration->pg-interval (tick.core/new-duration 8 :hours))
  (pg-interval->duration (duration->pg-interval (tick.core/new-duration 8 :hours)))

  ;; Starting just the datasource
  (mount/start #'datasource)

  ;; Inserting an account record into the database
  (try-insert! @datasource :accounts {:auth0_id "foo bar baz"})

  (insert! @datasource :work_interval {:workdate "foo"})

  (require '[tick.alpha.api :as t])

  (type (t/new-period 100 :days))

  )