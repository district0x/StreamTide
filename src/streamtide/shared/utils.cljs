(ns streamtide.shared.utils
  (:require-macros [streamtide.shared.utils]))

(defn now []
  (.getTime (js/Date.)))
