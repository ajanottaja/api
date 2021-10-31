(ns ajanottaja.domain.calendar
  (:require [cambium.core :as log]
            [honey.sql :as hsql]
            [tick.core :as t]
            [ajanottaja.db :refer [query-many!]]
            [ajanottaja.server.interceptors :as interceptors]
            [ajanottaja.failjure :as f]
            [ajanottaja.schemas :as schemas]))

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
            [:targs.date :target-date]
            [:inters.id :interval-id]
            [:inters.interval :interval]]
   :from [:dates]
   :left-join [:targs [:= :dates.date :targs.date]
               :inters [:= :dates.date [:date [:lower :inters.interval]]]]
   :order-by [:dates.date]})


(defn calendar-date
  "Group up rows of a single date into a date map with
   a target and list of intervals."
  [vals]
  (-> (first vals)
      (select-keys [:date])
      (assoc :target (when (:target-id (first vals))
                       {:id (:target-id (first vals))
                        :duration (:target-duration (first vals))
                        :date (:target-date (first vals))}))
      (assoc :intervals (->> vals
                             (filter :interval-id)
                             (map (fn [v] {:id (:interval-id v)
                                           :interval (:interval v)}))))))

(defn calendar-summary!
  [ds m]
  (->> (query-many! calendar-summary ds m)
       (partition-by :date)
       (map calendar-date)))


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
                                                      [:duration :duration]
                                                      [:date :date]]]]
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
  (require '[tick.core :as t])
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

  {:vlaaad.reveal/command '(clear-output)})