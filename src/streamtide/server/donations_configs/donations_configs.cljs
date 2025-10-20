(ns streamtide.server.donations-configs.donations-configs
  (:require
    [district.shared.async-helpers :refer [<? safe-go]]))

(defmulti verify
          "Verify donation config"
          (fn [donation-type _args]
            donation-type))

(defmethod verify :default [donation-type _]
  (safe-go
    (throw (js/Error. (str "Donation config type not supported: " donation-type)))))
