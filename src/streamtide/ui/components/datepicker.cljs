(ns streamtide.ui.components.datepicker
  "Page to manage a specific campaign"
  (:require
    ["react-datepicker" :default DatePicker]
    ["react-datepicker/dist/react-datepicker.css"]
    [district.ui.component.form.input :refer [assoc-by-path get-by-path]]
    [reagent.core :as r]))


(defn date-picker [{:keys [:form-data :id :on-change :class :initial-value]}]
  (r/create-element
    DatePicker
    #js {:showIcon true
         :isClearable true
         :className class
         :selected (let [value (get-by-path @form-data id)]
                     (if (= "" value) nil
                       (or value initial-value)))
         :onChange (fn [new-date]
                     (let [new-date (if (nil? new-date) "" new-date)]
                       (swap! form-data (fn [current-value]
                                          (with-meta (assoc-by-path current-value id new-date)
                                                     (assoc (meta current-value) :touched? true))))
                       (when on-change (on-change new-date))))}))
