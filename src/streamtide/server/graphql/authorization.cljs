(ns streamtide.server.graphql.authorization
  "Graphql utils for handling authentication. Takes signed data and generates a JWT"
  (:require [camel-snake-kebab.core :as csk]
            [camel-snake-kebab.extras :refer [transform-keys]]
            [cljs.nodejs :as nodejs]
            [cljs-web3-next.core :as web3-next]
            [district.server.config :as config]
            [district.shared.async-helpers :refer [safe-go <?]]
            [streamtide.shared.utils :as shared-utils]
            [taoensso.timbre :as log]))

(defonce JsonWebToken (nodejs/require "jsonwebtoken"))
(defonce viem (nodejs/require "viem"))
(defonce siwe (nodejs/require "siwe"))
(def otp-length 20)
(def otp-time-step 120)   ; OTP validity: 2 minutes

(defn validate-signature [payload signature]
  "Validates the given address has signed the given piece of data"
  (safe-go
    (let [siwe-message (new (.-SiweMessage siwe) (clj->js (transform-keys csk/->camelCase payload)))
          message (.toMessage siwe-message)
          public-client (.createPublicClient viem #js {:transport (new (.-http viem) (-> @config/config :siwe :url))})
          isValid (<? (.verifyMessage public-client #js {:address (:address payload)
                                                         :message message
                                                         :signature signature}))]
      (when-not isValid (throw (js/Error. "Invalid signature"))))))

(defn generate-otp
  "Generates a One-Time Password (OTP) for authenticating the user"
  ([secret user-address]
   (generate-otp secret user-address (long (/ (shared-utils/now-secs) otp-time-step))))

  ([secret user-address epoch]
   (let [otp (web3-next/sha3 (str secret user-address epoch))]
    (subs (str otp) (- (count otp) otp-length)))))

(defn valid-otp? [otp secret user-address]
  (and (some? otp)
       (let [epoch (long (/ (shared-utils/now-secs) otp-time-step))]
         (or
           (= otp (generate-otp secret user-address epoch))
           (= otp (generate-otp secret user-address (dec epoch)))))))

(defn validate-payload [payload secret]
  "Check that the signed data follows the specific format and OTP is recent and valid"
  (when (or (not (valid-otp? (:nonce payload) secret (:address payload)))
            (not= (:domain payload) (-> @config/config :siwe-payload :domain)))
    (throw "Invalid signed data")))

(defn create-jwt [address secret expires-in]
  "Generates a JSON Web Token (JWT) including an ETH Address"
  (let [opts (cond-> {}
                     expires-in (assoc :expiresIn expires-in)
                     true clj->js)]
    (js-invoke JsonWebToken "sign" #js {:userAddress address} secret opts)))

(defn parse-jwt [token secret]
  "Verify a JSON Web Token (JWT) is valid"
  (js-invoke JsonWebToken "verify" token secret))

(defn token->user [access-token sign-in-secret]
  "Verify a JWT Token and gets the included ETH address"
  (try
    (cond
      (nil? access-token)
      {:error "No access-token header present in request"}

      access-token
      {:user/address (aget (parse-jwt access-token sign-in-secret) "userAddress")}

      :else {:error "Invalid access-token header"})
    (catch :default e
      (log/info "Failed to verify JWT" {:error e})
      {:error "Failed to verify JWT"})))
