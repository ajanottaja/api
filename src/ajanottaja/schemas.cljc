(ns ajanottaja.schemas
  (:require [clojure.string :as string]
            [malli.core :as m]
            [malli.registry :as mr]
            [malli.transform :as mt]
            [malli.util :as mu]
            [tick.core :as t])
  (:import [java.time Duration]))

;; Mostly used for testing
(def strict-json-transformer
  (mt/transformer
   mt/strip-extra-keys-transformer
   mt/json-transformer))

(comment
  (type #inst "2021-05-14T11:29:10.741408Z")
  (type #time/instant "2021-05-14T13:21:04.688317Z")
  (t/format #inst "2021-05-14T11:29:10.741408Z")
  (str #time/instant "2021-05-14T13:21:04.688317Z"))


(def registry
  (merge (m/default-schemas)
         {:instant (m/-simple-schema {:type :instant
                                      :pred t/instant?
                                      :type-properties {:error/message "should be a valid date time instant"
                                                        :decode/string #(some-> % t/instant)
                                                        :encode/string str
                                                        :decode/json #(some-> % t/instant)
                                                        :encode/json str
                                                        :json-schema/type "string"
                                                        :json-schema/format "date-time"}})
          :date (m/-simple-schema {:type :date
                                   :pred t/date?
                                   :type-properties {:error/message "should be a valid date"
                                                     :decode/string t/date
                                                     :encode/string t/format
                                                     :decode/json t/date
                                                     :encode/json t/format
                                                     :json-schema/type "string"
                                                     :json-schema/format "date"}})
          :duration (m/-simple-schema {:type :duration
                                       :pred t/duration?
                                       :type-properties {:error/message "should be a valid duration"
                                                         :decode/string #(Duration/parse %)
                                                         :encode/string #(when % (str %))
                                                         :decode/json #(Duration/parse %)
                                                         :encode/json #(when % (str %))
                                                         :json-schema/type "string"
                                                         :json-schema/format "duration"
                                                         :swagger/example "PT6H"}})


          :email (m/-simple-schema {:type :email
                                    :pred #(and (string? %)
                                                (re-matches #"^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,63}$" %))
                                    :type-properties {:error/fn '(fn [{:keys [schema value]} _]
                                                                   (str value " does not match regex " #"^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,63}$"))
                                                      :decode/string string/lower-case
                                                      :encode/string string/lower-case
                                                      :decode/json string/lower-case
                                                      :encode/json string/lower-case
                                                      :json-schema/type "string"
                                                      :json-schema/format "string"}})}))

;; Override the default registry so we get the instant type wherever we use malli
(mr/set-default-registry! registry)


;; Common postgres properties across all tables
(def pg-columns
  [:map
   [:id uuid?]
   [:created-at :instant]
   [:updated-at :instant]])

(def interval?
  "Represents an interval as a map with two properties,
   a beginning and an end. Converts into Interval record on decode.
   On encode just uses the default encoding strategy for a map with
   two instants."
  [:map
   {:decode/json {:enter '#(with-meta % {:ajanottaja/type :interval})}}
   [:beginning :instant]
   [:end [:maybe :instant]]])

(comment
  (require '[malli.error :as me]
           '[malli.core :as m]
           '[malli.transform :as mt]
           '[malli.util :as mu])
  
  (meta (with-meta {:beginning (t/now)} {:type :interval}))
  

  ;; Invalid email returns nil
  (me/humanize (m/explain :email "post+ajanottaja@snorre.io@"))
  (m/validate :email "post@snorre.io@")

  (m/decode :email "pOSt@snorRe.Io" mt/string-transformer)

  (m/encode :duration (t/new-duration 30 :minutes) mt/string-transformer)

  ;; Encoding and decoding interval maps is simple
  (type (m/decode interval? {:beginning "2020-01-01T10:00:00Z" :ends nil} mt/json-transformer))


  (t/date "2021-02-07T18:31:06.546874Z")

  (t/instant "2021-02-07T18:31:06.546874Z")

  (t/instant "2021-05-02T18:59:22.214Z")

  (t/date-time)

  (t/new-duration 3600000 :millis)
  (t/duration? (Duration/parse "PT1H"))

  (import [java.time Duration])
  (Duration/parse (str (t/new-duration 3600000 :millis)))

  (t/new-duration (str (t/new-duration 3600000 :millis)))
  (t/duration "PT1H")

  (str (t/new-duration 3600000 :millis))

  (str (t/new-duration 3600000 :millis))

  (+ 10 10)

  ;; Testing time utilities
  (t/now)
  (t/instant)
  (t/instant? #time/instant "2021-02-07T18:31:06.546874Z")
  (t/instant "2021-05-02T18:21:34.280Z")

  
  (t/date-time)

  ;; Valid pg-columns
  (->> {:id #uuid "b21271ce-cb9c-45e6-b744-79d94e9c633c"
        :created-at #time/instant "2021-05-02T09:40:27.589346Z"
        :updated-at #time/instant "2021-05-02T09:40:30.634035Z"}
       (m/explain pg-columns))

  ;; Invalid pg-column
  (->> {:id 100
        :created-at #time/instant "2021-05-02T09:40:27.589346Z"
        :updated-at #time/instant "2021-05-02T09:40:30.634035Z"}
       (m/explain pg-columns))

  ;; Wrong type of role and id
  (->> {:id #uuid "b21271ce-cb9c-45e6-b744-79d94e9c633c"
        :auth-zero-id "some-id"
        :email "example@example.com@"
        :role "admin-foo"
        :created-at (t/now)
        :updated-at (t/now)}
       (m/explain account-model)
       me/humanize)



  (.toString #time/date-time "2021-05-02T23:04:32.979881"))