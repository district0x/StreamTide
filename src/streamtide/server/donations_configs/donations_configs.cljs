(ns streamtide.server.donations-configs.donations-configs
  (:require
    [district.shared.async-helpers :refer [<? safe-go]]))

(defmulti verify
          "Verify donation config"
          (fn [donation-type _coin-address]
            donation-type))

(defmulti parse-call-data
          "Parse donation event call-data"
          (fn [donation-type {:keys [:target :call-data :amount :gained :token] :as _args}]
            donation-type))

(defmethod verify :default [donation-type _]
  (safe-go
    (throw (js/Error. (str "Donation config type not supported: " donation-type)))))

(defmethod parse-call-data :default [donation-type _]
  (safe-go
    (throw (js/Error. (str "Donation config type not supported: " donation-type)))))
