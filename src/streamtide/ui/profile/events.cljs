(ns streamtide.ui.profile.events
  (:require
    [district.ui.router.events :as router-events]
    [re-frame.core :as re-frame]
    [streamtide.ui.events :as st-events]))


(re-frame/reg-event-fx
  ::add-to-cart
  ; Adds the user to the cart and redirect to the cart page
  (fn [{:keys [db]} [_ {:keys [:user/address] :as data}]]
    {:dispatch-n [[::st-events/add-to-cart data]
                  [::router-events/navigate :route.send-support/index]]}))
