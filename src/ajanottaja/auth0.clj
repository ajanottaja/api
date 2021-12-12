(ns ajanottaja.auth0
  (:require [ajanottaja.config :as config]
            [ajanottaja.failjure :as f]
            [cambium.core :as log]
            [clj-http.client :as http]
            [clojure.java.io :as io]
            [clojure.string :as string]
            [mount.lite :refer (defstate) :as mount]
            [muuntaja.core :as muuntaja]))


(def actions
  "Define available actions"
  (->> [{:name "create-user"
         :trigger "post-user-registration"}
        {:name "login-user"
         :trigger "post-login"}]))


(defn access-token!
  "Returns Auth0 access token if one can be fetched, otherwise returns failjure"
  [{:keys [domain client-id client-secret]}]
  (tap> "Get access token")
  (f/ok->> (http/post (str "https://" domain "/oauth/token")
                      {:form-params {:grant_type "client_credentials"
                                     :client_id client-id
                                     :client_secret client-secret
                                     :audience "https://ajanottaja.eu.auth0.com/api/v2/"}})
           :body
           (muuntaja/decode "application/json")
           :access_token))

(defn +resource
  "Add resource URL for action based on name"
  [action]
  (assoc action :resource (io/resource (str "auth0-actions/" (:name action) ".js"))))


(defn +file-contents!
  "Read the file contents of the action, throws if file not read"
  [action]
  (assoc action :file (slurp (:resource action))))


(defn +desired-state!
  "Given action name and configuration returns the desired Auth0 action state.
   The state matches the request body for updating an action."
  [config action]
  (assoc action
         :desired
         {:name (:name action)
          :runtime "node16"
          :code (:file action)
          :secrets [{:name "API_DOMAIN" :value (-> config :auth0 :api-domain)}
                    {:name "API_TOKEN" :value (-> config :server :api-token)}]
          :dependencies [{:name "axios" :version "0.24.0"}]}))


(defn +current-state!
  "Adds current state of Auth0 action if one is found, otherwise returns {}"
  [config token action]
  (tap> "Get current action state")
  (f/ok-> (http/get (str "https://"
                         (-> config :auth0 :domain)
                         "/api/v2/actions/actions?actionName="
                         (:name action))
                    {:oauth-token token})
          :body
          (->> (muuntaja/decode "application/json"))
          :actions
          first
          (select-keys [:id :name :code :secrets :dependencies :runtime])
          (->> (assoc action :current))))


(defn +operation
  [action]
  (assoc action :operation
         (cond (= {} (:current action))
               :create

               (not= (update (:desired action)
                             :secrets
                             (fn [secrets] (map #(dissoc % :value) secrets)))
                     (-> (update (:current action)
                                 :secrets
                                 (fn [secrets] (map #(dissoc % :updated_at) secrets)))
                         (dissoc :id)))
               :update

               :else :nothing)))


(defn +payload
  [action]
  (assoc action
         :payload
         (assoc (:desired action)
                :supported_triggers
                [{:id (:trigger action)
                  :version "v2"}])))


(defn create-auth0-action!
  "Given config, an Auth0 oauth2 token, and an action creates the
   action based on the :desired state. Sets the supported trigger
   according to :trigger in the action."
  [config token action]
  (tap> "Create action")
  (f/ok->> (http/post (str "https://"
                           (-> config :auth0 :domain)
                           "/api/v2/actions/actions")
                      {:oauth-token token
                       :headers {"content-type" "application/json"}
                       :body (muuntaja/encode "application/json" (:payload action))})
           :body
           (muuntaja/decode "application/json")
           (assoc action :current)))


(defn update-auth0-action!
  "Given config and an Auth0 oauth2 token, and an action updates the
   action based on the :desired state."
  [config token action]
  (tap> "Update action")
  (tap> action)
  (f/ok-> (http/patch (str "https://"
                           (-> config :auth0 :domain)
                           "/api/v2/actions/actions/"
                           (:id (:current action)))
                      {:oauth-token token
                       :headers {"content-type" "application/json"}
                       :body (muuntaja/encode "application/json" (dissoc (:payload action) :supported_triggers))})
          :body
          (->> (muuntaja/decode "application/json"))
          (select-keys [:id :name :code :dependencies :runtime :secrets])
          (->> (assoc action :current))))


(defn deploy-auth0-action!
  "Given config, an Auth0 oauth2 token, and an action deploys the
   provided action. This needs to be done for both created and updated
   actions."
  [config token action]
  (tap> "Deploy action")
  (f/ok-> (http/post (str "https://"
                          (-> config :auth0 :domain)
                          "/api/v2/actions/actions/"
                          (:id (:current action))
                          "/deploy")
                     {:oauth-token token
                      :body nil})
          :body
          (->> (muuntaja/decode "application/json"))
          :actions
          first
          (select-keys [:name :code :dependencies :runtime :secrets])
          (->> (assoc action :deployed))))


(defn upsert-auth0-action!
  "Creates or updates action, then deploys it."
  [config token action]
  (tap> "Upsert auth0 action")
  (f/cond-ok->> action
                (= (:operation action) :create) (create-auth0-action! config token)
                (= (:operation action) :update) (update-auth0-action! config token)
                true (deploy-auth0-action! config token)))

(defn +trigger-payload
  [action]
  (assoc action
         :trigger-payload
         {:bindings [{:ref {:type "action_name"
                            :value (:name action)}
                      :display_name (:name action)}]}))


(defn bind-auth0-action-to-trigger!
  "Binds the actions to their specified triggers. As there are only
   one action per trigger we can bind the individual action to the trigger."
  [config token action]
  (f/ok-> (http/patch (str "https://" 
                           (-> config :auth0 :domain)
                           "/api/v2/actions/triggers/"
                           (:trigger action)
                           "/bindings")
                      {:oauth-token token
                       :headers {"content-type" "application/json"}
                       :body (muuntaja/encode "application/json" (:trigger-payload action))})))


(defn compute-action-operation!
  "Process seq of auth0 actions and compute the relevant operation, i.e. create, update, or noop.
   For each action map performs possibly side-effecting operation such as reading the desired action
   script from resource, fetching the current action state from Auth0, and determning the appropriate
   operation. If any step in the process fail returns a failjure. If not returns the action."
  [config token action]
  (f/as-ok-> action $
             (+resource $)
             (+file-contents! $)
             (+desired-state! config $)
             (+current-state! config token $)
             (+operation $)
             (+payload $)
             (upsert-auth0-action! config token $)
             (+trigger-payload $)
             (bind-auth0-action-to-trigger! config token $)))


(defn bootstrap!
  "Bootstraps Auth0 custom actions"
  [config]
  (f/attempt-all [token (access-token! (:auth0 config))]
                 (doall
                  (map (partial compute-action-operation! config token) actions))))



#_:clj-kondo/ignore
(defstate bootstrap
  :start (bootstrap! @config/config)
  :stop  (log/info "Spinning down migrations"))


(comment
  (require '[mount.lite])
  (require)
  (mount.lite/stop #'config/config)
  (mount.lite/start  #'config/config)

  (-> @config/config :auth0 :domain)

  (bootstrap! @config/config)
)