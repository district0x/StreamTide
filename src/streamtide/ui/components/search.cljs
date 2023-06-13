(ns streamtide.ui.components.search
  (:require
    [clojure.string :as str]
    [district.ui.component.form.input :refer [text-input select-input]]
    [reagent.core :as r]))

(defn search-tools [{:keys [:form-data :search-id]} & others]
  "display a search form"
  (let [search-input-form-data (r/atom {search-id (get @form-data search-id)})]
    (fn [{:keys [:form-data :search-id :select-options :search-result-count
                 :on-search-change :on-select-change]} & others]
      [:div.form.formFilter.simpleForm
       [:div.searchForm
        ;; TODO check if works on mobile. Check if we rather use :input or text-input from district-ui-component-form
        [text-input {:on-key-down (fn [e]
                                    (let [key-code (-> e .-keyCode)
                                          input (get @search-input-form-data search-id)]
                                      (cond
                                        (= key-code 13) ;; return key
                                        (do
                                          (swap! form-data assoc search-id input)
                                          (when on-search-change (on-search-change input))))))
                     :on-key-up #(let [input (get @search-input-form-data search-id)]
                                    (when (str/blank? input)
                                      (swap! form-data assoc search-id input)))
                     :placeholder "SEARCH BY:"
                     :form-data search-input-form-data
                     :id search-id
                     :class "inputField"
                     :type "search"}]
        [:input.btSearchField
         {:type "submit" :value "Search"
          :on-click #(let [input (get @search-input-form-data search-id)]
                        (swap! form-data assoc search-id input)
                        (when on-search-change (on-search-change input)))}]]
       [:div.custom-select.selectForm.inputField
        ;; TODO use custom select for prettifying
        [select-input {:form-data form-data
                       :id :order-key
                       :group-class :options
                       :value js/undefined
                       :options select-options
                       :on-change on-select-change}]]
       (map-indexed (fn [index item]
                      (with-meta item {:key (keyword "c" index)}))
                    others)])))
