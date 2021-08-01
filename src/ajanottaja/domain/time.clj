(ns ajanottaja.domain.time
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

(defn workday-upsert
  "Takes a map with work-date, work-interval, and account-id and
   returns a honey-sql map for upserting a workday row, and return
   the id value for the row."
  [{:keys [work-date work-duration account-id]}]
  {:insert-into :workdays
   :values [{:work-date work-date
             :work-duration work-duration
             :account-id account-id}]
   :on-conflict [:work-date :account-id]
   :do-update-set [:work-duration]
   :returning [:id]})

(defn create-work-interval
  "Takes map of workday and work-interval attributes and
   returns a honey-sql map for upsering a workday row, and
   inserting a new corresponding interval row."
  [{:keys [interval] :as m}]
  (tap> interval)
  {:with [[:workdays-insert (workday-upsert m)]]
   :insert-into [[:work-intervals [:workday-id :interval]]
                 {:select [[:workdays-insert.id] [[[:lift interval]]]]
                  :from [:workdays-insert]}]
   :returning :*})

(defn stop-work-interval
  [{:keys [account-id]}]
  {:with [[:workday {:select [:id]
                     :from :workdays
                     :where [:= :account-id account-id]}]]
   :update :work-intervals
   :from :workday
   :set {:interval [:tstzrange [:lower :interval] (t/now) "[)"]}
   :where [:and
           [:= :workday.id :workday-id]
           [:= [:upper :interval] nil]]
   :returning :*})

(defn active-interval
  [{:keys [account-id]}]
  {:select [:*]
   :from [[:workdays :wd]]
   :join [[:work-intervals :wi] [:= :wi.workday_id :wd.id]]
   :where [:and
           [:= :wd.account-id account-id]
           [:= [:upper :wi.interval] nil]]})

(defn intervals
  [{:keys [work-date account-id]}]
  {:select [:*]
   :from [[:workdays :wd]]
   :join [[:work-intervals :wi] [:= :wi.workday_id :wd.id]]
   :where [:and
           [:= :wd.account-id account-id]
           [:= :wd.work-date work-date]]})


(defn insert-work-interval!
  "Given datasource wrapped in atom and map of workday and work interval values
   returns the started interval."
  [ds m]
  (log/info m "Insert work interval!")
  (tap> m)
  (try (->> (create-work-interval m)
            hsql/format
            (query! ds)
            first)
       (catch Exception e (tap> e) e)))


(defn stop-work-interval!
  "Given datasource wrapped in atom and map with account-id stops any active
   work interval and returns the interval, if any."
  [ds m]
  (log/info m "Stop work interval!")
  (tap> m)
  (try (->> (stop-work-interval m)
            hsql/format
            (query! ds)
            first)
       (catch Exception e (tap> e) e)))


(defn active-interval!
  "Given datasource wrapped in atom and a map with account-id
   returns any active work interval."
  [ds m]
  (->> (active-interval m)
       hsql/format
       (query! ds)
       first))

(defn intervals!
  [ds m]
  (->> (intervals m)
       hsql/format
       (query! ds)))

(comment
  (active-interval!
   ajanottaja.db/datasource
   {:account-id #uuid "c3ce6f30-4b13-4252-8799-80783f3d3546"}))


(defn routes
  "Defines all the routes for timing related stuff"
  [server-config]
  ["/time"
   {:tags [:time]
    :description "Endpoints for registeirng work days and intervals."
    :interceptors [(interceptors/validate-bearer-token (:jwks-url server-config))
                   (interceptors/transform-claims)]
    :swagger {:security [{:bearer []}]}}
   ["/intervals"
    {:get {:name :intervals
           :description "Returns list of intervals"
           :parameters {:query (mu/select-keys schemas/workday
                                               [:work-date])}
           :responses {200 {:body [:sequential schemas/work-interval]}}
           :handler (fn [req]
                      (log/info "Fetch intervals")
                      (f/if-let-ok? [intervals (intervals! (-> req :state :datasource)
                                                           {:account-id (-> req :claims :sub)
                                                            :work-date (-> req :parameters :query :work-date)})]
                                    {:status 200
                                     :body intervals}))}}]
   ["/active-interval"
    {:get {:name :active-work-interval
           :description "Return the active work interval if any exists."
           :responses {200 {:body schemas/work-interval}
                       404 {:body res-schemas/not-found-schema}}
           :handler (fn [req]
                      (log/info "Fetch any active interval")
                      (let [interval (active-interval! (-> req :state :datasource)
                                                       {:account-id (-> req :claims :sub)})]
                        (cond
                          (nil? interval) {:status 404
                                           :body {:status 404
                                                  :message "Not found"}}
                          (f/failed? interval) {:status 500
                                                :body {:status 500
                                                       :message "Failed to look up active interval"}}
                          :else {:status 200
                                 :body interval})))}}]
   ["/start-interval"
    {:post {:name :create-work-interval
            :description "Upsert workday with workday duration and adds new work interval for the provided workday."
            :parameters {:body (mu/select-keys schemas/workday
                                               [:work-duration])}
            :responses {200 {:body schemas/work-interval}}
            :handler (fn [req]
                       (log/info "Insert interval")
                       (f/if-let-ok? [interval (insert-work-interval! (-> req :state :datasource)
                                                                      (-> req :parameters :body
                                                                          (assoc :account-id (-> req :claims :sub)
                                                                                 :work-date (t/today)
                                                                                 :interval ^{:type :interval} {:beginning (t/now)})))]
                                     {:status 200
                                      :body interval}))}}]
   ["/stop-interval"
    {:post {:name :stop-work-interval
            :description "Stop any active work interval, returns latest interval"
            :parameters {}
            :responses {200 {:body schemas/work-interval}}
            :handler (fn [req]
                       (f/if-let-ok? [interval (stop-work-interval! (-> req :state :datasource)
                                                                    {:account-id (-> req :claims :sub)})]
                                     {:status 200
                                      :body interval}))}}]])


;; Rich comments
(comment
  (require '[tick.core :as tick])
  (try-insert! ajanottaja.db/datasource
               :workdays
               {:work-date #time/date "2021-04-25"
                :work-duration (tick.core/new-duration (* 60 7.5) :minutes)
                :account-id #uuid "5d90856c-5a48-46ae-8f6e-a06295ea0dfb"})

  (try-insert! ajanottaja.db/datasource
               :work-intervals
               {:workday-id 1
                :interval (->Interval (t/now) nil)})

  (f/if-let-ok? [foo (f/fail nil)]
                :ok
                :not-ok)

  (->> {:select [:*]
        :from [:work-intervals]}
       hsql/format
       (query! ajanottaja.db/datasource))

  ;; Test sql generation for workday upserts
  (hsql/format (workday-upsert {:work-date (t/today)
                                :work-interval (t/new-duration 7 :hours)
                                :account-id  #uuid "5d90856c-5a48-46ae-8f6e-a06295ea0dfb"}))

  ;; Test sql generation for interval creation
  (hsql/format (create-work-interval {:work-date (t/today)
                                      :work-interval (t/new-duration 7 :hours)
                                      :account-id #uuid "5d90856c-5a48-46ae-8f6e-a06295ea0dfb"
                                      :interval-start (t/now)}))


  (insert-work-interval! ajanottaja.db/datasource
                         {:work-date (t/today)
                          :work-duration (t/new-duration 7 :hours)
                          :account-id #uuid "5d90856c-5a48-46ae-8f6e-a06295ea0dfb"
                          :interval (->Interval (t/now) nil)})
  
  (stop-work-interval! ajanottaja.db/datasource
                       {:account-id #uuid "5d90856c-5a48-46ae-8f6e-a06295ea0dfb"})
  
  (slurp (muuntaja.core/encode
        ajanottaja.server/muuntaja-instance
        "application/json"
        (malli.core/encode
         (mu/update schemas/work-interval :interval-stop
                    #(conj [:maybe] %))
         {:id #uuid "a78ed4cf-fc74-4cd7-83de-3e893b161c20"
          :interval-start #time/instant "2021-05-05T17:05:27.334638Z"
          :interval-stop nil
          :workday-id #uuid "ca1f5a5f-b242-49aa-ba2f-3616407d3645"
          :created-at #time/instant "2021-05-05T17:05:27.356747Z"
          :updated-at #time/instant "2021-05-05T17:05:27.356747Z"}
         ajanottaja.schemas/strict-json-transformer)))
  
  (malli.core/validate (mu/update schemas/work-interval :interval-stop
                                  #(conj [:maybe] %))
                       {:id #uuid "a78ed4cf-fc74-4cd7-83de-3e893b161c20"
                        :interval-start #time/instant "2021-05-05T17:05:27.334638Z"
                        :interval-stop nil
                        :workday-id #uuid "ca1f5a5f-b242-49aa-ba2f-3616407d3645"
                        :created-at #time/instant "2021-05-05T17:05:27.356747Z"
                        :updated-at #time/instant "2021-05-05T17:05:27.356747Z"})
  

  (require '[malli.util :as mu])

  (malli.core)

  

  #time/date "2021-05-04"
)