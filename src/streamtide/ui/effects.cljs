(ns streamtide.ui.effects
  "Streamtide common effects"
  (:require [cljs-web3-next.personal :as web3-personal]
            [re-frame.core :as re]))

(re/reg-fx
  :web3/personal-sign
  ; sign a message using the connected wallet
  (fn [{:keys [web3 data-str from on-success on-error]}]
    (web3-personal/sign web3 data-str from (fn [err result]
                                             (if err
                                               (re/dispatch (conj on-error err))
                                               (re/dispatch (conj on-success result)))))))

(re/reg-fx
  :callback
  ; executes a callback function
  (fn [{:keys [:fn :error :result]}]
    (fn error result)))
