(ns streamtide.ui.oauth-callback.events
  (:require
    [clojure.string :as string]
    [streamtide.shared.utils :as utils]
    [re-frame.core :as re-frame]))

(re-frame/reg-event-fx
  ::broadcast-oauth-verifier
  ; Broadcast the verifier to the main window to continue the validation process
  (fn [{:keys [db]} [_ {:keys [:code :state :error] :as data}]]
    (let [network (utils/uuid->network state)
          channel (js/BroadcastChannel. (str "verifier_" (name network)))]
      (.postMessage channel (clj->js data))
      (.close channel)
      {:db (assoc db :auth-complete? true)
       :close-window {}  ;; Note that closing window does not work in all browser
       })))

(re-frame/reg-fx
  :close-window
  ; Close the current browser window
  (fn []
    (js/window.close)))
