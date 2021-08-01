(ns ajanottaja.schemas.time
  (:require [malli.core :as m]
            [malli.util :as mu]
            [ajanottaja.schemas :as schemas]))

(def workday
  "Workday schema"
  (mu/merge schemas/pg-columns
            [:map
             [:work-date :date]
             [:account-id :uuid]
             [:work-duration :duration]]))

(def work-interval
  "Work interval schema"
  (mu/merge schemas/pg-columns
            [:map
             [:workday-id :uuid]
             [:interval schemas/interval?]]))

(comment 
  (require '[malli.error :as me]
           '[malli.core :as m]
           '[malli.transform :as mt]
           '[malli.util :as mu])
  
  ;; Valid workday
  (me/humanize workday
               {:id #uuid "6ed4efaa-61bc-4a08-b746-a2919c53ba1b"
                :work-date #time/date "2021-04-25"
                :account-id #uuid "5d90856c-5a48-46ae-8f6e-a06295ea0dfb"
                :work-duration #time/duration "PT7H30M"
                :created-at #time/date-time "2021-05-02T12:16:22.381001"
                :updated-at #time/date-time "2021-05-02T12:16:22.381001"})
  
  (me/humanize work-interval
               {:id #uuid "6ed4efaa-61bc-4a08-b746-a2919c53ba1b"
                :workday-id  #uuid "6ed4efaa-61bc-4a08-b746-a2919c53ba1b"
                :interval-start #time/instant "2021-05-02T17:41:32.361877Z"
                :interval-stop #time/instant "2021-05-02T17:41:53.627934Z"})
  

  (m/decode work-interval
            {:id "e4387d12-0019-49a5-aa4d-ba1d870172c9"
             :interval {:beginning "2021-05-14T13:23:36.081839Z"
                        :end nil}
             :workday-id "30e9f3f0-dddb-4bce-bba5-885d5e2e65fd"
             :created-at "2021-05-14T13:23:36.081839Z"
             :updated-at "2021-05-14T13:23:36.081839Z"}
            schemas/strict-json-transformer)
  
  (-> (m/decode work-interval
                {:id "e4387d12-0019-49a5-aa4d-ba1d870172c9"
                 :interval {:beginning "2021-05-14T13:23:36.081839Z"
                            :end nil}
                 :workday-id "30e9f3f0-dddb-4bce-bba5-885d5e2e65fd"
                 :created-at "2021-05-14T13:23:36.081839Z"
                 :updated-at "2021-05-14T13:23:36.081839Z"}
                schemas/strict-json-transformer)
      :interval
      meta)
  
  (me/humanize (m/explain work-interval
                          {:id #uuid "e4387d12-0019-49a5-aa4d-ba1d870172c9"
                           :interval {:beginning #time/instant "2021-05-14T13:23:36.026628Z"}
                           :workday-id #uuid "30e9f3f0-dddb-4bce-bba5-885d5e2e65fd"
                           :created-at #time/instant "2021-05-14T13:23:36.081839Z"
                           :updated-at #time/instant "2021-05-14T13:23:36.081839Z"}))
  
  (m/encode work-interval
            {:id #uuid "e4387d12-0019-49a5-aa4d-ba1d870172c9"
             :interval (->Interval #time/instant "2021-05-14T13:23:36.026628Z" nil)
             :workday-id #uuid "30e9f3f0-dddb-4bce-bba5-885d5e2e65fd"
             :created-at #time/instant "2021-05-14T13:23:36.081839Z"
             :updated-at #time/instant "2021-05-14T13:23:36.081839Z"}
            schemas/strict-json-transformer)
)