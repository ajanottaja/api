(ns ajanottaja.schemas.time
  (:require [malli.core :as m]
            [malli.util :as mu]
            [ajanottaja.schemas :as schemas]))

(def target
  "Target schema"
  (mu/merge schemas/pg-columns
            [:map
             [:date :date]
             [:account :uuid]
             [:duration :duration]]))

(def interval
  "Work interval schema"
  (mu/merge schemas/pg-columns
            [:map
             [:account :uuid]
             [:interval schemas/interval?]]))

(comment 
  (require '[malli.error :as me]
           '[malli.core :as m]
           '[malli.transform :as mt]
           '[malli.util :as mu])
  
  ;; Valid target
  (me/humanize (m/explain target
                          {:id #uuid "e178f3a9-c2ef-4af7-9183-fad6920dce5b"
                           :date #time/date "2021-08-08"
                           :account #uuid "c3ce6f30-4b13-4252-8799-80783f3d3546"
                           :duration #time/duration "PT7H"
                           :created-at #time/instant "2021-08-08T10:30:30.972687Z"
                           :updated-at #time/instant "2021-08-08T10:30:30.972687Z"}))
  (tick.alpha.api/new-duration #time/duration "PT7H" :millis)

  (as-> {:id #uuid "e178f3a9-c2ef-4af7-9183-fad6920dce5b"
       :date #time/date "2021-08-08"
       :account #uuid "c3ce6f30-4b13-4252-8799-80783f3d3546"
       :duration #time/duration "PT7H"
       :created-at #time/instant "2021-08-08T10:30:30.972687Z"
       :updated-at #time/instant "2021-08-08T10:30:30.972687Z"} t
      (malli.core/encode target t schemas/strict-json-transformer)
    (muuntaja.core/encode "application/json" t)
    (slurp t))
  
  (me/humanize interval
               {:id #uuid "6ed4efaa-61bc-4a08-b746-a2919c53ba1b"
                :account-id  #uuid "6ed4efaa-61bc-4a08-b746-a2919c53ba1b"
                :interval-start #time/instant "2021-05-02T17:41:32.361877Z"
                :interval-stop #time/instant "2021-05-02T17:41:53.627934Z"})
  

  (m/decode interval
            {:id "e4387d12-0019-49a5-aa4d-ba1d870172c9"
             :interval {:beginning "2021-05-14T13:23:36.081839Z"
                        :end nil}
             :workday-id "30e9f3f0-dddb-4bce-bba5-885d5e2e65fd"
             :created-at "2021-05-14T13:23:36.081839Z"
             :updated-at "2021-05-14T13:23:36.081839Z"}
            schemas/strict-json-transformer)
  
  (-> (m/decode interval
                {:id "e4387d12-0019-49a5-aa4d-ba1d870172c9"
                 :interval {:beginning "2021-05-14T13:23:36.081839Z"
                            :end nil}
                 :workday-id "30e9f3f0-dddb-4bce-bba5-885d5e2e65fd"
                 :created-at "2021-05-14T13:23:36.081839Z"
                 :updated-at "2021-05-14T13:23:36.081839Z"}
                schemas/strict-json-transformer)
      :interval
      meta)
  
  (me/humanize (m/explain interval
                          {:id #uuid "e4387d12-0019-49a5-aa4d-ba1d870172c9"
                           :interval {:beginning #time/instant "2021-05-14T13:23:36.026628Z"}
                           :workday-id #uuid "30e9f3f0-dddb-4bce-bba5-885d5e2e65fd"
                           :created-at #time/instant "2021-05-14T13:23:36.081839Z"
                           :updated-at #time/instant "2021-05-14T13:23:36.081839Z"}))
)