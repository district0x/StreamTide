(ns streamtide.server.graphql.authorization
  "Graphql utils for handling authentication. Takes signed data and generates a JWT"
  (:require [cljs.nodejs :as nodejs]
            [district.shared.error-handling :refer [try-catch]]
            [eip55.core :as eip55]
            [taoensso.timbre :as log]))

(defonce JsonWebToken (nodejs/require "jsonwebtoken"))
(defonce EthSigUtil (nodejs/require "@metamask/eth-sig-util"))

(defn recover-personal-signature [data data-signature]
  "From a piece of data and its signature, gets the ETH account which signed it"
  (-> (js-invoke EthSigUtil "recoverPersonalSignature" #js {:data data :signature data-signature})
      eip55/address->checksum))

(defn create-jwt [address secret]
  "Generates a JSON Web Token (JWT) including an ETH Address"
  (js-invoke JsonWebToken "sign" #js {:userAddress address} secret))

(defn parse-jwt [token secret]
  "Verify a JSON Web Token (JWT) is valid"
  (js-invoke JsonWebToken "verify" token secret))

(defn token->user [access-token sign-in-secret]
  "Verify a JWT Token and gets the included ETH address"
  (try-catch
    (cond
      (nil? access-token)
      (log/info "No access-token header present in request")

      access-token
      {:user/address (aget (parse-jwt access-token sign-in-secret) "userAddress")}

      :else (log/info "Invalid access-token header"))))
