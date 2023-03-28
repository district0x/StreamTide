(ns streamtide.ui.oauth-callback.subs
  (:require
    [re-frame.core :as re-frame]))


(re-frame/reg-sub
  ::auth-complete?
  (fn [db [_]]
    (get db :auth-complete?)))
