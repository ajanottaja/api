(ns ajanottaja.shared.schemas.account
  (:require [malli.core :as m]
            [malli.util :as mu]
            [ajanottaja.shared.schemas :as schemas]))


(def account-model
  "Account model"
  (mu/merge [:map
             [:auth-zero-id :string]
             [:email :email]]
            schemas/pg-columns))

(comment
  ;; Require malli related utils for easy access
  (require '[malli.error :as me]
           '[malli.core :as m]
           '[malli.transform :as mt]
           '[malli.util :as mu])

  
  ;; Transform account data as json using malli and kebab case
  (malli.core/encode account-model
                     {:id #uuid "b21271ce-cb9c-45e6-b744-79d94e9c633c"
                      :email "example@example.com"
                      :created-at #inst "2017-01-01"
                      :updated-at #inst "2017-01-01"
                      :not-in-account 1000}
                     (malli.transform/transformer
                      (malli.transform/key-transformer
                       {:encode camel-snake-kebab.core/->camelCaseString})
                      schemas/strict-json-transformer))

(malli.core/explain account-model
                    (malli.core/decode account-model
                                       {"id" "b21271ce-cb9c-45e6-b744-79d94e9c633c"
                                        "email" "example@example.com"
                                        "role" "test"
                                        "password" "longer-than-sixteen-characters"
                                        "passwordConfirmation" "longer-than-sixteen-characters"
                                        "createdAt" "2017-01-01T00:00:00.000Z"
                                        "updatedAt" "2017-01-01T00:00:00.000Z"}
                                       (malli.transform/transformer
                                        (malli.transform/key-transformer
                                         {:decode camel-snake-kebab.core/->kebab-case-keyword})
                                        malli.transform/json-transformer)))

(mu/update-in account-model
              [0]
              #(mu/select-keys % [:email :password])))