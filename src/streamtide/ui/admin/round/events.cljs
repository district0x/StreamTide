(ns streamtide.ui.admin.round.events
  (:require
    [re-frame.core :as re-frame]))

(def interceptors [re-frame/trim-v])

(re-frame/reg-event-fx
  ::set-multiplier
  ; Sets the multiplier factor for a receiver
  [interceptors (re-frame/inject-cofx :store)]
  (fn [{:keys [db store]} [{:keys [:id :factor]}]]
    {:store (assoc-in store [:multipliers id] factor)
     :db (assoc-in db [:multipliers id] factor)}))

(re-frame/reg-event-fx
  ::enable-donation
  ; Enables or disables a donation for a matching round
  [interceptors (re-frame/inject-cofx :store)]
  (fn [{:keys [db store]} [{:keys [:id :enabled?]}]]
    {:store (assoc-in store [:donations id] enabled?)
     :db (assoc-in db [:donations id] enabled?)}))
