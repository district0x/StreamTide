(ns streamtide.server.graphql.authorization
  "Graphql utils for handling authentication. Takes signed data and generates a JWT"
  (:require [cljs.nodejs :as nodejs]
            [cljs-web3-next.core :as web3-next]
            [eip55.core :as eip55]
            [goog.string :as gstring]
            [streamtide.shared.utils :as shared-utils]
            [taoensso.timbre :as log]))

(defonce JsonWebToken (nodejs/require "jsonwebtoken"))
(defonce EthSigUtil (nodejs/require "@metamask/eth-sig-util"))

(def otp-length 20)
(def otp-time-step 120)   ; OTP validity: 2 minutes

(def signed-data-regex (re-pattern (gstring/format shared-utils/auth-data-msg (str "([a-zA-Z0-9_]{" otp-length "})"))))

(defn recover-personal-signature [data data-signature]
  "From a piece of data and its signature, gets the ETH account which signed it"
  (-> (js-invoke EthSigUtil "recoverPersonalSignature" #js {:data data :signature data-signature})
      eip55/address->checksum))

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

(defn validate-data [data secret user-address]
  "Check that the signed data follows the specific format and OTP is recent and valid"
  (when-not (valid-otp? (last (re-matches signed-data-regex data)) secret user-address)
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
