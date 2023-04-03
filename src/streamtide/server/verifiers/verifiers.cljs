(ns streamtide.server.verifiers.verifiers
  (:require
    [streamtide.server.verifiers.discord-verifier :as discord]
    [streamtide.server.verifiers.twitter-verifier :as twitter]))

(defmulti verify
          "Verify social network authentication"
          (fn [network _args]
            network))

(defmethod verify :default [network _]
  (js/Error. (str "Network not supported: " network)))


(defmethod verify :twitter [_ args]
  (twitter/verify-oauth-verifier args))

(defmethod verify :discord [_ args]
  (discord/verify-oauth-verifier args))

