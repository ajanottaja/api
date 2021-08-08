(ns ajanottaja.domain.interval
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

(defn target-upsert
  "Takes a map with date, interval, and account (id) and
   returns a honey-sql map for upserting a target row, and return
   the id and account id value for the row."
  [{:keys [date duration account] :or {duration (t/new-duration (* 60 7.5) :hours)}}]
  {:insert-into :targets
   :values [{:date date
             :duration duration
             :account account}]
   :on-conflict [:date :account]
   :do-nothing []
   :returning [:id]})

(defn create-interval
  "Takes map of workday and work-interval attributes and
   returns a honey-sql map for upsering a workday row, and
   inserting a new corresponding interval row."
  [{:keys [interval account] :as m}]
  {:with [[:target-upsert (target-upsert m)]]
   :insert-into :intervals
   :values [{:account account :interval [:lift interval]}]
   :returning :*})


(defn stop-interval
  ""
  [{:keys [account]}]
  {:update :intervals
   :set {:interval [:tstzrange [:lower :interval] (t/now) "[)"]}
   :where [:and
           [:= :account account]
           [:= [:upper :interval] nil]]
   :returning :*})


(defn active-interval
  [{:keys [account]}]
  {:select [:*]
   :from :intervals
   :where [:and
           [:= :account account]
           [:= [:upper :interval] nil]]})


(defn intervals
  [{:keys [account]}]
  {:select [:id :interval]
   :from :intervals
   :where [:and
           [:= :account account]]
   :order-by [:interval]})

(defn create-interval!
  "Given datasource wrapped in atom and map of workday and work interval values
   returns the started interval."
  [ds m]
  (log/info m "Insert work interval!")
  (try (->> (create-interval m)
            hsql/format
            (query! ds)
            first)
       (catch Exception e (tap> e) e)))


(defn stop-interval!
  "Given datasource wrapped in atom and map with account-id stops any active
   work interval and returns the interval, if any."
  [ds m]
  (log/info m "Stop work interval!")
  (try (->> (stop-interval m)
            hsql/format
            (query! ds)
            first)
       (catch Exception e e)))


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

(defn routes
  "Defines all the routes for timing related stuff"
  [server-config]
  ["/intervals"
   {:tags [:interval]
    :description "Endpoints for registeirng work days and intervals."
    :interceptors [(interceptors/validate-bearer-token (:jwks-url server-config))
                   (interceptors/transform-claims)]
    :swagger {:security [{:bearer []}]}}
   [""
    {:get {:name :intervals
           :description "Returns list of intervals"
           :parameters {}
           :responses {200 {:body [:sequential (mu/select-keys schemas/interval
                                                               [:id :interval])]}}
           :handler (fn [req]
                      (log/info "Fetch intervals")
                      (f/if-let-ok? [intervals (intervals! (-> req :state :datasource)
                                                           {:account (-> req :claims :sub)})]
                                    {:status 200
                                     :body intervals}))}}]
   ["/active"
    {:get {:name :active-interval
           :description "Return the active work interval if any exists."
           :responses {200 {:body schemas/interval}
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
   ["/start"
    {:post {:name :create-interval
            :description "Upsert workday with workday duration and adds new work interval for the provided workday."
            :parameters {}
            :responses {200 {:body schemas/interval}}
            :handler (fn [req]
                       (log/info "Insert interval")
                       (f/if-let-ok? [interval (create-interval! (-> req :state :datasource)
                                                                      {:account (-> req :claims :sub)
                                                                       :date (t/today)
                                                                       :interval ^{:type :interval} {:beginning (t/now)}})]
                                     {:status 200
                                      :body interval}))}}]
   ["/stop"
    {:post {:name :stop-interval
            :description "Stop any active work interval, returns latest interval"
            :parameters {}
            :responses {200 {:body schemas/interval}}
            :handler (fn [req]
                       (f/if-let-ok? [interval (stop-interval! (-> req :state :datasource)
                                                               {:account (-> req :claims :sub)})]
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
  (hsql/format (target-upsert {:work-date (t/today)
                                :work-interval (t/new-duration 7 :hours)
                                :account-id  #uuid "5d90856c-5a48-46ae-8f6e-a06295ea0dfb"}))

  ;; Test sql generation for interval creation
  (hsql/format (create-interval {:date (t/today)
                                 :work-interval (t/new-duration 7 :hours)
                                 :account #uuid "5d90856c-5a48-46ae-8f6e-a06295ea0dfb"
                                 :interval (t/now)}))



  (create-interval! ajanottaja.db/datasource
   {:date (t/today)
    :duration (t/new-duration 7 :hours)
    :account #uuid "c3ce6f30-4b13-4252-8799-80783f3d3546"
    :interval ^{:type :interval}{:beginning (t/now)}})
  
  (stop-interval! ajanottaja.db/datasource
                       {:account #uuid "c3ce6f30-4b13-4252-8799-80783f3d3546"})
  

  (active-interval! ajanottaja.db/datasource
                    {:account #uuid "c3ce6f30-4b13-4252-8799-80783f3d3546"})
  

  (intervals! ajanottaja.db/datasource {:account #uuid "c3ce6f30-4b13-4252-8799-80783f3d3546"
                                         :date (t/today)})

  (hsql/format (intervals #_ajanottaja.db/datasource {:account #uuid "c3ce6f30-4b13-4252-8799-80783f3d3546"
                                                      :date (t/today)}))

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
  

)