(ns ajanottaja.domain.calendar
  (:require [cambium.core :as log]
            [honey.sql :as hsql]
            [honey.sql.helpers :as hsqlh]
            [malli.util :as mu]
            [tick.alpha.api :as t]
            [ajanottaja.db :refer [try-insert! query!]]
            [ajanottaja.server.interceptors :as interceptors]
            [ajanottaja.failjure :as f]
            [ajanottaja.schemas :as schemas]
            [ajanottaja.schemas.responses :as res-schemas]
            [ajanottaja.schemas.time :as time-schemas]))

(defn calendar-summary
  "Get target and list of intervals per day for given period"
  [{:keys [account date step] :or {step :month}}]
  {:with [[:dates {:select [[:t.date :date]]
                   :from [[[:generate_series
                            [:cast date :timestamptz]
                            [:-
                             [:+
                              [:cast date :timestamptz]
                              [:inline (step {:day "1 day"
                                                  :week "1 week"
                                                  :month "1 month"
                                                  :year "1 year"})]]
                             [:raw "'1 day'::interval"]]
                            [:raw "interval '1 day'"]]
                           [[:raw "t(date)"]]]]}]
          [:targs {:select [:*]
                   :from [:targets]
                   :where [:= :targets.account account]}]
          [:inters {:select [:*]
                    :from [:intervals]
                    :where [:= :intervals.account account]}]]
   :select [[[:cast :dates.date :date]]
            [:targs.id :target-id]
            [:targs.duration :target-duration]
            [:inters.id :interval-id]
            [:inters.interval :interval]]
   :from [:dates]
   :left-join [:targs [:= :dates.date :targs.date]
               :inters [:= :dates.date [:date [:lower :inters.interval]]]]
   #_#_:group-by [:dates.date]
   :order-by [:dates.date]})


(defn calendar-date
  "Group up rows of a single date into a date map with
   a target and list of intervals."
  [vals]
  (-> (first vals)
      (select-keys [:date])
      (assoc :target (when (:target-id (first vals))
                       {:id (:target-id (first vals))
                        :duration (:target-duration (first vals))}))
      (assoc :intervals (->> vals
                             (filter :interval-id)
                             (map (fn [v] {:id (:interval-id v)
                                           :interval (:interval v)}))))))

(defn calendar-summary!
  [ds m]
  (->> (calendar-summary m)
       hsql/format
       (query! ds)
       (partition-by :date)
       (map calendar-date)))



(comment
  (require '[tick.alpha.api :as t])
  (-> {:account  #uuid "c3ce6f30-4b13-4252-8799-80783f3d3546"
       :date (t/today)}
      calendar-summary
      (hsql/format {:pretty true})
      first
      println)

  (calendar-summary!
   ajanottaja.db/datasource
   {:account  #uuid "c3ce6f30-4b13-4252-8799-80783f3d3546"
    :date (t/new-date 2021 9 1)})
  
  {:vlaaad.reveal/command '(clear-output)}
)

(defn routes
  "Defines all the routes for calendar functionality"
  [server-config]
  ["/calendar"
   {:tags [:calendar]
    :description "Endpoints for browsing logged intervals and targets in a calendar"
    :interceptors [(interceptors/validate-bearer-token (:jwks-url server-config))
                   (interceptors/transform-claims)]
    :swagger {:security [{:bearer []}]}}
   [""
    {:get {:name :calendar
           :description "Returns list of targets and intervals for a range of dates"
           :parameters {:query [:map
                                [:step [:enum "day" "week" "month" "year"]]
                                [:date :date]]}
           :responses {200 {:body [:sequential
                                   [:map
                                    [:date :date]
                                    [:target [:maybe [:map
                                                      [:id :uuid]
                                                      [:duration :duration]]]]
                                    [:intervals [:sequential [:map
                                                              [:id :uuid]
                                                              [:interval schemas/interval?]]]]]]}}
           :handler (fn [req]
                      (log/info "Fetch intervals")
                      (f/if-let-ok? [dates (calendar-summary! (-> req :state :datasource)
                                                              (merge {:account (-> req :claims :sub)}
                                                                     (-> req :parameters :query
                                                                         (update :step keyword))))]
                                    {:status 200
                                     :body dates}))}}]])


;; Rich comments
(comment


  (->> {:select [:*]
        :from [:work-intervals]}
       hsql/format
       (query! ajanottaja.db/datasource))

  ;; Test sql generation for workday upserts
  (hsql/format (target-upsert {:work-date (t/today)
                               :work-interval (t/new-duration 7 :hours)
                               :account-id  #uuid "5d90856c-5a48-46ae-8f6e-a06295ea0dfb"}))

  ;; Test sql generation for interval creation
  (hsql/format (create-interval {:date (t/today)
                                 :work-interval (t/new-duration 7 :hours)
                                 :account #uuid "5d90856c-5a48-46ae-8f6e-a06295ea0dfb"
                                 :interval (t/now)})))