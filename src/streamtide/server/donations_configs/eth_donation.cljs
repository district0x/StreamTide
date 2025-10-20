(ns streamtide.server.donations-configs.eth-donation
  (:require [streamtide.server.donations-configs.donations-configs :as donations-configs]
            [district.shared.async-helpers :refer [<? safe-go]]))

(defmethod donations-configs/verify :eth [_ _]
  (safe-go) ; nothing to verify
  )
