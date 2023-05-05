(ns streamtide.ui.subs
  "Streamtide common subs"
  (:require [district.ui.web3-accounts.subs :as accounts-subs]
            [re-frame.core :as re-frame]))

(re-frame/reg-sub
  ::day-night-switch
  (fn [db _]
    (if (:day-night-switch db)
      "day"
      "night")))

(re-frame/reg-sub
  ::active-session
  (fn [db _]
    (get db :active-session)))


(re-frame/reg-sub
  ::active-account-has-session?
  :<- [::active-session]
  :<- [::accounts-subs/active-account]
  (fn [[active-session active-account]]
    (and (some? (:user/address active-session))
         (= (:user/address active-session) active-account))))

(re-frame/reg-sub
  ::cart
  (fn [db _]
    (get db :cart)))