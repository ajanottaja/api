(ns ajanottaja.domain.statistics
  (:require [cambium.core :as log]
            [honey.sql :as hsql]
            [honey.sql.helpers :as hsqlh]
            [malli.util :as mu]
            [tick.alpha.api :as t]
            [ajanottaja.db :refer [try-insert! query!]]
            [ajanottaja.server.interceptors :as interceptors]
            [ajanottaja.failjure :as f]
            [ajanottaja.schemas.responses :as res-schemas]
            [ajanottaja.schemas.time :as schemas]))


;; Some helper functions to simplify the queries
(defn namespaced-col
  [table col]
  (keyword (str (name table) "." (name col))))

;; Group dates by day, week, or month
(defn date->day [expr] [:to_char [:cast expr :timestamptz] [:inline "yyyy-mm-dd"]])
(defn date->week [expr] [:to_char [:cast expr :timestamptz] [:inline "yyyy-IW"]])
(defn date->month [expr] [:to_char [:cast expr :timestamptz] [:inline "yyyy-mm"]])


(defn target-for
  [{:keys [account date date-fn] :or {date (t/today) date-fn identity}}]
  {:select [[(date-fn :targets.date) :when]
            [[:sum :targets.duration] :target]]
   :from [:targets]
   :where [:and
           [:= (date-fn :targets.date) (date-fn date)]
           [:= :targets.account account]]
   :group-by [:targets.account
              (date-fn :targets.date)]})


(def summed-intervals
  [:sum
   [:-
    [:coalesce [:upper :intervals.interval] [:now]]
    [:lower :intervals.interval]]])

(defn tracked-diff
  [{:keys [unit table date-fn date account] :or {date (t/today) date-fn identity}}]
  {:select [[[:inline unit] :unit]
            [(namespaced-col table :target) :target]
            [summed-intervals :tracked]
            [[:- summed-intervals (namespaced-col table :target)] :diff]]
   :from [:intervals table]
   :where [:and 
           [:= (date-fn [:date [:lower :intervals.interval]]) (date-fn date)]
           [:= :intervals.account account]]
   :group-by [(namespaced-col table :target)]})

(defn summarised-stats
  [args]
  {:with [[:target-day (target-for (assoc args :date-fn date->day))]
          [:target-week (target-for (assoc args :date-fn date->week))]
          [:target-month (target-for (assoc args :date-fn date->month))]]
   :union-all [(tracked-diff (merge args {:unit "day" :date-fn date->day :table :target-day}))
               (tracked-diff (merge args {:unit "week" :date-fn date->week :table :target-week}))
               (tracked-diff (merge args {:unit "month" :date-fn date->month :table :target-month}))]})



(defn summarised-stats!
  "Given a derefable datasource and a map of date and account id
   returns stats for period of day, week, and month for the given date.
   Returns the tracked time intervals diffed with the sum of target durations."
  [ds m]
  (->> (summarised-stats m)
       hsql/format
       (query! ds)))


(defn routes
  [server-config]
  ["/statistics"
   {:tags [:statistics]
    :description "Endpoints for fetching stats about your logged intervals and targets."
    :interceptors [(interceptors/validate-bearer-token (:jwks-url server-config))
                   (interceptors/transform-claims)]
    :swagger {:security [{:bearer []}]}}
   ["/summary"
    {:get {:name :statistics-summary
           :description "Returns day, week, and month summaries for tracked intervals vs targets"
           :parameters {}
           :responses {200 {:body [:sequential [:map
                                                [:unit [:enum "day" "week" "month"]]
                                                [:target :duration]
                                                [:tracked :duration]
                                                [:diff :duration]]]}}
           :handler (fn [req]
                      (log/info "Fetch summary")
                      (f/if-let-ok? [summary (summarised-stats! (-> req :state :datasource)
                                                                {:account (-> req :claims :sub)})]
                                    {:status 200
                                     :body summary}))}}]])

(comment
  ;; Query
  (-> {:account #uuid "c3ce6f30-4b13-4252-8799-80783f3d3546"
       :date-fn date->day
       :date (t/today)}
      target-for
      (hsql/format {:pretty true})
      println)

  (-> {:account #uuid "c3ce6f30-4b13-4252-8799-80783f3d3546"
       :date (t/today)}
      summarised-stats
      (hsql/format {:pretty true})
      println)

  (as->
   {:account #uuid "c3ce6f30-4b13-4252-8799-80783f3d3546"
    :date (t/today)} q
    (summarised-stats q)
    (hsql/format q)
    (query! ajanottaja.db/datasource q))

  (as-> {:account #uuid "c3ce6f30-4b13-4252-8799-80783f3d3546"} q
    (interval-target-diff q {:date-agg (fn [val] [:to_char [:cast val :timestamptz] [:inline "IYYY-IW"]])})
    (hsql/format q {:pretty true})
    (#(do (tap> q) q))
    (query! ajanottaja.db/datasource q)))

