(ns streamtide.server.verifiers.verifiers
  (:require
    [district.shared.async-helpers :refer [<? safe-go]]))

(defmulti verify
          "Verify social network authentication"
          (fn [network _args]
            network))

(defmethod verify :default [network _]
  (safe-go
    (throw (js/Error. (str "Network not supported: " network)))))
