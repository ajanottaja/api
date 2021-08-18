(ns ajanottaja.domain.target
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

(defn upsert-target
  "Takes a map with date, interval, and account (id) and
   returns a honey-sql map for upserting a target row, and return
   the id and account id value for the row."
  [{:keys [date duration account] :or {duration (t/new-duration (* 60 7.5) :hours)}}]
  {:insert-into :targets
   :values [{:date date
             :duration duration
             :account account}]
   :on-conflict [:date :account]
   :do-update-set [:duration]
   :returning [:*]})

(defn targets
  [{:keys [account]}]
  {:select [:*]
   :from :targets
   :where [:and
           [:= :account account]]
   :order-by [:date]})

(defn target-on-date
  [{:keys [account date] :or {date (t/new-date)}}]
  {:select [:*]
   :from :targets
   :where [:and
           [:= :account account]
           [:= :date date]]})

(defn targets!
  "Given datasource and map with account (id) returns list of targets for account."
  [ds m]
  (log/info m "Fetch targets for account")
  (try (->> (targets m)
            hsql/format
            (query! ds))
       (catch Exception e (tap> e) e)))

(defn active-target!
  "Given datasource and map with account (id) returns todays target"
  [ds m]
  (log/info m "Fetch todays target for account")
  (try (->> (target-on-date m)
            hsql/format
            (query! ds)
            first)
       (catch Exception e (tap> e) e)))

(defn upsert-target!
  "Given datasource wrapped in atom and map of workday and work interval values
   returns the started interval."
  [ds m]
  (log/info m "Insert work interval!")
  (try (->> (upsert-target m)
            hsql/format
            (query! ds)
            first)
       (catch Exception e (tap> e) e)))


(defn routes
  "Defines all the routes for timing related stuff"
  [server-config]
  ["/targets"
   {:tags [:targets]
    :description "Endpoints for creating, updating, listing, and deleting targets."
    :interceptors [(interceptors/validate-bearer-token (:jwks-url server-config))
                   (interceptors/transform-claims)]
    :swagger {:security [{:bearer []}]}}
   [""
    {:get {:name :targets
           :description "Returns list of targets"
           :parameters {}
           :responses {200 {:body [:sequential schemas/target]}}
           :handler (fn [req]
                      (log/info "Fetch targets")
                      (f/if-let-ok? [targets (targets! (-> req :state :datasource)
                                                       {:account (-> req :claims :sub)})]
                                    (do (tap> targets)
                                        {:status 200
                                         :body targets})))}
     :post {:name :upsert-target
            :description "Upserts a target"
            :parameters {:body (mu/select-keys schemas/target
                                               [:date :duration])}
            :responses {200 {:body schemas/target}}
            :handler (fn [req]
                       (log/info "Upsert target")
                       (f/if-let-ok? [target (upsert-target! (-> req :state :datasource)
                                                             (-> req :parameters :body (merge {:account (-> req :claims :sub)})))]
                                     {:status 200
                                      :body target}))}}]
   ["/active"
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