(ns streamtide.ui.send-support.events
  (:require
    [re-frame.core :as re-frame]
    [streamtide.ui.events :as st-events]))

(re-frame/reg-event-fx
  ::send-support
  (fn [{:keys [db]} [_ {:keys [:form-data] :as data}]]
    (print form-data)
    ;; TODO send tx
    {:dispatch [::st-events/clean-cart]}))