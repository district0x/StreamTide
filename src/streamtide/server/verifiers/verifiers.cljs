(ns streamtide.server.verifiers.verifiers)

(defmulti verify
          "Verify social network authentication"
          (fn [network _args]
            network))

(defmethod verify :default [network _]
  (js/Error. (str "Network not supported: " network)))
