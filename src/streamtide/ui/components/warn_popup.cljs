(ns streamtide.ui.components.warn-popup
  "This aims to be a generic component to show a warning before proceeding with an operation"
  (:require [reagent.core :as r]
            [re-frame.core :refer [dispatch subscribe] :as re-frame]))


(def show-popup? (r/atom false))
(def on-accept-atom (r/atom nil))

(defn warn-popup [{:keys [:title :content :button-label] :or {title "Warning"
                                                              button-label "Accept"}}]
  "Add this component to a page so the warning can be shown"
  (let [close-popup (fn [e]
                      (when e
                        (.stopPropagation e))
                      (reset! show-popup? false))]
    (reset! show-popup? false)
    (fn []
      [:div {:id "popUpWarn" :style {"display" (if @show-popup? "flex" "none")}}
       [:div.bgPopUp {:on-click #(close-popup %)}]
       [:div.popUpWarn
        [:button.btClose {:on-click #(close-popup %)} "Close" ]
        [:div.content
         [:h3 title]
         [:p content]
         [:button.btBasic.btBasic-light.btAccept {:on-click (fn [e]
                                                (@on-accept-atom)
                                                (close-popup e))} button-label ]]]])))

(re-frame/reg-event-fx
  ::show-popup
  ; show popup to confirm operation
  [re-frame/trim-v]
  (fn [{:keys [db]} [{:keys [:on-accept :check]}]]
    (if (or (nil? check)
            (check))
      (do
        (reset! on-accept-atom #(dispatch on-accept))
        (reset! show-popup? true)
        {})
      (dispatch on-accept))))
