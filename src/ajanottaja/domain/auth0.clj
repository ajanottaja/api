(ns ajanottaja.domain.auth0
  (:require [cambium.core :as log]
            [clj-http.client :as http]
            [malli.util :as mu]
            [muuntaja.core :as muuntaja]
            [honey.sql :as hsql]
            [ajanottaja.db :refer [try-insert! query-one!]]
            [ajanottaja.failjure :as f]
            [ajanottaja.schemas.account :as schemas]
            [ajanottaja.server.interceptors :as interceptors]))


(def setup-account-input-schema
  "Validate account input values"
  (mu/select-keys schemas/account-model [:auth-zero-id :email]))

(def setup-account-response-schema
  "Validate account setup output body"
  (mu/select-keys schemas/account-model [:id :auth-zero-id :email :created-at :updated-at]))


(defn create-account!
  [datasource account]
  (log/info account "Creating new user account from auth0")
  (let [account (try-insert! datasource :accounts account)]
    (cond
      (:error account) (do (log/error account "Error")
                           {:status 400 :body (:error account)})
      :else {:status 201 :body account})))


(defn get-account-by-auth-zero-id
  [{:keys [auth-zero-id]}]
  {:select [:id]
   :from   [:accounts]
   :where [:= :auth-zero-id auth-zero-id]})

(def get-account-by-auth-zero-id! (partial query-one! get-account-by-auth-zero-id))


(defn routes
  "Defines account related routes"
  [server-config]
  ["/auth-zero"
   {:tags [:auth0]
    :description "Endpoints for Auth0 register and login workflows"
    :interceptors [(interceptors/validate-api-token (:api-token server-config))]
    :swagger {:security [{:apikey []}]}}
   ["/create-account"
    {:post {:name :create-account
            :description "Create Ajanottaja account based on Auth0 register workflow action."
            :parameters {:body setup-account-input-schema}
            :responses {200 {:body setup-account-response-schema}}
            :handler (fn [req]
                       (create-account! (-> req :state :datasource)
                                        (-> req :parameters :body)))}}]
   ["/get-account/:auth-zero-id"
    {:get {:name :get-account
           :description "Give auth0 acccess to ajanottaja id to use in custom claim in jwt during login workflow."
           :parameters {:path [:map [:auth-zero-id string?]]}
           :responses {200 {:body (mu/select-keys schemas/account-model [:id])}}
           :handler (fn [req]
                      (let [account (get-account-by-auth-zero-id! (-> req :state :datasource)
                                                                  (-> req :parameters :path))]
                        (if account
                          {:status 200 :body account}
                          {:status 404 :body {:message "Not found"}})))}}]])



;; Rich comments
(comment

  (->> {:select [:*]
        :from [:accounts]
        :where [:= :email "post+ajanottaja@snorre.io"]}
       hsql/format
       (query! @ajanottaja.db/datasource)
       first))
