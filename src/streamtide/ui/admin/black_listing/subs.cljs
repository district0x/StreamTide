(ns streamtide.ui.admin.black-listing.subs
  (:require
    [re-frame.core :as re-frame]))

  (re-frame/reg-sub
    ::blacklisting?
    (fn [db [_ address]]
    (get-in db [:blacklisting address])))
