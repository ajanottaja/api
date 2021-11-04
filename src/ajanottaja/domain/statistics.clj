(ns ajanottaja.domain.statistics
  (:require [cambium.core :as log]
            [honey.sql :as hsql]
            [honey.sql.helpers :as hsqlh]
            [malli.util :as mu]
            [tick.core :as t]
            [ajanottaja.db :refer [query-many!]]
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
          [:target-month (target-for (assoc args :date-fn date->month))]
          [:target-all (target-for (assoc args :date-fn (constantly "")))]]
   :union-all [(tracked-diff (merge args {:unit "day" :date-fn date->day :table :target-day}))
               (tracked-diff (merge args {:unit "week" :date-fn date->week :table :target-week}))
               (tracked-diff (merge args {:unit "month" :date-fn date->month :table :target-month}))
               (tracked-diff (merge args {:unit "all" :date-fn (constantly "") :table :target-all}))]})

(def summarised-stats! (partial query-many! summarised-stats))



(defn calendar-statistics
  "Return the daily difference between duration worked (intervals) and target durations.-
   Use an outer join to capture all dates with one or more intervals register and/or a target
   registered."
  [{:keys [account]}]
  (let [tracked [:sum
                 [:-
                  [:coalesce [:upper :intervals.interval] [:now]]
                  [:lower :intervals.interval]]]]
    {:select [[[:date [:lower :intervals.interval]] :date]
              [[:min :targets.duration] :target]
              [tracked :tracked]
              [[:- tracked [:min :targets.duration]] :diff]]
     :from [:targets]
     :full-join [[:intervals]
                 [:= :targets.date [:date [:lower :intervals.interval]]]]
     :where [:and
             [:= :targets.account account]
             [:= :intervals.account account]]
     :group-by [[:date [:lower :intervals.interval]]]}))

(def calendar-statistics! (partial query-many! calendar-statistics))

(def sum-tracked-interval
  "Select statement for getting sum duration of intervals tracked"
  [:sum
   [:-
    [:coalesce [:upper :intervals.interval] [:now]]
    [:lower :intervals.interval]]])


(defn cumulative-diff
  "Return the cumulative daily difference between tracked intervals and
   target durations. Use outer join to capture all dates with one or more
   intervals and/or target. Also returns the tracked diff and target for
   each date"
  [{:keys [account]}]
  {:with [[:tracked {:select [[[:date [:lower :intervals.interval]] :date]
                              [sum-tracked-interval :tracked]]
                     :from [:intervals]
                     :where [:= :intervals.account account]
                     :group-by [[:inline 1]]}]
          [:diff {:select [[[:coalesce :tracked.date :targets.date] :date]
                           [[:- :tracked.tracked :targets.duration] :diff]
                           [:targets.duration :target]
                           [:tracked.tracked :tracked]]
                  :from [:targets]
                  :full-join [[:tracked]
                              [:= :targets.date :tracked.date]]
                  :where [:= :targets.account account]}]]
   :select [:*
            [[:over
              [[:sum :diff.diff]
               {:order-by [[:diff.date "asc rows between unbounded preceding and current row"]]}]] :cumulative_diff]]
   :from [:diff]})


(def cumulative-diff! (partial query-many! cumulative-diff))


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
                                                [:unit [:enum "day" "week" "month" "all"]]
                                                [:target :duration]
                                                [:tracked :duration]
                                                [:diff :duration]]]}}
           :handler (fn [req]
                      (log/info "Fetch summary")
                      (f/if-let-ok? [summary (summarised-stats! (-> req :state :datasource)
                                                                {:account (-> req :claims :sub)})]
                                    {:status 200
                                     :body summary}))}}]
   ["/calendar"
    {:get {:name :statistics-calendar
           :description "Returns list of dates with summary of target duration, sum of interval durations, and their diff"
           :parameters {}
           :responses {200 {:body [:sequential [:map
                                                [:date :date]
                                                [:target :duration]
                                                [:tracked :duration]
                                                [:diff :duration]]]}}
           :handler (fn [req]
                      (log/info "Fetch summary")
                      (f/if-let-ok? [calendar (calendar-statistics! (-> req :state :datasource)
                                                                    {:account (-> req :claims :sub)})]
                                    {:status 200
                                     :body calendar}))}}]
   ["/cumulative"
    {:get {:name :statistics-cumulative
           :description "Returns list of dates with cumulative diff between tracked intervals and targets"
           :parameters {}
           :responses {200 {:body [:sequential [:map
                                                [:date :date]
                                                [:target :duration]
                                                [:tracked :duration]
                                                [:diff :duration]
                                                [:cumulative-diff :duration]]]}}
           :handler (fn [req]
                      (log/info "Fetch summary")
                      (f/if-let-ok? [calendar (cumulative-diff! (-> req :state :datasource)
                                                                {:account (-> req :claims :sub)})]
                                    {:status 200
                                     :body calendar}))}}]])

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

  ;; Generate calendar statistics query
  (->  {:account #uuid "c3ce6f30-4b13-4252-8799-80783f3d3546"}
       (calendar-statistics)
       (hsql/format  {:pretty true})
       (println))

  ;; Cumulative calendar diff
  (-> {:account #uuid "c3ce6f30-4b13-4252-8799-80783f3d3546"}
      cumulative-calendar-diff
      (hsql/format {:pretty true}))

  (query-many!
   cumulative-calendar-diff
   ajanottaja.db/datasource
   {:account #uuid "c3ce6f30-4b13-4252-8799-80783f3d3546"})

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
    (query! ajanottaja.db/datasource q))
  )

