(ns ajanottaja.domain.target
  (:require [cambium.core :as log]
            [honey.sql :as hsql]
            [honey.sql.helpers :as hsqlh]
            [malli.util :as mu]
            [tick.core :as t]
            [ajanottaja.db :refer [try-insert! query-one! query-many!]]
            [ajanottaja.helpers :as h]
            [ajanottaja.server.interceptors :as interceptors]
            [ajanottaja.failjure :as f]
            [ajanottaja.schemas.responses :as res-schemas]
            [ajanottaja.schemas.time :as schemas]))

(defn create-target
  "Takes a map with date, interval, and account (id) and
   returns a honey-sql map for upserting a target row, and return
   the id and account id value for the row."
  [{:keys [date duration account] :or {duration (t/new-duration (* 60 7.5) :hours)}}]
  {:insert-into :targets
   :values [{:date date
             :duration duration
             :account account}]
   :returning [:*]})


(def create-target! (partial query-one! create-target))


(defn update-target
  "Given account (id), target id, and a new duration returns sql query for updating
   the target record."
  [{:keys [account date duration id] :as m}]
  {:update :targets
   :set (-> m
            (select-keys [:date :duration :id])
            h/filter-nils)
   :where [:and
           [:= :account account]
           [:= :id id]]
   :returning [:*]})

(def update-target! (partial query-one! update-target))


(defn delete-target
  "Given account (id), target id, and a new duration returns sql query for updating
   the target record."
  [{:keys [account id]}]
  {:delete-from :targets
   :where [:and
           [:= :id id]
           [:= :account account]]
   :returning [:*]})

(def delete-target! (partial query-one! delete-target))


(defn targets
  [{:keys [account]}]
  {:select [:*]
   :from :targets
   :where [:and
           [:= :account account]]
   :order-by [:date]})

(def targets! (partial query-many! targets))

(defn target-on-date
  [{:keys [account date] :or {date (t/new-date)}}]
  {:select [:*]
   :from :targets
   :where [:and
           [:= :account account]
           [:= :date date]]})

(def active-target! (partial query-one! target-on-date))


(defn routes
  "Defines all the routes for timing related stuff"
  [server-config]
  [""
   {:tags [:targets]
    :description "Endpoints for creating, updating, listing, and deleting targets."
    :interceptors [(interceptors/validate-bearer-token (:jwks-url server-config))
                   (interceptors/transform-claims)]
    :swagger {:security [{:bearer []}]}}
   ["/targets"
    [""
     {:get {:name :targets
            :description "Returns list of targets"
            :parameters {}
            :responses {200 {:body [:sequential schemas/target]}}
            :handler (fn [req]
                       (log/info "Fetch targets")
                       (f/if-let-ok? [targets (targets! (-> req :state :datasource)
                                                        {:account (-> req :claims :sub)})]
                                     {:status 200
                                      :body targets}))}
      :post {:name :create-target
             :description "Creates a target"
             :parameters {:body (mu/select-keys schemas/target [:duration :date])}
             :responses {200 {:body schemas/target}}
             :handler (fn [req]
                        (log/info "Upsert target")
                        (f/if-let-ok? [target (create-target! (-> req :state :datasource)
                                                              (-> req :parameters :body (merge {:account (-> req :claims :sub)})))]
                                      {:status 200
                                       :body target}))}}]
    ["/:id" {:patch {:name :update-target
                     :description "Update target's date and/or duration.
                                            Omitted value is not set."
                     :parameters {:path (mu/select-keys schemas/target [:id])
                                  :body (-> schemas/target
                                            (mu/select-keys [:date :duration])
                                            (mu/optional-keys [:date :duration]))}
                     :responses {200 {:body schemas/target}}
                     :handler (fn [req]
                                (log/info "Upsert target")
                                (f/if-let-ok? [target (update-target! (-> req :state :datasource)
                                                                      (-> (-> req :parameters :body)
                                                                          (merge {:account (-> req :claims :sub)})
                                                                          (merge (-> req :parameters :path))))]
                                              (do (tap> target)
                                                  {:status 200
                                                   :body target})))}
             :delete {:name :delete-target
                     :description "Delete target identified by id if it matches account"
                     :parameters {:path (mu/select-keys schemas/target [:id])}
                     :responses {200 {:body schemas/target}
                                 404 {:body res-schemas/not-found-schema}}
                     :handler (fn [req]
                                (log/info "Upsert target")
                                (f/if-let-ok? [target (delete-target! (-> req :state :datasource)
                                                                      (-> (-> req :parameters :body)
                                                                          (merge {:account (-> req :claims :sub)})
                                                                          (merge (-> req :parameters :path))))]
                                              (if (nil? target)
                                                {:status 404
                                                 :body {:message "Not Found"}}
                                                {:status 200
                                                 :body target})))}}]]
   ["/targets-active"
    {:get {:name :active-target
           :description "Returns target of current date if it exists"
           :parameters {}
           :responses {200 {:body schemas/target}}
           :handler (fn [req]
                      (log/info "Fetch targets")
                      (f/if-let-ok? [target (active-target! (-> req :state :datasource)
                                                            {:account (-> req :claims :sub)})]
                                    (if-not (nil? target)
                                      {:status 200
                                       :body target}
                                      {:status 404
                                       :body {:message "Not Found"}})))}}]])


;; Rich comments
(comment
  
  

  (targets! ajanottaja.db/datasource {:account #uuid "c3ce6f30-4b13-4252-8799-80783f3d3546"})

  (as->
   (targets! ajanottaja.db/datasource {:account #uuid "c3ce6f30-4b13-4252-8799-80783f3d3546"}) r
    (malli.core/encode [:sequential schemas/target] r ajanottaja.schemas/strict-json-transformer))

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