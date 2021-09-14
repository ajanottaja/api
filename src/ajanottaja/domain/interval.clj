(ns ajanottaja.domain.interval
  (:require [cambium.core :as log]
            [honey.sql :as hsql]
            [malli.util :as mu]
            [tick.core :as t]
            [ajanottaja.db :refer [try-insert! query-one!]]
            [ajanottaja.helpers :as h]
            [ajanottaja.server.interceptors :as interceptors]
            [ajanottaja.failjure :as f]
            [ajanottaja.schemas.responses :as res-schemas]
            [ajanottaja.schemas.time :as schemas]))

(defn create-interval
  [{:keys [interval account]}]
  {:insert-into :intervals
   :values [{:account account :interval [:lift interval]}]
   :returning :*})

(def create-interval!
  "Insert a new interval for datasource, "
  (partial query-one! create-interval))

(defn update-interval
  ""
  [{:keys [account id] :as m}]
  {:update :intervals
   :set (-> m
            (select-keys [:interval])
            (update :interval (fn [i] (when i [:lift i])))
            h/filter-nils)
   :where [:and
           [:= :id id]
           [:= :account account]]
   :returning [:*]})

(def update-interval! (partial query-one! update-interval))

(defn delete-interval
  "Takes map of id and account and returns a honeysql map for deleting
   record identified by id if it matches account."
  [{:keys [account id] :as m}]
  {:delete-from [:intervals]
   :where [:and
           [:= :id id]
           [:= :account account]]
   :returning [:*]})

(def delete-interval! (partial query-one! delete-interval))


(defn stop-interval
  ""
  [{:keys [account]}]
  {:update :intervals
   :set {:interval [:tstzrange [:lower :interval] (t/now) "[)"]}
   :where [:and
           [:= :account account]
           [:= [:upper :interval] nil]]
   :returning :*})

(def stop-interval! (partial query-one! stop-interval))


(defn active-interval
  [{:keys [account]}]
  {:select [:*]
   :from :intervals
   :where [:and
           [:= :account account]
           [:= [:upper :interval] nil]]})

(def active-interval! (partial query-one! active-interval!))


(defn intervals
  [{:keys [account]}]
  {:select [:id :interval]
   :from :intervals
   :where [:and
           [:= :account account]]
   :order-by [:interval]})

(def intervals! (partial query-one! intervals))


(defn routes
  "Defines all the routes for timing related stuff"
  [server-config]
  [""
   {:tags [:interval]
    :description "Endpoints for registeirng work days and intervals."
    :interceptors [(interceptors/validate-bearer-token (:jwks-url server-config))
                   (interceptors/transform-claims)]
    :swagger {:security [{:bearer []}]}}
   ["/intervals"
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
    ["/:id"
     {:patch {:name :update-target
              :description "Update target's date and/or duration.
                                            Omitted value is not set."
              :parameters {:path (mu/select-keys schemas/interval [:id])
                           :body (-> schemas/interval
                                     (mu/select-keys [:interval])
                                     (mu/optional-keys [:interval]))}
              :responses {200 {:body schemas/interval}}
              :handler (fn [req]
                         (log/info "Update interval")
                         (f/if-let-ok? [interval (update-interval! (-> req :state :datasource)
                                                                   (-> (-> req :parameters :body)
                                                                       (merge {:account (-> req :claims :sub)})
                                                                       (merge (-> req :parameters :path))))]
                                       {:status 200
                                        :body interval}))}
      :delete {:name :delete-interval
               :description "Delete interval (hard delete)"
               :parameters {:path (mu/select-keys schemas/interval [:id])}
               :responses {200 {:body schemas/interval}
                           400 {:body res-schemas/not-found-schema}}
               :handler (fn [req]
                          (log/info "Delete interval")
                          (f/if-let-ok? [interval (delete-interval! (-> req :state :datasource)
                                                                    (-> (-> req :parameters :body)
                                                                        (merge {:account (-> req :claims :sub)})
                                                                        (merge (-> req :parameters :path))))]
                                        (if (nil? interval)
                                          {:status 404
                                           :body {:message "Not found"}}
                                          {:status 200
                                           :body interval})))}}]]
   
   ["/intervals-active"
    [""
     {:get {:name :active-interval
            :description "Return the active work interval if any exists."
            :responses {200 {:body schemas/interval}
                        404 {:body res-schemas/not-found-schema}}
            :handler (fn [req]
                       (log/info "Fetch any active interval")
                       (let [interval (active-interval! (-> req :state :datasource)
                                                        {:account (-> req :claims :sub)})]
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
     {:conflicting true
      :post {:name :create-interval
             :description "Upsert workday with workday duration and adds new work interval for the provided workday."
             :parameters {}
             :responses {200 {:body schemas/interval}}
             :handler (fn [req]
                        (log/info "Insert interval")
                        (f/if-let-ok? [interval (create-interval! (-> req :state :datasource)
                                                                  {:account (-> req :claims :sub)
                                                                   :date (t/today)
                                                                   :interval ^{:ajanottaja/type :interval} {:beginning (t/now)}})]
                                      {:status 200
                                       :body interval}))}}]
    ["/stop"
     {:conflicting true
      :post {:name :stop-interval
             :description "Stop any active work interval, returns latest interval"
             :parameters {}
             :responses {200 {:body schemas/interval}}
             :handler (fn [req]
                        (f/if-let-ok? [interval (stop-interval! (-> req :state :datasource)
                                                                {:account (-> req :claims :sub)})]
                                      {:status 200
                                       :body interval}))}}]]])


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
                     :interval ^{:ajanottaja/type :interval} {:beginning (t/now)}})

  (update-interval! ajanottaja.db/datasource
                    {:account #uuid "c3ce6f30-4b13-4252-8799-80783f3d3546"
                     :id #uuid "59a8901f-df7e-43e6-a2a9-606e36b7a602"
                     :interval ^{:ajanottaja/type :interval} {:beginning #inst "2021-09-14T08:50:17.122402Z"
                                                   :end #inst "2021-09-14T08:55:17.122402Z"}})
  
  (delete-interval! ajanottaja.db/datasource
                    {:account #uuid "c3ce6f30-4b13-4252-8799-80783f3d3546"
                     :id #uuid "59a8901f-df7e-43e6-a2a9-606e36b7a602"})


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