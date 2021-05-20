(ns ajanottaja.backend.jwt
  (:require [buddy.core.keys :as buddy-keys]
            [buddy.sign.jwt :as jwt]
            [cambium.core :as log]
            [clojure.core.memoize :as memoize]
            [diehard.core :as diehard]
            [malli.core :as m]
            [muuntaja.core :as muuntaja]
            [org.httpkit.client :as client]
            [ajanottaja.shared.schemas :as schemas]
            [ajanottaja.shared.failjure :as f]))

(def retry-opts?
  [:map
   [:timeout [:int {:min 100 :max 10000}]]
   [:retries [:int {:min 0 :max 5}]]])

(def example-jwk
  {:alg "RS256"
   :use "sig"
   :n
   "5OG5KSiEUy1feSRAmhEQEcoeJQX1alsaqb2ilNQAczWzsqonGTVTEF3pTeq4TMo2IfagpsJpmKHXDOA4p4vEilRoChqN11f_I0VJykz6xaFV0lrOCzlRq1uN2FHg-zWt8aE8R7ejbRCTCRjHZr3p0KFekRi4lwgcsVcnDYOyni9_EFgi0UdmKzH7Ym6WmX4KJcMcgDksPj6Hhz0IBNQrw1SRyIZmQN2k7vvurZvRb-vESLC0sTCFSiU2ob0zd3tqa1k-CWpjF91iQg6MrbLAGvT1Jvy8TgTEmhc4iC6OjO7mk-CvjEQx-jI9B49ZiYEfIrpUodEIAep9Vp6_pDApUQ"
   :kid "kZZv3j0W4OJCmmJNjAudm"
   :e "AQAB"
   :x5c
   ["MIIDCTCCAfGgAwIBAgIJbzq2s2dr7mM/MA0GCSqGSIb3DQEBCwUAMCIxIDAeBgNVBAMTF2FqYW5vdHRhamEuZXUuYXV0aDAuY29tMB4XDTIxMDMwMTE4MTAyMVoXDTM0MTEwODE4MTAyMVowIjEgMB4GA1UEAxMXYWphbm90dGFqYS5ldS5hdXRoMC5jb20wggEiMA0GCSqGSIb3DQEBAQUAA4IBDwAwggEKAoIBAQDk4bkpKIRTLV95JECaERARyh4lBfVqWxqpvaKU1ABzNbOyqicZNVMQXelN6rhMyjYh9qCmwmmYodcM4Dini8SKVGgKGo3XV/8jRUnKTPrFoVXSWs4LOVGrW43YUeD7Na3xoTxHt6NtEJMJGMdmvenQoV6RGLiXCByxVycNg7KeL38QWCLRR2YrMftibpaZfgolwxyAOSw+PoeHPQgE1CvDVJHIhmZA3aTu++6tm9Fv68RIsLSxMIVKJTahvTN3e2prWT4JamMX3WJCDoytssAa9PUm/LxOBMSaFziILo6M7uaT4K+MRDH6Mj0Hj1mJgR8iulSh0QgB6n1Wnr+kMClRAgMBAAGjQjBAMA8GA1UdEwEB/wQFMAMBAf8wHQYDVR0OBBYEFOtC5P3UNOliVTdUNroCedCWiG+vMA4GA1UdDwEB/wQEAwIChDANBgkqhkiG9w0BAQsFAAOCAQEAPs6XseFLSx9NdYA1TjxtGLdrzPoWwZe4v5oPSeVpbW5HDr+0Z/OoZAWP22a1VvBb9dcTmxE+yA0mZ9koToXSMPV9jEp/6rBOriroGfI6OvrYWBTA+NtxdWsvIiRDWGdEyMx1S1xQTrcCSh3Ke74sflr5OyvnSKZNezNN2LDpLB/ikZtfVAWPNh73WjMsbADuMi+Ip5jBFNuFws6tI/hKJa5xC9DDAIpAoG6c1OXuBugYgw3M04J3pNpnqFeMJpTjZVfpin6QQC9znpiKjL4lCNWg31tL5vQ2Ny7ifzBr4mpyujCD0Be1EVaS+nsE0OIUNpEUtifArsTQtDkySnJevg=="]
   :kty "RSA"
   :x5t "DQx42QR_lyy4u5gXtgJXMK7Acu0"})

(def jwk?
  "Model of what a json web key looks like"
  [:map
   {:gen/elements [example-jwk]}
   [:alg [:enum "RS256"]]
   [:use [:enum "sig"]]
   [:n :string]
   [:kid :string]
   [:e :string]
   [:x5c [:vector :string]]
   [:kty [:enum "RSA"]]
   [:x5t :string]])


(def public-key?
  "Malli public key schema"
  (m/-simple-schema
   {:type ::public-key?
    :pred buddy-keys/public-key?
    :type-properties {:error/message "should be a public key"
                      :decode/string str
                      :encode/string buddy-keys/str->public-key}}))

(def claims?
  "Malli schema for claims"
  [:map
   [:sub :string]
   [:iss :string]
   [:aud :string]
   [:iat :int]
   [:exp :int]
   [:azp :string]
   [:https://ajanottaja.app/sub :uuid]])


(defn fetch-jwks!
  "Performs http request to given url to fetch jwks contents.
   Retries the request for a maximum of three requests if it fails."
  [url]
  (diehard/with-retry
    {:max-retries       3
     :backoff-ms [250 1500]
     :retry-if          (fn [val ex] (or ex (f/failed? val)))
     :on-retry          (fn [val ex] (log/info "Retrying JWKS request"))
     :on-failure        (fn [_ _] (log/info "Failed to fetch JWKS"))
     :on-failed-attempt (fn [_ _] (log/info "JWKS Request failed"))
     :on-success        (fn [_] (log/info "Successfully fetched JWKS"))}
    (let [{:keys [status body error]} @(client/get url)]
      (log/info status)
      (cond
        error (f/fail "JWKS request error" {:error error})
        (<= 200 status 299) body
        :else (f/fail "JWKS request error" {:error status})))))

(m/=> fetch-jwks! [:=> [:cat string?]
                   :string])


(defn decode-jwks!
  "Decodes jwks and extracts keys array if valid json,
   otherwise returns failure."
  [body]
  (f/ok->> body
           (muuntaja/decode "application/json")
           :keys))

(m/=> decode-jwks!
      [:=> [:cat string?]
       [:sequential jwk?]])


(defn public-key-by-id!
  "Fetch public key by url and kid. Throws exception if it fails
   to get the key. Otherwise returns key."
  [url {:keys [kid]}]
  (f/if-let-ok? [key (f/ok->> url
                              fetch-jwks!
                              decode-jwks!
                              (filter #(= (:kid %) kid))
                              first
                              buddy-keys/jwk->public-key)]
                key
                (throw key)))

(m/=> public-key-by-id!
      [:=> [:cat :string [:map [:kid :string]]]
       public-key?])

(def public-key-by-id-cached!
  "Memoized form of key fetching function, with ttl of 1 hour.
   Cache key is the combination of url and key id. If we get a new key id
   from auth0 we want to refetch from the url, so this is as intended.
   Only caches successful key fetches."
  (memoize/ttl public-key-by-id! :ttl/threshold (* 1000 3600)))

(defn validate-token!
  "Validates a token given a jwks url. Uses the cached public key
   function as a key resolver for buddy's unsign function. If key
   fetching or unsigning fails we return a failure."
  [url token]
  (f/if-let-ok? [claims (jwt/unsign token
                                    (partial public-key-by-id-cached! url)
                                    {:alg :rs256})]
                (do (log/info claims "Succesfully validated token")
                    (m/decode claims? claims schemas/strict-json-transformer))))


;; Rich comments
(comment
  (malli.core/explain claims? (m/decode claims?
                                         {:https://ajanottaja.snorre.io/sub "sXpy83miL-qhhYhsnzV5p"}
                                         schemas/strict-json-transformer))
  (m/decode claims?
            {:https://ajanottaja.snorre.io/sub "sXpy83miL-qhhYhsnzV5p"}
            schemas/strict-json-transformer)
  (malli.error/humanize (m/explain :uuid (java.util.UUID/randomUUID)))

  ;; Generate clj-kondo malli specs for namespace
  (require '[malli.clj-kondo :as mc])
  (-> (mc/collect *ns*) (mc/linter-config))
  (mc/emit!)

  ;; Validate and generating
  (malli/validate jwk? example-jwk)
  (require '[malli.generator :as mg])
  (mg/generate jwk?)


  (require '[malli.provider :as provider])
  ;; Use http-kit client to get the jwks response from Auth0
  @(client/get "https://aajanottaja.eu.auth0.com/.well-known/jwks.json")

  (->> @(client/get "https://ajanottaja.eu.auth0.com/.well-known/jwks.json")
       :body
       (muuntaja/decode "application/json")
       :keys
       provider/provide)
  
  (cheshire.core/parse-string
   "{\"https://ajanottaja.snorre.io\": \"test\"}"
   true)

  (fetch-jwks! "https://dalkklajsdjldkjlkdajkladsl.com/.well-known/jwks.jsons")

  (validate-token! "https://ajanottaja.eu.auth0.com/.well-known/jwks.json"
                   "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCIsImtpZCI6ImtaWnYzajBXNE9KQ21tSk5qQXVkbSJ9.eyJodHRwczovL2FqYW5vdHRhamEuc25vcnJlLmlvL3N1YiI6IjVkOTA4NTZjLTVhNDgtNDZhZS04ZjZlLWEwNjI5NWVhMGRmYiIsImlzcyI6Imh0dHBzOi8vYWphbm90dGFqYS5ldS5hdXRoMC5jb20vIiwic3ViIjoiYXV0aDB8NjA2NDc5ZTJmMjhkZTUwMDY5MTYxYzBkIiwiYXVkIjoiaHR0cHM6Ly9hamFub3R0YWphLnNub3JyZS5pbyIsImlhdCI6MTYxODc0NzI1NywiZXhwIjoxNjE4NzU0NDU3LCJhenAiOiI1Q3hyTlZjUEJtWnlLZk9ndlNiWlQwTFNZdUpKMU9vbSIsInNjb3BlIjoiIn0.aqodPCmQ7uPZq8nJeoRnjO2O_sfRpEkeXqw__DZ-V8su3yG9jMtqeCLaSS_B1g9GtaJSZwIQkmm9g4kj7OvQCUuzIOeeLy0NxlV05_BT2T1DXM2q711D68CBTD6fM88gdaCWBmFqwOuyjMSYAhNh9bRgV4kg6GMGkl68-phYsKTkP70agSyHU3gqRKN_0kg1CKc3S2jHe4hPJYlB-5dEEeC_ejzQnfUyIdmdc3jAbmH6AoqN5jTwsLJIbYzj-GUYoQvhtZ9nWoW70kK-IMa0eXDC6E4y2oB7BjMMa83h3t-RrlAnuBIBj80PknQHouXyp9qwdF85780efCWLErPcOw")

  (validate-token! "https://ajanottaja.eu.auth0.com/.well-known/jwks.json" "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCIsImtpZCI6ImtaWnYzajBXNE9KQ21tSk5qQXVkbSJ9.eyJodHRwczovL2FqYW5vdHRhamEuc25vcnJlLmlvL3N1YiI6IjVkOTA4NTZjLTVhNDgtNDZhZS04ZjZlLWEwNjI5NWVhMGRmYiIsImlzcyI6Imh0dHBzOi8vYWphbm90dGFqYS5ldS5hdXRoMC5jb20vIiwic3ViIjoiYXV0aDB8NjA2NDc5ZTJmMjhkZTUwMDY5MTYxYzBkIiwiYXVkIjoiaHR0cHM6Ly9hamFub3R0YWphLnNub3JyZS5pbyIsImlhdCI6MTYxOTk4MTg3MywiZXhwIjoxNjE5OTg5MDczLCJhenAiOiI1Q3hyTlZjUEJtWnlLZk9ndlNiWlQwTFNZdUpKMU9vbSIsInNjb3BlIjoiIn0.jYFHEw5nJqrtggy-X5uCGD3RasMmduUBP-DOpELqihMJwn0vGrR7U0TSQx_Vn2krHuWKkaLsu5B_QpGtLW6KJFGLS0cUNR0zYkua3aEN4HLJ9DdZnrVRnu6v4uvd1PQnW-ZunkUVDD7W9nwrotyIm0Y_GsBB5PQtWGu_R2AI7aZvmSMwxVj2Wf-Iiq7paKruVzX4j32l8wVA2SqPiHT9Lzr7TIK80-3ROwNnOyAFSi8rZqWBUtFENTZR1eQI8Fe_dA0gQeAq8weFOmrLPN6NhxZpCfw81VXW7Rhl1kjC_dONN9XqP3blSZlfqQK3BCtzOeAe60XsmjyBPkqZPfFrkQ"
                   )
  (->> (public-key-by-id! "https://ajanottaja.eu.auth0.com/.well-known/jwks.json" "sXpy83miL-qhhYhsnzV5p")

       (malli/validate public-key?))



  (public-key-by-id-cached! "https://ajanottaja.eu.auth0.com/.well-known/jwks.json" "sXpy83miL-qhhYhsnzV5p")



  (buddy-keys/public-key? :foo)

  (fetch-jwks! 10)

  (malli/validate retry-opts?
                  {:timeout 1000
                   :retries 200})
  
  (m/decode :uuid "026fccaf-29ff-49fb-acaa-d81c5f4b07d2" schemas/strict-json-transformer)
  )
